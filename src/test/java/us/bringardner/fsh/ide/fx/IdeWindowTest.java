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

	@Test
	public void theViewMenuChangesTheEditorFont() throws Exception {
		TestWindow w = window();
		assertEquals(us.bringardner.fsh.ide.core.EditorFont.defaults(), Fx.call(w::editorFont));
		assertFalse(Fx.call(()->w.stage().getScene().getRoot().getStyleClass()
				.contains(us.bringardner.fsh.ide.core.EditorFont.CSS_CLASS)), "the standard font adds nothing");
		Fx.run(w::biggerText);
		Fx.run(w::biggerText);
		assertEquals(15, Fx.call(w::editorFont).getSize());
		assertEquals(15, prefs.getInt(us.bringardner.fsh.ide.core.EditorFont.PREF_SIZE, -1), "kept");
		assertTrue(Fx.call(()->w.stage().getScene().getRoot().getStyleClass()
				.contains(us.bringardner.fsh.ide.core.EditorFont.CSS_CLASS)));
		int sheets = Fx.call(()->w.stage().getScene().getStylesheets().size());
		Fx.run(w::smallerText);
		assertEquals(14, Fx.call(w::editorFont).getSize());
		assertEquals(sheets, (int) Fx.call(()->w.stage().getScene().getStylesheets().size()), "replaced, not added");
		Fx.run(w::resetSize);
		assertEquals(us.bringardner.fsh.ide.core.EditorFont.defaults(), Fx.call(w::editorFont));
		assertFalse(Fx.call(()->w.stage().getScene().getRoot().getStyleClass()
				.contains(us.bringardner.fsh.ide.core.EditorFont.CSS_CLASS)));
		assertEquals(sheets - 1, (int) Fx.call(()->w.stage().getScene().getStylesheets().size()));

		// the keys: the keypad's + and -, shortcut = and shortcut 0; others pass by
		Fx.run(()->w.textSizeKeys(key(javafx.scene.input.KeyCode.ADD, "+", false)));
		Fx.run(()->w.textSizeKeys(key(javafx.scene.input.KeyCode.EQUALS, "=", false)));
		assertEquals(15, Fx.call(w::editorFont).getSize());
		Fx.run(()->w.textSizeKeys(key(javafx.scene.input.KeyCode.SUBTRACT, "-", false)));
		assertEquals(14, Fx.call(w::editorFont).getSize());
		javafx.scene.input.KeyEvent other = key(javafx.scene.input.KeyCode.S, "s", false);
		Fx.run(()->w.textSizeKeys(other));
		assertFalse(other.isConsumed());
		Fx.run(()->w.textSizeKeys(key(javafx.scene.input.KeyCode.DIGIT0, "0", false)));
		assertEquals(us.bringardner.fsh.ide.core.EditorFont.defaults(), Fx.call(w::editorFont));
		Fx.run(()->w.stage().close());
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
