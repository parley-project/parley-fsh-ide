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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.GraphicsEnvironment;
import java.util.prefs.Preferences;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.TokenTypes;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import us.bringardner.fsh.ide.core.SyntaxColors;
import us.bringardner.swing.gradient.Theme;

/** File > Editor Colors, and the editors painted with the colors chosen. */
public class TestSyntaxColorsDialog {

	private Preferences prefs;

	@BeforeEach
	public void setUp() {
		Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display");
		prefs = Preferences.userRoot().node("us/bringardner/fsh/ide/test/dialog-" + System.nanoTime());
	}

	@AfterEach
	public void tearDown() throws Exception {
		EditorColors.setChosen(null);
		Theme.setCurrent(Theme.GOLD);
		if( prefs != null ) {
			prefs.removeNode();
		}
	}

	@Test
	public void schemesAndColorsAreChosenAndPreviewed() throws Exception {
		SwingUtilities.invokeAndWait(()->{
			SyntaxColorsDialog d = new SyntaxColorsDialog(null, SyntaxColors.followTheme());
			assertTrue(d.getColors().isFollowTheme());
			assertEquals("#0000FF", d.hexField(SyntaxColors.KEYWORDS).getText(), "the light colors, as Gold is light");
			assertEquals(Color.white, d.getPreview().getBackground());

			d.select(SyntaxColors.named("Monokai"));
			assertEquals(new Color(0x2E2E2E), d.getPreview().getBackground());
			assertEquals(new Color(0xF92672),
					d.getPreview().getSyntaxScheme().getStyle(TokenTypes.RESERVED_WORD).foreground);

			d.setColor(SyntaxColors.KEYWORDS, new Color(0x00C0FF));
			assertTrue(d.getColors().isCustom());
			assertEquals(0x2E2E2E, d.getColors().rgb(SyntaxColors.BACKGROUND), "the rest stay Monokai's");
			assertEquals(new Color(0x00C0FF),
					d.getPreview().getSyntaxScheme().getStyle(TokenTypes.RESERVED_WORD).foreground);

			// typed: a color, and something that isn't one
			d.hexField(SyntaxColors.COMMENTS).setText("#123456");
			d.readHex(SyntaxColors.COMMENTS);
			assertEquals(0x123456, d.getColors().rgb(SyntaxColors.COMMENTS));
			assertEquals(0x00C0FF, d.getColors().rgb(SyntaxColors.KEYWORDS), "the earlier change is kept");
			d.hexField(SyntaxColors.COMMENTS).setText("green");
			d.readHex(SyntaxColors.COMMENTS);
			assertEquals("#123456", d.hexField(SyntaxColors.COMMENTS).getText(), "put back");

			// a scheme again, and back to the custom set
			d.select(SyntaxColors.LIGHT);
			assertFalse(d.getColors().isCustom());
			d.cancel();
			assertFalse(d.isAccepted());
		});
	}

	@Test
	public void followingTheThemeIsDarkWithADarkTheme() throws Exception {
		Theme.setCurrent(Theme.DARK);
		SwingUtilities.invokeAndWait(()->{
			SyntaxColorsDialog d = new SyntaxColorsDialog(null, SyntaxColors.followTheme());
			assertEquals("#D0AA42", d.hexField(SyntaxColors.KEYWORDS).getText());
			assertEquals(EditorColors.DEBUG_LINE_DARK, EditorColors.debugLine());
			d.ok();
			assertTrue(d.isAccepted());
		});
	}

	@Test
	public void okPutsTheColorsOnTheOpenEditorsAndKeepsThem() throws Exception {
		SwingUtilities.invokeAndWait(()->{
			JFrame f = new JFrame();
			RSyntaxTextArea editor = new RSyntaxTextArea(5, 20);
			editor.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_UNIX_SHELL);
			f.add(new RTextScrollPane(editor));
			f.pack();
			EditorColors.apply(editor);
			assertEquals(Color.white, editor.getBackground());
			try {
				SyntaxColors chosen = SyntaxColors.named("Druid").withColor(SyntaxColors.VARIABLES, 0xABCDEF);
				SyntaxColorsDialog.choose(chosen, prefs);
				assertSame(chosen, EditorColors.getChosen());
				assertEquals(new Color(0x001C00), editor.getBackground());
				assertEquals(new Color(0xABCDEF), editor.getSyntaxScheme().getStyle(TokenTypes.VARIABLE).foreground);
				assertEquals(new Color(0x88EE88), editor.getCaretColor());
				assertEquals(EditorColors.DEBUG_LINE_DARK, EditorColors.debugLine());
				assertEquals(chosen, SyntaxColors.load(prefs));

				// a theme change leaves chosen colors alone
				Theme.setCurrent(Theme.LIGHT);
				EditorColors.applyToOpenWindows();
				assertEquals(new Color(0x001C00), editor.getBackground());
			} finally {
				f.dispose();
			}
		});
	}
}
