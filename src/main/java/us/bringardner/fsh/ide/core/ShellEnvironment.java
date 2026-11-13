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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * The environment a script would have from a terminal, for the scripts the IDE runs.
 * <p>
 * An app opened from the Finder or the Dock on macOS (and from some Linux desktops) gets a
 * small environment, not the one a terminal's login shell makes from the user's startup files,
 * so a script that finds its programs in a terminal wouldn't in the IDE. As JetBrains' IDEs and
 * VS Code do, the IDE runs the user's shell once when it starts ({@code $SHELL -l -i -c}), in
 * the background, and takes the environment variables that shell has; the scripts it runs get
 * them. Only the exported variables: not aliases, functions or options, which a script from a
 * terminal doesn't get either. While it runs, {@value #READER} is set, so a startup file can
 * skip what needs a terminal.
 * <p>
 * It isn't done on Windows (whose environment is the same for every program), nor when the IDE
 * was started from a shell (SHLVL is set: it already has that environment).
 */
public final class ShellEnvironment {

	/** set while the shell runs for its environment */
	public static final String READER = "FSH_IDE_ENVIRONMENT_READER";

	/** how long the shell may take */
	static final long TIMEOUT_SECONDS = 15;

	/** what the shell prints before its environment, so what its startup files print is passed over */
	static final String MARKER = "--fsh-ide-environment--";

	/** the shell's own, not the environment's: not taken */
	static final Set<String> SHELLS_OWN = Set.of("PWD", "OLDPWD", "SHLVL", "_", READER);

	private static volatile CompletableFuture<Map<String,String>> capture;
	private static volatile String problem;
	/** what was done, in a sentence (see status()) */
	private static volatile String status = "Not got: the IDE didn't ask for it.";
	/** the shell run, or null */
	private static volatile String shellRun;

	private ShellEnvironment() {
	}

	/**
	 * Starts getting the environment (once; the IDEs call it as they start). onProblem is told,
	 * on the background thread, if it couldn't be had.
	 */
	public static synchronized void start(Consumer<String> onProblem) {
		if( capture != null ) {
			return;
		}
		if( File.separatorChar == '\\' ) {
			status = "Not needed on Windows: scripts run with the IDE's own environment, which every program shares.";
			capture = CompletableFuture.completedFuture(Map.of());
			return;
		}
		if( System.getenv("SHLVL") != null ) {
			status = "Not needed: the IDE was started from a shell (SHLVL is set), so scripts run with its environment.";
			capture = CompletableFuture.completedFuture(Map.of());
			return;
		}
		String shell = System.getenv("SHELL");
		if( shell == null || shell.isBlank()) {
			shell = new File("/bin/zsh").canExecute() ? "/bin/zsh" : "/bin/sh";
		}
		String sh = shell;
		shellRun = sh;
		status = "Getting the environment of the login shell ("+sh+" -l -i)...";
		capture = CompletableFuture.supplyAsync(()->{
			try {
				Map<String,String> env = capture(sh, TIMEOUT_SECONDS);
				status = "Scripts run with the environment of the login shell ("+sh+" -l -i): "+env.size()+" variables.";
				return env;
			} catch (Exception e) {
				problem = "Couldn't get the environment of the login shell ("+sh+"): "+e.getMessage()
						+". Scripts run with the IDE's own environment.";
				status = problem;
				if( onProblem != null ) {
					onProblem.accept(problem);
				}
				return Map.of();
			}
		});
	}

	/** What was done, in a sentence: used the login shell's environment, not needed (and why), or failed (and why). */
	public static String status() {
		return status;
	}

	/**
	 * Puts the status in log (the IDE's Log tab, as a run starts: every run, in every window), as
	 * a line; nothing if it wasn't started. On a thread of its own, since it may wait for the
	 * login shell.
	 */
	public static void logStatus(Consumer<String> log) {
		if( capture == null ) {
			return;
		}
		new Thread(()->{
			forScripts();
			String s = status;
			if( s != null ) {
				log.accept("Fsh Studio: "+s+" (Help > Script Environment... says more)\n");
			}
		}, "script-environment-status").start();
	}

