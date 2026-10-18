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
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import us.bringardner.fsh.ide.core.SyntaxColors;
import us.bringardner.swing.gradient.Theme;

/** The JavaFX IDE's script editor in the theme's syntax colors. */
public class SyntaxColorsFxTest {

	@Test
	public void aThemeWithoutColorsOfItsOwnAddsNothing() throws Exception {
		Scene scene = Fx.call(()->{
			Scene s = new Scene(new StackPane());
			IdeWindow.applySyntaxColors(s, Theme.DARK);
			return s;
		});
		assertTrue(scene.getStylesheets().isEmpty());
		assertFalse(scene.getRoot().getStyleClass().contains(SyntaxColors.CSS_CLASS));
	}

	@Test
	public void aThemesOwnColorsComeAsAStylesheet() throws Exception {
		SyntaxColors c = SyntaxColors.named("Monokai");
		java.util.Map<String, String> extras = new java.util.HashMap<>();
		c.toValues().forEach((k, v)->extras.put(SyntaxColors.EXTENSION_ID + "." + k, v));
		Theme theme = Theme.GOLD.withExtras(extras);
		Scene scene = Fx.call(()->{
			Scene s = new Scene(new StackPane());
			IdeWindow.applySyntaxColors(s, theme);
			return s;
		});
		assertTrue(scene.getRoot().getStyleClass().contains(SyntaxColors.CSS_CLASS));
		assertEquals(1, scene.getStylesheets().size());
		String url = scene.getStylesheets().get(0);
		assertTrue(url.startsWith("data:text/css;base64,"));
		assertTrue(new String(java.util.Base64.getDecoder().decode(url.substring(url.indexOf(',') + 1)),
				java.nio.charset.StandardCharsets.UTF_8).endsWith(c.css()));

		// another theme's colors replace them; a theme without its own takes them off
		Theme other = Theme.DARK.withExtras(extras);
		Fx.run(()->IdeWindow.applySyntaxColors(scene, other));
		assertEquals(1, scene.getStylesheets().size(), "replaced, not added");
		Fx.run(()->IdeWindow.applySyntaxColors(scene, Theme.LIGHT));
		assertTrue(scene.getStylesheets().isEmpty());
		assertFalse(scene.getRoot().getStyleClass().contains(SyntaxColors.CSS_CLASS));
	}
}
