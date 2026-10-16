/**
 *	Copyright 2026 Tony Bringardner
 *
 *	Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. 
 *	You may obtain a copy of the License at
 *
 *	http://www.apache.org/licenses/LICENSE-2.0
 *
 *	Unless required by applicable law or agreed to in writing, software distributed under the License is distributed 
 *	on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for 
 *	the specific language governing permissions and limitations under the License.
 */
package us.bringardner.fsh.ide.core;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import us.bringardner.fsh.loupe.FshAdapterMain;
import us.bringardner.parley.dap.json.JsonException;
import us.bringardner.parley.loupe.LoupeConnection;
import us.bringardner.parley.loupe.LoupeEndpoint;
import us.bringardner.parley.loupe.LoupeException;
import us.bringardner.parley.loupe.protocol.AddBreakpointParams;
import us.bringardner.parley.loupe.protocol.BreakpointStatusEvent;
import us.bringardner.parley.loupe.protocol.ChildrenParams;
import us.bringardner.parley.loupe.protocol.EvaluateParams;
import us.bringardner.parley.loupe.protocol.EvaluateResult;
import us.bringardner.parley.loupe.protocol.Frame;
import us.bringardner.parley.loupe.protocol.Hello;
import us.bringardner.parley.loupe.protocol.LaunchParams;
import us.bringardner.parley.loupe.protocol.Location;
import us.bringardner.parley.loupe.protocol.Loupe;
import us.bringardner.parley.loupe.protocol.OutputEvent;
import us.bringardner.parley.loupe.protocol.RemoveBreakpointParams;
import us.bringardner.parley.loupe.protocol.Scope;
import us.bringardner.parley.loupe.protocol.ScopesResult;
import us.bringardner.parley.loupe.protocol.SetVariableParams;
import us.bringardner.parley.loupe.protocol.Snapshot;
import us.bringardner.parley.loupe.protocol.Source;
import us.bringardner.parley.loupe.protocol.StepKind;
import us.bringardner.parley.loupe.protocol.StepParams;
import us.bringardner.parley.loupe.protocol.TargetState;
import us.bringardner.parley.loupe.protocol.TargetStateEvent;
import us.bringardner.parley.loupe.protocol.Value;
import us.bringardner.parley.loupe.protocol.Variable;
import us.bringardner.parley.loupe.protocol.VariablesResult;

/**
 * The IDE's side of a Loupe debugging session: runs a script on an adapter (an fsh-loupe
 * adapter in this JVM, a program started for the purpose, or one listening on a socket), keeps
 * the adapter's breakpoints those of the editor, and tells a {@link Listener} where the
 * script stopped, what its variables are and what it wrote.
 * <p>
 * It is the counterpart of {@link ScriptRun} with {@link DebugSession} (which debug the script
 * in this JVM through fsh's hooks) for a debuggee that is somewhere else. What it can't do
 * that those can: log every statement, give the script the IDE's standard input or redirect
 * its streams, and show the variables as the script left them at its end.
 * <p>
 * Lines given to and by this class are the editor's (0-based); Loupe's are 1-based. The
 * listener is called on a thread of this class's, one call at a time, in the order of events.
 */
public final class LoupeSession implements Closeable {

	public interface Listener {
		/**
		 * The script stopped before the statement on line.
		 * @param reason breakpoint, step, pause, exception or entry (the entry stop is not
		 *        reported: breakpoints are sent and the script runs on)
		 * @param variables the shell's variables, by name (strings: a value with parts is its
		 *        summary)
		 */
		void paused(int line, String reason, Map<String,Object> variables);

		/** Text the script wrote; category is stdout, stderr or console (the adapter's remarks). */
		void output(String category, String text);

		/**
		 * The adapter placed (or couldn't place) a breakpoint.
		 * @param status verified or rejected
		 * @param line where it is (the editor's line), or -1
		 */
		void breakpoint(Breakpoint bp, String status, int line, String message);

		/** The script ended (killed: it was terminated); or the session failed (error). */
		void finished(int exitCode, boolean killed, Exception error);
	}

	private static final String TARGET = "t1";

