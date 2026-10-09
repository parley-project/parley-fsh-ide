package us.bringardner.fsh.ide;

import us.bringardner.fsh.ide.core.Breakpoint;
import us.bringardner.fsh.ide.core.CompileError;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.nio.file.Path;
import java.util.Set;

import javax.swing.SwingUtilities;
import javax.swing.text.Document;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Breakpoints stay on their statement as lines are added and removed above them. */
public class TestEditorBreakpoints {

	@TempDir
	static Path home;

	@BeforeAll
	public static void headless() {
		System.setProperty("java.awt.headless", "true");
		// EditorPanel reads the configuration; keep it out of the real home folder
		System.setProperty("user.home", home.toString());
	}

	/**
	 * Lets each editor finish setting up its spelling (in the background, once the dictionary
	 * is read: it creates the user's word list in the home folder) before that folder is deleted.
	 */
	@org.junit.jupiter.api.AfterAll
	public static void spellingSetUp() throws Exception {
		us.bringardner.fsh.ide.core.SpellChecking.english().get(60, java.util.concurrent.TimeUnit.SECONDS);
		Thread.sleep(200);
		SwingUtilities.invokeAndWait(()->{});
	}

	private static void onEdt(ThrowingRunnable r) throws Exception {
		Exception[] err = new Exception[1];
		SwingUtilities.invokeAndWait(()->{
			try {
				r.run();
			} catch (Exception e) {
				err[0] = e;
			}
		});
		if( err[0] != null ) {
			throw err[0];
		}
	}

	interface ThrowingRunnable {
		void run() throws Exception;
	}

	@Test
	public void followsItsLine() throws Exception {
		onEdt(()->{
			EditorPanel editor = new EditorPanel();
			editor.setText("echo a\necho b\necho c\necho d\n", null);
			Breakpoint bp = editor.addBreakpoint(2);
			assertNotNull(bp);
			Document doc = editor.getTextArea().getDocument();

			doc.insertString(0, "echo x\necho y\n", null);
			assertEquals(Set.of(4), editor.getBreakpoints().keySet());
			assertSame(bp, editor.getBreakpoint(4));
			assertEquals(4, bp.getLine());

			doc.remove(0, "echo x\necho y\n".length());
			assertSame(bp, editor.getBreakpoint(2));

			// a line typed below it doesn't move it
			doc.insertString(doc.getLength(), "echo e\n", null);
			assertSame(bp, editor.getBreakpoint(2));
		});
	}

	@Test
	public void blankLinesTakeNoBreakpoint() throws Exception {
		onEdt(()->{
			EditorPanel editor = new EditorPanel();
			editor.setText("echo a\n\necho c\n", null);
			assertNull(editor.addBreakpoint(1));
			assertEquals(0, editor.getBreakpoints().size());
		});
	}

	@Test
	public void twoThatMeetBecomeOne() throws Exception {
		onEdt(()->{
			EditorPanel editor = new EditorPanel();
			editor.setText("echo a\necho b\necho c\n", null);
			editor.addBreakpoint(0);
			editor.addBreakpoint(1);
			Document doc = editor.getTextArea().getDocument();
			doc.remove(0, "echo a\n".length());
			assertEquals(1, editor.getBreakpoints().size());
		});
	}

	@Test
	public void removedAndClearedWithTheText() throws Exception {
		onEdt(()->{
			EditorPanel editor = new EditorPanel();
			editor.setText("echo a\necho b\n", null);
			Breakpoint bp = editor.addBreakpoint(1);
			editor.removeBreakpoint(bp);
			assertEquals(0, editor.getBreakpoints().size());
			editor.addBreakpoint(0);
			editor.setText("echo z\n", null);
			assertEquals(0, editor.getBreakpoints().size());
		});
	}

	@Test
	public void listenersHearWhenABreakpointMoves() throws Exception {
		onEdt(()->{
			EditorPanel editor = new EditorPanel();
			editor.setText("echo a\necho b\n", null);
			editor.addBreakpoint(1);
			int[] calls = {0};
			editor.addBreapointListner(()->calls[0]++);
			Document doc = editor.getTextArea().getDocument();
			doc.insertString(doc.getLength(), "more", null);
			assertEquals(0, calls[0]);
			doc.insertString(0, "\n", null);
			assertEquals(1, calls[0]);
		});
	}

	@Test
	public void errorMarkers() throws Exception {
		onEdt(()->{
			EditorPanel editor = new EditorPanel();
			editor.setText("echo a\nfi\n", null);
			editor.addErrorMarker(new CompileError(2, 0, "unexpected fi"));
			// lines that aren't there are ignored
			editor.addErrorMarker(new CompileError(99, 0, "past the end"));
			assertEquals("unexpected fi", editor.errorAt(1));
			assertNull(editor.errorAt(0));
			editor.clearErrorMarkers();
			assertNull(editor.errorAt(1));
		});
	}
}
