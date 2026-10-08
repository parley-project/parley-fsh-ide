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

import java.awt.Point;
import java.util.Map;
import java.util.function.IntFunction;


import us.bringardner.fsh.DebugContext;
import us.bringardner.fsh.ShellContext;
import us.bringardner.fsh.ShellContext.LoopControl;
import us.bringardner.fsh.exec.Executor;
import us.bringardner.fsh.signal.LoopControlException;
import us.bringardner.fsh.syntax.Ast;

/**
 * The IDE's side of fsh's debugger: stops at breakpoints (with their conditions and hit
 * counts) and steps, and tells a listener where the script stopped and what it ran.
 * <p>
 * fsh calls before(), isBreakpoint() and after() on the script's thread for each statement,
 * and when the script stops it sets the state to AtBreakpoint and waits until something
 * (resume(), stepOver() ...) changes it. Lines given to the listener and to the breakpoint
 * lookup are the editor's, 0-based.
 */
public class DebugSession extends DebugContext {

	public interface Listener {
		/** The script stopped before the statement on line. Called on the script's thread. */
		void paused(int line, ShellContext ctx, Map<String,Object> variables);

		/** The statement on line has run. Called on the script's thread, for every statement. */
		void statement(int line, String text);

		/** A breakpoint's condition couldn't be evaluated (it stops anyway). Once per run per breakpoint. */
		void conditionFailed(Breakpoint bp, int line, Exception error);

		/** The script is about to leave the line it stopped on. Called on the thread that resumed it. */
		void resuming();

		/** Terminate was pressed; the UI should stop the run. Called on the thread that pressed it. */
		void terminateRequested();
	}

	private final IntFunction<Breakpoint> breakpoints;
	private final Listener listener;

	// editor line (0-based) = script line (1-based) + lineAdjust
	private volatile int lineAdjust = -1-ScriptRun.HEADER_LINES;
	// false while just running: no stopping and no statement log, only terminate
	private volatile boolean active;

	// the statement about to run, and its context; read when the script stops
	private Ast.Node current;
	private ShellContext currentCtx;
	// a breakpoint's condition is running: its own commands are not the script's
	private boolean evaluating;

	/**
	 * @param breakpoints the breakpoint on an editor line (0-based), or null; called on the
	 *        script's thread, so it must be safe to call from any thread
	 */
	public DebugSession(IntFunction<Breakpoint> breakpoints, Listener listener) {
		this.breakpoints = breakpoints;
		this.listener = listener;
	}

	/** True to stop at breakpoints and steps and log statements; false to just run. */
	public void setActive(boolean active) {
		this.active = active;
	}

	public boolean isActive() {
		return active;
	}

	/** The editor line (0-based) the code being run starts on (non-zero when running a selection). */
	public void setFirstLine(int line) {
		lineAdjust = line-1-ScriptRun.HEADER_LINES;
	}

	/** The editor line (0-based) of a line (1-based) in the code as run. */
	public int editorLine(int scriptLine) {
		return scriptLine+lineAdjust;
	}

	@Override
	public void suspend() {
		setCurrentState(RunState.StepInto);
	}

	@Override
	public void terminate() {
		listener.terminateRequested();
	}

	@Override
	public void resume() {
		listener.resuming();
		setCurrentState(RunState.Running);
	}

	@Override
	public void stepOver() {
		listener.resuming();
		setCurrentState(RunState.StepOver);
	}

	@Override
	public void stepInto() {
		listener.resuming();
		setCurrentState(RunState.StepInto);
	}

	@Override
	public synchronized boolean isBreakpoint(Point linePt,ShellContext ctx) {
		if( !active || evaluating ) {
			return false;
		}
		int line = editorLine(linePt.x);
		if( line < 0) {
			return false;
		}
		Breakpoint bp = breakpoints.apply(line);
		boolean ret = bp !=null && bp.isEnabled(true);
		if( ret && bp.isConditional()) {
			evaluating = true;
			try {
				// a command, such as [ $i -gt 3 ]: true if it succeeds
				ret = Executor.test(ctx, bp.getCondition());
			} catch (Exception e) {
				if( bp.reportConditionError()) {
					listener.conditionFailed(bp, line, e);
				}
			} finally {
				evaluating = false;
			}
		}
		return ret;
	}

	@Override
	public synchronized void before(Ast.Node node, String source, ShellContext ctx) {
		if( evaluating ) {
			return;
		}
		if( getCurrentState() == RunState.Terminate) {
			throw new LoopControlException(LoopControl.Break, 100000);
		}
		current = node;
		currentCtx = ctx;
	}

	@Override
	public synchronized void setCurrentState(RunState state) {
		super.setCurrentState(state);
		// set by the script's thread as it stops at a breakpoint or step. Nothing is
		// reported for the statements in between.
		if( state == RunState.AtBreakpoint && active && current != null && currentCtx != null) {
			listener.paused(editorLine(current.line), currentCtx, currentCtx.getVariables());
		}
	}

	@Override
	public synchronized void after(Ast.Node node, String source, ShellContext ctx) {
		if( active && !evaluating && node != null ) {
			int line = editorLine(node.line);
			if( line >=0) {
				listener.statement(line, ScriptText.statementText(node, source));
			}
		}
	}
}
