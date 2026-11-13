package us.bringardner.fsh.studio.core;

import us.bringardner.fsh.debug.ScriptText;
import us.bringardner.fsh.studio.core.CompileError;
import us.bringardner.fsh.studio.core.ScriptParser;
import us.bringardner.fsh.studio.core.SyntaxNode;
import us.bringardner.fsh.syntax.Ast;
import us.bringardner.fsh.syntax.Parser;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

public class TestScriptText {

	@Test
	public void cleanKeepsNonAsciiText() {
		String text = "echo \"café → 日本 ✓\"\n\tls\r\n";
		assertEquals(text, ScriptText.clean(text));
	}

	@Test
	public void cleanDropsInvisibleCharacters() {
		assertEquals("echo hi\n", ScriptText.clean("﻿echo​ hi\u0007\n"));
		assertEquals("echo hi", ScriptText.clean("echo hi"));
		assertNull(ScriptText.clean(null));
	}

	@Test
	public void noArgumentsForABlankField() {
		assertEquals(List.of(), ScriptText.splitArguments(""));
		assertEquals(List.of(), ScriptText.splitArguments("   "));
		assertEquals(List.of(), ScriptText.splitArguments(null));
	}

	@Test
	public void argumentsSplitOnAnyWhiteSpace() {
		assertEquals(List.of("a", "b", "c"), ScriptText.splitArguments("  a  b\tc "));
	}

	@Test
	public void statementTextIsTheFirstLine() {
		String code = "while true\ndo\n  echo x\ndone\n";
		Ast.Sequence seq = Parser.parse(code);
		assertEquals("while true ...", ScriptText.statementText(seq.items.get(0).command, code));
	}

	@Test
	public void longStatementTextIsCutShort() {
		String echo = "echo "+"x".repeat(500);
		Ast.Sequence seq = Parser.parse(echo);
		String text = ScriptText.statementText(seq.items.get(0).command, echo);
		assertTrue(text.length() < 200, text);
		assertTrue(text.startsWith("echo xxx") && text.endsWith(" ..."), text);
	}

	@Test
	public void parsesIntoASyntaxTree() {
		List<CompileError> errors = new ArrayList<>();
		SyntaxNode tree = ScriptParser.parse("x=1\nif [ $x = 1 ]; then\n  echo yes | wc -l\nfi\n", errors);
		assertEquals(List.of(), errors);
		assertEquals("script", tree.getKind());
		assertEquals(2, tree.getChildCount());
		assertEquals("assignment", tree.getChild(0).getChild(0).getKind());
		SyntaxNode ifNode = tree.getChild(1);
		assertEquals("if", ifNode.getKind());
		assertEquals(2, ifNode.getLine());
		SyntaxNode pipe = ifNode.getChild(1).getChild(0);
		assertEquals("pipeline", pipe.getKind());
		assertEquals(3, pipe.getLine());
		assertEquals(ifNode, pipe.getParent().getParent());
	}

	@Test
	public void aSyntaxErrorIsReportedWithItsLine() {
		List<CompileError> errors = new ArrayList<>();
		ScriptParser.parse("echo a\nif true; then\n  echo b\nfi fi\n", errors);
		assertEquals(1, errors.size());
		assertEquals(4, errors.get(0).line);
		assertEquals("syntax error near unexpected token `fi'", errors.get(0).msg);
	}

	@Test
	public void atLinePutsCodeOnItsLine() {
		assertEquals("\n\necho", ScriptText.atLine("echo", 2));
		assertEquals("echo", ScriptText.atLine("echo", 0));
		assertEquals(null, ScriptText.atLine(null, 3));
	}

	/** The variable a debugger's right-click menu offers, where the text shows it is one. */
	@Test
	public void theVariableAtAPlaceInTheText() {
		String code = "count=3; echo $count ${name}x ${#list[@]} ${!ref} $1 total+=1 arr[2]=x\nfor i in a; do echo done; done\nif [ a == b ]; then x=y; fi\n";
		java.util.function.Predicate<String> none = n->false;
		assertEquals("count", ScriptText.variableAt(code, code.indexOf("count="), none));
		assertEquals("count", ScriptText.variableAt(code, code.indexOf("count=")+5, none), "just after the name");
		assertEquals("count", ScriptText.variableAt(code, code.indexOf("$count")+3, none));
		assertEquals("name", ScriptText.variableAt(code, code.indexOf("name"), none));
		assertEquals("list", ScriptText.variableAt(code, code.indexOf("list"), none));
		assertEquals("ref", ScriptText.variableAt(code, code.indexOf("ref"), none));
		assertEquals("$1", ScriptText.variableAt(code, code.indexOf("$1")+1, none));
		assertEquals("total", ScriptText.variableAt(code, code.indexOf("total"), none));
		assertEquals("arr", ScriptText.variableAt(code, code.indexOf("arr"), none));
		assertEquals("x", ScriptText.variableAt(code, code.indexOf("x=y"), none));
		// not variables, as far as the text shows
		assertNull(ScriptText.variableAt(code, code.indexOf("echo"), none));
		assertNull(ScriptText.variableAt(code, code.indexOf("for i")+4, none), "a loop variable is known only by name");
		assertNull(ScriptText.variableAt(code, code.indexOf("y; fi"), none), "the value of x=y");
		assertNull(ScriptText.variableAt(code, code.indexOf("a =="), none));
		assertNull(ScriptText.variableAt(code, code.indexOf("; echo"), none));
		assertNull(ScriptText.variableAt(code, code.indexOf("2]"), none));
		// one the paused script has
		assertEquals("i", ScriptText.variableAt(code, code.indexOf("for i")+4, n->n.equals("i")));
		assertNull(ScriptText.variableAt(code, -1, none));
		assertNull(ScriptText.variableAt(null, 0, none));
		assertNull(ScriptText.variableAt(code, code.length()+1, none));
	}
}
