package us.bringardner.fsh.ide.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

/** Breakpoints, stepping and the statement log, with a script run by fsh and no UI. */
public class TestDebugSession {

	private final Map<Integer,Breakpoint> breakpoints = new ConcurrentHashMap<>();
	private final RecordingListener listener = new RecordingListener();
	private final DebugSession session = new DebugSession(breakpoints::get, listener);
	private final CompletableFuture<Integer> finished = new CompletableFuture<>();

	private Breakpoint breakpoint(int line) {
		Breakpoint bp = new Breakpoint();
		bp.setLine(line);
		bp.reset();
		breakpoints.put(line, bp);
		return bp;
	}

	private ScriptRun start(String code) {
		session.setActive(true);
		ScriptRun run = new ScriptRun(code, (r, exitCode, ctx, error)->finished.complete(exitCode))
				.console(new ByteArrayInputStream(new byte[0]), new PrintStream(new ByteArrayOutputStream()), System.err)
				.debug(session);
		run.start();
		return run;
	}

	private RecordingListener.Pause nextPause() throws InterruptedException {
		RecordingListener.Pause p = listener.pauses.poll(10, TimeUnit.SECONDS);
		assertNotNull(p, "didn't stop");
		return p;
	}

	@Test
	public void stopsAtABreakpointWithTheVariablesSoFar() throws Exception {
		breakpoint(2);
		start("x=1\necho a\nx=2\necho b\n");
		RecordingListener.Pause p = nextPause();
		assertEquals(2, p.line());
		assertEquals("1", ""+p.variables().get("x"));
		assertEquals(List.of("0: x=1", "1: echo a"), listener.statements);

		session.resume();
		assertEquals(0, finished.get(10, TimeUnit.SECONDS));
		assertEquals(1, listener.resumes);
		assertEquals(List.of("0: x=1", "1: echo a", "2: x=2", "3: echo b"), listener.statements);
		assertNull(listener.pauses.poll());
	}

	@Test
	public void stepsFromABreakpoint() throws Exception {
		breakpoint(1);
		start("x=1\nx=2\nx=3\n");
		assertEquals(1, nextPause().line());
		session.stepOver();
		assertEquals(2, nextPause().line());
		session.resume();
		finished.get(10, TimeUnit.SECONDS);
	}

	@Test
	public void linesOfARunSelection() throws Exception {
		// the selection starts on editor line 5
		session.setFirstLine(5);
		breakpoint(6);
		start("y=1\ny=2\n");
		assertEquals(6, nextPause().line());
		session.resume();
		finished.get(10, TimeUnit.SECONDS);
		assertEquals(List.of("5: y=1", "6: y=2"), listener.statements);
	}

	@Test
	public void disabledAndInactiveDontStop() throws Exception {
		breakpoint(0).setEnabled(false);
		start("x=1\n");
		assertEquals(0, finished.get(10, TimeUnit.SECONDS));
		assertTrue(listener.pauses.isEmpty());

		CompletableFuture<Integer> again = new CompletableFuture<>();
		breakpoint(0);
		session.setActive(false);
		new ScriptRun("x=1\n", (r, exitCode, ctx, error)->again.complete(exitCode))
				.console(new ByteArrayInputStream(new byte[0]), new PrintStream(new ByteArrayOutputStream()), System.err)
				.debug(session).start();
		again.get(10, TimeUnit.SECONDS);
		assertTrue(listener.pauses.isEmpty());
	}

	@Test
	public void conditionalBreakpoint() throws Exception {
		Breakpoint bp = breakpoint(2);
		bp.setConditional(true);
		bp.setCondition("[ $i == 3 ]");
		start("for i in 1 2 3 4\ndo\n  x=$i\ndone\n");
		RecordingListener.Pause p = nextPause();
		assertEquals("3", ""+p.variables().get("i"));
		session.resume();
		finished.get(10, TimeUnit.SECONDS);
		assertTrue(listener.pauses.isEmpty());
		assertTrue(listener.conditionErrors.isEmpty());
	}

	@Test
	public void aBadConditionIsReportedOnce() throws Exception {
		Breakpoint bp = breakpoint(2);
		bp.setConditional(true);
		bp.setCondition("[ (((");
		start("for i in 1 2 3\ndo\n  x=$i\ndone\n");
		for(int i=0; i < 3; i++) {
			nextPause();
			session.resume();
		}
		finished.get(10, TimeUnit.SECONDS);
		assertEquals(1, listener.conditionErrors.size());
	}

	@Test
	public void terminateIsHandedToTheUi() {
		session.terminate();
		assertEquals(1, listener.terminates);
	}

	@Test
	public void cancelWhileStopped() throws Exception {
		breakpoint(1);
		ScriptRun run = start("x=1\nx=2\nx=3\n");
		nextPause();
		run.cancel();
		finished.get(10, TimeUnit.SECONDS);
		assertTrue(run.join(5000));
		assertTrue(listener.statements.size() < 3, ""+listener.statements);
	}

	@Test
	public void runToEndDoesntStopAgain() throws Exception {
		breakpoint(1);
		start("for i in a b c; do\n  x=$i\ndone\n");
		assertEquals(1, nextPause().line());
		session.runToEnd();
		assertEquals(0, finished.get(10, TimeUnit.SECONDS));
		assertNull(listener.pauses.poll());
		// still logged
		assertEquals(3, listener.statements.stream().filter(st->st.startsWith("1: ")).count());
	}

