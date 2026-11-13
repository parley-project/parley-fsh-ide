package us.bringardner.fsh.studio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Point;
import java.awt.geom.Rectangle2D;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;

import org.fife.com.swabunga.spell.engine.SpellDictionary;
import org.fife.ui.rsyntaxtextarea.RSyntaxDocument;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import us.bringardner.fsh.studio.core.SpellChecking;

/**
 * The editor's spelling: the words that are flagged, the menu
 * (suggestions, Ignore, Add to Dictionary), and the user's own words (~/.fsh-studio/words.txt).
 */
public class TestSwingSpelling {

	static final String SCRIPT = "# a mispeled wrod here\necho \"and wroldwide too\"\nmispeled=1\n";

	@TempDir
	static Path home;

	@BeforeAll
	public static void setUp() throws Exception {
		System.setProperty("java.awt.headless", "true");
		System.setProperty("user.home", home.toString());
		// a word added earlier
		Path words = home.resolve(".fsh-studio").resolve("words.txt");
		Files.createDirectories(words.getParent());
		Files.writeString(words, "fshide\n");
	}

	@Test
	public void readsTheUsersWords() throws Exception {
		SpellDictionary dict = SpellChecking.english().get(60, TimeUnit.SECONDS);
		assertTrue(dict.isCorrect("fshide"));
	}

	private FshStudioTextArea area(String text) {
		FshStudioTextArea area = new FshStudioTextArea(10, 40);
		area.setSize(600, 200);
		area.setText(text);
		return area;
	}

	private List<String> flagged(FshSpellingParser parser, FshStudioTextArea area) {
		parser.parse((RSyntaxDocument) area.getDocument(), SyntaxConstants.SYNTAX_STYLE_UNIX_SHELL);
		String text = area.getText();
		List<String> ret = new ArrayList<>();
		for(int i = 0; i < text.length(); i++) {
			int[] r = parser.misspelledAt(i);
			if( r != null && r[0] == i ) {
				ret.add(text.substring(r[0], r[1]));
			}
		}
		return ret;
	}

	@Test
	public void flagsCommentsAndQuotedText() throws Exception {
		SpellDictionary dict = SpellChecking.english().get(60, TimeUnit.SECONDS);
		FshStudioTextArea area = area(SCRIPT);
		FshSpellingParser parser = new FshSpellingParser(area, dict);
		// the comment and the quoted text, not the variable name
		assertEquals(List.of("mispeled", "wrod", "wroldwide"), flagged(parser, area));
		assertEquals(SpellChecking.misspelled(SCRIPT, dict::isCorrect).size(), 3);
	}

	@Test
	public void ignoreLeavesAWordAlone() throws Exception {
		SpellDictionary dict = SpellChecking.english().get(60, TimeUnit.SECONDS);
		FshStudioTextArea area = area(SCRIPT);
		FshSpellingParser parser = new FshSpellingParser(area, dict);
		parser.ignore("wrod");
		assertEquals(List.of("mispeled", "wroldwide"), flagged(parser, area));
	}

	@Test
	public void addToDictionaryAddsToTheUsersWords() throws Exception {
		SpellDictionary dict = SpellChecking.english().get(60, TimeUnit.SECONDS);
		FshStudioTextArea area = area("# zzyzx\n");
		FshSpellingParser parser = new FshSpellingParser(area, dict);
		assertEquals(List.of("zzyzx"), flagged(parser, area));
		parser.addToDictionary("zzyzx");
		assertEquals(List.of(), flagged(parser, area));
		String saved = Files.readString(SpellChecking.userWordsFile().toPath());
		assertTrue(saved.contains("zzyzx"), saved);
		assertTrue(saved.contains("fshide"), saved);
	}

	@Test
	public void checkingCanBeSwitchedOff() throws Exception {
		SpellDictionary dict = SpellChecking.english().get(60, TimeUnit.SECONDS);
		FshStudioTextArea area = area(SCRIPT);
		FshSpellingParser parser = new FshSpellingParser(area, dict);
		assertEquals(3, flagged(parser, area).size());
		parser.setChecking(false);
		assertEquals(List.of(), flagged(parser, area));
		parser.setChecking(true);
		assertEquals(3, flagged(parser, area).size());
	}

