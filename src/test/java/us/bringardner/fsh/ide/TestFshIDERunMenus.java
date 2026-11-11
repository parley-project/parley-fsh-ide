/**
 *	Copyright 2026 Tony Bringardner
 *
 *	Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.
 *	You may obtain a copy of the License at
 *
 *	http://www.apache.org/licenses/LICENSE-2.0
 *
 *	Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *	on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for
 *	the specific language governing permissions and limitations under the License.
 */
package us.bringardner.fsh.ide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import us.bringardner.fsh.ide.core.ScriptDocument;

/**
 * While a script runs in the Swing IDE: it can't be edited, and the editor's right-click menu
 * is the run's (Stop, Copy) or the debugger's (Run to Line, breakpoints, the variable clicked,
 * the steps) in place of the edit menu.
 */
public class TestFshIDERunMenus {

	@TempDir
	static Path home;

	@BeforeAll
	public static void quietHome() {
		System.setProperty("user.home", home.toString());
	}

	private static <T> T onEdt(Callable<T> code) throws Exception {
		Object[] ret = new Object[1];
		Exception[] failed = new Exception[1];
		SwingUtilities.invokeAndWait(()->{
			try {
				ret[0] = code.call();
			} catch (Exception e) {
				failed[0] = e;
			}
		});
		if( failed[0] != null ) {
			throw failed[0];
		}
		@SuppressWarnings("unchecked")
		T t = (T) ret[0];
		return t;
	}

	private static void waitFor(Callable<Boolean> condition, String what) throws Exception {
		long end = System.currentTimeMillis()+20_000;
		while( !onEdt(condition)) {
			assertTrue(System.currentTimeMillis() < end, what);
			Thread.sleep(20);
		}
	}

