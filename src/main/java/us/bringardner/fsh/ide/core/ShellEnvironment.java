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
		if( File.separatorChar == '\\' || System.getenv("SHLVL") != null ) {
			capture = CompletableFuture.completedFuture(Map.of());
			return;
		}
		String shell = System.getenv("SHELL");
		if( shell == null || shell.isBlank()) {
			shell = new File("/bin/zsh").canExecute() ? "/bin/zsh" : "/bin/sh";
		}
		String sh = shell;
		capture = CompletableFuture.supplyAsync(()->{
			try {
				return capture(sh, TIMEOUT_SECONDS);
			} catch (Exception e) {
				problem = "Couldn't get the environment of the login shell ("+sh+"): "+e.getMessage()
						+". Scripts run with the IDE's own environment.";
				if( onProblem != null ) {
					onProblem.accept(problem);
				}
				return Map.of();
			}
		});
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

	/** for the tests: what forScripts gives (null: as if never started) */
	static synchronized void setForTests(Map<String,String> env) {
		capture = env == null ? null : CompletableFuture.completedFuture(env);
		problem = null;
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
