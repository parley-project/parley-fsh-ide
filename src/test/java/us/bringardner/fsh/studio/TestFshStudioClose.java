package us.bringardner.fsh.studio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.GraphicsEnvironment;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import us.bringardner.fsh.debug.ScriptDocument;

import us.bringardner.fsh.studio.core.ExitChoice;
import us.bringardner.parley.files.FileSource;
import us.bringardner.parley.files.FileSourceFactory;

/** Closing the window is left to FshStudio.close(), which may keep it open (if the user says no or Cancel). */
public class TestFshStudioClose {

	@TempDir
	static Path home;

	@BeforeAll
	public static void quietHome() {
		System.setProperty("user.home", home.toString());
		// no recent script of the user's: the window shows only the script the test gives it
		FshStudio.usePreferences(new MemoryPreferences());
	}

	@Test
	public void theWindowDoesntHideItself() throws Exception {
		Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "needs a screen");
		SwingUtilities.invokeAndWait(()->{
			FshStudio w = new FshStudio();
			try {
				assertEquals(WindowConstants.DO_NOTHING_ON_CLOSE, w.getDefaultCloseOperation());
			} finally {
				w.dispose();
			}
		});
	}

	@Test
	public void nothingToLoseAsksNothing() throws Exception {
		Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "needs a screen");
		java.util.List<String> questions = new java.util.ArrayList<>();
		SwingUtilities.invokeAndWait(()->{
			FshStudio w = new FshStudio() {
				private static final long serialVersionUID = 1L;
				@Override
				protected boolean confirm(String question) {
					questions.add(question);
					return false;
				}
			};
			try {
				assertEquals(true, w.okToStopScript());
			} finally {
				w.dispose();
			}
		});
		assertEquals(java.util.List.of(), questions);
	}

	/** A window whose exit question answers itself, and whose errors are kept, not shown. */
	static class ExitWindow extends FshStudio {
		private static final long serialVersionUID = 1L;
		ExitChoice answer = ExitChoice.CANCEL;
		int asked;
		final java.util.List<String> errors = new java.util.ArrayList<>();

		@Override
		protected ExitChoice askExit() {
			asked++;
			return answer;
		}

		@Override
		public void showError(Throwable e, String msg) {
			errors.add(msg);
		}

		// not the user's recent list
		@Override
		protected void addRecent(FileSource file) {
		}
	}

	/** A window showing file (its text being code), then with a line added. */
	private static ExitWindow changed(Path file, String code) throws Exception {
		Files.writeString(file, code);
		FileSource f = FileSourceFactory.getDefaultFactory().createFileSource(file.toString());
		ExitWindow[] w = new ExitWindow[1];
		SwingUtilities.invokeAndWait(()->{
			w[0] = new ExitWindow();
			w[0].setDocument(ScriptDocument.parse(code), f);
			w[0].editor().getTextArea().append("echo more\n");
		});
		return w[0];
	}

	@Test
	public void exitWithNothingChangedAsksNothing() throws Exception {
		Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "needs a screen");
		SwingUtilities.invokeAndWait(()->{
			ExitWindow w = new ExitWindow();
			try {
				assertTrue(w.okToExit());
				assertEquals(0, w.asked);
			} finally {
				w.dispose();
			}
		});
	}

	@Test
	public void exitCancelKeepsTheChanges(@TempDir Path dir) throws Exception {
		Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "needs a screen");
		ExitWindow w = changed(dir.resolve("a.sh"), "echo a\n");
		SwingUtilities.invokeAndWait(()->{
			try {
				w.answer = ExitChoice.CANCEL;
				assertFalse(w.okToExit());
				assertEquals(1, w.asked);
				assertTrue(w.hasChanged());
			} finally {
				w.dispose();
			}
		});
		assertEquals("echo a\n", Files.readString(dir.resolve("a.sh")));
	}

	@Test
	public void exitDiscardDoesntSave(@TempDir Path dir) throws Exception {
		Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "needs a screen");
		ExitWindow w = changed(dir.resolve("a.sh"), "echo a\n");
		SwingUtilities.invokeAndWait(()->{
			try {
				w.answer = ExitChoice.DISCARD;
				assertTrue(w.okToExit());
			} finally {
				w.dispose();
			}
		});
		assertEquals("echo a\n", Files.readString(dir.resolve("a.sh")));
	}

	@Test
	public void exitSaveSavesFirst(@TempDir Path dir) throws Exception {
		Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "needs a screen");
		ExitWindow w = changed(dir.resolve("a.sh"), "echo a\n");
		SwingUtilities.invokeAndWait(()->{
			try {
				w.answer = ExitChoice.SAVE;
				assertTrue(w.okToExit());
				assertFalse(w.hasChanged());
			} finally {
				w.dispose();
			}
		});
		assertEquals("echo a\necho more\n", Files.readString(dir.resolve("a.sh")));
	}

	@Test
	public void exitSaveThatFailsKeepsTheWindow(@TempDir Path dir) throws Exception {
		Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "needs a screen");
		ExitWindow w = changed(dir.resolve("a.sh"), "echo a\n");
		SwingUtilities.invokeAndWait(()->{
			try {
				// the file's folder goes away, so the save can't happen
				w.answer = ExitChoice.SAVE;
				try {
					w.setDocument(w.getDocument(), FileSourceFactory.getDefaultFactory()
							.createFileSource(dir.resolve("gone/a.sh").toString()));
				} catch (java.io.IOException e) {
					throw new RuntimeException(e);
				}
				w.editor().getTextArea().append("echo again\n");
				assertFalse(w.okToExit());
				assertEquals(1, w.errors.size(), w.errors.toString());
				assertTrue(w.hasChanged());
			} finally {
				w.dispose();
			}
		});
	}
}
