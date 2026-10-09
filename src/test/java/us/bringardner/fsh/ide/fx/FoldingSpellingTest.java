package us.bringardner.fsh.ide.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.stage.Stage;
import us.bringardner.fsh.ide.core.Breakpoint;
import us.bringardner.fsh.ide.core.SpellChecking;

/** Folding blocks and checking spelling in the JavaFX editor. */
public class FoldingSpellingTest {

	@TempDir
	static Path home;

	@BeforeAll
	public static void quietHome() {
		// the user's own words are kept in the home folder; keep them out of the real one
		System.setProperty("user.home", home.toString());
	}

	static final String SCRIPT = String.join("\n",
			"for i in 1 2",      // 0
			"do",                // 1
			"  if [ $i = 1 ]",   // 2
			"  then",            // 3
			"    echo one",      // 4
			"  fi",              // 5
			"done",              // 6
			"echo end",          // 7
			"");

	private static boolean collapsed(ScriptEditor e, int line) {
		return e.getCodeArea().getParagraph(line).getParagraphStyle().contains("collapse");
	}

	@Test
	public void foldsAndUnfolds() throws Exception {
		IdeWindow w = Fx.call(()->{
			IdeWindow win = new IdeWindow(new Stage(), new IdeWindowTest.MemoryPreferences());
			win.setCode(SCRIPT, null);
			return win;
		});
		ScriptEditor e = w.editor();
		assertNotNull(Fx.call(()->e.foldAt(0)));
		assertNotNull(Fx.call(()->e.foldAt(2)));
		Breakpoint bp = Fx.call(()->e.addBreakpoint(4));

		Fx.run(()->e.fold(0));
		assertTrue(Fx.call(()->e.isFolded(0)));
		assertTrue(Fx.call(()->collapsed(e, 1) && collapsed(e, 6)));
		assertFalse(Fx.call(()->collapsed(e, 7)));
		// folding doesn't change the script or move breakpoints
		assertEquals(SCRIPT, Fx.call(e::getText));
		assertFalse(Fx.call(w::hasChanged));
		assertEquals(4, bp.getLine());

		Fx.run(()->e.unfold(0));
		assertFalse(Fx.call(()->collapsed(e, 4)));
		assertEquals(4, bp.getLine());

		Fx.run(e::foldAll);
		assertTrue(Fx.call(()->e.isFolded(0)));
		Fx.run(e::unfoldAll);
		assertFalse(Fx.call(()->collapsed(e, 1) || collapsed(e, 4)));
	}

	@Test
	public void thePausedLineIsShownEvenWhenFolded() throws Exception {
		ScriptEditor e = Fx.call(()->{
			ScriptEditor ed = new ScriptEditor();
			ed.setText(SCRIPT);
			ed.fold(0);
			return ed;
		});
		Fx.run(()->e.setCurrentLine(4));
		assertFalse(Fx.call(()->collapsed(e, 4)));
		assertTrue(Fx.call(()->e.getCodeArea().getParagraph(4).getParagraphStyle().contains("current-line")));
		Fx.run(()->e.setCurrentLine(-1));
		assertFalse(Fx.call(()->e.getCodeArea().getParagraph(4).getParagraphStyle().contains("current-line")));
	}

	@Test
	public void spelling() throws Exception {
		ScriptEditor e = Fx.call(ScriptEditor::new);
		Fx.waitFor(e::isDictionaryReady);
		Fx.run(()->e.setText("# count the filse\necho \"wrold\" $wrold\n"));
		Fx.waitFor(()->e.misspelledWords().size() == 2);
		int[] first = Fx.call(()->e.misspelledWords().get(0));
		assertEquals("filse", Fx.call(()->e.getCodeArea().getText(first[0], first[1])));

		ContextMenu menu = Fx.call(()->e.contextMenu(first[0]+1));
		List<String> items = Fx.call(()->menu.getItems().stream().map(MenuItem::getText).filter(t->t != null).toList());
		assertTrue(items.contains("files"), items.toString());
		assertTrue(items.contains("Add \"filse\" to Dictionary"), items.toString());
		Fx.run(()->menu.getItems().stream().filter(i->"files".equals(i.getText())).findFirst().get().fire());
		assertTrue(Fx.call(e::getText).startsWith("# count the files\n"));
		Fx.waitFor(()->e.misspelledWords().size() == 1);

		// the variable $wrold isn't checked; the quoted "wrold" is
		Fx.run(()->e.addToDictionary("wrold"));
		Fx.waitFor(()->e.misspelledWords().isEmpty());
		assertTrue(Files.readString(SpellChecking.userWordsFile().toPath()).contains("wrold"));

		Fx.run(()->{
			e.getCodeArea().appendText("# anothr\n");
			e.ignore("anothr");
		});
		Fx.waitFor(()->e.misspelledWords().isEmpty());

		Fx.run(()->{
			e.getCodeArea().appendText("# mistaek\n");
		});
		Fx.waitFor(()->e.misspelledWords().size() == 1);
		Fx.run(()->e.setSpellChecking(false));
		assertTrue(Fx.call(()->e.misspelledWords().isEmpty()));
		// without a misspelled word there, the menu is just cut, copy and paste
		assertEquals(3, Fx.call(()->e.contextMenu(0).getItems().size()));
	}
}
