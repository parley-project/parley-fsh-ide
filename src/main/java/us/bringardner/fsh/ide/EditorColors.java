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
 * The script editors' colors, which are part of the appearance theme (swing-widgets' Theme): the
 * syntax colors set on the theme designer's Script editor page ({@link EditorThemeExtension}), or
 * for a theme without its own, colors that match it ({@link EditorThemeExtension#matching}). The
 * parts with no color of their own there come from RSyntaxTextArea's light colors or the Parley dark
 * ones (editor-dark.xml: ink background, gold keywords).
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

	private EditorColors() {
	}

	/** The colors of the theme in effect. */
	static SyntaxColors current() {
		return EditorThemeExtension.colorsOf(us.bringardner.swing.gradient.Theme.current());
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
		// the editor font first: the colors keep the font the editor has
		EditorFonts.apply(editor);
		apply(editor, current());
	}

	/** Gives an editor these colors, keeping its font. */
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

	/**
	 * Gives a console the editors' background, text, caret and selection colors (its errors stay
	 * red), so it matches the editor above it.
	 */
	static void apply(us.bringardner.fsh.ConsolePanel console) {
		SyntaxColors colors = current();
		for(java.awt.Component k : panes(console, new java.util.ArrayList<>())) {
			javax.swing.JTextPane pane = (javax.swing.JTextPane) k;
			pane.setBackground(color(colors, SyntaxColors.BACKGROUND));
			pane.setForeground(color(colors, SyntaxColors.TEXT));
			pane.setCaretColor(color(colors, SyntaxColors.CARET));
			pane.setSelectionColor(color(colors, SyntaxColors.SELECTION));
			pane.repaint();
		}
		console.setBackground(color(colors, SyntaxColors.BACKGROUND));
		// below the text, the scroll pane's viewport shows (the gradient look clears the panel)
		java.awt.Container viewport = javax.swing.SwingUtilities.getAncestorOfClass(javax.swing.JViewport.class, console);
		if( viewport != null ) {
			viewport.setBackground(color(colors, SyntaxColors.BACKGROUND));
		}
		console.repaint();
	}

	private static java.util.List<java.awt.Component> panes(Container c, java.util.List<java.awt.Component> ret) {
		if( c instanceof javax.swing.JTextPane ) {
			ret.add(c);
		}
		for(Component k : c.getComponents()) {
			if( k instanceof Container ) {
				panes((Container) k, ret);
			}
		}
		return ret;
	}

	/** Gives every editor in the open windows the current theme's colors (after a theme change). */
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
			} else if( k instanceof us.bringardner.fsh.ConsolePanel ) {
				apply((us.bringardner.fsh.ConsolePanel) k);
			} else if( k instanceof Container ) {
				applyAll((Container) k);
			}
		}
	}

	/** An editor that shows colors other than the theme's (a preview), which a theme change leaves alone. */
	interface Preview {
	}
}
