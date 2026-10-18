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
	public void builtInThemesGetColorsThatMatchThem() {
		assertEquals("Script editor", extension.getTitle());
		assertEquals(EditorThemeExtension.matching(Theme.GOLD).toValues(), Theme.GOLD.values(extension));
		assertEquals(EditorThemeExtension.matching(Theme.DARK).toValues(), Theme.DARK.values(extension));
		assertEquals("#FDF6E5", Theme.GOLD.values(extension).get("background"), "a light tint of gold");
		assertTrue(EditorThemeExtension.colorsOf(Theme.DARK).isDark());
		assertTrue(EditorThemeExtension.colorsOf(Theme.GOLD).isMatch());
	}

	@Test
	public void thePageShowsLoadsAndChangesColors() throws Exception {
		SwingUtilities.invokeAndWait(()->{
			EditorThemeExtension.ColorsPage page = (EditorThemeExtension.ColorsPage) extension.createPage();
			AtomicInteger changes = new AtomicInteger();
			page.setChangeListener(changes::incrementAndGet);
			page.setValues(Theme.DARK.values(extension), Theme.DARK);
			assertEquals(EditorThemeExtension.matching(Theme.DARK), page.getColors(), "Dark has none of its own");
			assertSame(page.getColors(), page.schemeBox().getSelectedItem());
			assertEquals(0, page.schemeBox().getSelectedIndex(), "Match the theme comes first");
			assertTrue(page.getValues().isEmpty(), "matching keeps none of its own");
			assertEquals("#D0AA42", page.hexField(SyntaxColors.KEYWORDS).getText());
			assertEquals(new Color(page.getColors().rgb(SyntaxColors.BACKGROUND)), page.getPreview().getBackground());
			assertEquals(0, changes.get(), "showing isn't changing");

			// matching follows the theme being designed, without being a change of its own
			page.themeChanged(Theme.named("Ocean"));
			assertEquals(EditorThemeExtension.matching(Theme.named("Ocean")), page.getColors());
			assertEquals(new Color(page.getColors().rgb(SyntaxColors.BACKGROUND)), page.getPreview().getBackground());
			assertEquals(0, changes.get());

			// a theme's own colors are shown as they are
			page.setValues(SyntaxColors.PARLEY_DARK.toValues(), Theme.DARK.withValues(extension, SyntaxColors.PARLEY_DARK.toValues()));
			assertSame(SyntaxColors.PARLEY_DARK, page.getColors());
			page.themeChanged(Theme.GOLD);
			assertSame(SyntaxColors.PARLEY_DARK, page.getColors(), "a scheme doesn't follow the theme");

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
			assertEquals(SyntaxColors.SCHEMES.size() + 1, page.schemeBox().getItemCount(), "no Custom entry now");

			page.schemeBox().setSelectedIndex(0);
			assertTrue(page.getColors().isMatch());
			assertEquals(EditorThemeExtension.matching(Theme.GOLD), page.getColors(), "the theme it was last told of");
			assertTrue(page.getValues().isEmpty());
			assertEquals(5, changes.get());
		});
	}

	private static javax.swing.JTextPane findPane(java.awt.Container c) {
		for(java.awt.Component k : c.getComponents()) {
			if( k instanceof javax.swing.JTextPane ) {
				return (javax.swing.JTextPane) k;
			}
			if( k instanceof java.awt.Container ) {
				javax.swing.JTextPane p = findPane((java.awt.Container) k);
				if( p != null ) {
					return p;
				}
			}
		}
		return null;
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
					assertEquals(new Color(0xFDF6E5), editor.getBackground(), "Gold: a light tint of gold");
					assertEquals(EditorColors.DEBUG_LINE_LIGHT, EditorColors.debugLine());

					Theme.setCurrent(night);
					EditorColors.applyToOpenWindows();
					assertEquals(new Color(0x0E0D0B), editor.getBackground());
					assertEquals(new Color(0x00C0FF),
							editor.getSyntaxScheme().getStyle(TokenTypes.RESERVED_WORD).foreground);
					assertEquals(EditorColors.DEBUG_LINE_DARK, EditorColors.debugLine());

					// a console in the window follows too
					us.bringardner.fsh.ConsolePanel console = new us.bringardner.fsh.ConsolePanel();
					f.add(new javax.swing.JScrollPane(console), java.awt.BorderLayout.SOUTH);

					Theme.setCurrent(Theme.named("Ocean"));
					EditorColors.applyToOpenWindows();
					Color oceanBackground = new Color(EditorThemeExtension.matching(Theme.named("Ocean")).rgb(SyntaxColors.BACKGROUND));
					javax.swing.JTextPane pane = findPane(console);
					assertEquals(oceanBackground, pane.getBackground(), "the console: the editor's background");
					assertEquals(oceanBackground, ((javax.swing.JViewport) console.getParent()).getBackground());
					assertEquals(new Color(EditorThemeExtension.matching(Theme.named("Ocean")).rgb(SyntaxColors.BACKGROUND)),
							editor.getBackground(), "back to a light theme: its tint");
				} finally {
					f.dispose();
				}
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		});
	}
}
