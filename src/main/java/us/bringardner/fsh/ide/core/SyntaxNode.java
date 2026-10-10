package us.bringardner.fsh.ide.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import us.bringardner.fsh.syntax.Ast;
import us.bringardner.fsh.syntax.Word;

/**
 * A node of a script's syntax tree, for the tree view: what kind it is (if, pipeline, a word ...),
 * its text, the line it starts on and its children. Built from fsh's syntax tree (Ast).
 */
public class SyntaxNode {

	private final String kind;
	private final String text;
	private final int line;
	private final List<SyntaxNode> children = new ArrayList<>();
	private SyntaxNode parent;

	public SyntaxNode(String kind, String text, int line) {
		this.kind = kind;
		this.text = text;
		this.line = line;
	}

	/** what kind of node: script, list, if, while, command, word, redirect ... */
	public String getKind() {
		return kind;
	}

	/** the node's text: the word, operator or keyword it stands for */
	public String getText() {
		return text;
	}

	/** the line it starts on (1 is the first), or 0 if it has none */
	public int getLine() {
		return line;
	}

	public List<SyntaxNode> getChildren() {
		return Collections.unmodifiableList(children);
	}

	public int getChildCount() {
		return children.size();
	}

	public SyntaxNode getChild(int i) {
		return children.get(i);
	}

	public SyntaxNode getParent() {
		return parent;
	}

	SyntaxNode add(SyntaxNode child) {
		if( child != null ) {
			child.parent = this;
			children.add(child);
		}
		return this;
	}

	@Override
	public String toString() {
		return kind+": "+text;
	}

	// ------------------------------------------------------------------ from Ast

	/** the tree of a parsed script, whose source is source */
	public static SyntaxNode of(Ast.Sequence script, String source) {
		return new Builder(source).sequence("script", script);
	}

	private static final class Builder {
		private final String source;

		Builder(String source) {
			this.source = source;
		}

		private String text(Ast.Node n) {
			int start = Math.max(0, Math.min(n.start, source.length()));
			int end = Math.max(start, Math.min(n.end, source.length()));
			String t = source.substring(start, end).trim();
			int nl = t.indexOf('\n');
			return nl < 0 ? t : t.substring(0, nl)+" ...";
		}

		SyntaxNode sequence(String kind, Ast.Sequence seq) {
			SyntaxNode ret = new SyntaxNode(kind, kind.equals("script") ? "" : text(seq), seq.line);
			for(Ast.Item item : seq.items) {
				SyntaxNode n = andOr(item.command);
				if( item.background ) {
					n = new SyntaxNode("background", "&", item.command.line).add(n);
				}
				ret.add(n);
			}
			return ret;
		}

		SyntaxNode andOr(Ast.AndOr ao) {
			if( ao.pipelines.size() == 1 ) {
				return pipeline(ao.pipelines.get(0));
			}
			SyntaxNode ret = new SyntaxNode("and-or", text(ao), ao.line);
			for (int i = 0; i < ao.pipelines.size(); i++) {
				if( i > 0 ) {
					ret.add(new SyntaxNode("operator", ao.ops.get(i-1), 0));
				}
				ret.add(pipeline(ao.pipelines.get(i)));
			}
			return ret;
		}

		SyntaxNode pipeline(Ast.Pipeline p) {
			if( p.commands.size() == 1 && !p.negated && !p.timed ) {
				return command(p.commands.get(0));
			}
			SyntaxNode ret = new SyntaxNode("pipeline", (p.timed ? "time " : "")+(p.negated ? "! " : "")+"|", p.line);
			for(Ast.Command c : p.commands) {
				ret.add(command(c));
			}
			return ret;
		}