	/** Edit > Check Spelling switches the text area's checking, and the setting is kept for next time. */
	@Test
	public void theEditMenuSwitchesCheckingAndRemembersIt() throws Exception {
		java.util.prefs.Preferences prefs = new MemoryPreferences();
		FshStudioTextArea area = area(SCRIPT);
		JCheckBoxMenuItem item = FshStudio.spellingItem(area, prefs);
		assertEquals("Check Spelling", item.getText());
		// on at first
		assertTrue(item.isSelected());
		assertTrue(area.isSpellChecking());

		item.doClick();
		assertFalse(item.isSelected());
		assertFalse(area.isSpellChecking());
		assertFalse(prefs.getBoolean(FshStudio.PREF_SPELLING, true));

		// a new window starts as it was left
		FshStudioTextArea next = area(SCRIPT);
		JCheckBoxMenuItem nextItem = FshStudio.spellingItem(next, prefs);
		assertFalse(nextItem.isSelected());
		assertFalse(next.isSpellChecking());

		nextItem.doClick();
		assertTrue(next.isSpellChecking());
		assertTrue(prefs.getBoolean(FshStudio.PREF_SPELLING, false));
	}

	/** Preferences kept in memory, so the test does not change the user's own. */
	private static final class MemoryPreferences extends java.util.prefs.AbstractPreferences {
		private final java.util.Map<String, String> values = new java.util.HashMap<>();

		MemoryPreferences() {
			super(null, "");
		}

		@Override protected void putSpi(String key, String value) { values.put(key, value); }
		@Override protected String getSpi(String key) { return values.get(key); }
		@Override protected void removeSpi(String key) { values.remove(key); }
		@Override protected void removeNodeSpi() { }
		@Override protected String[] keysSpi() { return values.keySet().toArray(new String[0]); }
		@Override protected String[] childrenNamesSpi() { return new String[0]; }
		@Override protected java.util.prefs.AbstractPreferences childSpi(String name) { throw new UnsupportedOperationException(); }
		@Override protected void syncSpi() { }
		@Override protected void flushSpi() { }
	}

	/** The popup menu on a misspelled word has the suggestions, Ignore and Add first, then the usual items. */
	@Test
	public void theRightClickMenuHasTheSpellingItems() throws Exception {
		SpellDictionary dict = SpellChecking.english().get(60, TimeUnit.SECONDS);
		FshStudioTextArea area = area("# qqwwee here\n");
		FshSpellingParser parser = new FshSpellingParser(area, dict);
		setSpelling(area, parser);
		assertEquals(List.of("qqwwee"), flagged(parser, area));

		JPopupMenu menu = area.getPopupMenu();
		int usual = menu.getComponentCount();
		Rectangle2D r = area.modelToView2D(4);
		area.popupPoint = new Point((int) r.getCenterX() + 2, (int) r.getCenterY());
		area.configurePopupMenu(menu);
		List<String> labels = labels(menu);
		assertTrue(labels.contains("Ignore \"qqwwee\""), labels.toString());
		assertTrue(labels.contains("Add \"qqwwee\" to Dictionary"), labels.toString());
		assertTrue(menu.getComponentCount() > usual);

		// the same click twice does not stack the items
		int count = menu.getComponentCount();
		area.configurePopupMenu(menu);
		assertEquals(count, menu.getComponentCount());

		// a click elsewhere takes them out
		Rectangle2D elsewhere = area.modelToView2D(area.getText().length() - 1);
		area.popupPoint = new Point((int) elsewhere.getX() + 400, (int) elsewhere.getCenterY());
		area.configurePopupMenu(menu);
		assertEquals(usual, menu.getComponentCount());

		// "Add" puts the word in the user's words
		area.popupPoint = new Point((int) r.getCenterX() + 2, (int) r.getCenterY());
		area.configurePopupMenu(menu);
		for(Component c : menu.getComponents()) {
			if( c instanceof JMenuItem && ((JMenuItem) c).getText().startsWith("Add \"qqwwee\"") ) {
				((JMenuItem) c).doClick();
			}
		}
		assertTrue(Files.readString(SpellChecking.userWordsFile().toPath()).contains("qqwwee"));
		assertEquals(List.of(), flagged(parser, area));
		assertNull(parser.misspelledAt(4));
	}

	private static List<String> labels(JPopupMenu menu) {
		List<String> ret = new ArrayList<>();
		for(Component c : menu.getComponents()) {
			if( c instanceof JMenuItem ) {
				ret.add(((JMenuItem) c).getText());
			}
		}
		return ret;
	}

	/** The text area makes its own parser when the dictionary is read; the test uses its own. */
	private static void setSpelling(FshStudioTextArea area, FshSpellingParser parser) throws Exception {
		java.lang.reflect.Field f = FshStudioTextArea.class.getDeclaredField("spelling");
		f.setAccessible(true);
		f.set(area, parser);
		SwingUtilities.invokeAndWait(()->{});
	}
}
