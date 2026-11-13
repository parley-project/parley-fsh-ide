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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.GraphicsEnvironment;
import java.nio.file.Path;

import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import us.bringardner.fsh.studio.FshStudio;

/** Run > Clear ALL Run Settings... and, below it, Clear Current Run Settings (this window's fields only). */
public class TestFshStudioClearRunSettings {

	@TempDir
	static Path home;

	@BeforeAll
	public static void quietHome() {
		System.setProperty("user.home", home.toString());
		FshStudio.usePreferences(new MemoryPreferences());
	}

	private static JMenu runMenu(FshStudio w) {
		JMenuBar bar = w.getJMenuBar();
		assertNotNull(bar);
		for(int i=0; i < bar.getMenuCount(); i++ ) {
			JMenu m = bar.getMenu(i);
			if( m != null && "Run".equals(m.getText())) {
				return m;
			}
		}
		return null;
	}

	private static int indexOf(JMenu menu, String text) {
		for(int i=0; i < menu.getItemCount(); i++ ) {
			JMenuItem item = menu.getItem(i);
			if( item != null && text.equals(item.getText())) {
				return i;
			}
		}
		return -1;
	}

	@Test
	public void theCurrentItemIsRightBelowTheAllItem() throws Exception {
		Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "needs a screen");
		FshStudio[] w = new FshStudio[1];
		SwingUtilities.invokeAndWait(()->w[0] = new FshStudio());
		try {
			SwingUtilities.invokeAndWait(()->{
				JMenu run = runMenu(w[0]);
				assertNotNull(run, "Run menu");
				int all = indexOf(run, "Clear ALL Run Settings...");
				assertTrue(all >= 0, "Clear ALL Run Settings...");
				assertEquals(all+1, indexOf(run, "Clear Current Run Settings"));
				assertEquals(-1, indexOf(run, "Clear Run Settings..."), "the old name is gone");
			});
		} finally {
			SwingUtilities.invokeAndWait(()->w[0].dispose());
		}
	}

	@Test
	public void clearCurrentEmptiesTheFourFields() throws Exception {
		Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "needs a screen");
		FshStudio[] w = new FshStudio[1];
		SwingUtilities.invokeAndWait(()->w[0] = new FshStudio());
		try {
			SwingUtilities.invokeAndWait(()->{
				JTextField[] fields = w[0].runSettingsFields();
				assertEquals(4, fields.length);
				String[] values = {"x y", "/tmp/in", "/tmp/out", "/tmp/err"};
				for(int i=0; i < fields.length; i++ ) {
					fields[i].setText(values[i]);
				}
				JMenu run = runMenu(w[0]);
				run.getItem(indexOf(run, "Clear Current Run Settings")).doClick();
				for(JTextField f : fields) {
					assertEquals("", f.getText());
				}
				// the window started with none: emptied, it is as it was (it is not "kept" or "forgotten" anywhere)
				assertEquals(false, w[0].hasChanged());
			});
		} finally {
			SwingUtilities.invokeAndWait(()->w[0].dispose());
		}
	}
}
