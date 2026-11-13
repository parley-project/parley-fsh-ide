package us.bringardner.fsh.ide.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.AbstractPreferences;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javafx.stage.Stage;
import us.bringardner.fsh.ide.core.ExitChoice;
import us.bringardner.fsh.ide.core.RunSettings;
import us.bringardner.fsh.ide.core.RunSettingsStore;
import us.bringardner.parley.files.FileSource;
import us.bringardner.parley.files.FileSourceFactory;

/** The JavaFX IDE window's workings, without showing it. */
public class IdeWindowTest {

	@TempDir
	static Path home;

	@TempDir
	Path dir;

	@BeforeAll
	public static void quietHome() {
		// the IDE reads its configuration from the home folder; keep it out of the real one
		System.setProperty("user.home", home.toString());
	}

	/** Preferences kept in memory, so tests don't touch the real ones. */
	static class MemoryPreferences extends AbstractPreferences {
		final Map<String,String> values = new HashMap<>();
		MemoryPreferences() { super(null, ""); }
		@Override protected void putSpi(String key, String value) { values.put(key, value); }
		@Override protected String getSpi(String key) { return values.get(key); }
		@Override protected void removeSpi(String key) { values.remove(key); }
		@Override protected void removeNodeSpi() { }
		@Override protected String[] keysSpi() { return values.keySet().toArray(new String[0]); }
		@Override protected String[] childrenNamesSpi() { return new String[0]; }
		@Override protected AbstractPreferences childSpi(String name) { return new MemoryPreferences(); }
		@Override protected void syncSpi() { }
		@Override protected void flushSpi() { }
	}

	static class TestWindow extends IdeWindow {
		final List<String> questions = new ArrayList<>();
		final List<String> problems = new ArrayList<>();
		boolean answer = true;

		TestWindow(MemoryPreferences prefs) {
			super(new Stage(), prefs);
		}

		@Override
		protected boolean confirm(String question) {
			questions.add(question);
			return answer;
		}

		@Override
		protected void report(String title, Throwable error) {
			problems.add(title+": "+error);
		}

		@Override
		protected RunSettingsStore runSettings() {
			return store;
		}

		// the answer to the exit question, and how often it was asked
		ExitChoice exitAnswer = ExitChoice.CANCEL;
		int exitQuestions;

		@Override
		protected ExitChoice askExit() {
			exitQuestions++;
			return exitAnswer;
		}

		// the file Save As picks (null: canceled), and whether it was asked
		FileSource saveAsFile;
		boolean saveAsAsked;

		@Override
		protected FileSource chooseSaveFile() {
			saveAsAsked = true;
			return saveAsFile;
		}
	}

	// the windows' run settings, not the user's
	static RunSettingsStore store;

	private final MemoryPreferences prefs = new MemoryPreferences();

	private TestWindow window() throws Exception {
		store = new RunSettingsStore(dir.resolve("config").resolve("run-settings").toFile());
		return Fx.call(()->new TestWindow(prefs));
	}

	private FileSource file(String name, String text) throws Exception {
		Path p = dir.resolve(name);
		Files.writeString(p, text);
		return FileSourceFactory.getDefaultFactory().createFileSource(p.toString());
	}

	/** The console's right-click menu: Clear Console, as in the Run menu. */
	@Test
	public void theConsoleRightClickMenuClears() throws Exception {
		TestWindow w = window();
		Fx.run(()->w.console().append("some output\n", false));
		Fx.waitFor(()->!w.console().getArea().getText().isEmpty());
		javafx.scene.control.MenuItem clear = Fx.call(()->{
			javafx.scene.control.ContextMenu menu = w.console().getArea().getContextMenu();
			assertTrue(menu != null, "the console has a right-click menu");
			return menu.getItems().stream().filter(i->"Clear Console".equals(i.getText())).findFirst().orElse(null);
		});
		assertTrue(clear != null, "Clear Console");
		Fx.run(clear::fire);
		Fx.waitFor(()->w.console().getArea().getText().isEmpty());
	}

