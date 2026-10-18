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

import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.prefs.Preferences;

import javax.swing.JTextPane;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;

import us.bringardner.fsh.ConsolePanel;
import us.bringardner.fsh.ide.core.EditorFont;

/**
 * The Swing IDE's editor font ({@link EditorFont}): on the script editors and the console, apart
 * from the appearance font that menus and tables use. View &gt; Bigger Text, Smaller Text, Actual
 * Size and Editor Font... change it; it is kept in the IDE's preferences.
 */
final class EditorFonts {

	private static EditorFont current = EditorFont.defaults();
	private static List<String> fixedWidth;

	private EditorFonts() {
	}

	static EditorFont current() {
		return current;
	}

	/** The font kept in preferences, for the editors made from now on (at start). */
	static void load(Preferences prefs) {
		current = EditorFont.load(prefs);
	}

	/** Makes a font the editor font: on every open editor and console, and kept in prefs (if not null). */
	static void choose(EditorFont font, Preferences prefs) {
		current = font;
		if( prefs != null ) {
			font.save(prefs);
		}
		applyToOpenWindows();
	}

	/** The java.awt.Font for an editor font. */
	static Font font(EditorFont f) {
		return new Font(f.getFamily() == null ? Font.MONOSPACED : f.getFamily(), Font.PLAIN, f.getSize());
	}

	/** Gives an editor the editor font. */
	static void apply(RSyntaxTextArea editor) {
		Font f = font(current);
		if( !f.equals(editor.getFont()) ) {
			editor.setFont(f);
		}
		// the line numbers grow and shrink with the text, a little smaller as they always were
		org.fife.ui.rtextarea.Gutter gutter = org.fife.ui.rsyntaxtextarea.RSyntaxUtilities.getGutter(editor);
		if( gutter != null ) {
			gutter.setLineNumberFont(f.deriveFont((float) Math.max(EditorFont.MIN_SIZE, f.getSize() - 1)));
		}
	}

	/** Gives a console the editor font, keeping the console's own style (italic). */
	static void apply(ConsolePanel console) {
		for(JTextPane pane : panes(console, new ArrayList<>())) {
			pane.setFont(font(current).deriveFont(pane.getFont().getStyle()));
		}
	}

	private static List<JTextPane> panes(Container c, List<JTextPane> ret) {
		for(Component k : c.getComponents()) {
			if( k instanceof JTextPane ) {
				ret.add((JTextPane) k);
			} else if( k instanceof Container ) {
				panes((Container) k, ret);
			}
		}
		return ret;
	}

	/** Gives every editor and console in the open windows the editor font. */
	static void applyToOpenWindows() {
		for(Window w : Window.getWindows()) {
			if( w.isDisplayable() ) {
				applyAll(w);
			}
		}
	}

	private static void applyAll(Container c) {
		for(Component k : c.getComponents()) {
			if( k instanceof RSyntaxTextArea && !(k instanceof EditorColors.Preview) ) {
				apply((RSyntaxTextArea) k);
			} else if( k instanceof ConsolePanel ) {
				apply((ConsolePanel) k);
			} else if( k instanceof Container ) {
				applyAll((Container) k);
			}
		}
	}

	/** The fixed-width font families on this computer, by name (worked out once). */
	static synchronized List<String> fixedWidthFamilies() {
		if( fixedWidth == null ) {
			List<String> ret = new ArrayList<>();
			java.awt.Graphics g = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB).getGraphics();
			for(String name : GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()) {
				if( name.startsWith(".") ) {
					// hidden system fonts (macOS)
					continue;
				}
				Font f = new Font(name, Font.PLAIN, 12);
				if( f.canDisplayUpTo("Wim0") != -1 ) {
					continue;
				}
				FontMetrics m = g.getFontMetrics(f);
				int w = m.charWidth('W');
				if( w > 0 && w == m.charWidth('i') && w == m.charWidth('m') && w == m.charWidth('0') ) {
					ret.add(name);
				}
			}
			g.dispose();
			fixedWidth = Collections.unmodifiableList(ret);
		}
		return fixedWidth;
	}
}
