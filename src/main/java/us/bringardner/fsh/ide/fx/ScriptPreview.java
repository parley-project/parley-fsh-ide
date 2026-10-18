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

import javafx.scene.layout.StackPane;
import us.bringardner.fsh.ide.EditorThemeExtension;
import us.bringardner.fsh.ide.core.EditorFont;
import us.bringardner.fsh.ide.core.SyntaxColors;

/**
 * A short shell script in a script editor, in colors and a font of its own (not the theme's): the
 * preview of the theme designer's Script editor page and of View &gt; Editor Font....
 */
class ScriptPreview extends StackPane {

	/** The style class the preview's own stylesheet is for. */
	static final String CSS_CLASS = "syntax-preview";

	private final ScriptEditor editor = new ScriptEditor();
	private String css;

	ScriptPreview() {
		getStyleClass().add(CSS_CLASS);
		// the dialogs it is in don't have the IDE's stylesheet
		getStylesheets().add(ScriptPreview.class.getResource("fsh-ide.css").toExternalForm());
		editor.setSpellChecking(false);
		editor.setText(EditorThemeExtension.SAMPLE);
		editor.getCodeArea().setEditable(false);
		editor.getCodeArea().moveTo(0);
		getChildren().add(editor);
		setPrefSize(400, 200);
	}

	/**
	 * Shows the script in these colors and this font.
	 *
	 * @param colors the syntax colors
	 * @param font the editor font
	 */
	void show(SyntaxColors colors, EditorFont font) {
		// the IDE's rules, made for this preview (and more specific than fsh-ide.css's, light or dark)
		String p = "." + CSS_CLASS + "." + CSS_CLASS;
		String rules = colors.css().replace(".root." + SyntaxColors.CSS_CLASS, p);
		if( !font.isDefault() ) {
			// the standard font is fsh-ide.css's, as in the IDE's windows
			rules += font.css().replace(".root." + EditorFont.CSS_CLASS, p);
		}
		if( css != null ) {
			getStylesheets().remove(css);
		}
		css = "data:text/css;base64," + Base64.getEncoder().encodeToString(rules.getBytes(StandardCharsets.UTF_8));
		getStylesheets().add(css);
	}

	/** The editor, for tests. */
	ScriptEditor editor() {
		return editor;
	}
}
