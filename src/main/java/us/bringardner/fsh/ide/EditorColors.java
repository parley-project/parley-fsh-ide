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
import org.fife.ui.rsyntaxtextarea.RSyntaxUtilities;
import org.fife.ui.rsyntaxtextarea.Style;
import org.fife.ui.rsyntaxtextarea.SyntaxScheme;
import org.fife.ui.rsyntaxtextarea.Theme;
import org.fife.ui.rsyntaxtextarea.TokenTypes;
import org.fife.ui.rtextarea.Gutter;

import us.bringardner.fsh.ide.core.SyntaxColors;

/**
 * The script editors' colors: the syntax colors the user chose (File &gt; Editor Colors, kept in
 * the IDE's preferences as {@link SyntaxColors}), or by default ones that follow the theme
 * (swing-widgets' Theme): RSyntaxTextArea's own light colors with a light theme, the Parley dark
 * ones (editor-dark.xml: ink background, gold keywords) with a dark one.
 */
final class EditorColors {

	/** The dark editor colors, next to this class. */
	static final String DARK = "editor-dark.xml";

	/** RSyntaxTextArea's own colors. */
	static final String LIGHT = "/org/fife/ui/rsyntaxtextarea/themes/default.xml";

	/** The line a debugged script is stopped on. */
	static final Color DEBUG_LINE_LIGHT = Color.green;
	static final Color DEBUG_LINE_DARK = new Color(0x33401C);

	/** The token types painted in each of {@link SyntaxColors#ROLES} that is a token's color. */
	private static final int[][] TOKENS = new int[SyntaxColors.ROLES.size()][];
	static {
		TOKENS[SyntaxColors.TEXT] = new int[] {TokenTypes.IDENTIFIER, TokenTypes.SEPARATOR};
		TOKENS[SyntaxColors.KEYWORDS] = new int[] {TokenTypes.RESERVED_WORD, TokenTypes.RESERVED_WORD_2,
			TokenTypes.DATA_TYPE};
		TOKENS[SyntaxColors.COMMANDS] = new int[] {TokenTypes.FUNCTION};
		TOKENS[SyntaxColors.COMMENTS] = new int[] {TokenTypes.COMMENT_EOL, TokenTypes.COMMENT_MULTILINE,
			TokenTypes.COMMENT_DOCUMENTATION, TokenTypes.COMMENT_KEYWORD, TokenTypes.COMMENT_MARKUP};
		TOKENS[SyntaxColors.STRINGS] = new int[] {TokenTypes.LITERAL_STRING_DOUBLE_QUOTE, TokenTypes.LITERAL_CHAR,
			TokenTypes.LITERAL_BACKQUOTE};
		TOKENS[SyntaxColors.VARIABLES] = new int[] {TokenTypes.VARIABLE};
		TOKENS[SyntaxColors.NUMBERS] = new int[] {TokenTypes.LITERAL_NUMBER_DECIMAL_INT,
			TokenTypes.LITERAL_NUMBER_FLOAT, TokenTypes.LITERAL_NUMBER_HEXADECIMAL, TokenTypes.LITERAL_BOOLEAN};
		TOKENS[SyntaxColors.OPERATORS] = new int[] {TokenTypes.OPERATOR};
	}

	/** The colors chosen; by default they follow the theme. */
	private static SyntaxColors chosen = SyntaxColors.followTheme();

	private EditorColors() {
	}

	/** The colors chosen (perhaps "Follow the theme"). */
	static SyntaxColors getChosen() {
		return chosen;
	}

	/** Sets the colors chosen; {@link #applyToOpenWindows()} puts them on the open editors. */
	static void setChosen(SyntaxColors colors) {
		chosen = colors == null ? SyntaxColors.followTheme() : colors;
	}

	static boolean darkTheme() {
		return us.bringardner.swing.gradient.Theme.current().isDark();
	}

	/** The colors to paint with now: the chosen ones, or the theme's. */
	static SyntaxColors current() {
		return chosen.resolve(darkTheme());
	}

	/** The highlight for the line a debugged script is stopped on. */
	static Color debugLine() {
		return current().isDark() ? DEBUG_LINE_DARK : DEBUG_LINE_LIGHT;
	}

	/**
	 * Gives an editor the current colors, keeping its font. Call it once the editor is in its scroll
	 * pane, so the line numbers and fold icons in the gutter get them too.
	 */
	static void apply(RSyntaxTextArea editor) {
		apply(editor, current());
	}

	/** Gives an editor these colors (resolved, not "Follow the theme"), keeping its font. */
	static void apply(RSyntaxTextArea editor, SyntaxColors colors) {
		// the light or dark editor colors first, for the parts with no color of their own here
		// (bracket matching, fold icons and so on), then these colors
		try (InputStream in = colors.isDark() ? EditorColors.class.getResourceAsStream(DARK)
				: RSyntaxTextArea.class.getResourceAsStream(LIGHT)) {
			if( in != null ) {
				Theme.load(in, editor.getFont()).apply(editor);
			}
		} catch (IOException e) {
			// the editor keeps the colors it has, and gets these below
		}
		editor.setBackground(color(colors, SyntaxColors.BACKGROUND));
		editor.setForeground(color(colors, SyntaxColors.TEXT));
		editor.setCaretColor(color(colors, SyntaxColors.CARET));
		editor.setSelectionColor(color(colors, SyntaxColors.SELECTION));
		editor.setCurrentLineHighlightColor(color(colors, SyntaxColors.CURRENT_LINE));

		SyntaxScheme scheme = (SyntaxScheme) editor.getSyntaxScheme().clone();
		for(int role = 0; role < TOKENS.length; role++) {
			if( TOKENS[role] != null ) {
				for(int token : TOKENS[role]) {
					Style style = scheme.getStyle(token);
					if( style == null ) {
						style = new Style();
						scheme.setStyle(token, style);
					}
					style.foreground = color(colors, role);
				}
			}
		}
		editor.setSyntaxScheme(scheme);

		Gutter gutter = RSyntaxUtilities.getGutter(editor);
		if( gutter != null ) {
			gutter.setBackground(color(colors, SyntaxColors.GUTTER));
			gutter.setLineNumberColor(color(colors, SyntaxColors.LINE_NUMBERS));
		}
		editor.repaint();
	}

	static Color color(SyntaxColors colors, int role) {
		return new Color(colors.rgb(role));
	}

	/** Gives every editor in the open windows the current colors (after a change). */
	static void applyToOpenWindows() {
		for(Window w : Window.getWindows()) {
			if( w.isDisplayable() ) {
				applyAll(w);
			}
		}
	}

	private static void applyAll(Container c) {
		for(Component k : c.getComponents()) {
			if( k instanceof RSyntaxTextArea && !(k instanceof Preview) ) {
				apply((RSyntaxTextArea) k);
			} else if( k instanceof Container ) {
				applyAll((Container) k);
			}
		}
	}

	/** An editor that shows colors not (yet) chosen, which a change to the chosen ones leaves alone. */
	interface Preview {
	}
}
