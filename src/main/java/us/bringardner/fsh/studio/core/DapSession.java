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
package us.bringardner.fsh.studio.core;

import us.bringardner.fsh.debug.Breakpoint;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import us.bringardner.parley.dap.DapConnection;
import us.bringardner.parley.dap.protocol.AttachRequestArguments;
import us.bringardner.parley.dap.protocol.Capabilities;
import us.bringardner.parley.dap.protocol.ConfigurationDoneArguments;
import us.bringardner.parley.dap.protocol.ContinueArguments;
import us.bringardner.parley.dap.protocol.DisconnectArguments;
import us.bringardner.parley.dap.protocol.EvaluateArguments;
import us.bringardner.parley.dap.protocol.ExitedEventBody;
import us.bringardner.parley.dap.protocol.IDebugAdapter;
import us.bringardner.parley.dap.protocol.IDebugClient;
import us.bringardner.parley.dap.protocol.InitializeRequestArguments;
import us.bringardner.parley.dap.protocol.LaunchRequestArguments;
import us.bringardner.parley.dap.protocol.NextArguments;
import us.bringardner.parley.dap.protocol.OutputEventBody;
import us.bringardner.parley.dap.protocol.PauseArguments;
import us.bringardner.parley.dap.protocol.Scope;
import us.bringardner.parley.dap.protocol.ScopesArguments;
import us.bringardner.parley.dap.protocol.SetBreakpointsArguments;
import us.bringardner.parley.dap.protocol.SetBreakpointsResponseBody;
import us.bringardner.parley.dap.protocol.SetVariableArguments;
import us.bringardner.parley.dap.protocol.Source;
import us.bringardner.parley.dap.protocol.SourceArguments;
import us.bringardner.parley.dap.protocol.SourceBreakpoint;
import us.bringardner.parley.dap.protocol.StackFrame;
import us.bringardner.parley.dap.protocol.StackTraceArguments;
import us.bringardner.parley.dap.protocol.StepInArguments;
import us.bringardner.parley.dap.protocol.StepOutArguments;
import us.bringardner.parley.dap.protocol.StoppedEventBody;
import us.bringardner.parley.dap.protocol.TerminateArguments;
import us.bringardner.parley.dap.protocol.TerminatedEventBody;
import us.bringardner.parley.dap.protocol.Variable;
import us.bringardner.parley.dap.protocol.VariablesArguments;

/**
 * The IDE's side of a debugging session in the Debug Adapter Protocol (DAP): the counterpart of
 * {@link LoupeSession} for an adapter that speaks DAP on a socket. fsh-loupe's adapter does
 * (the port it listens on, and the port a script waits on, take either protocol); so do other
 * languages' adapters, though what is launched is said in fsh-loupe's terms ({@code source},
 * {@code script}, {@code args}).
 * <p>
 * What DAP gives less of than Loupe: a script's text only if the adapter answers DAP's
 * {@code source} request for a path (parley-loupe's bridge doesn't, so a script that isn't the
 * editor's is debugged without its text being shown, and {@link Started#getNote()} says so);
 * and values come as display text (a string in quotes is shown without them).
 */
public final class DapSession implements AdapterSession {

