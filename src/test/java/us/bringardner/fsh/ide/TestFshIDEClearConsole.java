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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.nio.file.Path;

import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import us.bringardner.fsh.ConsolePanel;

/** Clear Console, from the Run menu and from the console's right-click menu, clears the script output. */
public class TestFshIDEClearConsole {

	@TempDir
	static Path home;

	@BeforeAll
	public static void quietHome() {
		System.setProperty("user.home", home.toString());
		// no recent script of the user's: the window shows only the script the test gives it
		FshIDE.usePreferences(new MemoryPreferences());
	}

	@Test
	public void theRunMenuClearsTheConsole() throws Exception {
		check(w->{
			JMenuBar bar = w.getJMenuBar();
			assertNotNull(bar);
			for(int i=0; i < bar.getMenuCount(); i++ ) {
				JMenu m = bar.getMenu(i);
				if( m != null && "Run".equals(m.getText())) {
					return item(m.getPopupMenu(), "Clear Console");
				}
			}
			return null;
		});
	}

	@Test
	public void theRightClickMenuClearsTheConsole() throws Exception {
		check(w->{
			// the popup the text shows on a right-click (its own or, inherited, the console's)
			JTextPane pane = find(find(w.getContentPane(), ConsolePanel.class), JTextPane.class);
			JPopupMenu popup = pane.getComponentPopupMenu();
			assertNotNull(popup, "the console text has a right-click menu");
			return item(popup, "Clear Console");
		});
	}

	/**
	 * The text under the debug view's variables has a right-click menu too: Clear Log clears it and
	 * leaves a line that says it was cleared, which what is shown next comes after.
	 */
	@Test
	public void theRightClickMenuClearsTheLogAndSaysSo() throws Exception {
		Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "needs a screen");
		FshIDE[] w = new FshIDE[1];
		SwingUtilities.invokeAndWait(()->{
			w[0] = new FshIDE();
			w[0].setSize(900, 700);
			w[0].setVisible(true);
		});
		try {
			SwingUtilities.invokeAndWait(()->{
				w[0].logView().append("count = 3\n");
				JPopupMenu popup = w[0].logView().getComponentPopupMenu();
				assertNotNull(popup, "the log has a right-click menu");
				JMenuItem item = item(popup, "Clear Log");
				assertNotNull(item, "Clear Log");
				item.doClick();
			});
			SwingUtilities.invokeAndWait(()->{
				assertEquals(FshIDE.LOG_CLEARED+"\n", w[0].logText());
				assertEquals("Cleared...", FshIDE.LOG_CLEARED);
				w[0].logView().append("count = 4\n");
				assertEquals("Cleared...\ncount = 4\n", w[0].logText());
			});
		} finally {
			SwingUtilities.invokeAndWait(()->w[0].dispose());
		}
	}

	/**
	 * Run > Clear Run Settings empties this window's arguments and redirects and nothing more: no
	 * question, no message, and what's kept for other scripts stays.
	 */
	@Test
	public void clearRunSettingsEmptiesThisScriptsOnly() throws Exception {
		Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "needs a screen");
		us.bringardner.fsh.ide.core.RunSettingsStore store = us.bringardner.fsh.ide.core.Configuration.getInstance().runSettings();
		// (a script that is there: the IDE drops what's kept for local scripts that are gone)
		String other = java.nio.file.Files.writeString(home.resolve("another-script.fsh"), "echo other\n").toString();
		store.put(other, new us.bringardner.fsh.ide.core.RunSettings("kept", "", "", ""));
		FshIDE[] w = new FshIDE[1];
		SwingUtilities.invokeAndWait(()->{
			w[0] = new FshIDE();
			w[0].setSize(900, 700);
			w[0].setVisible(true);
			w[0].setDocument(new us.bringardner.fsh.ide.core.ScriptDocument("echo hi\n",
					new us.bringardner.fsh.ide.core.RunSettings("a b", "in.txt", "out.txt", "err.txt")), null);
		});
		try {
			SwingUtilities.invokeAndWait(()->{
				assertEquals("a b", w[0].runSettingsFields()[0].getText());
				assertTrue(!w[0].hasChanged());
				JMenuItem item = null;
				JMenuBar bar = w[0].getJMenuBar();
				for(int i=0; i < bar.getMenuCount(); i++ ) {
					JMenu m = bar.getMenu(i);
					if( m != null && "Run".equals(m.getText())) {
						item = item(m.getPopupMenu(), "Clear Run Settings...");
					}
				}
				assertNotNull(item, "Run > Clear Run Settings...");
				// (a question or a message would block here: the test would not go on)
				item.doClick();
				for(javax.swing.JTextField field : w[0].runSettingsFields()) {
					assertEquals("", field.getText());
				}
				// as deleting the fields' text: changed until it's saved, and the code is as it was
				assertTrue(w[0].hasChanged());
				assertEquals("echo hi\n", w[0].editor().getText());
				// no dialog was opened
				for(java.awt.Window dialog : java.awt.Window.getWindows()) {
					assertTrue(!(dialog instanceof javax.swing.JDialog && dialog.isShowing()), String.valueOf(dialog));
				}
			});
			// the other scripts' are still kept
			assertEquals("kept", store.get(other).arguments());
		} finally {
			SwingUtilities.invokeAndWait(()->w[0].dispose());
			store.remove(other);
		}
	}

	interface ItemFinder {
		JMenuItem find(FshIDE w);
	}

	private void check(ItemFinder finder) throws Exception {
		Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "needs a screen");
		FshIDE[] w = new FshIDE[1];
		JTextPane[] pane = new JTextPane[1];
		SwingUtilities.invokeAndWait(()->{
			w[0] = new FshIDE();
			w[0].setSize(900, 700);
			w[0].setVisible(true);
			ConsolePanel console = find(w[0].getContentPane(), ConsolePanel.class);
			pane[0] = find(console, JTextPane.class);
			console.append("some output\n", false);
			console.append("an error\n", true);
		});
		try {
			SwingUtilities.invokeAndWait(()->{
				assertEquals("some output\nan error\n", pane[0].getText().replace("\r", ""));
				JMenuItem item = finder.find(w[0]);
				assertNotNull(item, "Clear Console");
				item.doClick();
			});
			// (the console clears on the event thread)
			SwingUtilities.invokeAndWait(()->{});
			SwingUtilities.invokeAndWait(()->assertEquals("", pane[0].getText()));
		} finally {
			SwingUtilities.invokeAndWait(()->w[0].dispose());
		}
	}

	private static JMenuItem item(JPopupMenu menu, String text) {
		for(Component c : menu.getComponents()) {
			if( c instanceof JMenuItem && text.equals(((JMenuItem) c).getText())) {
				return (JMenuItem) c;
			}
		}
		return null;
	}

	private static <T> T find(Container c, Class<T> type) {
		for(Component k : c.getComponents()) {
			if( type.isInstance(k)) {
				return type.cast(k);
			}
			if( k instanceof Container ) {
				T ret = find((Container) k, type);
				if( ret != null ) {
					return ret;
				}
			}
		}
		return null;
	}
}
