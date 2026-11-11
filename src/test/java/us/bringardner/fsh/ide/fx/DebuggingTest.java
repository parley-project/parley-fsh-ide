package us.bringardner.fsh.ide.fx;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javafx.stage.Stage;
import us.bringardner.fsh.ide.core.Breakpoint;
import us.bringardner.fsh.ide.core.Variable;

/** Debugging in the JavaFX IDE: breakpoints, stepping, variables, the log, the syntax check. */
public class DebuggingTest {

	@TempDir
	static Path home;

	@BeforeAll
	public static void quietHome() {
		System.setProperty("user.home", home.toString());
	}

	static class TestWindow extends IdeWindow {
		final List<String> problems = new ArrayList<>();
		BreakpointDialog.Result dialogResult = BreakpointDialog.Result.CANCELED;

		TestWindow() {
			super(new Stage(), new IdeWindowTest.MemoryPreferences());
		}

		// the answer to confirm(), and the questions asked
		boolean answer = true;
		final List<String> questions = new java.util.concurrent.CopyOnWriteArrayList<>();

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
		protected BreakpointDialog.Result editBreakpointDialog(Breakpoint bp) {
			return dialogResult;
		}

		// what the variable editor gives (null for Cancel), and whether it was opened editable
		String variableText;
		Boolean variableEditable;

		@Override
		protected java.util.Optional<String> editVariableDialog(Variable v, boolean editable) {
			variableEditable = editable;
			return java.util.Optional.ofNullable(variableText);
		}
	}

	private static TestWindow window(String code) throws Exception {
		return Fx.call(()->{
			TestWindow w = new TestWindow();
			w.setCode(code, null);
			return w;
		});
	}

	private static Object variable(TestWindow w, String name) throws Exception {
		return Fx.call(()->{
			for(Variable v : w.debugPanel().variables()) {
				if( v.getName().equals(name)) {
					return v.getValue();
				}
			}
			return null;
		});
	}

	private static String console(TestWindow w) throws Exception {
		return Fx.call(()->w.console().getArea().getText());
	}

	@Test
	public void stopsStepsAndResumes() throws Exception {
		TestWindow w = window("x=1\necho a\nx=2\necho b\n");
		Fx.run(()->{
			w.editor().addBreakpoint(2);
			w.debug();
		});
		Fx.waitFor(w::isPaused);
		assertEquals(2, Fx.call(()->w.editor().getCurrentLine()));
		assertEquals("1", String.valueOf(variable(w, "x")));
		assertTrue(Fx.call(()->w.debugPanel().variablesEditable()));
		assertEquals("1: x=1\n2: echo a\n", Fx.call(()->w.debugPanel().logText()));
		assertEquals("Paused at line 3", Fx.call(w::statusText));

		Fx.run(()->w.debugSession().stepOver());
		Fx.waitFor(()->w.isPaused() && w.editor().getCurrentLine() == 3);
		assertEquals("2", String.valueOf(variable(w, "x")));

		Fx.run(()->w.debugSession().resume());
		Fx.waitFor(()->!w.isRunning());
		assertEquals(-1, Fx.call(()->w.editor().getCurrentLine()));
		assertEquals("a\nb\n", console(w));
		assertFalse(Fx.call(()->w.debugPanel().variablesEditable()));
		assertTrue(Fx.call(()->w.debugPanel().logText()).endsWith("4: echo b\n"));
	}

	@Test
	public void stepIntoMarksJustTheStatement() throws Exception {
		TestWindow w = window("x=1; x=2\necho a\n");
		Fx.run(()->{
			w.editor().addBreakpoint(0);
			w.debug();
		});
		Fx.waitFor(w::isPaused);
		assertNull(Fx.call(()->w.editor().getCurrentStatement()), "a breakpoint marks the line");
		Fx.run(()->w.debugSession().stepInto());
		Fx.waitFor(()->w.isPaused() && w.editor().getCurrentStatement() != null);
		assertArrayEquals(new int[] {5, 8}, Fx.call(()->w.editor().getCurrentStatement()));
		Fx.run(()->w.debugSession().resume());
		Fx.waitFor(()->!w.isRunning());
		assertNull(Fx.call(()->w.editor().getCurrentStatement()));
	}

