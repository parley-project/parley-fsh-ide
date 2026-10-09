package us.bringardner.fsh.ide.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import us.bringardner.fsh.Console;
import us.bringardner.fsh.FshList;
import us.bringardner.fsh.ShellContext;
import us.bringardner.fsh.Argument;

/** Showing and changing a paused script's variables, the same in both IDEs. */
public class TestDebugVariables {

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
}