	/** Run > Clear ALL Run Settings...: the store's, and the open windows' (so saving doesn't keep them again). */
	@Test
	public void clearRunSettingsClearsTheOpenWindowsToo() throws Exception {
		TestWindow w = window();
		FileSource f = file("clear.sh", "echo a\n");
		store.put(f, new RunSettings("x y", "", "/tmp/out", ""));
		Fx.run(()->w.load(f));
		Fx.waitFor(()->w.scriptFile() != null);
		assertEquals("x y", Fx.call(()->w.argumentsField().getText()));

		Fx.run(w::actionClearRunSettings);
		Fx.waitFor(()->w.statusText().startsWith("Cleared"));
		assertEquals("Cleared the run settings of 1 script.", Fx.call(w::statusText));
		assertEquals(0, store.size());
		assertEquals("", Fx.call(()->w.argumentsField().getText()));
		assertEquals("", Fx.call(()->w.stdOutField().getText()));
		// as it's kept now: not a change
		assertFalse(Fx.call(w::hasChanged));
	}

	/** Run > Clear Current Run Settings: this window's fields only; what's kept stays. */
	@Test
	public void clearCurrentRunSettingsEmptiesOnlyThisWindow() throws Exception {
		TestWindow w = window();
		FileSource f = file("current.sh", "echo a\n");
		store.put(f, new RunSettings("x y", "", "/tmp/out", ""));
		Fx.run(()->w.load(f));
		Fx.waitFor(()->w.scriptFile() != null);
		Fx.run(w::actionClearCurrentRunSettings);
		assertEquals("", Fx.call(()->w.argumentsField().getText()));
		assertEquals("", Fx.call(()->w.stdOutField().getText()));
		assertEquals(1, store.size());
		assertTrue(Fx.call(w::hasChanged));
	}

	@Test
	public void theViewMenuChangesTheEditorFont() throws Exception {
		TestWindow w = window();
		TestWindow other = Fx.call(()->new TestWindow(prefs));
		assertEquals(us.bringardner.fsh.ide.core.EditorFont.defaults(), Fx.call(w::editorFont));
		assertFalse(Fx.call(()->w.stage().getScene().getRoot().getStyleClass()
				.contains(us.bringardner.fsh.ide.core.EditorFont.CSS_CLASS)), "the standard font adds nothing");
		int sheets = Fx.call(()->w.stage().getScene().getStylesheets().size());

		// the text size: this window only, for this session only
		Fx.run(w::biggerText);
		Fx.run(w::biggerText);
		assertEquals(15, Fx.call(w::editorFont).getSize());
		assertEquals(-1, prefs.getInt(us.bringardner.fsh.ide.core.EditorFont.PREF_SIZE, -1), "not kept");
		assertEquals(13, Fx.call(other::editorFont).getSize(), "the other window keeps its size");
		assertTrue(Fx.call(()->w.stage().getScene().getRoot().getStyleClass()
				.contains(us.bringardner.fsh.ide.core.EditorFont.CSS_CLASS)));
		Fx.run(w::smallerText);
		assertEquals(14, Fx.call(w::editorFont).getSize());
		assertEquals(sheets + 1, (int) Fx.call(()->w.stage().getScene().getStylesheets().size()), "replaced, not added");
		Fx.run(w::resetSize);
		assertEquals(us.bringardner.fsh.ide.core.EditorFont.defaults(), Fx.call(w::editorFont));
		assertFalse(Fx.call(()->w.stage().getScene().getRoot().getStyleClass()
				.contains(us.bringardner.fsh.ide.core.EditorFont.CSS_CLASS)));
		assertEquals(sheets, (int) Fx.call(()->w.stage().getScene().getStylesheets().size()));

		// the keys: the keypad's + and -, shortcut = and shortcut 0; others pass by
		Fx.run(()->w.textSizeKeys(key(javafx.scene.input.KeyCode.ADD, "+", false)));
		Fx.run(()->w.textSizeKeys(key(javafx.scene.input.KeyCode.EQUALS, "=", false)));
		assertEquals(15, Fx.call(w::editorFont).getSize());
		Fx.run(()->w.textSizeKeys(key(javafx.scene.input.KeyCode.SUBTRACT, "-", false)));
		assertEquals(14, Fx.call(w::editorFont).getSize());
		javafx.scene.input.KeyEvent otherKey = key(javafx.scene.input.KeyCode.S, "s", false);
		Fx.run(()->w.textSizeKeys(otherKey));
		assertFalse(otherKey.isConsumed());

		// Editor Font... is for good: kept, and on every window, in place of a window's own size
		us.bringardner.fsh.ide.core.EditorFont chosen = us.bringardner.fsh.ide.core.EditorFont.of(null, 18);
		Fx.run(()->w.chooseEditorFont(chosen));
		assertEquals(18, prefs.getInt(us.bringardner.fsh.ide.core.EditorFont.PREF_SIZE, -1));
		assertEquals(chosen, Fx.call(w::editorFont));
		assertEquals(chosen, Fx.call(other::editorFont));
		// and Reset Text Size goes back to it
		Fx.run(w::biggerText);
		assertEquals(20, Fx.call(w::editorFont).getSize());
		Fx.run(()->w.textSizeKeys(key(javafx.scene.input.KeyCode.DIGIT0, "0", false)));
		assertEquals(chosen, Fx.call(w::editorFont));
		Fx.run(()->{
			w.stage().close();
			other.stage().close();
		});
	}

