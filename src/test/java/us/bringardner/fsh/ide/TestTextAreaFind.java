package us.bringardner.fsh.ide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import us.bringardner.fsh.ide.core.TextSearch;

/** The Swing find dialog's search (TextAreaFind): forward and back, replace one and all, Selected Lines. */
public class TestTextAreaFind {

	private static final TextSearch.Options PLAIN = new TextSearch.Options(false, false, false, true);
	private static final TextSearch.Options NO_WRAP = new TextSearch.Options(false, false, false, false);

	@BeforeAll
	public static void headless() {
		System.setProperty("java.awt.headless", "true");
	}

	private static FshIDETextArea area(String text) {
		FshIDETextArea area = new FshIDETextArea(10, 40);
		area.setText(text);
		area.setCaretPosition(0);
		return area;
	}

	private static String selected(FshIDETextArea area) {
		return area.getSelectionStart()+"-"+area.getSelectionEnd();
	}

	@Test
	public void findsForwardThenWraps() {
		FshIDETextArea area = area("echo one\necho two\necho three\n");
		TextAreaFind f = new TextAreaFind(area);
		assertTrue(f.find("echo", true, PLAIN));
		assertEquals("0-4", selected(area));
		assertEquals("3 matches", f.result());
		assertTrue(f.find("echo", true, PLAIN));
		assertEquals("9-13", selected(area));
		assertTrue(f.find("echo", true, PLAIN));
		assertEquals("18-22", selected(area));
		assertTrue(f.find("echo", true, PLAIN));
		assertEquals("0-4", selected(area));
		assertEquals("Wrapped. 3 matches", f.result());
		// without wrap: not found at the end
		area.setCaretPosition(area.getDocument().getLength());
		assertFalse(f.find("echo", true, NO_WRAP));
		assertEquals("Not found", f.result());
	}

	@Test
	public void findsBack() {
		FshIDETextArea area = area("echo one\necho two\necho three\n");
		TextAreaFind f = new TextAreaFind(area);
		area.setCaretPosition(area.getDocument().getLength());
		assertTrue(f.find("echo", false, PLAIN));
		assertEquals("18-22", selected(area));
		assertTrue(f.find("echo", false, PLAIN));
		assertEquals("9-13", selected(area));
		assertTrue(f.find("echo", false, PLAIN));
		assertEquals("0-4", selected(area));
		// round the start, back to the last
		assertTrue(f.find("echo", false, PLAIN));
		assertEquals("18-22", selected(area));
		assertTrue(f.result().startsWith("Wrapped."), f.result());
	}

	@Test
	public void matchCaseWholeWordsAndRegularExpressions() {
		FshIDETextArea area = area("Echo echoes echo\n");
		TextAreaFind f = new TextAreaFind(area);
		assertEquals(true, f.find("echo", true, new TextSearch.Options(true, true, false, true)));
		assertEquals("12-16", selected(area));
		area.setCaretPosition(0);
		assertTrue(f.find("e[a-z]+s", true, new TextSearch.Options(false, false, true, true)));
		assertEquals("5-11", selected(area));
		assertFalse(f.find("(", true, new TextSearch.Options(false, false, true, true)));
		assertEquals("Bad regular expression", f.result());
	}

	@Test
	public void replaceReplacesAMatchOnly() {
		// (a match at the very start is replaced too)
		FshIDETextArea area = area("cat one\ncat two\n");
		TextAreaFind f = new TextAreaFind(area);
		// nothing selected: Replace finds the first one
		f.replace("cat", "dog", false, PLAIN);
		assertEquals("0-3", selected(area));
		assertEquals("cat one\ncat two\n", area.getText());
		// it's selected: Replace replaces it, and selects what replaced it
		f.replace("cat", "dog", false, PLAIN);
		assertEquals("dog one\ncat two\n", area.getText());
		assertEquals("0-3", selected(area));
		// Replace / Find: and the next
		f.find("cat", true, PLAIN);
		f.replace("cat", "dog", true, PLAIN);
		assertEquals("dog one\ndog two\n", area.getText());
		assertEquals("Not found", f.result());
	}

	@Test
	public void replaceFillsInGroups() {
		FshIDETextArea area = area("x=1\ny=2\n");
		TextAreaFind f = new TextAreaFind(area);
		TextSearch.Options rx = new TextSearch.Options(false, false, true, true);
		f.find("(\\w)=(\\d)", true, rx);
		f.replace("(\\w)=(\\d)", "$2=$1", false, rx);
		assertEquals("1=x\ny=2\n", area.getText());
	}

	@Test
	public void replaceAllIsUndoneAsOne() {
		FshIDETextArea area = area("a b a c a\n");
		TextAreaFind f = new TextAreaFind(area);
		assertEquals(3, f.replaceAll("a", "zz", PLAIN));
		assertEquals("zz b zz c zz\n", area.getText());
		assertEquals("Replaced 3", f.result());
		area.undoLastAction();
		assertEquals("a b a c a\n", area.getText());
	}

	@Test
	public void selectedLinesLimitTheSearch() throws Exception {
		FshIDETextArea area = area("x one\nx two\nx three\nx four\n");
		TextAreaFind f = new TextAreaFind(area);
		// part of lines 2 and 3 selected: the scope is both lines, whole
		area.select(8, 14);
		f.setScope(true);
		assertTrue(f.hasScope());
		assertEquals("6-20", f.range()[0]+"-"+f.range()[1]);
		area.setCaretPosition(0);
		assertTrue(f.find("x", true, PLAIN));
		assertEquals("6-7", selected(area));
		assertEquals("2 matches", f.result());
		// replacing in it moves its end with the text
		assertEquals(2, f.replaceAll("x", "yyy", PLAIN));
		assertEquals("x one\nyyy two\nyyy three\nx four\n", area.getText());
		assertEquals("6-24", f.range()[0]+"-"+f.range()[1]);
		// and its highlight
		javax.swing.text.Highlighter.Highlight[] h = area.getHighlighter().getHighlights();
		boolean shown = false;
		for(javax.swing.text.Highlighter.Highlight x : h) {
			shown |= x.getStartOffset() == 6 && x.getEndOffset() == 24;
		}
		assertTrue(shown);
		// All again
		f.setScope(false);
		assertFalse(f.hasScope());
		assertEquals(2, f.replaceAll("x", "w", PLAIN));
	}
}
