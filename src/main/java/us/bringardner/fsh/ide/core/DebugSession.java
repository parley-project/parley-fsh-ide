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

	// the statement about to run, the text it was read from, and its context; read when the script stops
	private Ast.Node current;
	private String currentSource;
	private ShellContext currentCtx;
	// Step Into was pressed (till the script stops), and the statement it stopped at
	private boolean steppingInto;
	private Statement pausedStatement;
	// a breakpoint's condition is running: its own commands are not the script's
	private boolean evaluating;
	/*
	 * Where the script stopped, for each call depth (the functions and sourced files running):
	 * the line it stopped on, and the statements started on that line since. The visit to the
	 * line lasts until a statement on another line starts at that depth, the same statement
	 * starts again (the next time round a loop) or the function returns. While it lasts, the
	 * line's breakpoint doesn't stop the script again (only Step Into stops at the rest of the line).
	 */
	private static final class Visit {
		final int line;
		final java.util.Set<Ast.Node> started = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

		Visit(int line, Ast.Node node) {
			this.line = line;
			started.add(node);
		}
	}
	private final java.util.TreeMap<Integer,Visit> visits = new java.util.TreeMap<>();
	// Step Over: the call depth it started at (it ends when that depth's visit does)
	private boolean steppingOver;
	private int stepDepth;

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
	// Run to Line: the editor line (0-based) to stop at, till the script next stops; -1 for none
	private volatile int runToLine = -1;

	/** True to stop at breakpoints and steps and log statements; false to just run. */
	public void setActive(boolean active) {
		this.active = active;
		ignoreBreakpoints = false;
		runToLine = -1;
		synchronized (this) {
			endStepOver();
			steppingInto = false;
			pausedStatement = null;
			visits.clear();
		}
	}

	/**
	 * Lets a paused script run to its end without stopping at any more breakpoints; its
	 * statements are still logged and Suspend still stops it.
	 */
	public void runToEnd() {
		ignoreBreakpoints = true;
		resume();
	}

	/**
	 * Run to Line: lets a paused script run, and stops it when a statement on line (the
	 * editor's, 0-based) starts, as if a breakpoint were there for this once. It stops sooner at a
	 * breakpoint or a watchpoint on the way (or Suspend), and then the line is forgotten. A line
	 * the script never reaches doesn't stop it. The line the script is stopped on stops it the
	 * next time it's reached (the next time round a loop), not for the rest of this visit.
	 */
	public void runToLine(int line) {
		runToLine = line;
		resume();
	}

	private synchronized void endStepOver() {
		steppingOver = false;
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
		// (a watchpoint's stop shows the whole line)
		steppingInto = false;
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
		endStepOver();
		setCurrentState(RunState.StepInto);
	}

	@Override
	public void terminate() {
		listener.terminateRequested();
	}

	@Override
	public void resume() {
		listener.resuming();
		endStepOver();
		setCurrentState(RunState.Running);
	}

	/**
	 * Step Over: runs the rest of the line it stopped on, and stops at the first statement on
	 * another line, in the same function or the one it returns to; the functions it calls run
	 * without stopping (unless at a breakpoint or a watchpoint). In a loop, a statement starting
	 * again on the same line (the next time round) stops too.
	 */
	@Override
	public void stepOver() {
		listener.resuming();
		synchronized (this) {
			endStepOver();
			if( current != null && currentCtx != null && visits.containsKey(currentCtx.callFrames.size())) {
				steppingOver = true;
				stepDepth = currentCtx.callFrames.size();
			}
			// (fsh stops at the next statement in StepOver: isBreakpoint decides where instead)
			setCurrentState(steppingOver ? RunState.Running : RunState.StepOver);
		}
	}

	/** Step Into: stops at the next statement, the next one on the same line or in a function it calls. */
	@Override
	public void stepInto() {
		listener.resuming();
		synchronized (this) {
			endStepOver();
			steppingInto = true;
			setCurrentState(RunState.StepInto);
		}
	}

	/**
	 * Where on its line the statement a Step Into stopped at is, for an editor to highlight just
	 * it: column (0-based) and length on the line, and its text there (so an editor can check
	 * its line still says that).
	 */
	public static final class Statement {
		public final int column;
		public final int length;
		public final String text;

		Statement(int column, int length, String text) {
			this.column = column;
			this.length = length;
			this.text = text;
		}
	}

	/**
	 * The statement the script is paused at, when Step Into stopped it at one that shares its
	 * line with others; null to highlight the whole line (a breakpoint, a watchpoint, Step Over,
	 * Suspend, or a statement that is the whole line). Call it from Listener.paused.
	 */
	public synchronized Statement pausedStatement() {
		return pausedStatement;
	}

	/** the statement's place on its (first) line, or null if it is all there is on the line */
	static Statement statementOnLine(Ast.Node node, String source) {
		if( node == null || source == null || node.start < 0 || node.end > source.length() || node.start >= node.end ) {
			return null;
		}
		int lineStart = source.lastIndexOf('\n', node.start-1)+1;
		int lineEnd = source.indexOf('\n', node.start);
		if( lineEnd < 0 ) {
			lineEnd = source.length();
		}
		int end = Math.min(node.end, lineEnd);
		String line = source.substring(lineStart, lineEnd);
		String text = source.substring(node.start, end);
		if( text.trim().equals(line.trim())) {
			return null;
		}
		return new Statement(node.start-lineStart, end-node.start, text);
	}

	/**
	 * A statement is starting at line (the script's) and depth: the visits it ends (a returned
	 * function's, or this depth's when it's another line or the same statement again).
	 * @return true if it's part of the visit to the line the script stopped on at this depth
	 */
	private boolean stillVisiting(int line, int depth, Ast.Node node) {
		// (the functions returned from)
		visits.tailMap(depth, false).clear();
		Visit v = visits.get(depth);
		if( v == null ) {
			return false;
		}
		if( line == v.line && v.started.add(node)) {
			return true;
		}
		visits.remove(depth);
		return false;
	}

	@Override
	public synchronized boolean isBreakpoint(Point linePt,ShellContext ctx) {
		if( !active || evaluating ) {
			return false;
		}
		int depth = ctx.callFrames.size();
		if( stillVisiting(linePt.x, depth, ctx.getLastNode())) {
			// the rest of the line it stopped on: its breakpoint doesn't stop it again
			return false;
		}
		if( steppingOver && depth <= stepDepth && !visits.containsKey(stepDepth)) {
			// Step Over is done: the line it stepped over is (at a statement on another line, the
			// same one again, or back in a caller); a breakpoint here counts its hit too
			breakpointHit(linePt, ctx);
			return true;
		}
		if( runToLine >= 0 && editorLine(linePt.x) == runToLine ) {
			// Run to Line's line (a breakpoint here counts its hit too)
			breakpointHit(linePt, ctx);
			return true;
		}
		return breakpointHit(linePt, ctx);
	}

	/** a breakpoint on the line, enabled, whose condition holds and whose hit count is reached */
	private boolean breakpointHit(Point linePt,ShellContext ctx) {
		if( ignoreBreakpoints ) {
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
		// as Loupe (fsh-loupe's FshBackend and parley-loupe's BreakpointTable) and the Debug
		// Adapter Protocol: the condition first; only a hit where it's true counts; then the
		// hit count (stop on hit number N and every one after)
		if( bp.hasCondition() && !conditionHolds(bp, line, ctx)) {
			return false;
		}
		return bp.countHit();
	}

	/** True if the condition (a command, such as [ $i -gt 3 ]) succeeds; an error is true, as Loupe's, reported once a run. */
	private boolean conditionHolds(Breakpoint bp, int line, ShellContext ctx) {
		evaluating = true;
		try {
			return Executor.test(ctx, bp.getCondition());
		} catch (Exception e) {
			if( bp.reportConditionError()) {
				listener.conditionFailed(bp, line, e);
			}
			return true;
		} finally {
			evaluating = false;
		}
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
		currentSource = source;
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
		if( state == RunState.AtBreakpoint ) {
			// stopped (at the step's end, a breakpoint, a watchpoint or Suspend): the step is
			// over (and Run to Line, wherever it stopped), and the script is visiting this line
			steppingOver = false;
			runToLine = -1;
			pausedStatement = steppingInto ? statementOnLine(current, currentSource) : null;
			steppingInto = false;
			if( current != null && currentCtx != null ) {
				int depth = currentCtx.callFrames.size();
				visits.tailMap(depth, false).clear();
				visits.put(depth, new Visit(current.line, current));
			}
		}
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
