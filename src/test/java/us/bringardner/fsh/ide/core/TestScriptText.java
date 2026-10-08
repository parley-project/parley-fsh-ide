package us.bringardner.fsh.ide.core;

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
}
