package us.bringardner.fsh.ide.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import us.bringardner.fsh.Console;
import us.bringardner.fsh.FshList;
import us.bringardner.fsh.ShellContext;
import us.bringardner.fsh.Argument;

/** Showing and changing a paused script's variables. */
public class TestDebugVariables {

	/**
	 * Windows names its search path Path and ignores case when it is asked for a variable; a script's
	 * own variable that differs from an environment variable only in case (path) is its own, not fsh's.
	 */
	@Test
	public void aVariableThatDiffersFromTheEnvironmentsOnlyInCaseIsTheScripts() {
		String mixed = null;
		for(String key : System.getenv().keySet()) {
			if( !key.equals(key.toLowerCase()) && !key.equals(key.toUpperCase())
					&& System.getenv().keySet().stream().noneMatch(k -> k.equals(key.toLowerCase()))) {
				mixed = key;
				break;
			}
		}
		org.junit.jupiter.api.Assumptions.assumeTrue(mixed != null, "no environment variable with mixed case");
		String value = System.getenv(mixed);
		String lower = mixed.toLowerCase();
		assertTrue(DebugVariables.isFshDefined(mixed));
		assertTrue(DebugVariables.isInheritedEnvironment(mixed, value));
		assertFalse(DebugVariables.isFshDefined(lower), lower + " is the script's");
		assertFalse(DebugVariables.isInheritedEnvironment(lower, value), lower + " is the script's");
	}

	/**
	 * A variable the login shell's environment gives a script (see ShellEnvironment), which an
	 * IDE opened from the Finder doesn't have itself, is fsh's: not the script's own.
	 */
	@Test
	public void theLoginShellsEnvironmentIsFshs() {
		String name = "FSH_IDE_TEST_ONLY_IN_THE_LOGIN_SHELL";
		assertFalse(DebugVariables.isFshDefined(name));
		ShellEnvironment.setForTests(Map.of(name, "/a/b"));
		try {
			assertTrue(DebugVariables.isFshDefined(name));
			assertTrue(DebugVariables.isInheritedEnvironment(name, "/a/b"));
			// (changed by the script: still fsh's, but no longer as inherited)
			assertFalse(DebugVariables.isInheritedEnvironment(name, "/c"));
			Map<String,Object> vars = ScriptRun.startVariables("fsh", "");
			vars.put("mine", "1");
			List<String> fsh = DebugVariables.list(vars, true, true).stream().map(Variable::getName).collect(Collectors.toList());
			List<String> user = DebugVariables.list(vars, true, false).stream().map(Variable::getName).collect(Collectors.toList());
			assertTrue(fsh.contains(name), fsh.toString());
			assertEquals(List.of("mine"), user);
		} finally {
			ShellEnvironment.setForTests(null);
		}
		assertFalse(DebugVariables.isFshDefined(name));
	}

	@Test
	public void listedByNameWithTheirTypes() {
		List<Variable> list = DebugVariables.list(Map.of("b", 2, "a", "x"));
		assertEquals(List.of("a", "b"), list.stream().map(Variable::getName).collect(Collectors.toList()));
		assertEquals("Integer", DebugVariables.typeName(2));
		assertEquals("", DebugVariables.typeName(null));
		assertEquals(List.of(), DebugVariables.list(null));
	}

	@Test
	public void setsVariablesAndPositionalParameters() {
		Console console = new Console();
		FshList args = new FshList();
		args.add(new Argument("script"));
		args.add(new Argument("first"));
		console.setPositionalParameters(true, args);
		ShellContext ctx = new ShellContext(console);

		DebugVariables.set(ctx, "name", "value");
		assertEquals("value", String.valueOf(ctx.getVariables().get("name")));

		DebugVariables.set(ctx, "$1", "changed");
		assertEquals("changed", String.valueOf(ctx.getAllPositionalParameters().get(1)));

		assertThrows(IllegalArgumentException.class, ()->DebugVariables.set(ctx, "$7", "x"));
		assertThrows(IllegalArgumentException.class, ()->DebugVariables.set(ctx, "$x", "x"));
		assertThrows(IllegalArgumentException.class, ()->DebugVariables.set(ctx, "$0", "x"));
	}

	@Test
	public void setsTheRunningFunctionsParameters() throws Exception {
		// stopped in f: $1 is f's; the script's stay as they were
		Console console = new Console();
		FshList args = new FshList();
		args.add(new Argument("script"));
		args.add(new Argument("a"));
		args.add(new Argument("b"));
		console.setPositionalParameters(true, args);
		java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
		console.setStdOut(new java.io.PrintStream(out, true));
		List<Throwable> problems = new java.util.ArrayList<>();
		console.setDebugContext(new us.bringardner.fsh.DebugContext() {
			private boolean done;

			@Override
			public synchronized void before(us.bringardner.fsh.syntax.Ast.Node node, String source, ShellContext ctx) {
				if( !done && node.text(source).startsWith("echo \"f:")) {
					done = true;
					try {
						DebugVariables.set(ctx, "$1", "changed");
						assertEquals("script", String.valueOf(ctx.getVariables().get("$0")));
						assertEquals("changed", String.valueOf(ctx.getVariables().get("$1")));
						assertThrows(IllegalArgumentException.class, ()->DebugVariables.set(ctx, "$2", "x"));
					} catch (Throwable e) {
						problems.add(e);
					}
				}
			}
		});
		console.executeScript("f() {\n  echo \"f: $1 $#\"\n}\nf world\necho \"main: $@\"\n");
		assertEquals(List.of(), problems);
		assertEquals("f: changed 1\nmain: a b\n", out.toString());
	}

