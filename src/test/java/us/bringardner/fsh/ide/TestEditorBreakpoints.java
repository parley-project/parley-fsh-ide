package us.bringardner.fsh.ide;

import us.bringardner.fsh.debug.Breakpoint;
import us.bringardner.fsh.ide.core.CompileError;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

	/** Where Step Into stopped: just the statement, if the line still says it. */
	@Test
	public void highlightsAStatement() throws Exception {
		onEdt(()->{
			EditorPanel editor = new EditorPanel();
			editor.setText("echo a\nx=1; echo b\n", null);
			assertTrue(editor.addStatementHighlight(1, 5, 6, "echo b", java.awt.Color.GREEN));
			int at = "echo a\nx=1; ".length();
			assertEquals(at, editor.getStatementHighlight()[0]);
			assertEquals(at+6, editor.getStatementHighlight()[1]);

			// one at a time; a line that says something else isn't highlighted
			assertTrue(!editor.addStatementHighlight(1, 5, 6, "echo c", java.awt.Color.GREEN));
			assertNull(editor.getStatementHighlight());
			assertTrue(!editor.addStatementHighlight(1, 9, 6, "echo b", java.awt.Color.GREEN));

			editor.addStatementHighlight(1, 5, 6, "echo b", java.awt.Color.GREEN);
			editor.removeAllLineHighlights();
			assertNull(editor.getStatementHighlight());
		});
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

	/** The Breakpoints tab shows a breakpoint can be changed: its tooltip, and Edit... once one is selected. */
	@Test
	public void theBreakpointsTabHasAnEditButton() throws Exception {
		onEdt(()->{
			EditorPanel editor = new EditorPanel();
			editor.setText("echo a\necho b\n", null);
			editor.addBreakpoint(1);
			DebugVariablePanel panel = new DebugVariablePanel();
			panel.setContext(editor, null);
			assertEquals(1, panel.breakpointsTable().getRowCount());
			assertEquals(Breakpoint.EDIT_HINT, panel.breakpointsTable().getToolTipText());
			assertEquals(Breakpoint.EDIT_HINT, panel.editBreakpointButton().getToolTipText());
			assertTrue(!panel.editBreakpointButton().isEnabled());
			panel.breakpointsTable().setRowSelectionInterval(0, 0);
			assertTrue(panel.editBreakpointButton().isEnabled());
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
