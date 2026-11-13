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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.GraphicsEnvironment;
import java.nio.file.Path;

import javax.swing.JTable;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * While the Swing IDE's debug view shows and no script runs, its fsh Variables tab shows what
 * the next run starts with.
 */
public class TestFshIDEStartVariables {

	@TempDir
	static Path home;

	@BeforeAll
	public static void quietHome() {
		System.setProperty("user.home", home.toString());
		// no recent script of the user's: the window shows only the script the test gives it
		FshIDE.usePreferences(new MemoryPreferences());
	}

	private static String value(JTable table, String name) {
		for(int row=0; row < table.getModel().getRowCount(); row++ ) {
			if( name.equals(table.getModel().getValueAt(row, 0))) {
				return String.valueOf(table.getModel().getValueAt(row, 1));
			}
		}
		return null;
	}

	@Test
	public void theDebugViewShowsWhatTheNextRunStartsWith() throws Exception {
		Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "needs a screen");
		FshIDE[] w = new FshIDE[1];
		DebugVariablePanel[] panel = new DebugVariablePanel[1];
		SwingUtilities.invokeAndWait(()->{
			w[0] = new FshIDE();
			w[0].setSize(900, 700);
			w[0].setVisible(true);
			panel[0] = w[0].showDebugView(true);
		});
		try {
			// (shown on the event thread, after the view opens)
			SwingUtilities.invokeAndWait(()->{});
			SwingUtilities.invokeAndWait(()->{
				assertTrue(panel[0].showsStartVariables());
				assertEquals(" \t\n", value(panel[0].fshTable(), "IFS"));
				// a script that has no file yet
				assertEquals("fsh", value(panel[0].fshTable(), "$0"));
				assertEquals(0, panel[0].userTable().getModel().getRowCount());
			});
			// a debug run: its own take their place (cleared as it starts)
			SwingUtilities.invokeAndWait(()->{
				w[0].setDocument(us.bringardner.fsh.ide.core.ScriptDocument.parse("IFS=,\nx=1\necho $x\n"), null);
			});
			SwingUtilities.invokeAndWait(()->{});
			SwingUtilities.invokeAndWait(()->assertTrue(panel[0].showsStartVariables()));
			SwingUtilities.invokeAndWait(()->w[0].actionDebug(true));
			SwingUtilities.invokeAndWait(()->assertFalse(panel[0].showsStartVariables()));
			// when it ends: the next run's again, and the script's own as it left them
			long end = System.currentTimeMillis()+20_000;
			boolean[] shows = new boolean[1];
			while( !shows[0] ) {
				assertTrue(System.currentTimeMillis() < end, "the run didn't end");
				Thread.sleep(20);
				SwingUtilities.invokeAndWait(()->shows[0] = panel[0].showsStartVariables());
			}
			SwingUtilities.invokeAndWait(()->{
				assertEquals(" \t\n", value(panel[0].fshTable(), "IFS"));
				assertEquals("1", value(panel[0].userTable(), "x"));
			});
		} finally {
			SwingUtilities.invokeAndWait(()->w[0].dispose());
		}
	}
}
