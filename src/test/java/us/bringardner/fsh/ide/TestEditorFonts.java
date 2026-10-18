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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.prefs.Preferences;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.RSyntaxUtilities;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import us.bringardner.fsh.ConsolePanel;
import us.bringardner.fsh.ide.core.EditorFont;
import us.bringardner.swing.gradient.GradientLook;

/** View > Bigger Text, Smaller Text and Editor Font... in the Swing IDE. */
public class TestEditorFonts {

	private Preferences prefs;

	@BeforeEach
	public void setUp() {
		Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display");
		prefs = Preferences.userRoot().node("us/bringardner/fsh/ide/test/fonts-" + System.nanoTime());
		GradientLook.install();
	}

	@AfterEach
	public void tearDown() throws Exception {
		if( prefs != null ) {
			EditorFonts.choose(EditorFont.defaults(), null);
			prefs.removeNode();
		}
	}

	@Test
	public void theEditorsAndConsoleTakeTheEditorFont() throws Exception {
		SwingUtilities.invokeAndWait(()->{
			JFrame f = new JFrame();
			RSyntaxTextArea editor = new RSyntaxTextArea(5, 20);
			ConsolePanel console = new ConsolePanel();
			JPanel p = new JPanel(new java.awt.BorderLayout());
			p.add(new RTextScrollPane(editor), java.awt.BorderLayout.CENTER);
			p.add(console, java.awt.BorderLayout.SOUTH);
			f.add(p);
			f.pack();
			try {
				EditorColors.apply(editor);
				EditorFonts.apply(console);
				assertEquals(13, editor.getFont().getSize());

				EditorFonts.choose(EditorFont.of(null, 13).bigger().bigger().bigger(), prefs);
				assertEquals(16, editor.getFont().getSize());
				assertEquals(15, RSyntaxUtilities.getGutter(editor).getLineNumberFont().getSize(), "line numbers follow");
				JTextPane pane = find(console);
				assertEquals(16, pane.getFont().getSize());
				assertTrue(pane.getFont().isItalic(), "the console keeps its style");
				assertEquals(16, prefs.getInt(EditorFont.PREF_SIZE, -1));

				// a theme change keeps the editor font
				EditorColors.applyToOpenWindows();
				assertEquals(16, editor.getFont().getSize());
			} finally {
				f.dispose();
			}
		});
	}

	@Test
	public void theDialogChoosesAFixedWidthFamilyAndSize() throws Exception {
		assertFalse(EditorFonts.fixedWidthFamilies().isEmpty(), "every system has a fixed-width font");
		SwingUtilities.invokeAndWait(()->{
			EditorFontDialog d = new EditorFontDialog(null, EditorFont.of(null, 15));
			assertEquals(EditorFontDialog.STANDARD, d.familyList().getSelectedValue());
			assertEquals(15, d.sizeSpinner().getValue());
			assertEquals(15, d.getPreview().getFont().getSize());

			String family = EditorFonts.fixedWidthFamilies().get(0);
			d.familyList().setSelectedValue(family, false);
			d.sizeSpinner().setValue(20);
			assertEquals(EditorFont.of(family, 20), d.getChosen());
			assertEquals(new Font(family, Font.PLAIN, 20).getFamily(), d.getPreview().getFont().getFamily());

			// a family that isn't among the fixed-width ones found is still offered
			d.select(EditorFont.of("Not Here", 12));
			assertEquals("Not Here", d.familyList().getSelectedValue());
			d.ok();
			assertTrue(d.isAccepted());

			EditorFontDialog c = new EditorFontDialog(null, EditorFont.defaults());
			c.cancel();
			assertFalse(c.isAccepted());
			assertNotNull(c.getChosen());
		});
	}

	private static JTextPane find(java.awt.Container c) {
		for(java.awt.Component k : c.getComponents()) {
			if( k instanceof JTextPane ) {
				return (JTextPane) k;
			}
			if( k instanceof java.awt.Container ) {
				JTextPane t = find((java.awt.Container) k);
				if( t != null ) {
					return t;
				}
			}
		}
		return null;
	}
}
