package us.bringardner.fsh.ide.core;

import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;

import us.bringardner.fsh.ShellContext;

/** A DebugSession.Listener that records what it's told. */
class RecordingListener implements DebugSession.Listener {

	static final class Pause { private final int line; private final Map<String,Object> variables; Pause(int line, Map<String,Object> variables) { this.line = line; this.variables = variables; } public int line() { return line; } public Map<String,Object> variables() { return variables; } @Override public boolean equals(Object x) { if (this == x) return true; if (!(x instanceof Pause)) return false; Pause o = (Pause) x; return line == o.line && java.util.Objects.equals(variables, o.variables); } @Override public int hashCode() { return java.util.Objects.hash(line, variables); } @Override public String toString() { return "Pause[" + "line=" + line + ", " + "variables=" + variables + "]"; }}

	final BlockingQueue<Pause> pauses = new LinkedBlockingQueue<>();
	final List<String> statements = new CopyOnWriteArrayList<>();
	final List<Exception> conditionErrors = new CopyOnWriteArrayList<>();
	volatile int resumes;
	volatile int terminates;

	@Override
	public void paused(int line, ShellContext ctx, Map<String, Object> variables) {
		pauses.add(new Pause(line, variables));
	}

	@Override
	public void statement(int line, String text) {
		statements.add(line+": "+text);
	}

	@Override
	public void conditionFailed(Breakpoint bp, int line, Exception error) {
		conditionErrors.add(error);
	}

	@Override
	public void resuming() {
		resumes++;
	}

	@Override
	public void terminateRequested() {
		terminates++;
	}
}