	/** Help > Script Environment...: the status, the shell, and the variables scripts get. */
	public static String report() {
		Map<String,String> env = forScripts();
		StringBuilder b = new StringBuilder(status).append("\n\n");
		if( shellRun != null ) {
			b.append("Shell: ").append(shellRun).append(" -l -i -c (").append(READER).append("=1 while it runs)\n");
		}
		String fshEnv = env.containsKey("FSH_ENV") ? env.get("FSH_ENV") : System.getenv("FSH_ENV");
		b.append("FSH_ENV: ").append(fshEnv == null || fshEnv.isEmpty() ? "not set (no startup file for scripts)" : fshEnv).append("\n\n");
		Map<String,String> all = new java.util.TreeMap<>(System.getenv());
		all.putAll(env);
		b.append(env.isEmpty() ? "The variables scripts get (the IDE's own):\n" : "The variables scripts get:\n");
		String path = all.get("PATH");
		if( path != null ) {
			b.append("\nPATH, in the order it's searched:\n");
			for(String dir : path.split(File.pathSeparator)) {
				b.append("  ").append(dir).append("\n");
			}
		}
		b.append("\n");
		for(Map.Entry<String,String> e : all.entrySet()) {
			if( !e.getKey().equals("PATH")) {
				b.append(e.getKey()).append('=').append(e.getValue()).append("\n");
			}
		}
		return b.toString();
	}

	/** Why the environment couldn't be had, or null. */
	public static String problem() {
		return problem;
	}

	/**
	 * The variables for a script (waiting for them, if the shell is still running): the login
	 * shell's environment; none if it wasn't started, isn't needed, or couldn't be had.
	 */
	public static Map<String,String> forScripts() {
		CompletableFuture<Map<String,String>> c = capture;
		if( c == null ) {
			return Map.of();
		}
		try {
			return c.get(TIMEOUT_SECONDS+5, TimeUnit.SECONDS);
		} catch (Exception e) {
			return Map.of();
		}
	}

	/**
	 * Runs action once the variables for a script are known, so forScripts() won't wait: now, on
	 * this thread, if they are (or it wasn't started); otherwise on the thread that gets them.
	 */
	public static void whenKnown(Runnable action) {
		CompletableFuture<Map<String,String>> c = capture;
		if( c == null ) {
			action.run();
		} else {
			// (it always completes: a shell that fails or takes too long gives none)
			c.thenRun(action);
		}
	}

	/**
	 * What forScripts() gives, without waiting: none while the login shell's environment is
	 * still being got.
	 */
	public static Map<String,String> known() {
		CompletableFuture<Map<String,String>> c = capture;
		if( c == null || !c.isDone()) {
			return Map.of();
		}
		try {
			return c.getNow(Map.of());
		} catch (RuntimeException e) {
			return Map.of();
		}
	}

	/** for the tests: what forScripts gives (null: as if never started) */
	static synchronized void setForTests(Map<String,String> env) {
		capture = env == null ? null : CompletableFuture.completedFuture(env);
		problem = null;
		shellRun = null;
		status = env == null ? "Not got: the IDE didn't ask for it." : "Scripts run with the environment of the login shell (a test's): "+env.size()+" variables.";
	}

	/** Runs shell as a login, interactive shell and returns the environment it has. */
	static Map<String,String> capture(String shell, long timeoutSeconds) throws IOException, InterruptedException {
		return capture(shell, timeoutSeconds, Map.of());
	}

	/** (with also: variables the shell is started with, for the tests) */
	static Map<String,String> capture(String shell, long timeoutSeconds, Map<String,String> also) throws IOException, InterruptedException {
		ProcessBuilder pb = new ProcessBuilder(shell, "-l", "-i", "-c",
				"printf '%s\\0' '"+MARKER+"'; exec /usr/bin/env -0");
		pb.environment().putAll(also);
		pb.environment().put(READER, "1");
		pb.redirectInput(ProcessBuilder.Redirect.from(new File("/dev/null")));
		pb.redirectError(ProcessBuilder.Redirect.DISCARD);
		Process p = pb.start();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Thread reader = new Thread(()->{
			try (InputStream in = p.getInputStream()) {
				in.transferTo(out);
			} catch (IOException e) {
				// (the process ended)
			}
		}, "shell-environment");
		reader.setDaemon(true);
		reader.start();
		if( !p.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
			p.destroyForcibly();
			throw new IOException("it took more than "+timeoutSeconds+" seconds (a startup file may be waiting for input; "
					+READER+" is set while it runs)");
		}
		reader.join(2000);
		if( p.exitValue() != 0 ) {
			throw new IOException("it ended with "+p.exitValue());
		}
		return parse(out.toString(StandardCharsets.UTF_8));
	}

	/** The variables after the marker in what env -0 printed (NAME=value, each ended by a NUL). */
	static Map<String,String> parse(String output) {
		int at = output.indexOf(MARKER+"\0");
		if( at < 0 ) {
			throw new IllegalArgumentException("no environment in what the shell printed");
		}
		Map<String,String> ret = new LinkedHashMap<>();
		for(String entry : output.substring(at+MARKER.length()+1).split("\0")) {
			int eq = entry.indexOf('=');
			if( eq > 0 ) {
				String name = entry.substring(0, eq);
				if( !SHELLS_OWN.contains(name)) {
					ret.put(name, entry.substring(eq+1));
				}
			}
		}
		return Collections.unmodifiableMap(ret);
	}
}