	@Test
	public void aVariableChangedWhilePausedIsUsed() throws Exception {
		TestWindow w = window("name=world\necho hello $name\n");
		Fx.run(()->{
			w.editor().addBreakpoint(1);
			w.debug();
		});
		Fx.waitFor(w::isPaused);
		Fx.run(()->w.setVariable(new Variable("name", "there"), "there"));
		assertEquals("there", String.valueOf(variable(w, "name")));
		Fx.run(()->w.debugSession().resume());
		Fx.waitFor(()->!w.isRunning());
		assertEquals("hello there\n", console(w));
	}

	@Test
	public void conditionalBreakpoint() throws Exception {
		TestWindow w = window("for i in 1 2 3 4\ndo\n  x=$i\ndone\n");
		Fx.run(()->{
			Breakpoint bp = w.editor().addBreakpoint(2);
			bp.setConditional(true);
			bp.setCondition("[ $i -eq 3 ]");
			w.debug();
		});
		Fx.waitFor(w::isPaused);
		assertEquals("3", String.valueOf(variable(w, "i")));
		Fx.run(()->w.debugSession().resume());
		Fx.waitFor(()->!w.isRunning());
		assertTrue(w.problems.isEmpty(), w.problems.toString());
	}

	@Test
	public void runningIgnoresBreakpoints() throws Exception {
		TestWindow w = window("echo a\necho b\n");
		Fx.run(()->{
			w.editor().addBreakpoint(1);
			w.run();
		});
		Fx.waitFor(()->!w.isRunning());
		assertEquals("a\nb\n", console(w));
	}

	@Test
	public void stoppingWhilePaused() throws Exception {
		TestWindow w = window("echo a\necho b\necho c\n");
		Fx.run(()->{
			w.editor().addBreakpoint(1);
			w.debug();
		});
		Fx.waitFor(w::isPaused);
		Fx.run(w::stop);
		Fx.waitFor(()->!w.isRunning());
		assertEquals("a\n", console(w));
		assertTrue(Fx.call(w::statusText).startsWith("Stopped"));
	}

	@Test
	public void breakpointsListAndDialog() throws Exception {
		TestWindow w = window("echo a\necho b\n");
		Breakpoint bp = Fx.call(()->w.editor().addBreakpoint(1));
		assertEquals(List.of(bp), Fx.call(()->List.copyOf(w.debugPanel().breakpoints())));
		w.dialogResult = BreakpointDialog.Result.DELETE;
		// what a right-click on the marker or a double-click in the list does
		Fx.run(()->w.editBreakpoint(bp));
		assertTrue(Fx.call(()->w.editor().getBreakpoints().isEmpty()));
		assertTrue(Fx.call(()->w.debugPanel().breakpoints().isEmpty()));
	}

	/** The Breakpoints tab's Edit... button: the dialog a double-click opens, for the selected one. */
	@Test
	public void editButtonOpensTheSelectedBreakpointsDialog() throws Exception {
		TestWindow w = window("echo a\necho b\n");
		Breakpoint bp = Fx.call(()->w.editor().addBreakpoint(1));
		assertTrue(Fx.call(()->w.debugPanel().editBreakpointButton().isDisabled()));
		assertEquals(Breakpoint.EDIT_HINT, Fx.call(()->w.debugPanel().editBreakpointButton().getTooltip().getText()));
		Fx.run(()->w.debugPanel().breakpointTable().getSelectionModel().select(bp));
		assertFalse(Fx.call(()->w.debugPanel().editBreakpointButton().isDisabled()));
		w.dialogResult = BreakpointDialog.Result.DELETE;
		Fx.run(()->w.debugPanel().editBreakpointButton().fire());
		assertTrue(Fx.call(()->w.editor().getBreakpoints().isEmpty()));
	}

	/** The breakpoint dialog's info button, and what it tells: the spec's rules and its reference. */
	@Test
	public void theBreakpointDialogHasAnInfoButton() throws Exception {
		TestWindow w = window("echo a\n");
		Breakpoint bp = Fx.call(()->w.editor().addBreakpoint(0));
		assertTrue(Fx.call(()->new BreakpointDialog(bp).infoButton.getGraphic() != null));
		assertTrue(us.bringardner.fsh.ide.core.BreakpointHelp.TEXT.contains(us.bringardner.fsh.ide.core.BreakpointHelp.SPEC_URL));
		assertTrue(us.bringardner.fsh.ide.core.BreakpointHelp.TEXT.contains("hitCondition should be evaluated only if the condition is met"));
	}