	private static FshIDE window(String code) throws Exception {
		Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "needs a screen");
		return onEdt(()->{
			FshIDE f = new FshIDE();
			f.setSize(900, 600);
			f.setVisible(true);
			f.setDocument(ScriptDocument.parse(code), null);
			return f;
		});
	}

	/** the items' texts ("-" for a separator), a * after one that is greyed */
	private static List<String> texts(List<Component> items) {
		List<String> ret = new ArrayList<>();
		for(Component c : items) {
			ret.add(c instanceof JMenuItem ? ((JMenuItem) c).getText()+(c.isEnabled() ? "" : "*") : "-");
		}
		return ret;
	}

	/** the items' texts without the greyed marks */
	private static List<String> names(List<String> texts) {
		List<String> ret = new ArrayList<>();
		for(String t : texts) {
			ret.add(t.endsWith("*") ? t.substring(0, t.length()-1) : t);
		}
		return ret;
	}

	private static void click(FshIDE w, int offset, String text) throws Exception {
		onEdt(()->{
			for(Component c : w.runPopupItems(offset)) {
				if( c instanceof JMenuItem && ((JMenuItem) c).getText().equals(text)) {
					((JMenuItem) c).doClick();
					return null;
				}
			}
			throw new AssertionError("no "+text+" in "+texts(w.runPopupItems(offset)));
		});
	}

	/** a variable as the debug view shows it, or null */
	private static String value(FshIDE w, String name) {
		JTable table = w.showDebugView(true).userTable();
		for(int row=0; row < table.getModel().getRowCount(); row++ ) {
			if( name.equals(table.getModel().getValueAt(row, 0))) {
				return String.valueOf(table.getModel().getValueAt(row, 2));
			}
		}
		return null;
	}

	/** what the editor's own popup menu holds when it's opened now */
	private static List<String> popup(FshIDE w) throws Exception {
		return onEdt(()->{
			FshIDETextArea area = w.editor().getTextArea();
			JPopupMenu menu = area.getPopupMenu();
			area.configurePopupMenu(menu);
			return texts(java.util.Arrays.asList(menu.getComponents()));
		});
	}

	@Test
	public void theDebugMenuTakesTheEditMenusPlace() throws Exception {
		//             line 1   2     3        4            5     6
		String code = "x=1\nx=2\ny=$x\nname=\"a b\"\nx=3\nx=4\n";
		FshIDE w = window(code);
		try {
			// nothing runs: the edit menu, and the text can be edited
			List<String> edit = popup(w);
			assertTrue(edit.contains("Paste"), edit.toString());
			// (it had a Breakpoint item that did nothing)
			assertFalse(edit.contains("Breakpoint"), edit.toString());
			assertNull(onEdt(()->w.runPopupItems(0)));
			assertTrue(onEdt(()->w.editor().isEditable()));

			onEdt(()->{
				w.editor().addBreakpoint(1);
				w.actionDebug(true);
				return null;
			});
			waitFor(()->"1".equals(value(w, "x")), "didn't stop at line 2");

			// stopped on line 2, a right-click on line 3's $x
			int atX = code.indexOf("$x")+1;
			assertEquals(List.of("Run to Line 3", "-", "Toggle Breakpoint", "-", "Watch x...", "Show Value of x", "-",
					"Resume", "Step Over", "Step Into", "Stop", "-", "Copy*"), texts(onEdt(()->w.runPopupItems(atX))));
			// on the breakpoint's line: its properties too
			List<String> onBreakpoint = texts(onEdt(()->w.runPopupItems(code.indexOf("x=2")+3)));
			assertTrue(onBreakpoint.contains("Breakpoint Properties..."), onBreakpoint.toString());
			assertFalse(onBreakpoint.contains("Watch x..."), "the 2 of x=2 isn't a variable: "+onBreakpoint);
			// the editor's own popup menu is that menu, not the edit menu
			List<String> shown = popup(w);
			assertTrue(shown.contains("Resume") && !shown.contains("Paste"), shown.toString());

			// it can't be edited, however it's tried, and the title says so
			assertFalse(onEdt(()->w.editor().isEditable()));
			onEdt(()->{
				w.editor().getTextArea().replaceRange("zzz", 0, 1);
				w.editor().getTextArea().insert("zzz", 0);
				return null;
			});
			assertEquals(code, onEdt(()->w.editor().getText()));
			assertTrue(onEdt(w::getTitle).endsWith(FshIDE.RUNNING_TITLE), onEdt(w::getTitle));

			// Show Value of the variable clicked
			click(w, atX, "Show Value of x");
			assertTrue(onEdt(w::logText).contains("x = 1\n"), onEdt(w::logText));

			// Run to Line 5 (no breakpoint there): it stops there, after lines 3 and 4
			click(w, code.indexOf("x=3"), "Run to Line 5");
			waitFor(()->"a b".equals(value(w, "name")), "didn't stop at line 5");
			assertEquals("2", onEdt(()->value(w, "x")));
			assertEquals("2", onEdt(()->value(w, "y")));
			// Show Value of a selection
			onEdt(()->{
				w.editor().getTextArea().select(code.indexOf("$x"), code.indexOf("$x")+2);
				return null;
			});
			List<String> withSelection = texts(onEdt(()->w.runPopupItems(0)));
			assertTrue(withSelection.contains("Show Value of $x") && withSelection.contains("Copy"), withSelection.toString());
			click(w, 0, "Show Value of $x");
			assertTrue(onEdt(w::logText).contains("$x = 2\n"), onEdt(w::logText));

			// Toggle Breakpoint on line 6, and Resume stops there
			click(w, code.indexOf("x=4"), "Toggle Breakpoint");
			assertNotNull(onEdt(()->w.editor().getBreakpoint(5)));
			click(w, 0, "Resume");
			waitFor(()->"3".equals(value(w, "x")), "didn't stop at line 6");
			click(w, 0, "Stop");
			waitFor(()->w.runButtonsEnabled()[0], "the run didn't end");

			// over: the edit menu again, it can be edited, and the title no longer says running
			assertNull(onEdt(()->w.runPopupItems(0)));
			// (the same items; Cut and Copy are no longer greyed, with the selection there is now)
			assertEquals(names(edit), names(popup(w)));
			assertTrue(onEdt(()->w.editor().isEditable()));
			assertFalse(onEdt(w::getTitle).endsWith(FshIDE.RUNNING_TITLE));
			onEdt(()->{
				w.editor().getTextArea().insert("#", 0);
				return null;
			});
			assertEquals("#"+code, onEdt(()->w.editor().getText()));
		} finally {
			SwingUtilities.invokeAndWait(w::dispose);
		}
	}

	@Test
	public void aPlainRunHasStopAndCopy() throws Exception {
		String code = "echo start\nsleep 30\necho end\n";
		FshIDE w = window(code);
		try {
			onEdt(()->{
				w.actionExecute(true);
				return null;
			});
			waitFor(()->w.runButtonsEnabled()[2], "the run didn't start");
			assertEquals(List.of("Stop", "Copy*"), texts(onEdt(()->w.runPopupItems(0))));
			assertEquals(List.of("Stop", "Copy*"), popup(w));
			assertFalse(onEdt(()->w.editor().isEditable()));
			onEdt(()->{
				w.editor().getTextArea().select(0, 4);
				return null;
			});
			assertEquals(List.of("Stop", "Copy"), texts(onEdt(()->w.runPopupItems(0))));
			click(w, 0, "Stop");
			waitFor(()->w.runButtonsEnabled()[0], "the run didn't stop");
			assertNull(onEdt(()->w.runPopupItems(0)));
			assertTrue(onEdt(()->w.editor().isEditable()));
		} finally {
			SwingUtilities.invokeAndWait(w::dispose);
		}
	}

	/** While a debug run isn't stopped, what needs a stopped script is greyed. */
	@Test
	public void whatNeedsAStoppedScriptIsGreyedWhileItRuns() throws Exception {
		String code = "echo start\nsleep 30\necho end\n";
		FshIDE w = window(code);
		try {
			onEdt(()->{
				w.actionDebug(true);
				return null;
			});
			waitFor(()->w.runButtonsEnabled()[2], "the run didn't start");
			assertEquals(List.of("Run to Line 1*", "-", "Toggle Breakpoint", "-", "Resume*", "Step Over*", "Step Into*", "Stop", "-", "Copy*"),
					texts(onEdt(()->w.runPopupItems(0))));
			click(w, 0, "Stop");
			waitFor(()->w.runButtonsEnabled()[0], "the run didn't stop");
		} finally {
			SwingUtilities.invokeAndWait(w::dispose);
		}
	}
}
