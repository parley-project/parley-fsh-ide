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
 * counts), at variable watchpoints (reads, writes or both) and steps, and tells a listener
 * where the script stopped and what it ran.
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

		/**
		 * A watchpoint is stopping the script: paused() follows, for the line of the statement
		 * that read or wrote the variable. Called on the script's thread.
		 * @param write true if the variable was set or unset, false if it was read
		 * @param value the variable's value (after the change, for a write)
		 */
		default void watchpointHit(Watchpoint wp, String name, boolean write, Object value) {
		}
	}

	/** Describes a watchpoint's stop for a status line: "x written (value: 3)" or "x read". */
	public static String describeHit(String name, boolean write, Object value) {
		if( !write ) {
			return name+" read";
		}
		if( value == null ) {
			return name+" unset";
		}
		String v = String.valueOf(value).replace("\r\n", "⏎").replace('\n', '⏎').replace('\r', '⏎');
		if( v.length() > 80 ) {
			v = v.substring(0, 77)+"...";
		}
		return name+" written (value: "+v+")";
	}

	private final IntFunction<Breakpoint> breakpoints;
	private final Listener listener;
	// the variables watched; read on the script's thread for every variable access
	private final java.util.List<Watchpoint> watchpoints = new java.util.concurrent.CopyOnWriteArrayList<>();
	// the watchpoint stopping the script, told to the listener as it stops
	private Watchpoint hit;
	private String hitName;
	private boolean hitWrite;

	// editor line (0-based) = script line (1-based) + lineAdjust
	private volatile int lineAdjust = -1;
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

	// Run to End was chosen: no more stopping at breakpoints this run (Suspend still stops)
	private volatile boolean ignoreBreakpoints;

	/** True to stop at breakpoints and steps and log statements; false to just run. */
	public void setActive(boolean active) {
		this.active = active;
		ignoreBreakpoints = false;
	}

	/**
	 * Lets a paused script run to its end without stopping at any more breakpoints; its
	 * statements are still logged and Suspend still stops it.
	 */
	public void runToEnd() {
		ignoreBreakpoints = true;
		resume();
	}

	public boolean isActive() {
		return active;
	}

	/**
	 * The watchpoints: add, remove or change them at any time (the list is safe to change from
	 * any thread while a script runs).
	 */
	public java.util.List<Watchpoint> getWatchpoints() {
		return watchpoints;
	}

	@Override
	public boolean variableRead(String name, ShellContext ctx) {
		return watched(name, false, ctx);
	}

	@Override
	public boolean variableWritten(String name, ShellContext ctx) {
		return watched(name, true, ctx);
	}

	// called for every variable access: nothing more than a look at the list when none is watched
	private boolean watched(String name, boolean write, ShellContext ctx) {
		if( watchpoints.isEmpty() || !active || ignoreBreakpoints ) {
			return false;
		}
		for(Watchpoint wp : watchpoints) {
			if( wp.matches(name, write)) {
				return watchpointHit(wp, name, write, ctx);
			}
		}
		return false;
	}

	private synchronized boolean watchpointHit(Watchpoint wp, String name, boolean write, ShellContext ctx) {
		// not while a condition runs, nor the debugger's own looks at (and changes to) a paused script
		RunState state = getCurrentState();
		if( evaluating || state == RunState.AtBreakpoint || state == RunState.Terminate ) {
			return false;
		}
		// the statement reading or writing it
		Ast.Node node = ctx.getLastNode();
		if( node != null ) {
			current = node;
		}
		currentCtx = ctx;
		hit = wp;
		hitName = Watchpoint.normalize(name);
		hitWrite = write;
		return true;
	}

	/** The editor line (0-based) the code being run starts on (non-zero when running a selection). */
	public void setFirstLine(int line) {
		lineAdjust = line-1;
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
		if( !active || evaluating || ignoreBreakpoints ) {
			return false;
		}
		int line = editorLine(linePt.x);
		if( line < 0) {
			return false;
		}
		Breakpoint bp = breakpoints.apply(line);
		if( bp == null || !bp.isEnabled(false)) {
			return false;
		}
		// the condition and the hit count are each a reason to stop, apart: every time the
		// expression is true, and the Nth time the line is reached (whatever the condition);
		// with neither, every time
		boolean hit = bp.countHit();
		if( !bp.hasCondition()) {
			return hit || !bp.hasHitCount();
		}
		if( hit ) {
			return true;
		}
		boolean ret = true;
		evaluating = true;
		try {
			// a command, such as [ $i -gt 3 ]: true if it succeeds
			ret = Executor.test(ctx, bp.getCondition());
		} catch (Exception e) {
			// (it stops, so the problem is seen)
			if( bp.reportConditionError()) {
				listener.conditionFailed(bp, line, e);
			}
		} finally {
			evaluating = false;
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
		if( state == RunState.AtBreakpoint && active && hit != null && currentCtx != null) {
			listener.watchpointHit(hit, hitName, hitWrite, hitWrite ? currentCtx.peekVariable(hitName) : null);
		}
		hit = null;
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