	@Test
	public void syntaxErrorsAreMarked() throws Exception {
		TestWindow w = window("echo a\n");
		Fx.run(()->w.editor().getCodeArea().appendText("if true; then\n  echo b\n"));
		Fx.waitFor(()->{
			for(int i=0; i < 5; i++) {
				if( w.editor().errorAt(i) != null ) {
					return true;
				}
			}
			return false;
		});
		Fx.run(()->w.editor().getCodeArea().appendText("fi\n"));
		Fx.waitFor(()->{
			for(int i=0; i < 5; i++) {
				if( w.editor().errorAt(i) != null ) {
					return false;
				}
			}
			return true;
		});
		assertNull(Fx.call(()->w.editor().errorAt(1)));
	}

	@Test
	public void breakpointDialogApplies() throws Exception {
		Breakpoint bp = new Breakpoint();
		bp.setCode("echo a");
		Fx.run(()->{
			BreakpointDialog d = new BreakpointDialog(bp);
			d.enabled.setSelected(false);
			d.conditional.setSelected(true);
			d.condition.setText(" [ $x = y ] ");
			d.hitCount.setSelected(true);
			d.hits.getValueFactory().setValue(3);
			d.apply();
		});
		assertFalse(bp.isEnabled(false));
		assertTrue(bp.isConditional());
		assertEquals("[ $x = y ]", bp.getCondition());
		assertEquals(3, bp.getHitCount());
	}

	private static java.util.List<String> names(java.util.List<Variable> list) {
		java.util.List<String> ret = new ArrayList<>();
		for(Variable v : list) {
			ret.add(v.getName());
		}
		return ret;
	}

	@Test
	public void theScriptsOwnAndFshsAreOnTheirOwnTabs() throws Exception {
		TestWindow w = window("x=1\nfor i in a b; do\n  echo $i\ndone\n");
		Fx.run(()->{
			w.editor().addBreakpoint(2);
			w.debug();
		});
		Fx.waitFor(w::isPaused);
		java.util.List<String> user = Fx.call(()->names(w.debugPanel().userVariables()));
		java.util.List<String> fsh = Fx.call(()->names(w.debugPanel().fshVariables()));
		assertTrue(user.contains("x"), user.toString());
		assertTrue(user.contains("i"), user.toString());
		assertFalse(user.contains("IFS"), user.toString());
		assertTrue(fsh.contains("IFS"), fsh.toString());
		assertFalse(fsh.contains("x"), fsh.toString());

		// one filter for both tabs
		Fx.run(()->w.debugPanel().filterField().setText("ifs"));
		assertTrue(Fx.call(()->names(w.debugPanel().fshVariables())).contains("IFS"));
		assertFalse(Fx.call(()->names(w.debugPanel().fshVariables())).contains("PS1"));
		assertEquals(java.util.List.of(), Fx.call(()->names(w.debugPanel().userVariables())));
		Fx.run(()->w.debugPanel().filterField().setText(""));

		// the breakpoint is in the loop: it stops again for b
		Fx.run(()->w.debugSession().resume());
		Fx.waitFor(()->w.isPaused() && w.debugPanel().variables().stream().anyMatch(v->v.getName().equals("i") && "b".equals(String.valueOf(v.getValue()))));
		Fx.run(()->w.debugSession().resume());
		Fx.waitFor(()->!w.isRunning());
	}

	private static String fshValue(TestWindow w, String name) throws Exception {
		return Fx.call(()->{
			for(Variable v : w.debugPanel().fshVariables()) {
				if( v.getName().equals(name)) {
					return String.valueOf(v.getValue());
				}
			}
			return null;
		});
	}

