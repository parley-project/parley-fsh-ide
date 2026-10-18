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
package us.bringardner.fsh.ide.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javafx.scene.control.MenuBar;
import javafx.scene.control.TabPane;
import javafx.stage.Stage;
import us.bringardner.fsh.ide.core.EditorFont;
import us.bringardner.fsh.ide.core.SyntaxColors;
import us.bringardner.fx.dialog.FxThemePage;
import us.bringardner.fx.dialog.FxThemePages;
import us.bringardner.fx.dialog.ThemeDesigner;
import us.bringardner.swing.gradient.Theme;
import us.bringardner.swing.gradient.ThemeExtensions;

/** Settings in the JavaFX IDE: the Script editor page, the editor font dialog, and re-theming. */
public class FxSettingsTest {

	@TempDir
	Path themes;
	private String oldDir;
	private Theme oldTheme;

	@BeforeEach
	public void themesOfOurOwn() {
		oldDir = System.getProperty(Theme.USER_THEMES_PROPERTY);
		System.setProperty(Theme.USER_THEMES_PROPERTY, themes.toString());
		oldTheme = Theme.current();
	}

	@AfterEach
	public void putBack() {
		if( oldDir == null ) {
			System.clearProperty(Theme.USER_THEMES_PROPERTY);
		} else {
			System.setProperty(Theme.USER_THEMES_PROPERTY, oldDir);
		}
		Theme.setCurrent(oldTheme);
	}

	private static String css(ScriptPreview p) {
		String url = p.getStylesheets().get(p.getStylesheets().size() - 1);
		return new String(java.util.Base64.getDecoder().decode(url.substring(url.indexOf(',') + 1)),
				java.nio.charset.StandardCharsets.UTF_8);
	}

	@Test
	public void theScriptEditorPageIsInTheDesigner() throws Exception {
		Fx.run(IdeWindow::registerThemePages);
		Fx.run(IdeWindow::registerThemePages);
		assertEquals(1, ThemeExtensions.all().stream().filter(x->x.getId().equals(SyntaxColors.EXTENSION_ID)).count(),
				"registered once");
		assertNotNull(FxThemePages.create(SyntaxColors.EXTENSION_ID));
		Fx.run(()->{
			ThemeDesigner d = new ThemeDesigner(null, Theme.DARK, false);
			TabPane tabs = (TabPane) d.getStage().getScene().getRoot().lookup(".tab-pane");
			assertNotNull(tabs);
			assertEquals("Script editor", tabs.getTabs().get(1).getText());
			EditorThemePage page = (EditorThemePage) tabs.getTabs().get(1).getContent();
			// a dark theme without colors of its own: the Parley dark ones
			assertEquals(SyntaxColors.PARLEY_DARK, page.getColors());
			page.schemeBox().setValue(SyntaxColors.named("Monokai"));
			d.setThemeName("Night shift");
			assertTrue(d.save());
		});
		Theme saved = Theme.named("Night shift");
		assertEquals(SyntaxColors.named("Monokai"), SyntaxColors.ofTheme(saved.getExtras(), saved.isDark()));
	}

	@Test
	public void thePageChangesColors() throws Exception {
		Fx.run(()->{
			EditorThemePage page = new EditorThemePage(EditorFont.of("Serif", 20));
			int[] heard = {0};
			page.setChangeListener(()->heard[0]++);
			FxThemePage p = page;
			assertEquals(page, p.getNode());
			p.setValues(SyntaxColors.LIGHT.toValues(), Theme.GOLD);
			assertEquals(SyntaxColors.LIGHT, page.getColors());
			assertEquals(0, heard[0], "showing a theme's colors isn't a change");

			page.hexField(SyntaxColors.KEYWORDS).setText("#123456");
			page.readHex(SyntaxColors.KEYWORDS);
			assertEquals(0x123456, page.getColors().rgb(SyntaxColors.KEYWORDS));
			assertTrue(page.getColors().isCustom());
			assertTrue(page.schemeBox().getValue().isCustom(), "Custom in the scheme box");
			assertEquals(1, heard[0]);
			page.hexField(SyntaxColors.KEYWORDS).setText("nonsense");
			page.readHex(SyntaxColors.KEYWORDS);
			assertEquals("#123456", page.hexField(SyntaxColors.KEYWORDS).getText());
			Map<String, String> v = p.getValues();
			assertEquals(page.getColors(), SyntaxColors.fromValues(v, false));

			// the preview shows the colors, in its own font
			String css = css(page.preview());
			assertTrue(css.contains("#123456"), css);
			assertTrue(css.contains("\"Serif\""), css);
			assertFalse(css.contains(".root."), "its own rules, not the IDE window's");

			page.schemeBox().setValue(SyntaxColors.PARLEY_DARK);
			assertEquals(SyntaxColors.PARLEY_DARK, page.getColors());
			assertFalse(page.schemeBox().getItems().stream().anyMatch(SyntaxColors::isCustom));
		});
	}

