package us.bringardner.fsh.ide.fx;

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

		@Override
		protected boolean confirm(String question) {
			return true;
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
}
