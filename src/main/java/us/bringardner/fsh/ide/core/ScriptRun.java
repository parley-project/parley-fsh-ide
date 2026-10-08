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
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import us.bringardner.fsh.Console;
import us.bringardner.fsh.ConsoleSignal;
import us.bringardner.fsh.DebugContext.RunState;
import us.bringardner.fsh.FshList;
import us.bringardner.fsh.ShellContext;
import us.bringardner.fsh.Argument;
import us.bringardner.fsh.job.AbstractJob;
import us.bringardner.fsh.job.ForgroundJob;
import us.bringardner.parley.files.FileSource;
import us.bringardner.parley.files.FileSourceFactory;

/**
 * One run of a script with fsh, on its own thread: its arguments, its standard streams
 * (the UI's console, or redirect files, which are closed when the run ends) and, for
 * debugging, a DebugSession.
 */
public class ScriptRun {

	/** Lines put in front of the code to run ("#!fsh"); DebugSession allows for them. */
	static final int HEADER_LINES = 1;
	private static final String HEADER = "#!fsh\n";
	private static final AtomicInteger count = new AtomicInteger();

	static {
		// Scripts run inside the IDE: a script's exit (or a cancelled run) ends the script,
		// not the JVM. fsh's default, for its own command line, is to exit the JVM.
		Console.exitJvm = false;
	}

	public interface Listener {
		/**
		 * The run ended. Called on the run's thread.
		 * @param ctx the script's context (for its variables), or null if it didn't start
		 * @param error why it couldn't start (a redirect file couldn't be opened, say), or null
		 */
		void finished(ScriptRun run, int exitCode, ShellContext ctx, Exception error);
	}

	private final String code;
	private final Listener listener;
	private String scriptName = "fsh";
	private String arguments = "";
	private String stdInFile = "";
	private String stdOutFile = "";
	private String stdErrFile = "";
	private InputStream in = System.in;
	private PrintStream out = System.out;
	private PrintStream err = System.err;
	private DebugSession debug;
	// -Xss4m
	private long stackSize = 1024*1024;

	private final List<Closeable> opened = new ArrayList<>();
	private volatile boolean canceled;
	private volatile AbstractJob job;
	private Thread thread;

	/** @param code the script, as in the editor (without "#!fsh") */
	public ScriptRun(String code, Listener listener) {
		this.code = code;
		this.listener = listener;
	}

	/** $0 */
	public ScriptRun scriptName(String name) {
		scriptName = name;
		return this;
	}

	/** Arguments separated by white space. */
	public ScriptRun arguments(String arguments) {
		this.arguments = arguments;
		return this;
	}

	/** The run's standard streams, used where there's no redirect file. */
	public ScriptRun console(InputStream in, PrintStream out, PrintStream err) {
		this.in = in;
		this.out = out;
		this.err = err;
		return this;
	}

	/** Files (paths any file system understands) to redirect to; "" or null for the console. */
	public ScriptRun redirects(String stdIn, String stdOut, String stdErr) {
		stdInFile = stdIn == null ? "" : stdIn.trim();
		stdOutFile = stdOut == null ? "" : stdOut.trim();
		stdErrFile = stdErr == null ? "" : stdErr.trim();
		return this;
	}

	/** Lets the run be stopped (and, if the session is active, debugged). */
	public ScriptRun debug(DebugSession debug) {
		this.debug = debug;
		return this;
	}

	public ScriptRun stackSize(long bytes) {
		stackSize = bytes;
		return this;
	}

	public synchronized void start() {
		if( thread != null ) {
			throw new IllegalStateException("Already started");
		}
		thread = new Thread(null, this::run, "Execute thread "+count.incrementAndGet(), stackSize);
		thread.setDaemon(true);
		thread.start();
	}

	/** True until the script has ended. */
	public boolean isRunning() {
		AbstractJob j = job;
		return j != null ? j.isAlive() : thread != null && thread.isAlive();
	}

	public boolean isCanceled() {
		return canceled;
	}

	/** Stops the script: kills it, and stops it at its next statement if it's in a loop. */
	public synchronized void cancel() {
		canceled = true;
		AbstractJob j = job;
		if( j != null ) {
			j.handleSignal(ConsoleSignal.Kill);
			// the script runs on the job's thread: wake it if it's waiting for input
			j.interuptJob();
		}
		if( debug != null ) {
			debug.setCurrentState(RunState.Terminate);
		}
		interrupt();
	}

	/** Interrupts the run's thread, for a script blocked on input. */
	public synchronized void interrupt() {
		if( thread != null ) {
			thread.interrupt();
		}
	}

	/**
	 * Waits up to millis (0 for ever) for the run to end.
	 * @return true if it has ended
	 */
	public boolean join(long millis) throws InterruptedException {
		Thread t;
		synchronized (this) {
			t = thread;
		}
		if( t != null ) {
			t.join(millis);
			return !t.isAlive();
		}
		return true;
	}

	private void run() {
		int exitCode = 0;
		ShellContext sc = null;
		Exception error = null;
		try {
			if( !code.isBlank()) {
				Console console = createConsole();
				FshList args = new FshList();
				args.add(new Argument(scriptName));
				for (String a : ScriptText.splitArguments(arguments)) {
					args.add( new Argument(a));
				}
				console.setPositionalParameters(true, args);
				sc = new ShellContext(console);

				job = new ForgroundJob(sc, HEADER+code);
				job.start();
				while(job.isAlive()) {
					try {
						job.join(0);
					} catch (InterruptedException e) {
						// cancel() interrupts; the kill signal it sent ends the job
					}
				}
				exitCode = job.getExitCode();
			}
		} catch (Exception e) {
			error = e;
			exitCode = 1;
		} finally {
			error = closeRedirects(error);
		}
		listener.finished(this, exitCode, sc, error);
	}

	private Console createConsole() throws IOException {
		Console console = new Console();
		console.setStdIn(in);
		console.setStdOut(out);
		console.setStdErr(err);

		if(!stdInFile.isEmpty()) {
			FileSource file = FileSourceFactory.getDefaultFactory().createFileSource(stdInFile);
			InputStream fin = file.getInputStream();
			opened.add(fin);
			console.setStdIn(fin);
		}
		if(!stdOutFile.isEmpty()) {
			FileSource file = FileSourceFactory.getDefaultFactory().createFileSource(stdOutFile);
			PrintStream fout = new PrintStream(file.getOutputStream(), true, StandardCharsets.UTF_8);
			opened.add(fout);
			console.setStdOut(fout);
		}
		if(!stdErrFile.isEmpty()) {
			FileSource file = FileSourceFactory.getDefaultFactory().createFileSource(stdErrFile);
			PrintStream ferr = new PrintStream(file.getOutputStream(), true, StandardCharsets.UTF_8);
			opened.add(ferr);
			console.setStdErr(ferr);
		}
		if( debug != null ) {
			debug.setCurrentState(RunState.Running);
			console.setDebugContext(debug);
		}
		return console;
	}

	private Exception closeRedirects(Exception error) {
		for(Closeable c : opened) {
			try {
				c.close();
			} catch (IOException e) {
				if( error == null ) {
					error = e;
				}
			}
		}
		opened.clear();
		return error;
	}
}
