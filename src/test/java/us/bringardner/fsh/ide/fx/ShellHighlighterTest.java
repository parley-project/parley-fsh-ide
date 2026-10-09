package us.bringardner.fsh.ide.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Collection;

import org.fxmisc.richtext.model.StyleSpan;
import org.fxmisc.richtext.model.StyleSpans;
import org.junit.jupiter.api.Test;

public class ShellHighlighterTest {

	/** The style at each character: c(omment), s(tring), v(ariable), k(eyword) or a space. */
	private static String styles(String text) {
		StyleSpans<Collection<String>> spans = ShellHighlighter.compute(text);
		StringBuilder ret = new StringBuilder();
		for(StyleSpan<Collection<String>> span : spans) {
			char c = span.getStyle().isEmpty() ? ' ' : span.getStyle().iterator().next().charAt(0);
			ret.append(String.valueOf(c).repeat(span.getLength()));
		}
		return ret.toString();
	}

	@Test
	public void colours() {
		String text = "if [ \"$x\" ]; then echo $HOME # done\nfi";
		// a variable inside double quotes is part of the string; the newline has no style
		assertEquals("kk   ssss    kkkk      vvvvv cccccc kk", styles(text));
	}

	@Test
	public void hashesThatArentComments() {
		assertEquals("     vv ", styles("echo $# "));
		assertEquals("     vvvvv", styles("echo ${#a}"));
		assertEquals("     vvvvvvv", styles("echo ${x#*/}"));
	}

	@Test
	public void quotesAndUnfinishedStrings() {
		assertEquals("     sssss", styles("echo 'a b'"));
		// an escaped quote doesn't end the string
		assertEquals("     sssss", styles("echo \"a \\\""));
		assertEquals("     ssss", styles("echo 'abc"));
	}

	@Test
	public void keywordsOnlyAsWords() {
		assertEquals("          ", styles("echo done1"));
		assertEquals("kkk    kk ", styles("for x  in "));
		assertEquals("", styles(""));
	}
}