	/**
	 * While the debug view shows and no script runs, the fsh Variables tab shows what the next
	 * run starts with (and says so); a debug run's own take their place while it runs.
	 */
	@Test
	public void theNextRunsVariablesShowWhileNothingRuns() throws Exception {
		TestWindow w = window("IFS=,\nx=1\necho $x\n");
		// not until the view shows
		assertFalse(Fx.call(()->w.debugPanel().showsStartVariables()));
		Fx.run(()->w.showDebugView(true));
		Fx.waitFor(()->w.debugPanel().showsStartVariables());
		assertEquals(" \t\n", fshValue(w, "IFS"));
		assertEquals("fsh", fshValue(w, "$0"));
		assertEquals(java.util.List.of(), Fx.call(()->names(w.debugPanel().userVariables())));
		// (nothing is running: they can't be changed)
		assertFalse(Fx.call(()->w.debugPanel().variablesEditable()));

		// a debug run: the script's own
		Fx.run(()->{
			w.editor().addBreakpoint(2);
			w.debug();
		});
		Fx.waitFor(w::isPaused);
		assertFalse(Fx.call(()->w.debugPanel().showsStartVariables()));
		assertEquals(",", fshValue(w, "IFS"));

		// when it ends: the next run's again, and the script's own as it left them
		Fx.run(()->w.debugSession().resume());
		Fx.waitFor(()->!w.isRunning());
		Fx.waitFor(()->w.debugPanel().showsStartVariables());
		assertEquals(" \t\n", fshValue(w, "IFS"));
		assertEquals("1", String.valueOf(variable(w, "x")));
		assertFalse(Fx.call(()->w.debugPanel().variablesEditable()));

		// a script's file is its $0
		us.bringardner.parley.files.FileSource file = us.bringardner.parley.files.FileSourceFactory.getDefaultFactory()
				.createFileSource(home.resolve("hello.fsh").toString());
		Fx.run(()->w.setCode("echo hi\n", file));
		Fx.waitFor(()->file.getAbsolutePath().equals(fshValue0(w)));
	}

	// (on the JavaFX thread)
	private static String fshValue0(TestWindow w) {
		for(Variable v : w.debugPanel().fshVariables()) {
			if( v.getName().equals("$0")) {
				return String.valueOf(v.getValue());
			}
		}
		return null;
	}

	@Test
	public void aVariableIsChangedInItsEditor() throws Exception {
		TestWindow w = window("name=world\necho hello $name\n");
		Fx.run(()->{
			w.editor().addBreakpoint(1);
			w.debug();
		});
		Fx.waitFor(w::isPaused);
		w.variableText = null;
		Fx.run(()->w.editVariable(new Variable("name", "world")));
		assertEquals(Boolean.TRUE, w.variableEditable);
		assertEquals("world", String.valueOf(variable(w, "name")));
		w.variableText = "line 1\nline 2";
		Fx.run(()->w.editVariable(new Variable("name", "world")));
		assertEquals("line 1\nline 2", String.valueOf(variable(w, "name")));
		Fx.run(()->w.debugSession().resume());
		Fx.waitFor(()->!w.isRunning());
		assertEquals("hello line 1 line 2\n", console(w));
		// after the run the value can only be read
		w.variableText = "ignored";
		Fx.run(()->w.editVariable(new Variable("name", "x")));
		assertEquals(Boolean.FALSE, w.variableEditable);
	}

	@Test
	public void aClickedVariableShowsAsNameEqualsValue() throws Exception {
		// (on the JavaFX thread: DebugPanel is a Control, which needs JavaFX started)
		assertEquals("count = 3", Fx.call(()->DebugPanel.describe(new Variable("count", 3))));
		assertEquals("empty = ", Fx.call(()->DebugPanel.describe(new Variable("empty", null))));
	}

	@Test
	public void runWhilePausedRunsToTheEnd() throws Exception {
		TestWindow w = window("for i in a b c; do\n  echo $i\ndone\necho end\n");
		Fx.run(()->{
			w.editor().addBreakpoint(1);
			w.debug();
		});
		Fx.waitFor(w::isPaused);
		// Run is on while paused (Debug isn't)
		assertFalse(Fx.call(()->w.runButtonDisabled()));
		Fx.run(()->w.run());
		Fx.waitFor(()->!w.isRunning());
		assertEquals("a\nb\nc\nend\n", console(w));
		assertFalse(Fx.call(()->w.isPaused()));
		// the next debug run stops at the breakpoint again
		Fx.run(()->w.debug());
		Fx.waitFor(w::isPaused);
		Fx.run(()->w.run());
		Fx.waitFor(()->!w.isRunning());
	}