	@Test
	public void theScriptsOwnStandOut() {
		String envName = System.getenv().keySet().iterator().next();
		Map<String,Object> vars = new java.util.HashMap<>();
		vars.put("count", 3);
		vars.put("OPTIND", "1");               // the shell's own, at its default
		vars.put("IFS", ",");                  // the shell's own, changed by the script
		vars.put(envName, System.getenv(envName));
		vars.put("$1", "x");
		List<String> shown = DebugVariables.list(vars, false).stream().map(Variable::getName).collect(Collectors.toList());
		assertEquals(List.of("$1", "IFS", "count"), shown);
		assertEquals(5, DebugVariables.list(vars, true).size());
	}

	@Test
	public void userAndFshDefinedAreSeparate() {
		String envName = System.getenv().keySet().iterator().next();
		Map<String,Object> vars = new java.util.HashMap<>();
		vars.put("count", 3);                  // the script's own
		vars.put("i", "a");                    // a loop variable
		vars.put("IFS", ",");                  // the shell's own, even when the script changed it
		vars.put("OPTIND", "1");
		vars.put("OLDPWD", "/tmp");
		vars.put(envName, System.getenv(envName));
		vars.put("$0", "script.sh");
		vars.put("$1", "x");
		List<String> user = DebugVariables.list(vars, true, false).stream().map(Variable::getName).collect(Collectors.toList());
		List<String> fsh = DebugVariables.list(vars, true, true).stream().map(Variable::getName).collect(Collectors.toList());
		assertEquals(List.of("$1", "count", "i"), user);
		assertEquals(5, fsh.size());
		assertEquals(true, fsh.containsAll(List.of("$0", "IFS", "OPTIND", "OLDPWD", envName)));
	}

	/** What a debugger shows for a name, or for text with a $ in it, of a paused script. */
	@Test
	public void theValueOfChosenText() {
		Console console = new Console();
		FshList args = new FshList();
		args.add(new Argument("script"));
		args.add(new Argument("first"));
		console.setPositionalParameters(true, args);
		ShellContext ctx = new ShellContext(console);
		ctx.setVariable("count", "3");
		ctx.setVariable("dir", "/a b");
		FshList list = new FshList();
		list.add("one");
		list.add("two");
		ctx.setVariable("list", list);

		assertEquals("3", DebugVariables.valueOf(ctx, "count"));
		assertEquals("3", DebugVariables.valueOf(ctx, " $count "));
		assertEquals("3", DebugVariables.valueOf(ctx, "${count}"));
		assertEquals("/a b/bin", DebugVariables.valueOf(ctx, "$dir/bin"));
		assertEquals("two", DebugVariables.valueOf(ctx, "${list[1]}"));
		assertEquals("2", DebugVariables.valueOf(ctx, "${#list[@]}"));
		assertEquals("first", DebugVariables.valueOf(ctx, "$1"));
		assertEquals("4", DebugVariables.valueOf(ctx, "$(( count + 1 ))"));
		// the whole array, as the variables view shows it
		assertEquals(String.valueOf(ctx.getVariables().get("list")), DebugVariables.valueOf(ctx, "list"));

		// nothing of the script's changes
		assertEquals("4", DebugVariables.valueOf(ctx, "$(( count += 1 ))"));
		assertEquals("new", DebugVariables.valueOf(ctx, "${fresh:=new}"));
		assertEquals("3", String.valueOf(ctx.getVariables().get("count")));
		assertFalse(ctx.getVariables().containsKey("fresh"));

		// no value to show, and why
		assertEquals("nosuch isn't set", assertThrows(IllegalArgumentException.class, ()->DebugVariables.valueOf(ctx, "nosuch")).getMessage());
		assertTrue(assertThrows(IllegalArgumentException.class, ()->DebugVariables.valueOf(ctx, "echo hi")).getMessage().startsWith("Not a variable"));
		for(String runs : new String[] {"$(date)", "`date`", "x$(( $(date) ))", "<(date)"}) {
			assertTrue(assertThrows(IllegalArgumentException.class, ()->DebugVariables.valueOf(ctx, runs)).getMessage().contains("would run a command"), runs);
		}
		assertThrows(IllegalArgumentException.class, ()->DebugVariables.valueOf(ctx, "  "));
	}
}
