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

	/**
	 * The environment a terminal would give (ShellEnvironment): the script and its programs have
	 * it; and FSH_ENV's file runs first, as for fsh script.
	 */
	@Test
	public void runsWithTheShellEnvironmentAndFshEnv() throws Exception {
		Path rc = dir.resolve("env.fsh");
		Files.writeString(rc, "greet() { echo \"hello $WHO\"; }\n");
		ShellEnvironment.setForTests(java.util.Map.of("WHO", "tony", "FSH_ENV", rc.toString()));
		try {
			CompletableFuture<Result> result = new CompletableFuture<>();
			Result r = runToEnd(run("greet\nsh -c 'echo child $WHO'\n", result), result);
			assertNull(r.error());
			assertEquals("hello tony\nchild tony\n", out());
		} finally {
			ShellEnvironment.setForTests(null);
		}
	}

	/**
	 * What a debugger shows while nothing runs: the variables the next run starts with, as a
	 * run's shell has them, without running FSH_ENV's file (and so nothing of the user's).
	 */
	@Test
	public void theVariablesTheNextRunStartsWith() throws Exception {
		Path marker = dir.resolve("ran");
		Path rc = dir.resolve("env.fsh");
		Files.writeString(rc, "FROM_RC=yes\necho ran > '"+marker+"'\n");
		ShellEnvironment.setForTests(java.util.Map.of("WHO", "tony", "FSH_ENV", rc.toString()));
		java.util.function.Function<String,String> aliases = us.bringardner.fsh.syntax.Parser.aliases;
		java.util.function.BooleanSupplier posixMode = us.bringardner.fsh.syntax.Parser.posixMode;
		try {
			java.util.Map<String,Object> vars = ScriptRun.startVariables("/scripts/hello.fsh", " a  b ");
			// the shell's own, the login shell's environment, $0 and the arguments
			assertEquals(" \t\n", String.valueOf(vars.get("IFS")));
			assertEquals("tony", String.valueOf(vars.get("WHO")));
			assertEquals("/scripts/hello.fsh", String.valueOf(vars.get("$0")));
			assertEquals("a", String.valueOf(vars.get("$1")));
			assertEquals("b", String.valueOf(vars.get("$2")));
			// the startup file isn't run for this
			assertNull(vars.get("FROM_RC"));
			assertTrue(!Files.exists(marker), "FSH_ENV's file ran");
			// fsh's parser still asks the shell it asked before
			assertTrue(aliases == us.bringardner.fsh.syntax.Parser.aliases);
			assertTrue(posixMode == us.bringardner.fsh.syntax.Parser.posixMode);

			// the same as a run has before its first statement (apart from what its startup file set)
			CompletableFuture<Result> result = new CompletableFuture<>();
			Result r = runToEnd(run(":\n", result).scriptName("/scripts/hello.fsh").arguments(" a  b "), result);
			assertNull(r.error());
			java.util.Map<String,Object> ran = r.ctx().getVariables();
			for(String name : new String[] {"IFS", "WHO", "$0", "$1", "$2", "HOME", "PATH", "OPTIND"}) {
				assertEquals(String.valueOf(ran.get(name)), String.valueOf(vars.get(name)), name);
			}
			assertEquals("yes", String.valueOf(ran.get("FROM_RC")));
		} finally {
			ShellEnvironment.setForTests(null);
		}
	}

	/** whenKnown: at once when the environment is known (or wasn't asked for). */
	@Test
	public void whenTheEnvironmentIsKnown() {
		try {
			java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
			ShellEnvironment.setForTests(null);
			ShellEnvironment.whenKnown(calls::incrementAndGet);
			assertEquals(1, calls.get());
			ShellEnvironment.setForTests(java.util.Map.of("WHO", "tony"));
			ShellEnvironment.whenKnown(calls::incrementAndGet);
			assertEquals(2, calls.get());
		} finally {
			ShellEnvironment.setForTests(null);
		}
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
	public void lineNumbersAreTheScriptsOwn() throws Exception {
		// $LINENO and a function's caller line are the editor's (nothing is put in front of the code)
		CompletableFuture<Result> result = new CompletableFuture<>();
		runToEnd(run("echo $LINENO\nf() { echo ${BASH_LINENO[0]}; }\n\nf\n", result), result);
		assertEquals("1\n4\n", out());
	}

	@Test
	public void aSelectionRunsOnItsOwnLines() throws Exception {
		// lines 3 and 4 of a script, run on their own: $LINENO is still the script's
		CompletableFuture<Result> result = new CompletableFuture<>();
		runToEnd(run(ScriptText.atLine("echo $LINENO\necho $LINENO\n", 2), result), result);
		assertEquals("3\n4\n", out());
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
