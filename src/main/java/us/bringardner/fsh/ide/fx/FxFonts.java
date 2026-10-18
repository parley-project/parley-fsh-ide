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

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import javafx.scene.text.Font;

/**
 * The fixed-width font of the script editor and the console. JavaFX's CSS takes one font family,
 * not a list to choose from ("Menlo", "Consolas", ... falls back to the System font, which isn't
 * fixed-width), so the family is chosen here: the first of these this computer has.
 */
final class FxFonts {

	/** Menlo (macOS), Consolas (Windows), DejaVu Sans Mono or Liberation Mono (Linux). */
	static final List<String> PREFERRED = List.of("Menlo", "Consolas", "DejaVu Sans Mono", "Liberation Mono");

	private static String monospaced;
	private static String stylesheet;

	private FxFonts() {
	}

	/** The standard fixed-width family here; JavaFX's logical Monospaced if none of the preferred is. */
	static synchronized String monospaced() {
		if( monospaced == null ) {
			List<String> have = Font.getFamilies();
			monospaced = PREFERRED.stream().filter(have::contains).findFirst().orElse("Monospaced");
		}
		return monospaced;
	}

	/** A stylesheet (data: URL) giving the editor, its line numbers and the console that family. */
	static synchronized String stylesheet() {
		if( stylesheet == null ) {
			// .text too: RichTextFX's text doesn't take the family from the area (its own stylesheet
			// gives CodeArea "monospace")
			String css = ".script-editor, .script-editor .text, .script-editor .lineno, .console, .console .text"
					+ " { -fx-font-family: \"" + monospaced() + "\"; }\n";
			stylesheet = "data:text/css;base64," + Base64.getEncoder().encodeToString(css.getBytes(StandardCharsets.UTF_8));
		}
		return stylesheet;
	}
}
