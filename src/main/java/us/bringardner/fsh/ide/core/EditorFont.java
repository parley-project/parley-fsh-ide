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
package us.bringardner.fsh.ide.core;

import java.util.Objects;
import java.util.prefs.Preferences;

/**
 * The font of the script editors and the console: a fixed-width family (or the platform's standard
 * monospaced font) and a size. It is the IDE's own, apart from the appearance font that menus and
 * tables use, and kept in the IDE's preferences, where both IDEs find it. View &gt; Bigger Text and
 * Smaller Text change the size a step at a time; Actual Size goes back to {@link #DEFAULT_SIZE}.
 * <p>
 * No UI toolkit here: the Swing IDE makes a java.awt.Font of it, the JavaFX IDE a stylesheet
 * ({@link #css()}).
 */
public final class EditorFont {

	/** The preference keys. */
	public static final String PREF_FAMILY = "EditorFontFamily";
	public static final String PREF_SIZE = "EditorFontSize";

	/** The sizes the editors have had, and the smallest and largest offered. */
	public static final int DEFAULT_SIZE = 13;
	public static final int MIN_SIZE = 8;
	public static final int MAX_SIZE = 48;

	/** The style class the JavaFX IDE puts on its root when it uses {@link #css()}. */
	public static final String CSS_CLASS = "editor-font";

	private static final EditorFont DEFAULTS = new EditorFont(null, DEFAULT_SIZE);

	private final String family;
	private final int size;

	private EditorFont(String family, int size) {
		this.family = family == null || family.trim().isEmpty() ? null : family.trim();
		this.size = Math.max(MIN_SIZE, Math.min(MAX_SIZE, size));
	}

	/** The platform's standard monospaced font at {@link #DEFAULT_SIZE}: what the editors have always had. */
	public static EditorFont defaults() {
		return DEFAULTS;
	}

	/**
	 * @param family a font family, or null for the platform's standard monospaced font
	 * @param size the size in points, kept between {@link #MIN_SIZE} and {@link #MAX_SIZE}
	 */
	public static EditorFont of(String family, int size) {
		return new EditorFont(family, size);
	}

	/** The font kept in preferences, or the defaults. */
	public static EditorFont load(Preferences prefs) {
		if( prefs == null ) {
			return DEFAULTS;
		}
		EditorFont f = new EditorFont(prefs.get(PREF_FAMILY, null), prefs.getInt(PREF_SIZE, DEFAULT_SIZE));
		return f.equals(DEFAULTS) ? DEFAULTS : f;
	}

	/** Keeps this font in preferences; the defaults remove what was kept. */
	public void save(Preferences prefs) {
		if( family == null ) {
			prefs.remove(PREF_FAMILY);
		} else {
			prefs.put(PREF_FAMILY, family);
		}
		if( size == DEFAULT_SIZE ) {
			prefs.remove(PREF_SIZE);
		} else {
			prefs.putInt(PREF_SIZE, size);
		}
	}

	/** The family, or null for the platform's standard monospaced font. */
	public String getFamily() {
		return family;
	}

	public int getSize() {
		return size;
	}

	public boolean isDefault() {
		return equals(DEFAULTS);
	}

	/** A step bigger: 1 point up to 16, 2 up to 24, then 4. */
	public EditorFont bigger() {
		return withSize(size + step(size));
	}

	/** A step smaller (the reverse of {@link #bigger()}). */
	public EditorFont smaller() {
		return withSize(size - step(size - 1));
	}

	private static int step(int size) {
		return size < 16 ? 1 : size < 24 ? 2 : 4;
	}

	/** The same family at the default size. */
	public EditorFont actualSize() {
		return withSize(DEFAULT_SIZE);
	}

	public EditorFont withSize(int size) {
		return new EditorFont(family, size);
	}

	public EditorFont withFamily(String family) {
		return new EditorFont(family, size);
	}

	/**
	 * A stylesheet giving the JavaFX IDE's script editor and console this font (for a root with
	 * {@link #CSS_CLASS}); the line numbers are a little smaller, as they always were.
	 */
	public String css() {
		String f = family == null ? "monospace" : "\"" + family.replace("\"", "") + "\", monospace";
		String p = ".root." + CSS_CLASS;
		return p + " .script-editor, " + p + " .console { -fx-font-family: " + f + "; -fx-font-size: " + size
				+ "px; }\n" + p + " .script-editor .lineno { -fx-font-family: " + f + "; -fx-font-size: "
				+ Math.max(MIN_SIZE, size - 2) + "px; }\n";
	}

	@Override
	public boolean equals(Object o) {
		if( !(o instanceof EditorFont) ) {
			return false;
		}
		EditorFont f = (EditorFont) o;
		return size == f.size && Objects.equals(family, f.family);
	}

	@Override
	public int hashCode() {
		return Objects.hash(family, size);
	}

	@Override
	public String toString() {
		return (family == null ? "Monospaced" : family) + " " + size;
	}
}