		SyntaxNode command(Ast.Command c) {
			SyntaxNode ret;
			if( c instanceof Ast.SimpleCommand ) {
				Ast.SimpleCommand s = (Ast.SimpleCommand) c;
				ret = new SyntaxNode("command", text(s), s.line);
				for(Ast.Assignment a : s.assignments) {
					ret.add(assignment(a));
				}
				for(Word w : s.words) {
					ret.add(w.assignment != null ? assignment(w.assignment) : word(w));
				}
			} else if( c instanceof Ast.BraceGroup ) {
				Ast.BraceGroup g = (Ast.BraceGroup) c;
				ret = new SyntaxNode("group", "{ }", g.line).add(sequence("list", g.body));
			} else if( c instanceof Ast.Subshell ) {
				Ast.Subshell s = (Ast.Subshell) c;
				ret = new SyntaxNode("subshell", "( )", s.line).add(sequence("list", s.body));
			} else if( c instanceof Ast.If ) {
				Ast.If f = (Ast.If) c;
				ret = new SyntaxNode("if", "if", f.line);
				for (int i = 0; i < f.conditions.size(); i++) {
					ret.add(sequence(i == 0 ? "condition" : "elif", f.conditions.get(i)));
					ret.add(sequence("then", f.bodies.get(i)));
				}
				if( f.elseBody != null ) {
					ret.add(sequence("else", f.elseBody));
				}
			} else if( c instanceof Ast.Loop ) {
				Ast.Loop l = (Ast.Loop) c;
				ret = new SyntaxNode(l.until ? "until" : "while", l.until ? "until" : "while", l.line)
					.add(sequence("condition", l.condition)).add(sequence("do", l.body));
			} else if( c instanceof Ast.For ) {
				Ast.For f = (Ast.For) c;
				ret = new SyntaxNode("for", "for "+f.variable, f.line);
				ret.add(words("in", f.words, f.line));
				ret.add(sequence("do", f.body));
			} else if( c instanceof Ast.Select ) {
				Ast.Select s = (Ast.Select) c;
				ret = new SyntaxNode("select", "select "+s.variable, s.line);
				ret.add(words("in", s.words, s.line));
				ret.add(sequence("do", s.body));
			} else if( c instanceof Ast.ArithFor ) {
				Ast.ArithFor f = (Ast.ArithFor) c;
				ret = new SyntaxNode("for", "for (( "+f.init.raw+"; "+f.condition.raw+"; "+f.step.raw+" ))", f.line)
					.add(sequence("do", f.body));
			} else if( c instanceof Ast.Case ) {
				Ast.Case k = (Ast.Case) c;
				ret = new SyntaxNode("case", "case", k.line).add(word(k.subject));
				for(Ast.CaseClause cl : k.clauses) {
					StringBuilder pats = new StringBuilder();
					for(Word w : cl.patterns) {
						pats.append(pats.length() > 0 ? "|" : "").append(w.raw);
					}
					SyntaxNode clause = new SyntaxNode("pattern", pats+")", cl.line);
					clause.add(sequence("list", cl.body));
					if( cl.terminator != null ) {
						clause.add(new SyntaxNode("operator", cl.terminator, 0));
					}
					ret.add(clause);
				}
			} else if( c instanceof Ast.Arith ) {
				Ast.Arith a = (Ast.Arith) c;
				ret = new SyntaxNode("arithmetic", "(( "+a.expression.raw+" ))", a.line);
			} else if( c instanceof Ast.Cond ) {
				Ast.Cond k = (Ast.Cond) c;
				ret = new SyntaxNode("test", "[[ ]]", k.line).add(cond(k.expression, k.line));
			} else if( c instanceof Ast.FunctionDef ) {
				Ast.FunctionDef f = (Ast.FunctionDef) c;
				ret = new SyntaxNode("function", f.name, f.line).add(command(f.body));
			} else {
				ret = new SyntaxNode(c.getClass().getSimpleName(), text(c), c.line);
			}
			for(Ast.Redirect r : c.redirects) {
				ret.add(redirect(r));
			}
			return ret;
		}

		SyntaxNode cond(Ast.CondExpr e, int line) {
			if( e instanceof Ast.CondAnd ) {
				Ast.CondAnd a = (Ast.CondAnd) e;
				return new SyntaxNode("operator", "&&", line).add(cond(a.left(), line)).add(cond(a.right(), line));
			} else if( e instanceof Ast.CondOr ) {
				Ast.CondOr o = (Ast.CondOr) e;
				return new SyntaxNode("operator", "||", line).add(cond(o.left(), line)).add(cond(o.right(), line));
			} else if( e instanceof Ast.CondNot ) {
				return new SyntaxNode("operator", "!", line).add(cond(((Ast.CondNot) e).expression(), line));
			} else if( e instanceof Ast.CondUnary ) {
				Ast.CondUnary u = (Ast.CondUnary) e;
				return new SyntaxNode("operator", u.op(), line).add(word(u.operand()));
			} else if( e instanceof Ast.CondBinary ) {
				Ast.CondBinary b = (Ast.CondBinary) e;
				return new SyntaxNode("operator", b.op(), line).add(word(b.left())).add(word(b.right()));
			} else if( e instanceof Ast.CondWord ) {
				return word(((Ast.CondWord) e).word());
			}
			// (fsh's six kinds of [[ ]] expression are all above)
			throw new IllegalArgumentException("unknown [[ ]] expression: "+e);
		}

		SyntaxNode words(String kind, List<Word> words, int line) {
			SyntaxNode ret = new SyntaxNode(kind, words == null ? "\"$@\"" : kind, line);
			if( words != null ) {
				for(Word w : words) {
					ret.add(word(w));
				}
			}
			return ret;
		}

		SyntaxNode word(Word w) {
			return new SyntaxNode("word", w.raw, w.line);
		}

		SyntaxNode assignment(Ast.Assignment a) {
			String text = a.name+(a.index == null ? "" : "["+a.index+"]")+(a.append ? "+=" : "=");
			SyntaxNode ret = new SyntaxNode("assignment", text, a.line);
			if( a.array != null ) {
				for(Word w : a.array) {
					ret.add(word(w));
				}
			} else if( a.value != null && !a.value.raw.isEmpty()) {
				ret.add(word(a.value));
			}
			return ret;
		}

		SyntaxNode redirect(Ast.Redirect r) {
			String text = (r.fd == null ? "" : String.valueOf(r.fd))+(r.fdVariable == null ? "" : "{"+r.fdVariable+"}")+r.op;
			SyntaxNode ret = new SyntaxNode("redirect", text, r.line);
			if( r.hereDoc != null ) {
				ret.add(new SyntaxNode("here-document", r.hereDoc.delimiter, r.line));
			} else if( r.target != null ) {
				ret.add(word(r.target));
			}
			return ret;
		}
	}
}