	/** A key pressed with the shortcut key (Cmd on macOS, Ctrl elsewhere). */
	private static javafx.scene.input.KeyEvent key(javafx.scene.input.KeyCode code, String text, boolean shift) {
		boolean mac = System.getProperty("os.name", "").toLowerCase().startsWith("mac");
		return new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED, text, text, code, shift,
				!mac, false, mac);
	}

	@Test
	public void scriptSettingsAndChanges() throws Exception {
		TestWindow w = window();
		Fx.run(()->w.setCode("#BjlIdeScriptArgs=a b\necho $1\n", null));
		assertEquals("a b", Fx.call(()->w.argumentsField().getText()));
		assertEquals("echo $1\n", Fx.call(()->w.editor().getText()));
		assertFalse(Fx.call(w::hasChanged));
		assertTrue(Fx.call(()->w.stage().getTitle()).startsWith("Untitled"));

		Fx.run(()->w.editor().getCodeArea().appendText("echo $2\n"));
		assertTrue(Fx.call(w::hasChanged));
		assertTrue(Fx.call(()->w.stage().getTitle()).startsWith("* "));

		// nothing is discarded without asking
		w.answer = false;
		Fx.run(w::actionNew);
		assertEquals(1, w.questions.size());
		assertTrue(Fx.call(()->w.editor().getText()).contains("echo $2"));
		w.answer = true;
		Fx.run(w::actionNew);
		assertEquals("", Fx.call(()->w.editor().getText()));
		assertEquals("", Fx.call(()->w.argumentsField().getText()));
	}

	@Test
	public void opensAndSaves() throws Exception {
		FileSource f = file("hello.sh", "#BjlIdeScriptOut=/tmp/out\necho café\n");
		TestWindow w = window();
		Fx.run(()->w.load(f));
		Fx.waitFor(()->w.scriptFile() != null);
		assertEquals("echo café\n", Fx.call(()->w.editor().getText()));
		assertEquals("/tmp/out", Fx.call(()->w.stdOutField().getText()));
		assertEquals(f.getAbsolutePath(), w.recent().first());
		assertTrue(prefs.values.get(IdeWindow.PREF_RECENT_LIST).contains("hello.sh"));

		// saved: the file has only the code; the settings are in the store
		FileSource copy = file("copy.sh", "");
		Fx.run(()->{
			w.argumentsField().setText("x");
			w.save(copy);
		});
		Fx.waitFor(()->!w.hasChanged());
		assertEquals("echo café\n", Files.readString(dir.resolve("copy.sh")));
		assertEquals(new RunSettings("x", "", "/tmp/out", ""), store.get(copy));
		assertEquals("copy.sh", Fx.call(()->w.scriptFile().getName()));

		// opened again, the settings come back; cleared and saved, nothing is kept
		TestWindow again = window();
		Fx.run(()->again.load(copy));
		Fx.waitFor(()->again.scriptFile() != null);
		assertEquals("x", Fx.call(()->again.argumentsField().getText()));
		Fx.run(()->{
			again.argumentsField().setText("");
			again.stdOutField().setText("");
			again.save(copy);
		});
		Fx.waitFor(()->!again.hasChanged());
		Fx.waitFor(()->{
			try {
				return store.size() == 0;
			} catch (java.io.IOException e) {
				return false;
			}
		});
	}

	@Test
	public void exitWithNothingChangedAsksNothing() throws Exception {
		TestWindow w = window();
		Fx.run(()->w.setCode("echo a\n", null));
		assertTrue(Fx.call(w::close));
		assertEquals(0, w.exitQuestions);
	}

	@Test
	public void exitCancelKeepsTheWindowAndItsChanges() throws Exception {
		TestWindow w = window();
		Fx.run(()->{
			w.setCode("echo a\n", null);
			w.editor().getCodeArea().appendText("echo b\n");
		});
		w.exitAnswer = ExitChoice.CANCEL;
		assertFalse(Fx.call(w::close));
		assertEquals(1, w.exitQuestions);
		assertTrue(Fx.call(w::hasChanged));
		assertEquals("echo a\necho b\n", Fx.call(()->w.editor().getText()));
	}

	@Test
	public void exitDiscardClosesWithoutSaving() throws Exception {
		FileSource f = file("keep.sh", "echo a\n");
		TestWindow w = window();
		Fx.run(()->w.load(f));
		Fx.waitFor(()->w.scriptFile() != null);
		Fx.run(()->w.editor().getCodeArea().appendText("echo b\n"));
		w.exitAnswer = ExitChoice.DISCARD;
		assertTrue(Fx.call(w::close));
		assertEquals("echo a\n", Files.readString(dir.resolve("keep.sh")));
	}

	@Test
	public void exitSaveWritesTheFileFirst() throws Exception {
		FileSource f = file("save.sh", "echo a\n");
		TestWindow w = window();
		Fx.run(()->w.load(f));
		Fx.waitFor(()->w.scriptFile() != null);
		Fx.run(()->w.editor().getCodeArea().appendText("echo b\n"));
		w.exitAnswer = ExitChoice.SAVE;
		assertTrue(Fx.call(w::close));
		// written by the time close() answers, not later in the background
		assertEquals("echo a\necho b\n", Files.readString(dir.resolve("save.sh")));
		assertFalse(w.saveAsAsked);
		assertFalse(Fx.call(w::hasChanged));
	}

	@Test
	public void exitSaveOfANewScriptAsksForAFile() throws Exception {
		TestWindow w = window();
		Fx.run(()->{
			w.setCode("", null);
			w.editor().getCodeArea().appendText("echo new\n");
		});
		w.exitAnswer = ExitChoice.SAVE;

		// Save As canceled: the window stays open, still changed
		w.saveAsFile = null;
		assertFalse(Fx.call(w::close));
		assertTrue(w.saveAsAsked);
		assertTrue(Fx.call(w::hasChanged));

		// a file chosen: saved there, and the window closes
		w.saveAsFile = FileSourceFactory.getDefaultFactory().createFileSource(dir.resolve("new.sh").toString());
		assertTrue(Fx.call(w::close));
		assertEquals("echo new\n", Files.readString(dir.resolve("new.sh")));
	}

	@Test
	public void exitSaveThatFailsKeepsTheWindow() throws Exception {
		TestWindow w = window();
		Fx.run(()->{
			w.setCode("", null);
			w.editor().getCodeArea().appendText("echo x\n");
		});
		w.exitAnswer = ExitChoice.SAVE;
		// a folder that isn't there
		w.saveAsFile = FileSourceFactory.getDefaultFactory().createFileSource(dir.resolve("no/such/dir/x.sh").toString());
		assertFalse(Fx.call(w::close));
		assertEquals(1, w.problems.size(), w.problems.toString());
		assertTrue(w.problems.get(0).startsWith("Can't save"), w.problems.get(0));
		assertTrue(Fx.call(w::hasChanged));
	}

	@Test
	public void theExitQuestionsButtonsAreInOrder() throws Exception {
		TestWindow w = window();
		List<String> labels = Fx.call(()->{
			javafx.scene.control.Alert a = w.exitQuestion();
			List<String> l = new ArrayList<>();
			for(javafx.scene.control.ButtonType b : a.getDialogPane().getButtonTypes()) {
				l.add(b.getText()+"="+IdeWindow.exitChoice(b));
			}
			javafx.scene.control.ButtonBar bar = (javafx.scene.control.ButtonBar) a.getDialogPane().lookup(".button-bar");
			l.add(bar.getButtonOrder());
			// closing the question (the window's close box) is Cancel
			l.add(String.valueOf(a.getDialogPane().getButtonTypes().stream()
					.anyMatch(b->b.getButtonData() == javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE)));
			return l;
		});
		assertEquals(List.of("Cancel=CANCEL", "Discard and exit=DISCARD", "Save and exit=SAVE",
				javafx.scene.control.ButtonBar.BUTTON_ORDER_NONE, "true"), labels);
	}

	@Test
	public void aMissingFileIsReported() throws Exception {
		TestWindow w = window();
		FileSource missing = FileSourceFactory.getDefaultFactory().createFileSource(dir.resolve("none.sh").toString());
		Fx.run(()->w.load(missing));
		Fx.waitFor(()->!w.problems.isEmpty());
		assertTrue(w.problems.get(0).startsWith("Can't read"), w.problems.get(0));
	}

	@Test
	public void runsAndShowsOutput() throws Exception {
		TestWindow w = window();
		Fx.run(()->{
			w.setCode("#BjlIdeScriptArgs=world\necho hello $1\necho oops >&2\nexit 3\n", null);
			w.run();
		});
		Fx.waitFor(()->!w.isRunning());
		String out = Fx.call(()->w.console().getArea().getText());
		assertTrue(out.contains("hello world"), out);
		assertTrue(out.contains("oops"), out);
		assertEquals("Finished, exit code 3", Fx.call(w::statusText));
	}

	@Test
	public void runsTheSelectionOnly() throws Exception {
		TestWindow w = window();
		Fx.run(()->{
			w.setCode("echo first\necho second\n", null);
			w.useSelectionItem().setSelected(true);
			w.editor().getCodeArea().selectRange(11, 22);
			w.run();
		});
		Fx.waitFor(()->!w.isRunning());
		String out = Fx.call(()->w.console().getArea().getText());
		assertEquals("second\n", out);
	}

	@Test
	public void stopsALoop() throws Exception {
		TestWindow w = window();
		Fx.run(()->{
			w.setCode("while true\ndo\n  x=1\ndone\n", null);
			w.run();
		});
		Thread.sleep(300);
		assertTrue(Fx.call(w::isRunning));
		Fx.run(w::stop);
		Fx.waitFor(()->!w.isRunning());
		assertTrue(Fx.call(w::statusText).startsWith("Stopped"));
	}
}
