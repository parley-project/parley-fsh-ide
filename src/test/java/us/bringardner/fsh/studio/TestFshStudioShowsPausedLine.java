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
package us.bringardner.fsh.studio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import java.util.concurrent.Callable;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import us.bringardner.fsh.debug.ScriptDocument;


/**
 * While debugging in the Swing IDE, the line the script stopped on shows: the editor scrolls to
 * it when it isn't in view, and stays where it is when it is.
 */
public class TestFshStudioShowsPausedLine {

	@TempDir
	static Path home;

	@BeforeAll
	public static void quietHome() {
		System.setProperty("user.home", home.toString());
		// no recent script of the user's: the window shows only the script the test gives it
		FshStudio.usePreferences(new MemoryPreferences());
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

	/** x as the debug view shows it (the script is paused after x=N: before line N+1), or null */
	private static String x(FshStudio w) {
		javax.swing.JTable table = w.showDebugView(true).userTable();
		for(int row=0; row < table.getModel().getRowCount(); row++ ) {
			if( "x".equals(table.getModel().getValueAt(row, 0))) {
				return String.valueOf(table.getModel().getValueAt(row, 1));
			}
		}
		return null;
	}

	@Test
	public void theEditorScrollsToTheLineTheScriptStoppedOn() throws Exception {
		Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "needs a screen");
		// 400 lines: far more than the editor shows
		StringBuilder code = new StringBuilder();
		for(int i=1; i <= 400; i++ ) {
			code.append("x=").append(i).append('\n');
		}
		FshStudio w = onEdt(()->{
			FshStudio f = new FshStudio();
			f.setSize(900, 600);
			f.setVisible(true);
			f.setDocument(ScriptDocument.parse(code.toString()), null);
			return f;
		});
		try {
			// (laid out, at the top)
			onEdt(()->null);
			assertTrue(onEdt(()->w.editor().isLineShowing(0)));
			assertFalse(onEdt(()->w.editor().isLineShowing(299)));

			// a breakpoint out of view: the editor scrolls to it
			onEdt(()->{
				w.editor().addBreakpoint(299);
				w.actionDebug(true);
				return null;
			});
			waitFor(()->"299".equals(x(w)), "didn't stop at line 300");
			assertTrue(onEdt(()->w.editor().isLineShowing(299)), "line 300 doesn't show");
			int scrolled = onEdt(()->w.editor().scrolledTo());
			assertTrue(scrolled > 0);
			// (with lines around it: not at the very top or bottom)
			assertTrue(onEdt(()->w.editor().isLineShowing(295) && w.editor().isLineShowing(303)));

			// a step to the next line, which shows: the editor stays where it is
			w.debugSession().stepOver();
			waitFor(()->"300".equals(x(w)), "didn't step to line 301");
			assertEquals(scrolled, (int) onEdt(()->w.editor().scrolledTo()));

			// a breakpoint far below: scrolled to again, and the caret is left alone
			int caret = onEdt(()->w.editor().getTextArea().getCaretPosition());
			onEdt(()->w.editor().addBreakpoint(389));
			w.debugSession().resume();
			waitFor(()->"389".equals(x(w)), "didn't stop at line 390");
			assertTrue(onEdt(()->w.editor().isLineShowing(389)), "line 390 doesn't show");
			assertEquals(caret, (int) onEdt(()->w.editor().getTextArea().getCaretPosition()));

			w.debugSession().resume();
			waitFor(()->w.runButtonsEnabled()[0], "the run didn't end");
		} finally {
			SwingUtilities.invokeAndWait(w::dispose);
		}
	}
}
