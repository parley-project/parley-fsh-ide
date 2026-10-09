package us.bringardner.fsh.ide.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javafx.scene.control.TreeItem;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import us.bringardner.fsh.ide.core.Breakpoint;
import us.bringardner.fsh.ide.core.Completions.Completion;
import us.bringardner.fsh.ide.core.SyntaxNode;

/** Find and replace, go to line, completion and the syntax tree in the JavaFX IDE. */
public class EditingTest {

	@TempDir
	static Path home;

	@BeforeAll
	public static void quietHome() {
		System.setProperty("user.home", home.toString());
	}

	static class TestWindow extends IdeWindow {
		Integer line;

		TestWindow() {
			super(new Stage(), new IdeWindowTest.MemoryPreferences());
		}

		@Override
		protected boolean confirm(String question) {
			return true;
		}

		@Override
		protected void report(String title, Throwable error) {
		}

		@Override
		protected Integer askLine(int current, int max) {
			return line;
		}
	}

	private static TestWindow window(String code) throws Exception {
		return Fx.call(()->{
			TestWindow w = new TestWindow();
			w.setCode(code, null);
			return w;
		});
	}

	private static String selected(TestWindow w) throws Exception {
		return Fx.call(()->w.editor().getCodeArea().getSelectedText());
	}

	@Test
	public void findsAndWraps() throws Exception {
		TestWindow w = window("echo one\necho two\necho ONE\n");
		Fx.run(()->{
			w.editor().select(5, 8);
			w.findBar().show(false);
		});
		assertEquals("one", Fx.call(()->w.findBar().find.getText()));
		assertTrue(Fx.call(()->w.findBar().findNext(true)));
		assertEquals(23, Fx.call(()->w.editor().getCodeArea().getSelection().getStart()));
		assertEquals("2 matches", Fx.call(()->w.findBar().resultText()));
		// round the end, back to the first
		assertTrue(Fx.call(()->w.findBar().findNext(true)));
		assertEquals(5, Fx.call(()->w.editor().getCodeArea().getSelection().getStart()));
		assertTrue(Fx.call(()->w.findBar().resultText()).startsWith("Wrapped"));

		Fx.run(()->w.findBar().matchCase.setSelected(true));
		assertTrue(Fx.call(()->w.findBar().findNext(true)));
		assertEquals(5, Fx.call(()->w.editor().getCodeArea().getSelection().getStart()));

		Fx.run(()->{
			w.findBar().regex.setSelected(true);
			w.findBar().find.setText("(");
		});
		assertFalse(Fx.call(()->w.findBar().findNext(true)));
		assertEquals("Bad regular expression", Fx.call(()->w.findBar().resultText()));
	}

	@Test
	public void replaces() throws Exception {
		TestWindow w = window("x=1\necho $x\nx=2\necho $x\n");
		Breakpoint bp = Fx.call(()->w.editor().addBreakpoint(3));
		Fx.run(()->{
			w.findBar().show(true);
			w.findBar().find.setText("x");
			w.findBar().wholeWords.setSelected(true);
			w.findBar().replace.setText("count");
		});
		// the first Replace finds; the next replaces it and finds the next
		Fx.run(()->w.findBar().replace(true));
		assertEquals("x", selected(w));
		Fx.run(()->w.findBar().replace(true));
		assertTrue(Fx.call(()->w.editor().getText()).startsWith("count=1\n"));

		assertEquals(3, Fx.call(()->w.findBar().replaceAll()));
		assertEquals("count=1\necho $count\ncount=2\necho $count\n", Fx.call(()->w.editor().getText()));
		// the breakpoint is still on its line
		assertEquals(3, bp.getLine());
		assertEquals(bp, Fx.call(()->w.editor().getBreakpoint(3)));
	}

	@Test
	public void goesToALine() throws Exception {
		TestWindow w = window("a\nb\nc\nd\n");
		w.line = 3;
		Fx.run(w::actionGoToLine);
		assertEquals(2, Fx.call(()->w.editor().getCaretLine()));
		w.line = 99;
		Fx.run(w::actionGoToLine);
		assertEquals("There's no line 99", Fx.call(w::statusText));
	}

	@Test
	public void completesTemplatesAndVariables() throws Exception {
		TestWindow w = window("count=0\n");
		Fx.run(()->{
			w.editor().getCodeArea().appendText("whi");
			w.editor().getCodeArea().moveTo(w.editor().getCodeArea().getLength());
			w.completion().show();
		});
		Completion first = Fx.call(()->w.completion().candidates().get(0));
		assertEquals("while", first.label);
		Fx.run(()->w.completion().apply(first));
		assertTrue(Fx.call(()->w.editor().getText()).startsWith("count=0\nwhile [ false ]\ndo\n"), Fx.call(()->w.editor().getText()));
		// the first field is selected, to type over
		assertEquals("false", selected(w));
		assertFalse(Fx.call(()->w.completion().isActive()));

		Fx.run(()->{
			w.editor().getCodeArea().replaceText("count=0\necho $co");
			w.editor().getCodeArea().moveTo(16);
			w.completion().show();
		});
		assertEquals("$count", Fx.call(()->w.completion().candidates().get(0).label));
		Fx.run(()->w.completion().apply(w.completion().candidates().get(0)));
		assertEquals("count=0\necho $count", Fx.call(()->w.editor().getText()));
	}

	@Test
	public void escapeClosesTheList() throws Exception {
		TestWindow w = window("");
		Fx.run(()->{
			w.editor().getCodeArea().appendText("i");
			w.editor().getCodeArea().moveTo(1);
			w.completion().show();
		});
		assertTrue(Fx.call(()->w.completion().isActive()));
		KeyEvent esc = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE, false, false, false, false);
		Fx.run(()->javafx.event.Event.fireEvent(w.editor().getCodeArea(), esc));
		assertFalse(Fx.call(()->w.completion().isActive()));
	}

	@Test
	public void syntaxTree() throws Exception {
		TestWindow w = window("for i in 1 2\ndo\n  echo $i\ndone\nls | wc -l\n");
		Fx.run(w::checkSyntax);
		Fx.waitFor(()->w.debugPanel().syntaxView().treeView().getRoot() != null
				&& w.debugPanel().syntaxView().treeView().getRoot().getValue() != null);
		TreeItem<SyntaxNode> root = Fx.call(()->w.debugPanel().syntaxView().treeView().getRoot());
		String kinds = Fx.call(()->root.getChildren().stream().map(i->i.getValue().getKind()).collect(Collectors.joining(",")));
		assertTrue(kinds.contains("for") || kinds.contains("loop"), kinds);
		assertTrue(kinds.contains("pipeline"), kinds);

		// going to a node's line
		Fx.run(()->{
			w.debugPanel().syntaxView().treeView().getSelectionModel().select(root.getChildren().get(root.getChildren().size()-1));
			w.debugPanel().syntaxView().treeView().fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER, false, false, false, false));
		});
		assertEquals(4, Fx.call(()->w.editor().getCaretLine()));
	}
}
