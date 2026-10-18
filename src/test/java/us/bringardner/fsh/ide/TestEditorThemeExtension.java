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
import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;

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
import us.bringardner.swing.dialog.ThemeDesigner;
import us.bringardner.swing.gradient.GradientLook;
import us.bringardner.swing.gradient.Theme;
import us.bringardner.swing.gradient.ThemeExtensions;

/** The syntax colors as part of the theme: the theme designer's Script editor page, and the editors. */
public class TestEditorThemeExtension {

	private File dir;
	private String oldDir;
	private final EditorThemeExtension extension = new EditorThemeExtension();

	@BeforeEach
	public void setUp() {
		Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display");
		oldDir = System.getProperty(Theme.USER_THEMES_PROPERTY);
		dir = new File(System.getProperty("java.io.tmpdir"), "ide-themes-" + System.nanoTime());
		System.setProperty(Theme.USER_THEMES_PROPERTY, dir.getPath());
		ThemeExtensions.register(extension);
		GradientLook.install();
	}

	@AfterEach
	public void tearDown() {
		ThemeExtensions.unregister(SyntaxColors.EXTENSION_ID);
		Theme.setCurrent(Theme.GOLD);
		if( dir == null ) {
			return;
		}
		File[] files = dir.listFiles();
		if( files != null ) {
			for(File f : files) {
				f.delete();
			}
		}
		dir.delete();
		if( oldDir == null ) {
			System.clearProperty(Theme.USER_THEMES_PROPERTY);
		} else {
			System.setProperty(Theme.USER_THEMES_PROPERTY, oldDir);
		}
	}

	@Test
	public void builtInThemesGetLightOrDarkColors() {
		assertEquals("Script editor", extension.getTitle());
		assertEquals(SyntaxColors.LIGHT.toValues(), Theme.GOLD.values(extension));
		assertEquals(SyntaxColors.PARLEY_DARK.toValues(), Theme.DARK.values(extension));
	}

	@Test
	public void thePageShowsLoadsAndChangesColors() throws Exception {
		SwingUtilities.invokeAndWait(()->{
			EditorThemeExtension.ColorsPage page = (EditorThemeExtension.ColorsPage) extension.createPage();
			AtomicInteger changes = new AtomicInteger();
			page.setChangeListener(changes::incrementAndGet);
			page.setValues(Theme.DARK.values(extension), Theme.DARK);
			assertSame(SyntaxColors.PARLEY_DARK, page.getColors());
			assertEquals("#D0AA42", page.hexField(SyntaxColors.KEYWORDS).getText());
			assertEquals(new Color(0x0E0D0B), page.getPreview().getBackground());
			assertEquals(0, changes.get(), "showing isn't changing");

			page.schemeBox().setSelectedItem(SyntaxColors.named("Monokai"));
			assertSame(SyntaxColors.named("Monokai"), page.getColors());
			assertEquals(new Color(0xF92672),
					page.getPreview().getSyntaxScheme().getStyle(TokenTypes.RESERVED_WORD).foreground);
			assertEquals(1, changes.get());

			page.setColor(SyntaxColors.KEYWORDS, new Color(0x00C0FF));
			assertTrue(page.getColors().isCustom());
			assertEquals("Custom", page.schemeBox().getSelectedItem().toString());
			assertEquals("#00C0FF", page.getValues().get("keywords"));
			assertEquals("#2E2E2E", page.getValues().get("background"), "the rest stay Monokai's");

			page.hexField(SyntaxColors.COMMENTS).setText("#123456");
			page.readHex(SyntaxColors.COMMENTS);
			assertEquals("#123456", page.getValues().get("comments"));
			page.hexField(SyntaxColors.COMMENTS).setText("green");
			page.readHex(SyntaxColors.COMMENTS);
			assertEquals("#123456", page.hexField(SyntaxColors.COMMENTS).getText(), "put back");
			assertEquals(3, changes.get());

			page.schemeBox().setSelectedItem(SyntaxColors.LIGHT);
			assertFalse(page.getColors().isCustom());
			assertEquals(SyntaxColors.SCHEMES.size(), page.schemeBox().getItemCount(), "no Custom entry now");
		});
	}

	@Test
	public void aNewThemeTakesItsParentsColorsAndTheEditorsFollowTheTheme() throws Exception {
		SwingUtilities.invokeAndWait(()->{
			try {
				// designed from Dark, with its keywords changed
				ThemeDesigner d = new ThemeDesigner(null, Theme.DARK);
				Theme designed = d.getTheme().withValues(extension,
						SyntaxColors.PARLEY_DARK.withColor(SyntaxColors.KEYWORDS, 0x00C0FF).toValues())
						.withName("Night");
				Theme.saveUserTheme(designed);
				d.cancel();
				Theme night = Theme.named("Night");
				assertEquals("#00C0FF", night.values(extension).get("keywords"));

				// its child starts with them
				ThemeDesigner child = new ThemeDesigner(null, night);
				assertEquals("#00C0FF", child.getTheme().values(extension).get("keywords"));
				child.cancel();

				// the open editors follow the theme in effect
				JFrame f = new JFrame();
				RSyntaxTextArea editor = new RSyntaxTextArea(5, 20);
				editor.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_UNIX_SHELL);
				f.add(new RTextScrollPane(editor));
				f.pack();
				try {
					EditorColors.apply(editor);
					assertEquals(Color.white, editor.getBackground(), "Gold: the light colors");
					assertEquals(EditorColors.DEBUG_LINE_LIGHT, EditorColors.debugLine());

					Theme.setCurrent(night);
					EditorColors.applyToOpenWindows();
					assertEquals(new Color(0x0E0D0B), editor.getBackground());
					assertEquals(new Color(0x00C0FF),
							editor.getSyntaxScheme().getStyle(TokenTypes.RESERVED_WORD).foreground);
					assertEquals(EditorColors.DEBUG_LINE_DARK, EditorColors.debugLine());

					Theme.setCurrent(Theme.LIGHT);
					EditorColors.applyToOpenWindows();
					assertEquals(Color.white, editor.getBackground(), "back to a light theme: light colors");
				} finally {
					f.dispose();
				}
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		});
	}
}
