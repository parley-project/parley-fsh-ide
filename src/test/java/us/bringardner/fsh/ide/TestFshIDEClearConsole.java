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