	@Test
	public void closingAsksOnlyWhenAScriptIsRunning() throws Exception {
		TestWindow w = window("x=1\necho a\n");
		// nothing to lose: no question
		assertTrue(Fx.call(()->w.close()));
		assertEquals(List.of(), w.questions);

		Fx.run(()->{
			w.editor().addBreakpoint(1);
			w.debug();
		});
		Fx.waitFor(w::isPaused);
		w.answer = false;
		assertFalse(Fx.call(()->w.close()));
		assertEquals(List.of("A script is still running. Stop it and close?"), w.questions);
		assertTrue(Fx.call(()->w.isRunning()));
		w.answer = true;
		assertTrue(Fx.call(()->w.close()));
		Fx.waitFor(()->!w.isRunning());
	}

	@Test
	public void aWatchpointStopsWhereTheVariableIsWritten() throws Exception {
		TestWindow w = window("x=1\necho a\ncount=5\necho $count\n");
		Fx.run(()->{
			w.debugPanel().setWatchpointAsker((name, read, write)->java.util.Optional.of(new us.bringardner.fsh.ide.core.Watchpoint("count", false, true)));
			w.debugPanel().addWatchpoint("");
			w.debug();
		});
		assertEquals(1, Fx.call(()->w.debugPanel().watchpointRows().size()));
		assertEquals(1, w.debugSession().getWatchpoints().size());
		Fx.waitFor(w::isPaused);
		assertEquals(2, Fx.call(()->w.editor().getCurrentLine()));
		assertEquals("5", String.valueOf(variable(w, "count")));
		assertEquals("Paused at line 3: watchpoint, count written (value: 5)", Fx.call(w::statusText));
		assertTrue(Fx.call(()->w.debugPanel().logText()).contains("Watchpoint: count written (value: 5)\n"));

		Fx.run(()->w.debugSession().resume());
		Fx.waitFor(()->!w.isRunning());
		assertEquals("a\n5\n", console(w));
	}

	@Test
	public void aReadWatchpointAndRemovingIt() throws Exception {
		TestWindow w = window("v=1\ny=2\necho $v\necho $v\n");
		Fx.run(()->{
			w.debugPanel().setWatchpointAsker((name, read, write)->java.util.Optional.of(new us.bringardner.fsh.ide.core.Watchpoint(name, true, false)));
			// as the variables' right-click menu does
			w.debugPanel().addWatchpoint("v");
			w.debug();
		});
		Fx.waitFor(w::isPaused);
		assertEquals(2, Fx.call(()->w.editor().getCurrentLine()));
		assertEquals("Paused at line 3: watchpoint, v read", Fx.call(w::statusText));
		Fx.run(()->w.debugPanel().removeWatchpoints(new ArrayList<>(w.debugPanel().watchpointRows())));
		assertTrue(w.debugSession().getWatchpoints().isEmpty());
		Fx.run(()->w.debugSession().resume());
		Fx.waitFor(()->!w.isRunning());
		assertEquals("1\n1\n", console(w));
	}

	@Test
	public void aCanceledWatchpointDialogAddsNothing() throws Exception {
		TestWindow w = window("x=1\n");
		Fx.run(()->{
			w.debugPanel().setWatchpointAsker((name, read, write)->java.util.Optional.empty());
			w.debugPanel().addWatchpoint("x");
		});
		assertTrue(w.debugSession().getWatchpoints().isEmpty());
	}

	@Test
	public void theWatchpointDialogNeedsANameAndReadOrWrite() {
		assertEquals(null, WatchpointDialog.error("count", true, false));
		assertEquals("Enter a variable's name.", WatchpointDialog.error("", true, true));
		assertEquals("Enter a variable's name.", WatchpointDialog.error("1x", true, true));
		assertEquals("Choose Read, Write or both.", WatchpointDialog.error("x", false, false));
	}

