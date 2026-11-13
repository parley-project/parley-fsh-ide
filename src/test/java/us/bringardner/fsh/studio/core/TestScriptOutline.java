package us.bringardner.fsh.studio.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import us.bringardner.fsh.studio.core.CompileError;
import us.bringardner.fsh.studio.core.ScriptOutline;
import us.bringardner.fsh.studio.core.ScriptParser;

/** The outline the IDE shows: the script's functions and variables, with their lines. */
public class TestScriptOutline {

	private static ScriptOutline outline(String code) {
		List<CompileError> errors = new ArrayList<>();
		ScriptOutline ret = ScriptOutline.of(ScriptParser.parse(code, errors));
		assertEquals(List.of(), errors);
		return ret;
	}

	private static List<String> shown(List<ScriptOutline.Item> items) {
		return items.stream().map(ScriptOutline.Item::toString).collect(Collectors.toList());
	}

	@Test
	public void functionsAndVariablesWithTheirLines() {
		ScriptOutline o = outline(
				"count=0\n"+                       // 1
				"greet() {\n"+                     // 2
				"  local who=$1 greeting\n"+       // 3
				"  greeting=hello\n"+              // 4
				"  echo $greeting $who\n"+         // 5
				"}\n"+                             // 6
				"for name in a b; do\n"+           // 7
				"  greet $name\n"+                 // 8
				"  count=$((count+1))\n"+          // 9
				"done\n"+                          // 10
				"list[2]=x\n"+                     // 11
				"total+=1\n");                     // 12
		assertEquals(List.of("greet() 2"), shown(o.getFunctions()));
		assertEquals(List.of("who 3", "greeting 3"), shown(o.getFunctions().get(0).getChildren()));
		// in the order first set, each once
		assertEquals(List.of("count 1", "name 7", "list 11", "total 12"), shown(o.getVariables()));
	}

	@Test
	public void readGetoptsAndDeclare() {
		ScriptOutline o = outline(
				"read -r -p 'Name? ' answer other\n"+
				"while getopts \"ab:\" opt; do :; done\n"+
				"declare -i n\n"+
				"export PATH\n"+
				"echo plain words\n");
		assertEquals(List.of("answer 1", "other 1", "opt 2", "n 3", "PATH 4"), shown(o.getVariables()));
		assertTrue(o.getFunctions().isEmpty());
	}

	@Test
	public void aFunctionInAFunctionAndTheFunctionKeyword() {
		ScriptOutline o = outline(
				"function outer {\n"+
				"  x=1\n"+
				"  inner() { y=2; }\n"+
				"}\n");
		assertEquals(List.of("outer() 1", "inner() 3"), shown(o.getFunctions()));
		assertEquals(List.of("x 2"), shown(o.getFunctions().get(0).getChildren()));
		assertEquals(List.of("y 3"), shown(o.getFunctions().get(1).getChildren()));
		assertEquals("outer()", o.getFunctions().get(0).label());
		assertTrue(o.getVariables().isEmpty());
	}

	@Test
	public void nothingForNoScript() {
		assertTrue(ScriptOutline.of(null).isEmpty());
		assertTrue(outline("echo hi\n").isEmpty());
	}
}
