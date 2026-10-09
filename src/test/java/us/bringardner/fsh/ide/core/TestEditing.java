package us.bringardner.fsh.ide.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

/** Find and replace, templates and completions: the editing help both IDEs can share. */
public class TestEditing {

	private static final TextSearch.Options PLAIN = new TextSearch.Options(false, false, false, true);

	private static String at(String text, TextSearch.Match m) {
		return m == null ? null : text.substring(m.start, m.end);
	}

	@Test
	public void findsForwardAndBack() {
		String text = "echo Hi\necho hi there\necho HI";
		TextSearch.Match m = TextSearch.find(text, "hi", 0, true, PLAIN);
		assertEquals(5, m.start);
		m = TextSearch.find(text, "hi", m.end, true, PLAIN);
		assertEquals(13, m.start);
		m = TextSearch.find(text, "hi", m.end, true, PLAIN);
		assertEquals(27, m.start);
		// round the end
		m = TextSearch.find(text, "hi", m.end, true, PLAIN);
		assertEquals(5, m.start);
		assertTrue(m.wrapped);
		// backward from the start wraps to the last one
		m = TextSearch.find(text, "hi", 5, false, PLAIN);
		assertEquals(27, m.start);
		assertTrue(m.wrapped);
		assertEquals(13, TextSearch.find(text, "hi", 27, false, PLAIN).start);
		assertEquals(3, TextSearch.count(text, "hi", PLAIN));
	}

	@Test
	public void options() {
		String text = "cat catalog Cat";
		TextSearch.Options matchCase = new TextSearch.Options(true, false, false, false);
		assertEquals(2, TextSearch.count(text, "cat", matchCase));
		TextSearch.Options whole = new TextSearch.Options(false, true, false, false);
		assertEquals(2, TextSearch.count(text, "cat", whole));
		// no wrap: nothing after the last
		assertNull(TextSearch.find(text, "Cat", 13, true, matchCase));
		TextSearch.Options regex = new TextSearch.Options(false, false, true, false);
		assertEquals("catalog", at(text, TextSearch.find(text, "cat\\w+", 0, true, regex)));
		assertThrows(IllegalArgumentException.class, ()->TextSearch.count(text, "(", regex));
		// regular-expression characters are plain without the option
		assertEquals(0, TextSearch.count(text, "c.t", PLAIN));
		assertNull(TextSearch.find(text, "", 0, true, PLAIN));
		// an expression that can match nothing doesn't loop
		assertEquals(3, TextSearch.count("aaa", "a*?", regex) + 3);
	}

	@Test
	public void replaces() {
		TextSearch.Replaced r = TextSearch.replaceAll("x=1; x=2; xx=3", "x", "y", new TextSearch.Options(true, true, false, false));
		assertEquals("y=1; y=2; xx=3", r.text);
		assertEquals(2, r.count);
		TextSearch.Options regex = new TextSearch.Options(true, false, true, false);
		assertEquals("b=a c=d", TextSearch.replaceAll("a=b d=c", "(\\w)=(\\w)", "$2=$1", regex).text);
		assertEquals("d=c", TextSearch.replacementFor("c=d", "(\\w)=(\\w)", "$2=$1", regex));
		// a plain replacement keeps its $ and \
		assertEquals("$1\\", TextSearch.replaceAll("x", "x", "$1\\", PLAIN).text);
	}

	@Test
	public void templates() {
		TemplateText.Expansion e = TemplateText.expand("for((${var}=${start}; ${var}<${end}; ${var}++))");
		assertEquals("for((var=start; var<end; var++))", e.text);
		assertEquals(5, e.fields.size());
		assertEquals(5, e.fields.get(0)[0]);
		assertEquals(8, e.fields.get(0)[1]);
		assertEquals(-1, e.cursor);

		e = TemplateText.expand("$${${parameter}:+${word}}");
		assertEquals("${parameter:+word}", e.text);
		e = TemplateText.expand("$$(( ${cursor} ))");
		assertEquals("$((  ))", e.text);
		assertEquals(4, e.cursor);
		assertTrue(e.fields.isEmpty());
		assertEquals("echo ${oops", TemplateText.expand("echo ${oops").text);
	}

	@Test
	public void completions() {
		String script = "name=x\ncount+=1\nfor i in 1 2; do :; done\nread -r first last\nlocal -i total\narr[2]=y\nread -p 'Name? ' who\n";
		assertEquals(List.of("arr", "count", "first", "i", "last", "name", "total", "who"), Completions.variables(script));

		List<Template> templates = List.of(new Template("if", "if-then", "if [ ${condition} ]; then\nfi"),
				new Template("if-else", "", "x"), new Template("for", "loop", "for ..."));
		List<String> labels = Completions.matching("i", templates, script).stream().map(c->c.label).collect(Collectors.toList());
		// templates first; the variable i is already typed in full, so it isn't offered
		assertEquals(List.of("if", "if-else"), labels);
		assertEquals(List.of("count"), Completions.matching("co", templates, script).stream().map(c->c.label).collect(Collectors.toList()));
		Completions.Completion c = Completions.matching("$co", templates, script).get(0);
		assertEquals("$count", c.label);
		assertEquals("$count", TemplateText.expand(c.code).text);
		// the variable just typed in full isn't offered again
		assertTrue(Completions.matching("$name", templates, script).isEmpty());

		assertEquals("$na", Completions.prefix("echo $na", 8));
		assertEquals("", Completions.prefix("echo ", 5));
	}
}