	private final Listener listener;
	private final LoupeConnection connection;
	private final Closeable[] toClose;
	private final ExecutorService events = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "Loupe session events");
		t.setDaemon(true);
		return t;
	});

	// breakpoints as the adapter has them: Loupe id -> the editor's breakpoint and what was sent
	private final Map<String,Breakpoint> sent = new HashMap<>();
	private final Map<String,String> sentSignature = new HashMap<>();
	private final Object lock = new Object();
	private String name = "script.fsh";
	private volatile boolean started;
	private volatile boolean finished;
	// while stopped: the frame and variables scope the variables came from
	private volatile String variablesScope;
	private volatile boolean paused;

	private LoupeSession(Listener listener, InputStream in, OutputStream out, Closeable... toClose) {
		this.listener = listener;
		this.toClose = toClose;
		this.connection = LoupeConnection.open(new LoupeEndpoint.Handler() {
			@Override
			public CompletableFuture<Object> request(String method, String target, Object params) {
				throw new LoupeException(LoupeException.UNKNOWN_METHOD, method);
			}

			@Override
			public void event(String event, String target, Object params) {
				try {
					events.execute(() -> onEvent(event, params));
				} catch (RuntimeException e) {
					// closed
				}
			}
		}, in, out);
		this.connection.closed().thenRun(() -> {
			if( !finished ) {
				finish(-1, false, new IOException("The adapter's connection ended"));
			}
		});
	}

	/** A session with the fsh-loupe adapter in this JVM (over a loopback connection). */
	public static LoupeSession inProcess(Listener listener) throws IOException {
		ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
		Socket client = null;
		Socket accepted = null;
		try {
			client = new Socket(InetAddress.getLoopbackAddress(), server.getLocalPort());
			accepted = server.accept();
		} catch (IOException e) {
			server.close();
			throw e;
		}
		server.close();
		LoupeConnection adapter = LoupeConnection.serve(FshAdapterMain::handler, accepted.getInputStream(), accepted.getOutputStream());
		Socket a = accepted;
		Socket c = client;
		return new LoupeSession(listener, c.getInputStream(), c.getOutputStream(), adapter, a, c);
	}

	/** A session with an adapter listening on host:port. */
	public static LoupeSession connect(String host, int port, Listener listener) throws IOException {
		Socket s = new Socket(host, port);
		return new LoupeSession(listener, s.getInputStream(), s.getOutputStream(), s);
	}

	/** A session with an adapter the command starts, which speaks Loupe on its standard streams. */
	public static LoupeSession exec(List<String> command, Listener listener) throws IOException {
		Process p = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.INHERIT).start();
		return new LoupeSession(listener, p.getInputStream(), p.getOutputStream(), p::destroy);
	}

	// ---- starting

	/**
	 * Says hello, starts the script stopped at its first command, sends the breakpoints and lets
	 * it run. The script's stops and its end come to the listener.
	 * @param code the script
	 * @param scriptName what to call it ($0; the breakpoints' source)
	 * @param arguments its arguments
	 * @param breakpoints the editor's, by line
	 */
	public void start(String code, String scriptName, List<String> arguments, Collection<Breakpoint> breakpoints) throws Exception {
		if( started ) {
			throw new IllegalStateException("Already started");
		}
		started = true;
		name = scriptName == null || scriptName.isEmpty() ? "script.fsh" : scriptName;
		request(Loupe.HELLO, null, new Hello().setProtocols(List.of(Loupe.PROTOCOL)).setFeatures(List.of(Loupe.FEATURE_SNAPSHOT)).toJson());
		Map<String,Object> args = new LinkedHashMap<>();
		args.put("source", code);
		args.put("name", name);
		args.put("args", arguments);
		request(Loupe.LAUNCH, null, new LaunchParams().setTarget(TARGET).setArgs(args).setStartPaused(true).toJson());
		syncBreakpoints(breakpoints);
		request(Loupe.RESUME, TARGET, Map.of());
	}

	private Object request(String method, String target, Object params) throws Exception {
		try {
			return connection.getEndpoint().request(method, target, params).get(30, TimeUnit.SECONDS);
		} catch (java.util.concurrent.ExecutionException e) {
			Throwable c = e.getCause();
			throw c instanceof Exception ? (Exception) c : e;
		}
	}

	private CompletableFuture<Void> send(String method, Object params) {
		return connection.getEndpoint().request(method, TARGET, params).thenApply(r -> null);
	}

	// ---- breakpoints

	private static String loupeId(Breakpoint bp) {
		return "b"+bp.getId();
	}

	private static String signature(Breakpoint bp) {
		return bp.getLine()+"|"+(bp.isConditional() ? bp.getCondition() : "")+"|"+(bp.isHitCount() ? bp.getHitCount() : -1);
	}

	/**
	 * Makes the adapter's breakpoints the editor's (the enabled ones): adds, replaces and removes
	 * as needed. Call it when breakpoints are added, removed, moved or changed.
	 */
	public void syncBreakpoints(Collection<Breakpoint> breakpoints) throws Exception {
		Map<String,Breakpoint> wanted = new LinkedHashMap<>();
		for(Breakpoint bp : breakpoints) {
			if( bp.isEnabled(false) && bp.getLine() >= 0 ) {
				wanted.put(loupeId(bp), bp);
			}
		}
		List<String> remove = new ArrayList<>();
		Map<String,Breakpoint> add = new LinkedHashMap<>();
		synchronized (lock) {
			for(String id : new ArrayList<>(sent.keySet())) {
				if( !wanted.containsKey(id)) {
					remove.add(id);
					sent.remove(id);
					sentSignature.remove(id);
				}
			}
			for(Map.Entry<String,Breakpoint> e : wanted.entrySet()) {
				String sig = signature(e.getValue());
				if( !sig.equals(sentSignature.get(e.getKey()))) {
					add.put(e.getKey(), e.getValue());
					sent.put(e.getKey(), e.getValue());
					sentSignature.put(e.getKey(), sig);
				}
			}
		}
		for(String id : remove) {
			request(Loupe.REMOVE_BREAKPOINT, TARGET, new RemoveBreakpointParams().setId(id).toJson());
		}
		for(Map.Entry<String,Breakpoint> e : add.entrySet()) {
			Breakpoint bp = e.getValue();
			AddBreakpointParams p = new AddBreakpointParams().setId(e.getKey())
					.setWhere(new Location().setSource(new Source().setPath(name)).setLine(bp.getLine()+1));
			if( bp.isConditional() && bp.getCondition() != null && !bp.getCondition().isBlank()) {
				p.setCondition(bp.getCondition());
			}
			if( bp.isHitCount() && bp.getHitCount() > 0 ) {
				p.setHitCount(bp.getHitCount());
			}
			request(Loupe.ADD_BREAKPOINT, TARGET, p.toJson());
		}
	}

	// ---- control (each answers when the adapter has accepted it)

	public CompletableFuture<Void> resume() {
		return send(Loupe.RESUME, Map.of());
	}

	public CompletableFuture<Void> stepOver() {
		return send(Loupe.STEP, new StepParams().setKind(StepKind.OVER).toJson());
	}

	public CompletableFuture<Void> stepInto() {
		return send(Loupe.STEP, new StepParams().setKind(StepKind.IN).toJson());
	}

	public CompletableFuture<Void> stepOut() {
		return send(Loupe.STEP, new StepParams().setKind(StepKind.OUT).toJson());
	}

	/** Stops a running script at its next command. */
	public CompletableFuture<Void> suspend() {
		return send(Loupe.PAUSE, Map.of());
	}

	/** Ends the script. */
	public CompletableFuture<Void> terminate() {
		return send(Loupe.TERMINATE, Map.of());
	}

	/** True while the script is stopped (the variables can be asked about and changed). */
	public boolean isPaused() {
		return paused;
	}

	/**
	 * Sets a variable of the stopped script and returns the variables as they are then.
	 * @throws IllegalStateException if the script isn't stopped
	 */
	public Map<String,Object> setVariable(String variable, String value) throws Exception {
		String scope = variablesScope;
		if( !paused || scope == null ) {
			throw new IllegalStateException("The script isn't stopped");
		}
		request(Loupe.SET_VARIABLE, TARGET, new SetVariableParams().setId(scope).setName(variable).setValue(value).toJson());
		return readVariables(null);
	}

	/** The value of an expression (such as $x or ${a[1]}) in the stopped script's top frame. */
	public String evaluate(String expression) throws Exception {
		if( !paused ) {
			throw new IllegalStateException("The script isn't stopped");
		}
		EvaluateResult r = EvaluateResult.fromJson(request(Loupe.EVALUATE, TARGET,
				new EvaluateParams().setExpression(expression).setContext("repl").toJson()));
		return text(r.getValue());
	}

	// ---- events (on the session's thread)

	private void onEvent(String event, Object params) {
		try {
			if( Loupe.TARGET_STATE.equals(event)) {
				onState(TargetStateEvent.fromJson(params));
			} else if( Loupe.OUTPUT.equals(event)) {
				OutputEvent o = OutputEvent.fromJson(params);
				listener.output(o.getCategory() == null ? OutputEvent.STDOUT : o.getCategory(), o.getText());
			} else if( Loupe.BREAKPOINT_STATUS.equals(event)) {
				BreakpointStatusEvent b = BreakpointStatusEvent.fromJson(params);
				Breakpoint bp;
				synchronized (lock) {
					bp = sent.get(b.getId());
				}
				if( bp != null && !"removed".equals(b.getStatus())) {
					Integer line = b.getWhere() == null ? null : b.getWhere().getLine();
					listener.breakpoint(bp, b.getStatus(), line == null ? -1 : line-1, b.getMessage());
				}
			}
		} catch (JsonException | RuntimeException e) {
			// an event that can't be read is ignored
		}
	}

	private void onState(TargetStateEvent e) {
		if( TargetState.RUNNING.equals(e.getState())) {
			paused = false;
			variablesScope = null;
		} else if( TargetState.PAUSED.equals(e.getState())) {
			if( "entry".equals(e.getReason())) {
				return;
			}
			Snapshot snap = e.getSnapshot();
			int line = 0;
			Map<String,Object> vars;
			try {
				if( snap != null && snap.getFrames() != null && !snap.getFrames().isEmpty()) {
					Integer l = snap.getFrames().get(0).getLine();
					line = l == null ? 0 : l-1;
				} else {
					line = topLine();
				}
				vars = readVariables(snap);
			} catch (Exception ex) {
				finish(-1, false, ex);
				return;
			}
			paused = true;
			listener.paused(line, e.getReason(), vars);
		} else if( TargetState.TERMINATED.equals(e.getState())) {
			paused = false;
			finish(e.getExitCode() == null ? -1 : e.getExitCode(), "kill".equals(e.getReason()), null);
		}
	}

	private int topLine() throws Exception {
		Map<String,Object> r = asMap(request(Loupe.FRAMES, TARGET, Map.of("thread", 1, "start", 0, "count", 1)));
		List<?> frames = (List<?>) r.get("frames");
		Frame f = Frame.fromJson(frames.get(0));
		return f.getLine() == null ? 0 : f.getLine()-1;
	}

	@SuppressWarnings("unchecked")
	private static Map<String,Object> asMap(Object o) {
		return (Map<String,Object>) o;
	}

	/** The shell's variables and the environment of the top frame (the snapshot's, if there is one). */
	private Map<String,Object> readVariables(Snapshot snap) throws Exception {
		Map<String,Object> vars = new TreeMap<>();
		String frame;
		if( snap != null && snap.getFrames() != null && !snap.getFrames().isEmpty()) {
			frame = snap.getFrames().get(0).getId();
		} else {
			Map<String,Object> r = asMap(request(Loupe.FRAMES, TARGET, Map.of("thread", 1, "start", 0, "count", 1)));
			frame = Frame.fromJson(((List<?>) r.get("frames")).get(0)).getId();
		}
		ScopesResult scopes = ScopesResult.fromJson(request(Loupe.SCOPES, TARGET, Map.of("frame", frame)));
		for(Scope s : scopes.getScopes()) {
			if( "arguments".equals(s.getName())) {
				continue;
			}
			if( "variables".equals(s.getName())) {
				variablesScope = s.getId();
			}
			VariablesResult v = VariablesResult.fromJson(request(Loupe.CHILDREN, TARGET, new ChildrenParams().setId(s.getId()).setStart(0).toJson()));
			for(Variable x : v.getVariables()) {
				vars.put(x.getName(), text(x.getValue()));
			}
		}
		return vars;
	}

	private static String text(Value v) {
		if( v == null || Value.NULL.equals(v.getT())) {
			return "";
		}
		if( v.getV() != null ) {
			return String.valueOf(v.getV());
		}
		return v.getSummary() != null ? v.getSummary() : "";
	}

	private void finish(int exitCode, boolean killed, Exception error) {
		synchronized (lock) {
			if( finished ) {
				return;
			}
			finished = true;
		}
		paused = false;
		listener.finished(exitCode, killed, error);
	}

	/** Ends the session (and an adapter this class started). */
	@Override
	public void close() {
		finished = true;
		events.shutdownNow();
		connection.close();
		for(Closeable c : toClose) {
			try {
				c.close();
			} catch (IOException e) {
				// closing
			}
		}
	}
}
