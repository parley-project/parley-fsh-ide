package us.bringardner.fsh.ide.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import us.bringardner.fsh.ConsoleIO;
import us.bringardner.fsh.ShellContext;

/** Running scripts with fsh, without any UI. */
public class TestScriptRun {

	@TempDir
	Path dir;

	static final class Result {
		private final int exitCode;
		private final ShellContext ctx;
		private final Exception error;

		Result(int exitCode, ShellContext ctx, Exception error) {
			this.exitCode = exitCode;
			this.ctx = ctx;
			this.error = error;
		}

		public int exitCode() {
			return exitCode;
		}

		public ShellContext ctx() {
			return ctx;
		}

		public Exception error() {
			return error;
		}

		@Override public boolean equals(Object x) {
			if (this == x) return true;
			if (!(x instanceof Result)) return false;
			Result o = (Result) x;
			return exitCode == o.exitCode && java.util.Objects.equals(ctx, o.ctx) && java.util.Objects.equals(error, o.error);
		}

		@Override public int hashCode() {
			return java.util.Objects.hash(exitCode, ctx, error);
		}

		@Override public String toString() {
			return "Result[" + "exitCode=" + exitCode + ", " + "ctx=" + ctx + ", " + "error=" + error + "]";
		}
	}

	private final ByteArrayOutputStream out = new ByteArrayOutputStream();
	private final ByteArrayOutputStream err = new ByteArrayOutputStream();

	private ScriptRun run(String code, CompletableFuture<Result> result) {
		return new ScriptRun(code, (run, exitCode, ctx, error)->result.complete(new Result(exitCode, ctx, error)))
				.console(new ByteArrayInputStream(new byte[0]), new PrintStream(out, true), new PrintStream(err, true));
	}

	private Result runToEnd(ScriptRun run, CompletableFuture<Result> result) throws Exception {
		run.start();
		Result ret = result.get(30, TimeUnit.SECONDS);
		assertTrue(run.join(5000));
		return ret;
	}

	private String out() {
		return out.toString(StandardCharsets.UTF_8);
	}

	@Test
	public void runsWithArguments() throws Exception {
		CompletableFuture<Result> result = new CompletableFuture<>();
		Result r = runToEnd(run("echo $# $1 $2", result).arguments("  a   b "), result);
		assertNull(r.error());
		assertEquals(0, r.exitCode());
		assertEquals("2 a b\n", out());
	}

	@Test
	public void noArgumentsForABlankField() throws Exception {
		CompletableFuture<Result> result = new CompletableFuture<>();
		runToEnd(run("echo $#", result).arguments(""), result);
		assertEquals("0\n", out());
	}

	@Test
	public void variablesAreLeftInTheContext() throws Exception {
		CompletableFuture<Result> result = new CompletableFuture<>();
		Result r = runToEnd(run("x=5\n", result), result);
		assertNotNull(r.ctx());
		assertEquals("5", ""+r.ctx().getVariables().get("x"));
	}

	@Test
	public void redirectFileIsWrittenAndClosed() throws Exception {
		Path file = dir.resolve("out.txt");
		CompletableFuture<Result> result = new CompletableFuture<>();
		runToEnd(run("echo hi", result).redirects("", file.toString(), ""), result);
		assertEquals("hi\n", Files.readString(file));
		assertEquals("", out());
	}

	@Test
	public void aMissingInputFileIsReported() throws Exception {
		CompletableFuture<Result> result = new CompletableFuture<>();
		Result r = runToEnd(run("cat", result).redirects(dir.resolve("none.txt").toString(), "", ""), result);
		assertNotNull(r.error());
		assertNull(r.ctx());
	}

	@Test
	public void blankCodeJustFinishes() throws Exception {
		CompletableFuture<Result> result = new CompletableFuture<>();
		Result r = runToEnd(run("  \n", result), result);
		assertEquals(0, r.exitCode());
		assertNull(r.ctx());
	}

	@Test
	public void cancelStopsALoop() throws Exception {
		CompletableFuture<Result> result = new CompletableFuture<>();
		DebugSession session = new DebugSession(line->null, new RecordingListener());
		ScriptRun run = run("while true\ndo\n  x=1\ndone\n", result).debug(session);
		run.start();
		Thread.sleep(300);
		assertTrue(run.isRunning());
		run.cancel();
		result.get(10, TimeUnit.SECONDS);
		assertTrue(run.join(5000));
		assertTrue(run.isCanceled());
	}

	@Test
	public void cancelBeforeTheShellIsReady() throws Exception {
		// Stop pressed while the run is still setting up its shell (before its job exists): the
		// script must not start afterwards
		CompletableFuture<Result> result = new CompletableFuture<>();
		ScriptRun run = run("while true\ndo\n  x=1\ndone\n", result);
		run.start();
		run.cancel();
		result.get(10, TimeUnit.SECONDS);
		assertTrue(run.join(5000));
		assertTrue(run.isCanceled());
	}

	@Test
	public void exitEndsTheScriptNotTheProgram() throws Exception {
		CompletableFuture<Result> result = new CompletableFuture<>();
		Result r = runToEnd(run("echo a\nexit 3\necho b\n", result), result);
		assertEquals(3, r.exitCode());
		assertEquals("a\n", out());
	}

	@Test
	public void cancelStopsAScriptWaitingForInput() throws Exception {
		CompletableFuture<Result> result = new CompletableFuture<>();
		// typed input never comes
		ConsoleIO console = new ConsoleIO(Runnable::run, ()->false);
		ScriptRun run = new ScriptRun("read x\necho \"got $x\"\n", (r, exitCode, ctx, error)->result.complete(new Result(exitCode, ctx, error)))
				.console(console.getStdIn(), new PrintStream(out, true), new PrintStream(err, true))
				.debug(new DebugSession(line->null, new RecordingListener()));
		run.start();
		Thread.sleep(300);
		assertTrue(run.isRunning());
		run.cancel();
		result.get(10, TimeUnit.SECONDS);
		assertTrue(run.join(5000));
	}
}
