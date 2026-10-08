package us.bringardner.fsh.ide.core;

import java.util.List;

import us.bringardner.fsh.syntax.Ast;
import us.bringardner.fsh.syntax.Parser;
import us.bringardner.fsh.syntax.SyntaxError;

/** Parses scripts as fsh runs them, for the syntax check and the syntax-tree view. */
public class ScriptParser {

	private ScriptParser() {
	}

	/**
	 * The syntax tree of code, or an empty script if it does not parse: the syntax error (bash's
	 * message; fsh stops at the first, as bash does) is added to errors, with its line counted
	 * from code's first line.
	 */
	public static SyntaxNode parse(String code, List<CompileError> errors) {
		try {
			Ast.Sequence seq = Parser.parse(code);
			return SyntaxNode.of(seq, code);
		} catch (SyntaxError e) {
			errors.add(new CompileError(e.line, 0, e.getMessage()));
			return new SyntaxNode("script", "", 0);
		}
	}
}
