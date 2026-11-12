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
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * A debugging session with an adapter, as the IDEs drive it: {@link LoupeSession} speaks Loupe
 * and {@link DapSession} the Debug Adapter Protocol, and the IDEs don't need to know which.
 * <p>
 * Lines are the editor's (0-based). The listener is called on a thread of the session's, one
 * call at a time, in the order of events.
 */
public interface AdapterSession extends Closeable {

	interface Listener {
		/**
		 * The script stopped before the statement on line.
		 * @param reason breakpoint, step, pause, exception or entry
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

	/** What a remote session started with. */
	final class Started {
		private final String path;
		private final String text;
		private final int line;
		private final Map<String,Object> variables;
		private final String note;

		public Started(String path, String text, int line, Map<String,Object> variables, String note) {
			this.path = path;
			this.text = text;
			this.line = line;
			this.variables = variables;
			this.note = note;
		}

		/** The script's path where it runs, or null for the editor's script. */
		public String getPath() {
			return path;
		}

		/**
		 * The script's text as the remote machine has it, or null: the editor's script is the
		 * one running, or the adapter couldn't give the text (see {@link #getNote()}).
		 */
		public String getText() {
			return text;
		}

		/**
		 * True if the script is stopped before its first command, waiting for the user (a script
		 * that isn't the editor's: its breakpoints are set once its text is seen). The editor's own
		 * script is sent the editor's breakpoints and runs.
		 */
		public boolean isStopped() {
			return variables != null;
		}

		/** Where it is stopped (the editor's line), when {@link #isStopped()}. */
		public int getLine() {
			return line;
		}

		/** The variables where it is stopped, or null. */
		public Map<String,Object> getVariables() {
			return variables;
		}

		/** Something the user should be told (the text couldn't be read), or null. */
		public String getNote() {
			return note;
		}
	}

	/**
	 * Starts debugging as how says: the editor's script (code, called scriptName) run where the
	 * adapter is, a script file there, or the script that waits there for a debugger. For the
	 * editor's script the breakpoints are sent and it runs; the other two stop before their first
	 * command (see {@link Started}).
	 */
	Started start(RemoteDebug how, String code, String scriptName, List<String> arguments, Collection<Breakpoint> breakpoints) throws Exception;

	/**
	 * Makes the adapter's breakpoints the editor's (the enabled ones). Call it when breakpoints
	 * are added, removed, moved or changed.
	 */
	void syncBreakpoints(Collection<Breakpoint> breakpoints) throws Exception;

	CompletableFuture<Void> resume();

	CompletableFuture<Void> stepOver();

	CompletableFuture<Void> stepInto();

	CompletableFuture<Void> stepOut();

	/** Stops a running script at its next command. */
	CompletableFuture<Void> suspend();

	/** Ends the script. */
	CompletableFuture<Void> terminate();

	/** True while the script is stopped (the variables can be asked about and changed). */
	boolean isPaused();

	/**
	 * Sets a variable of the stopped script and returns the variables as they are then.
	 * @throws IllegalStateException if the script isn't stopped
	 */
	Map<String,Object> setVariable(String variable, String value) throws Exception;

	/** The value of an expression (such as $x or ${a[1]}) in the stopped script's top frame. */
	String evaluate(String expression) throws Exception;

	/**
	 * Ends the session. A script a debugger attached to runs on without it; one the session
	 * started is ended by its adapter.
	 */
	@Override
	void close();
}