	@Test
	public void aNewRunStopsAtBreakpointsAgain() throws Exception {
		session.runToEnd();
		breakpoint(0);
		start("x=1\n");
		assertEquals(0, nextPause().line());
		session.resume();
		assertEquals(0, finished.get(10, TimeUnit.SECONDS));
	}

	private Watchpoint watch(String name, boolean read, boolean write) {
		Watchpoint wp = new Watchpoint(name, read, write);
		session.getWatchpoints().add(wp);
		return wp;
	}

	@Test
	public void aWriteWatchpointStopsOnTheLineThatSetsIt() throws Exception {
		watch("x", false, true);
		start("y=1\necho $y\nx=5\necho $x\nx=6\n");
		RecordingListener.Pause p = nextPause();
		assertEquals(2, p.line());
		// stopped after the change: the new value shows
		assertEquals("5", ""+p.variables().get("x"));
		assertEquals(List.of("x written (value: 5)"), listener.watchpointHits);
		session.resume();
		assertEquals(4, nextPause().line());
		session.resume();
		assertEquals(0, finished.get(10, TimeUnit.SECONDS));
		// the read on line 3 doesn't stop a write watchpoint
		assertNull(listener.pauses.poll());
	}

	@Test
	public void aReadWatchpointStopsOnTheLineThatReadsIt() throws Exception {
		watch("x", true, false);
		start("x=5\ny=2\necho \"$x\"\n");
		RecordingListener.Pause p = nextPause();
		assertEquals(2, p.line());
		assertEquals(List.of("x read"), listener.watchpointHits);
		session.resume();
		assertEquals(0, finished.get(10, TimeUnit.SECONDS));
		assertNull(listener.pauses.poll());
	}

	@Test
	public void readAndWriteBothStop() throws Exception {
		watch("$n", true, true);
		start("n=1\n(( n = n + 1 ))\nunset n\n");
		assertEquals(0, nextPause().line());
		session.resume();
		// the arithmetic reads it, and writes it
		assertEquals(1, nextPause().line());
		session.resume();
		assertEquals(1, nextPause().line());
		session.resume();
		assertEquals(2, nextPause().line());
		session.resume();
		assertEquals(0, finished.get(10, TimeUnit.SECONDS));
		assertEquals(List.of("n written (value: 1)", "n read", "n written (value: 2)", "n unset"), listener.watchpointHits);
	}

	@Test
	public void anArraysElementIsAWriteOfTheArray() throws Exception {
		watch("a", false, true);
		start("b=1\na[2]=x\n");
		assertEquals(1, nextPause().line());
		session.resume();
		assertEquals(0, finished.get(10, TimeUnit.SECONDS));
	}

	@Test
	public void insideAFunctionAndALoop() throws Exception {
		watch("total", false, true);
		start("add() {\n  total=$(( total + $1 ))\n}\nfor i in 1 2; do\n  add $i\ndone\n");
		assertEquals(1, nextPause().line());
		session.resume();
		assertEquals(1, nextPause().line());
		session.resume();
		assertEquals(0, finished.get(10, TimeUnit.SECONDS));
		assertEquals(List.of("total written (value: 1)", "total written (value: 3)"), listener.watchpointHits);
	}

	@Test
	public void disabledOrOtherVariablesDontStop() throws Exception {
		watch("x", true, true).setEnabled(false);
		watch("xy", true, true);
		start("x=1\necho $x\n");
		assertEquals(0, finished.get(10, TimeUnit.SECONDS));
		assertTrue(listener.pauses.isEmpty());
	}

	@Test
	public void runToEndIgnoresWatchpoints() throws Exception {
		watch("x", false, true);
		start("x=1\nx=2\nx=3\n");
		assertEquals(0, nextPause().line());
		session.runToEnd();
		assertEquals(0, finished.get(10, TimeUnit.SECONDS));
		assertNull(listener.pauses.poll());
	}

	@Test
	public void stepsFromAWatchpoint() throws Exception {
		watch("x", false, true);
		start("x=1\ny=2\nz=3\n");
		assertEquals(0, nextPause().line());
		session.stepOver();
		assertEquals(1, nextPause().line());
		session.resume();
		assertEquals(0, finished.get(10, TimeUnit.SECONDS));
	}

	@Test
	public void theDebuggersOwnLooksAndChangesDontStop() throws Exception {
		watch("x", true, true);
		breakpoint(1);
		start("y=0\ny=1\n");
		assertEquals(1, nextPause().line());
		// as the variables view does while the script is paused
		DebugVariables.set(listener.lastContext, "x", "9");
		assertEquals("9", ""+listener.lastContext.getVariable("x"));
		session.resume();
		assertEquals(0, finished.get(10, TimeUnit.SECONDS));
		assertTrue(listener.watchpointHits.isEmpty());
		assertNull(listener.pauses.poll());
	}

	@Test
	public void describesAHit() {
		assertEquals("x read", DebugSession.describeHit("x", false, "1"));
		assertEquals("x unset", DebugSession.describeHit("x", true, null));
		assertEquals("x written (value: a⏎b)", DebugSession.describeHit("x", true, "a\nb"));
	}
}