	private final Listener listener;
	private final DapConnection connection;
	private final IDebugAdapter adapter;
	private final Closeable[] toClose;
	private final ExecutorService events = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "DAP session events");
		t.setDaemon(true);
		return t;
	});

	private final Object lock = new Object();
	// guarded by lock: what setBreakpoints last sent (so the same list isn't sent again), and its breakpoints in order
	private String sentSignature = "";
	private String name = "script.fsh";
	private volatile boolean started;
	private volatile boolean finished;
	private volatile boolean paused;
	private volatile boolean terminating;
	private volatile boolean attached;
	private volatile boolean canTerminate;
	private volatile int thread = 1;
	private volatile Integer exitCode;
	// while stopped: the top frame, and the scope setVariable changes
	private volatile Integer topFrame;
	private volatile Integer variablesReference;
	// set while start waits for the stop before the first command (it is the answer, not an event for the listener)
	private volatile CompletableFuture<StoppedEventBody> entryStop;
	private final CompletableFuture<Void> initialized = new CompletableFuture<>();

	private DapSession(Listener listener, InputStream in, OutputStream out, Closeable... toClose) {
		this.listener = listener;
		this.toClose = toClose;
		this.connection = DapConnection.client(new IDebugClient() {
			@Override
			public void initialized() {
				initialized.complete(null);
			}

			@Override
			public void stopped(StoppedEventBody body) {
				post(() -> onStopped(body));
			}

			@Override
			public void continued(us.bringardner.parley.dap.protocol.ContinuedEventBody body) {
				paused = false;
			}

			@Override
			public void output(OutputEventBody body) {
				String category = body.getCategory() == null ? "stdout" : body.getCategory();
				if( body.getOutput() != null && (category.equals("stdout") || category.equals("stderr") || category.equals("console"))) {
					post(() -> listener.output(category, body.getOutput()));
				}
			}

			@Override
			public void exited(ExitedEventBody body) {
				exitCode = body.getExitCode();
			}

			@Override
			public void terminated(TerminatedEventBody body) {
				post(() -> finish(exitCode == null ? -1 : exitCode, terminating, null));
			}
		}, in, out);
		this.adapter = connection.getAdapter();
		this.connection.closed().thenRun(() -> {
			if( !finished ) {
				// (asked to end the script: a connection that closes without saying so is the end, not a failure)
				post(() -> finish(exitCode == null ? -1 : exitCode, terminating, terminating ? null : new IOException("The adapter's connection ended")));
			}
		});
	}

	private void post(Runnable r) {
		try {
			events.execute(r);
		} catch (RuntimeException e) {
			// closed
		}
	}

	/** A session with a DAP adapter listening on host:port (10 seconds at most to connect). */
	public static DapSession connect(String host, int port, Listener listener) throws IOException {
		Socket s = new Socket();
		try {
			s.connect(new java.net.InetSocketAddress(host, port), LoupeSession.CONNECT_MS);
			return new DapSession(listener, s.getInputStream(), s.getOutputStream(), s);
		} catch (IOException | RuntimeException e) {
			s.close();
			throw e;
		}
	}

	private static <T> T get(CompletableFuture<T> f) throws Exception {
		try {
			return f.get(30, TimeUnit.SECONDS);
		} catch (java.util.concurrent.ExecutionException e) {
			Throwable c = e.getCause();
			throw c instanceof Exception ? (Exception) c : e;
		}
	}

	// ---- starting

	@Override
	public Started start(RemoteDebug how, String code, String scriptName, List<String> arguments, Collection<Breakpoint> breakpoints) throws Exception {
		if( started ) {
			throw new IllegalStateException("Already started");
		}
		started = true;
		Capabilities caps = get(adapter.initialize(new InitializeRequestArguments().setClientID("fshstudio").setClientName("Fsh Studio")
				.setAdapterID("fsh").setLinesStartAt1(true).setColumnsStartAt1(true).setPathFormat("path")));
		canTerminate = caps != null && Boolean.TRUE.equals(caps.getSupportsTerminateRequest());
		Map<String,Object> args = new LinkedHashMap<>();
		boolean own = how.getMode() == RemoteDebug.Mode.EDITOR_SCRIPT;
		switch(how.getMode()) {
		case REMOTE_FILE:
			name = how.getPath();
			args.put("script", name);
			args.put("args", arguments);
			break;
		case ATTACH:
			attached = true;
			break;
		default:
			name = scriptName == null || scriptName.isEmpty() ? "script.fsh" : scriptName;
			args.put("source", code);
			args.put("name", name);
			args.put("args", arguments);
			break;
		}
		if( !own ) {
			// a script that isn't the editor's stops before its first command
			args.put("stopOnEntry", true);
			entryStop = new CompletableFuture<>();
		}
		if( attached ) {
			get(adapter.attach(new AttachRequestArguments().setExtra(args)));
		} else {
			get(adapter.launch(new LaunchRequestArguments().setExtra(args)));
		}
		// (the adapter says when it takes breakpoints; some say so before they answer the launch)
		initialized.get(30, TimeUnit.SECONDS);
		if( own ) {
			syncBreakpoints(breakpoints);
			get(adapter.configurationDone(new ConfigurationDoneArguments()));
			return new Started(null, null, -1, null, null);
		}
		get(adapter.configurationDone(new ConfigurationDoneArguments()));
		StoppedEventBody stop;
		try {
			stop = entryStop.get(30, TimeUnit.SECONDS);
		} catch (java.util.concurrent.TimeoutException e) {
			throw new IOException("The adapter at "+how.where()+" didn't stop the script before its first command");
		} finally {
			entryStop = null;
		}
		if( stop.getThreadId() != null ) {
			thread = stop.getThreadId();
		}
		StackFrame top = top();
		if( attached && top.getSource() != null && top.getSource().getPath() != null ) {
			name = top.getSource().getPath();
		}
		Map<String,Object> vars = readVariables(top);
		String text = null;
		String note = null;
		try {
			text = get(adapter.source(new SourceArguments().setSource(new Source().setPath(name)).setSourceReference(0))).getContent();
		} catch (Exception e) {
			note = "The adapter at "+how.where()+" doesn't give a script's text over DAP: "+name
					+" is being debugged, but what the editor shows is its own script";
		}
		paused = true;
		return new Started(name, text, top.getLine() == null ? 0 : top.getLine()-1, vars, note);
	}

	// ---- breakpoints

	@Override
	public void syncBreakpoints(Collection<Breakpoint> breakpoints) throws Exception {
		List<Breakpoint> wanted = new ArrayList<>();
		List<SourceBreakpoint> send = new ArrayList<>();
		StringBuilder sig = new StringBuilder();
		for(Breakpoint bp : breakpoints) {
			if( bp.isEnabled(false) && bp.getLine() >= 0 ) {
				SourceBreakpoint s = new SourceBreakpoint().setLine(bp.getLine()+1);
				if( bp.isConditional() && bp.getCondition() != null && !bp.getCondition().isBlank()) {
					s.setCondition(bp.getCondition());
				}
				if( bp.isHitCount() && bp.getHitCount() > 0 ) {
					// (a whole number, as parley-loupe's bridge takes a hit condition)
					s.setHitCondition(String.valueOf(bp.getHitCount()));
				}
				wanted.add(bp);
				send.add(s);
				sig.append(bp.getId()).append('|').append(s.getLine()).append('|').append(s.getCondition()).append('|').append(s.getHitCondition()).append('\n');
			}
		}
		synchronized (lock) {
			if( sig.toString().equals(sentSignature)) {
				return;
			}
			sentSignature = sig.toString();
		}
		SetBreakpointsResponseBody r = get(adapter.setBreakpoints(new SetBreakpointsArguments().setSource(new Source().setPath(name)).setBreakpoints(send)));
		List<us.bringardner.parley.dap.protocol.Breakpoint> placed = r == null ? null : r.getBreakpoints();
		for(int i=0; placed != null && i < placed.size() && i < wanted.size(); i++) {
			us.bringardner.parley.dap.protocol.Breakpoint p = placed.get(i);
			Breakpoint bp = wanted.get(i);
			boolean ok = Boolean.TRUE.equals(p.getVerified());
			int line = p.getLine() == null ? -1 : p.getLine()-1;
			String message = p.getMessage();
			post(() -> listener.breakpoint(bp, ok ? "verified" : "rejected", line, message));
		}
	}

	// ---- control

	@Override
	public CompletableFuture<Void> resume() {
		paused = false;
		return adapter.continue_(new ContinueArguments().setThreadId(thread)).thenApply(r -> null);
	}

	@Override
	public CompletableFuture<Void> stepOver() {
		paused = false;
		return adapter.next(new NextArguments().setThreadId(thread));
	}

	@Override
	public CompletableFuture<Void> stepInto() {
		paused = false;
		return adapter.stepIn(new StepInArguments().setThreadId(thread));
	}

	@Override
	public CompletableFuture<Void> stepOut() {
		paused = false;
		return adapter.stepOut(new StepOutArguments().setThreadId(thread));
	}

	@Override
	public CompletableFuture<Void> suspend() {
		return adapter.pause(new PauseArguments().setThreadId(thread));
	}

	@Override
	public CompletableFuture<Void> terminate() {
		terminating = true;
		if( canTerminate ) {
			return adapter.terminate(new TerminateArguments());
		}
		return adapter.disconnect(new DisconnectArguments().setTerminateDebuggee(true));
	}

	@Override
	public boolean isPaused() {
		return paused;
	}

	@Override
	public Map<String,Object> setVariable(String variable, String value) throws Exception {
		Integer scope = variablesReference;
		if( !paused || scope == null ) {
			throw new IllegalStateException("The script isn't stopped");
		}
		get(adapter.setVariable(new SetVariableArguments().setVariablesReference(scope).setName(variable).setValue(value)));
		return readVariables(top());
	}

	@Override
	public String evaluate(String expression) throws Exception {
		if( !paused ) {
			throw new IllegalStateException("The script isn't stopped");
		}
		return unquote(get(adapter.evaluate(new EvaluateArguments().setExpression(expression).setFrameId(topFrame).setContext("repl"))).getResult());
	}

	// ---- events (on the session's thread)

	private void onStopped(StoppedEventBody body) {
		CompletableFuture<StoppedEventBody> entry = entryStop;
		if( entry != null && !entry.isDone()) {
			entry.complete(body);
			return;
		}
		if( body.getThreadId() != null ) {
			thread = body.getThreadId();
		}
		int line;
		Map<String,Object> vars;
		try {
			StackFrame top = top();
			line = top.getLine() == null ? 0 : top.getLine()-1;
			vars = readVariables(top);
		} catch (Exception e) {
			if( !finished ) {
				finish(-1, false, e);
			}
			return;
		}
		paused = true;
		listener.paused(line, body.getReason() == null ? "pause" : body.getReason(), vars);
	}

	private StackFrame top() throws Exception {
		List<StackFrame> frames = get(adapter.stackTrace(new StackTraceArguments().setThreadId(thread).setStartFrame(0).setLevels(1))).getStackFrames();
		if( frames == null || frames.isEmpty()) {
			throw new IOException("The adapter gave no stack for the stopped script");
		}
		topFrame = frames.get(0).getId();
		return frames.get(0);
	}

	/** The variables of the top frame's scopes, except the script's arguments (as LoupeSession gives them). */
	private Map<String,Object> readVariables(StackFrame top) throws Exception {
		Map<String,Object> vars = new TreeMap<>();
		List<Scope> scopes = get(adapter.scopes(new ScopesArguments().setFrameId(top.getId()))).getScopes();
		Integer first = null;
		Integer named = null;
		for(Scope s : scopes == null ? List.<Scope>of() : scopes) {
			String n = s.getName() == null ? "" : s.getName().toLowerCase(java.util.Locale.ROOT);
			if( n.equals("arguments") || s.getVariablesReference() == null || s.getVariablesReference() <= 0 ) {
				continue;
			}
			if( first == null ) {
				first = s.getVariablesReference();
			}
			if( n.equals("variables") || n.equals("locals")) {
				named = s.getVariablesReference();
			}
			List<Variable> list = get(adapter.variables(new VariablesArguments().setVariablesReference(s.getVariablesReference()))).getVariables();
			for(Variable v : list == null ? List.<Variable>of() : list) {
				vars.put(v.getName(), unquote(v.getValue()));
			}
		}
		variablesReference = named != null ? named : first;
		return vars;
	}

	/** A value as DAP shows it, without the quotes (and escapes) adapters put round a string. */
	static String unquote(String text) {
		if( text == null ) {
			return "";
		}
		if( text.length() < 2 || text.charAt(0) != '"' || text.charAt(text.length()-1) != '"') {
			return text;
		}
		StringBuilder b = new StringBuilder();
		for(int i=1; i < text.length()-1; i++) {
			char c = text.charAt(i);
			if( c == '\\' && i+1 < text.length()-1 ) {
				char n = text.charAt(++i);
				switch(n) {
				case 'n': b.append('\n'); break;
				case 'r': b.append('\r'); break;
				case 't': b.append('\t'); break;
				default: b.append(n);
				}
			} else {
				b.append(c);
			}
		}
		return b.toString();
	}

	private void finish(int code, boolean killed, Exception error) {
		synchronized (lock) {
			if( finished ) {
				return;
			}
			finished = true;
		}
		paused = false;
		listener.finished(code, killed, error);
	}

	/**
	 * Ends the session. If the script hasn't ended the adapter is told first (disconnect: a
	 * script the session attached to runs on, one it started is ended), and the connection is
	 * closed once the adapter has answered (a second at most), not by this call.
	 */
	@Override
	public void close() {
		boolean tell;
		synchronized (lock) {
			tell = started && !finished && !connection.isClosed();
			finished = true;
		}
		if( !tell ) {
			closeNow();
			return;
		}
		CompletableFuture<Void> told;
		try {
			told = adapter.disconnect(new DisconnectArguments().setTerminateDebuggee(!attached));
		} catch (RuntimeException e) {
			closeNow();
			return;
		}
		told.orTimeout(1, TimeUnit.SECONDS).whenComplete((r, e) -> closeNow());
	}

	private void closeNow() {
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
