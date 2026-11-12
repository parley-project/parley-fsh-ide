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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.event.MouseWheelEvent;
import java.nio.file.Path;

import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import us.bringardner.fsh.ConsolePanel;

/** The script output (stdout and stderr) under the editor scrolls with the mouse wheel. */
public class TestFshIDEConsoleScroll {

	@TempDir
	static Path home;

	@BeforeAll
	public static void quietHome() {
		System.setProperty("user.home", home.toString());
		// no recent script of the user's: the window shows only the script the test gives it
		FshIDE.usePreferences(new MemoryPreferences());
	}

	@Test
	public void theOutputScrollsWithTheMouseWheel() throws Exception {
		Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "needs a screen");
		FshIDE[] w = new FshIDE[1];
		ConsolePanel[] console = new ConsolePanel[1];
		SwingUtilities.invokeAndWait(()->{
			w[0] = new FshIDE();
			w[0].setSize(900, 700);
			w[0].setVisible(true);
			console[0] = find(w[0].getContentPane(), ConsolePanel.class);
			assertNotNull(console[0]);
			// no scroll pane around the console: it would take the wheel with nothing to scroll
			assertNull(SwingUtilities.getAncestorOfClass(JScrollPane.class, console[0]));
			StringBuilder text = new StringBuilder();
			for(int i=0; i < 500; i++ ) {
				text.append("line ").append(i).append('\n');
			}
			console[0].append(text.toString(), false);
		});
		try {
			int[] before = new int[1];
			JScrollBar[] bar = new JScrollBar[1];
			JTextPane[] pane = new JTextPane[1];
			SwingUtilities.invokeAndWait(()->{
				w[0].validate();
				pane[0] = find(console[0], JTextPane.class);
				JScrollPane sp = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, pane[0]);
				bar[0] = sp.getVerticalScrollBar();
				assertTrue(bar[0].getMaximum() > bar[0].getVisibleAmount(), "more output than shows");
				bar[0].setValue(bar[0].getMaximum());
				before[0] = bar[0].getValue();
				assertTrue(before[0] > 0);
			});
			SwingUtilities.invokeAndWait(()->{
				MouseWheelEvent up = new MouseWheelEvent(pane[0], MouseWheelEvent.MOUSE_WHEEL,
						System.currentTimeMillis(), 0, 10, 10, 0, false,
						MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, -3);
				pane[0].dispatchEvent(up);
			});
			SwingUtilities.invokeAndWait(()->{
				assertTrue(bar[0].getValue() < before[0], "the wheel scrolled up: "+bar[0].getValue()+" was "+before[0]);
			});
		} finally {
			SwingUtilities.invokeAndWait(()->w[0].dispose());
		}
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