	@Test
	public void theEditorFontDialog() throws Exception {
		Fx.run(()->{
			EditorFontDialog d = new EditorFontDialog(null, EditorFont.defaults());
			assertEquals(EditorFontDialog.STANDARD, d.familyList().getSelectionModel().getSelectedItem());
			assertFalse(css(d.preview()).contains("font-family"), "the standard font is the stylesheet's");
			d.sizeSpinner().getValueFactory().setValue(18);
			assertEquals(EditorFont.of(null, 18), d.getChosen());
			if( d.familyList().getItems().size() > 1 ) {
				String f = d.familyList().getItems().get(1);
				d.familyList().getSelectionModel().select(f);
				assertEquals(EditorFont.of(f, 18), d.getChosen());
				assertTrue(css(d.preview()).contains(f));
			}
			d.select(EditorFont.of("No Such Mono", 11));
			assertEquals("No Such Mono", d.familyList().getSelectionModel().getSelectedItem(), "still offered");
			d.ok();
			assertTrue(d.isAccepted());
			assertEquals(EditorFont.of("No Such Mono", 11), d.getChosen());

			EditorFontDialog c = new EditorFontDialog(null, EditorFont.defaults());
			c.cancel();
			assertFalse(c.isAccepted());
		});
		for(String f : Fx.call(EditorFontDialog::fixedWidthFamilies)) {
			assertFalse(f.isBlank());
		}
	}

	@Test
	public void settingsAndEditorFontAreInTheMenus() throws Exception {
		IdeWindowTest.MemoryPreferences prefs = new IdeWindowTest.MemoryPreferences();
		IdeWindow w = Fx.call(()->new IdeWindow(new Stage(), prefs));
		java.util.List<String> items = Fx.call(()->{
			java.util.List<String> ret = new java.util.ArrayList<>();
			MenuBar bar = (MenuBar) w.stage().getScene().getRoot().lookup(".menu-bar");
			bar.getMenus().forEach(m->m.getItems().forEach(i->ret.add(m.getText() + " > " + i.getText())));
			return ret;
		});
		assertTrue(items.contains("View > Editor Font..."), items.toString());
		boolean mac = us.bringardner.fx.menu.StandardMenus.isMac();
		if( !mac ) {
			assertTrue(items.contains("File > Settings..."), items.toString());
		}

		// a new theme's syntax colors go on the open windows
		SyntaxColors c = SyntaxColors.named("Monokai");
		java.util.Map<String, String> extras = new java.util.HashMap<>();
		c.toValues().forEach((k, v)->extras.put(SyntaxColors.EXTENSION_ID + "." + k, v));
		Theme.setCurrent(Theme.GOLD.withExtras(extras));
		Fx.run(IdeWindow::themeChanged);
		assertTrue(Fx.call(()->w.stage().getScene().getRoot().getStyleClass().contains(SyntaxColors.CSS_CLASS)));
		Theme.setCurrent(Theme.GOLD);
		Fx.run(IdeWindow::themeChanged);
		assertFalse(Fx.call(()->w.stage().getScene().getRoot().getStyleClass().contains(SyntaxColors.CSS_CLASS)));
		Fx.run(()->{
			w.stage().close();
		});
	}

	@Test
	public void theEditorAndConsoleAreFixedWidth() throws Exception {
		String family = Fx.call(FxFonts::monospaced);
		// the same width for every letter
		double[] w = Fx.call(()->{
			javafx.scene.text.Text narrow = new javafx.scene.text.Text("iiiiii");
			javafx.scene.text.Text wide = new javafx.scene.text.Text("WWWWWW");
			narrow.setFont(javafx.scene.text.Font.font(family, 13));
			wide.setFont(javafx.scene.text.Font.font(family, 13));
			return new double[] {narrow.getLayoutBounds().getWidth(), wide.getLayoutBounds().getWidth()};
		});
		assertEquals(w[0], w[1], 0.5, family + " is fixed-width");

		// the editor's text has it, not the System font a list of families falls back to
		IdeWindowTest.MemoryPreferences prefs = new IdeWindowTest.MemoryPreferences();
		IdeWindow win = Fx.call(()->new IdeWindow(new Stage(), prefs));
		String used = Fx.call(()->{
			win.editor().setText("echo hello");
			win.stage().show();
			win.stage().getScene().getRoot().applyCss();
			win.stage().getScene().getRoot().layout();
			for(javafx.scene.Node n : win.editor().getCodeArea().lookupAll(".text")) {
				if( n instanceof javafx.scene.text.Text && ((javafx.scene.text.Text) n).getText().contains("echo") ) {
					return ((javafx.scene.text.Text) n).getFont().getFamily();
				}
			}
			return null;
		});
		assertEquals(family, used);

		// and a font chosen with Editor Font... reaches the text too (RichTextFX's text doesn't take
		// it from the area), with the line numbers two points smaller
		Fx.run(()->win.chooseEditorFont(us.bringardner.fsh.ide.core.EditorFont.of(null, 20)));
		Thread.sleep(300);
		double[] sizes = Fx.call(()->{
			win.stage().getScene().getRoot().applyCss();
			double text = -1;
			double line = -1;
			for(javafx.scene.Node n : win.editor().getCodeArea().lookupAll(".text")) {
				if( n instanceof javafx.scene.text.Text ) {
					javafx.scene.text.Text t = (javafx.scene.text.Text) n;
					if( t.getText().contains("echo") ) {
						text = t.getFont().getSize();
					} else if( t.getText().trim().equals("1") ) {
						line = t.getFont().getSize();
					}
				}
			}
			return new double[] {text, line};
		});
		Fx.run(()->{
			win.chooseEditorFont(us.bringardner.fsh.ide.core.EditorFont.defaults());
			win.stage().close();
		});
		assertEquals(20, sizes[0], 0.01);
		assertEquals(18, sizes[1], 0.01);
	}
}