	@Test
	public void aVariableIsWatchedFromTheOutlineBeforeDebugging() throws Exception {
		TestWindow w = window("total=0\nfor i in 1 2; do\n  total=$(( total + i ))\ndone\necho $total\n");
		Fx.run(()->{
			w.debugPanel().setWatchpointAsker((name, read, write)->java.util.Optional.of(new us.bringardner.fsh.ide.core.Watchpoint(name, read, write)));
			w.checkSyntax();
		});
		Fx.waitFor(()->!w.outline().outline().getVariables().isEmpty());
		Fx.run(()->{
			for(us.bringardner.fsh.ide.core.ScriptOutline.Item v : w.outline().outline().getVariables()) {
				if( v.getName().equals("total")) {
					w.outline().watch(v);
				}
			}
		});
		assertEquals(1, w.debugSession().getWatchpoints().size());
		assertEquals("total", w.debugSession().getWatchpoints().get(0).getVariable());
		// functions aren't variables
		assertNull(OutlineView.variableName(null));

		Fx.run(w::debug);
		Fx.waitFor(w::isPaused);
		assertEquals(0, Fx.call(()->w.editor().getCurrentLine()));
		Fx.run(()->w.debugSession().resume());
		Fx.waitFor(()->w.isPaused() && w.editor().getCurrentLine() == 2);
		Fx.run(()->w.debugSession().runToEnd());
		Fx.waitFor(()->!w.isRunning());
		assertEquals("3\n", console(w));
	}

	// ---- while a script runs: no edits, and the run's or the debugger's right-click menu

	/** a menu's items' texts ("-" for a separator), a * after one that is greyed; on the JavaFX thread */
	private static List<String> texts(javafx.scene.control.ContextMenu menu) {
		List<String> ret = new ArrayList<>();
		for(javafx.scene.control.MenuItem m : menu.getItems()) {
			ret.add(m instanceof javafx.scene.control.SeparatorMenuItem ? "-" : m.getText()+(m.isDisable() ? "*" : ""));
		}
		return ret;
	}

	private static void click(TestWindow w, int pos, String text) throws Exception {
		Fx.run(()->{
			for(javafx.scene.control.MenuItem m : w.runMenu(pos).getItems()) {
				if( text.equals(m.getText())) {
					m.fire();
					return;
				}
			}
			throw new AssertionError("no "+text+" in "+texts(w.runMenu(pos)));
		});
	}

	@Test
	public void theDebugMenuTakesTheEditMenusPlace() throws Exception {
		//             line 1   2     3        4            5     6
		String code = "x=1\nx=2\ny=$x\nname=\"a b\"\nx=3\nx=4\n";
		TestWindow w = window(code);
		// nothing runs: the edit menu, and the text can be edited
		assertNull(Fx.call(()->w.runMenu(0)));
		assertTrue(Fx.call(()->texts(w.editor().menuFor(0))).contains("Paste"));
		assertTrue(Fx.call(()->w.editor().isEditable()));

		Fx.run(()->{
			w.editor().addBreakpoint(1);
			w.debug();
		});
		Fx.waitFor(w::isPaused);

		// stopped on line 2, a right-click on line 3's $x
		int atX = code.indexOf("$x")+1;
		assertEquals(List.of("Run to Line 3", "-", "Toggle Breakpoint", "-", "Watch x...", "Show Value of x", "-",
				"Resume", "Step Over", "Step Into", "Stop", "-", "Copy*"), Fx.call(()->texts(w.runMenu(atX))));
		// on the breakpoint's line: its properties too
		List<String> onBreakpoint = Fx.call(()->texts(w.runMenu(code.indexOf("x=2")+3)));
		assertTrue(onBreakpoint.contains("Breakpoint Properties..."), onBreakpoint.toString());
		assertFalse(onBreakpoint.contains("Watch x..."), "the 2 of x=2 isn't a variable: "+onBreakpoint);
		// the editor's right-click menu is that menu, not the edit menu
		List<String> shown = Fx.call(()->texts(w.editor().menuFor(atX)));
		assertTrue(shown.contains("Resume") && !shown.contains("Paste"), shown.toString());

		// it can't be edited: undo, a template, replace; and the status line says why
		assertFalse(Fx.call(()->w.editor().isEditable()));
		Fx.run(()->{
			w.editor().insert(0, 1, us.bringardner.fsh.ide.core.TemplateText.expand("zzz"));
			w.findBar().find.setText("x");
			w.findBar().replace.setText("q");
			assertEquals(0, w.findBar().replaceAll());
		});
		assertEquals(code, Fx.call(()->w.editor().getText()));

		// Show Value of the variable clicked: on the status line and under the variables
		click(w, atX, "Show Value of x");
		assertEquals("x = 1", Fx.call(w::statusText));
		assertEquals("x = 1", Fx.call(()->w.debugPanel().userDetailText()));

		// Run to Line 5 (no breakpoint there): it stops there, after lines 3 and 4
		click(w, code.indexOf("x=3"), "Run to Line 5");
		Fx.waitFor(()->w.isPaused() && w.editor().getCurrentLine() == 4);
		assertEquals("2", String.valueOf(variable(w, "y")));
		assertEquals("a b", String.valueOf(variable(w, "name")));
		// Show Value of a selection; text that would run a command isn't run
		Fx.run(()->w.editor().getCodeArea().selectRange(code.indexOf("$x"), code.indexOf("$x")+2));
		List<String> withSelection = Fx.call(()->texts(w.runMenu(0)));
		assertTrue(withSelection.contains("Show Value of $x") && withSelection.contains("Copy"), withSelection.toString());
		click(w, 0, "Show Value of $x");
		assertEquals("$x = 2", Fx.call(w::statusText));
		Fx.run(()->w.showValue("$(echo hi)"));
		assertTrue(Fx.call(w::statusText).contains("would run a command"), Fx.call(w::statusText));

		// Toggle Breakpoint on line 6, and Resume stops there
		click(w, code.indexOf("x=4"), "Toggle Breakpoint");
		assertTrue(Fx.call(()->w.editor().getBreakpoint(5) != null));
		click(w, 0, "Resume");
		Fx.waitFor(()->w.isPaused() && w.editor().getCurrentLine() == 5);
		click(w, 0, "Stop");
		Fx.waitFor(()->!w.isRunning());

		// over: the edit menu again, and it can be edited
		assertNull(Fx.call(()->w.runMenu(0)));
		assertTrue(Fx.call(()->texts(w.editor().menuFor(0))).contains("Paste"));
		assertTrue(Fx.call(()->w.editor().isEditable()));
	}

