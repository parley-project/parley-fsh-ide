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

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.io.IOException;
import java.io.InputStream;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.Theme;

/**
 * The script editor's colors, which follow the theme (swing-widgets' Theme): RSyntaxTextArea's own
 * light colors with a light theme, editor-dark.xml (ink background, gold keywords) with a dark one.
 */
final class EditorColors {

	/** The dark editor colors, next to this class. */
	static final String DARK = "editor-dark.xml";

	/** RSyntaxTextArea's own colors. */
	static final String LIGHT = "/org/fife/ui/rsyntaxtextarea/themes/default.xml";

	/** The line a debugged script is stopped on. */
	static final Color DEBUG_LINE_LIGHT = Color.green;
	static final Color DEBUG_LINE_DARK = new Color(0x33401C);

	private EditorColors() {
	}

	static boolean dark() {
		return us.bringardner.swing.gradient.Theme.current().isDark();
	}

	/** The highlight for the line a debugged script is stopped on. */
	static Color debugLine() {
		return dark() ? DEBUG_LINE_DARK : DEBUG_LINE_LIGHT;
	}

	/**
	 * Gives an editor the colors for the current theme, keeping its font. Call it once the editor is
	 * in its scroll pane, so the line numbers and fold icons in the gutter get them too.
	 */
	static void apply(RSyntaxTextArea editor) {
		try (InputStream in = dark() ? EditorColors.class.getResourceAsStream(DARK)
				: RSyntaxTextArea.class.getResourceAsStream(LIGHT)) {
			if( in != null ) {
				Theme.load(in, editor.getFont()).apply(editor);
			}
		} catch (IOException e) {
			// the editor keeps the colors it has
		}
	}

	/** Gives every editor in the open windows the colors for the current theme (after a theme change). */
	static void applyToOpenWindows() {
		for(Window w : Window.getWindows()) {
			if( w.isDisplayable() ) {
				applyAll(w);
			}
		}
	}

	private static void applyAll(Container c) {
		for(Component k : c.getComponents()) {
			if( k instanceof RSyntaxTextArea ) {
				apply((RSyntaxTextArea) k);
			} else if( k instanceof Container ) {
				applyAll((Container) k);
			}
		}
	}
}
