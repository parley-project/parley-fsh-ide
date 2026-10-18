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

/** The JavaFX IDE's script editor in the colors chosen in the Swing IDE. */
public class SyntaxColorsFxTest {

	@Test
	public void followingTheThemeAddsNothing() throws Exception {
		Scene scene = Fx.call(()->{
			Scene s = new Scene(new StackPane());
			IdeWindow.applySyntaxColors(s, SyntaxColors.followTheme());
			return s;
		});
		assertTrue(scene.getStylesheets().isEmpty());
		assertFalse(scene.getRoot().getStyleClass().contains(SyntaxColors.CSS_CLASS));
	}

	@Test
	public void chosenColorsComeAsAStylesheet() throws Exception {
		SyntaxColors c = SyntaxColors.named("Monokai");
		Scene scene = Fx.call(()->{
			Scene s = new Scene(new StackPane());
			IdeWindow.applySyntaxColors(s, c);
			return s;
		});
		assertTrue(scene.getRoot().getStyleClass().contains(SyntaxColors.CSS_CLASS));
		assertEquals(1, scene.getStylesheets().size());
		String url = scene.getStylesheets().get(0);
		assertTrue(url.startsWith("data:text/css;base64,"));
		assertEquals(c.css(), new String(java.util.Base64.getDecoder().decode(url.substring(url.indexOf(',') + 1)),
				java.nio.charset.StandardCharsets.UTF_8));
	}
}