	@Test
	public void aPlainRunHasStopAndCopyAndADebugRunGreysWhatNeedsAStop() throws Exception {
		String code = "echo start\nsleep 30\necho end\n";
		TestWindow w = window(code);
		Fx.run(w::run);
		Fx.waitFor(w::isRunning);
		assertEquals(List.of("Stop", "Copy*"), Fx.call(()->texts(w.runMenu(0))));
		assertEquals(List.of("Stop", "Copy*"), Fx.call(()->texts(w.editor().menuFor(0))));
		assertFalse(Fx.call(()->w.editor().isEditable()));
		Fx.run(()->w.editor().getCodeArea().selectRange(0, 4));
		assertEquals(List.of("Stop", "Copy"), Fx.call(()->texts(w.runMenu(0))));
		click(w, 0, "Stop");
		Fx.waitFor(()->!w.isRunning());
		assertNull(Fx.call(()->w.runMenu(0)));
		assertTrue(Fx.call(()->w.editor().isEditable()));

		// a debug run that isn't stopped
		Fx.run(()->{
			w.editor().getCodeArea().deselect();
			w.debug();
		});
		Fx.waitFor(w::isRunning);
		assertEquals(List.of("Run to Line 1*", "-", "Toggle Breakpoint", "-", "Resume*", "Step Over*", "Step Into*", "Stop", "-", "Copy*"),
				Fx.call(()->texts(w.runMenu(0))));
		click(w, 0, "Stop");
		Fx.waitFor(()->!w.isRunning());
	}

	/** Through the Loupe adapter: Run to Line and Show Value work there too. */
	@Test
	public void runToLineAndShowValueThroughTheAdapter() throws Exception {
		String code = "x=1\nx=2\ny=$x\nx=3\nx=4\n";
		TestWindow w = window(code);
		Fx.run(()->{
			w.useAdapter(true);
			w.editor().addBreakpoint(1);
			w.debug();
		});
		Fx.waitFor(w::isPaused);
		assertEquals(1, Fx.call(()->w.editor().getCurrentLine()));
		click(w, code.indexOf("x=3"), "Run to Line 4");
		Fx.waitFor(()->w.isPaused() && w.editor().getCurrentLine() == 3);
		click(w, code.indexOf("y=")+0, "Show Value of y");
		Fx.waitFor(()->"y = 2".equals(w.statusText()));
		// it was for that once: Resume runs to the end
		click(w, 0, "Resume");
		Fx.waitFor(()->!w.isRunning());
		assertEquals(List.of(), w.problems);
	}
}
