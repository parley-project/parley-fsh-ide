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

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The script editors' syntax colors: one color for each part of the editor ({@link #ROLES}), as
 * 0xRRGGBB. They are part of the appearance theme: the Swing IDE adds a "Script editor" page to the
 * theme designer, and a theme keeps them as {@code fsh-ide.editor.<key>} settings
 * ({@link #EXTENSION_ID}, {@link #KEYS}). A theme without them, such as a built-in one, gets colors
 * that match it ({@link #matching}): a background tinted with its light color, and its accent for
 * the line numbers, selection and caret, so they change with the theme. A new theme starts with
 * those of the theme it is made from. The ready-made schemes ({@link #SCHEMES}) are starting
 * points for the designer.
 * <p>
 * No UI toolkit here: the Swing IDE turns them into RSyntaxTextArea colors, the JavaFX IDE into a
 * stylesheet ({@link #css()}).
 */
public final class SyntaxColors {

	/** The prefix of the syntax colors' settings in a theme. */
	public static final String EXTENSION_ID = "fsh-ide.editor";

	/** The name of colors that aren't one of the ready-made schemes. */
	public static final String CUSTOM = "Custom";

	/** The name of the colors made from a theme's ({@link #matching}). */
	public static final String MATCH = "Match the theme";

	/** What each color is for. */
	public static final List<String> ROLES = Collections.unmodifiableList(Arrays.asList("Background", "Text",
			"Keywords", "Commands", "Comments", "Strings", "Variables", "Numbers", "Operators", "Selection",
			"Current line", "Line numbers", "Line number background", "Caret"));

	/** Each color's key in a theme (after {@link #EXTENSION_ID}), in the order of {@link #ROLES}. */
	public static final List<String> KEYS = Collections.unmodifiableList(Arrays.asList("background", "text",
			"keywords", "commands", "comments", "strings", "variables", "numbers", "operators", "selection",
			"currentLine", "lineNumbers", "lineNumberBackground", "caret"));

	public static final int BACKGROUND = 0, TEXT = 1, KEYWORDS = 2, COMMANDS = 3, COMMENTS = 4, STRINGS = 5,
			VARIABLES = 6, NUMBERS = 7, OPERATORS = 8, SELECTION = 9, CURRENT_LINE = 10, LINE_NUMBERS = 11,
			GUTTER = 12, CARET = 13;

	/** RSyntaxTextArea's own light colors. */
	public static final SyntaxColors LIGHT = scheme("Light",
			"#FFFFFF #000000 #0000FF #AD8000 #008000 #DC009C #EE8800 #6400C8 #804040 #C8C8FF #FFFFAA #787878 #FFFFFF #FF0000");

	/** The Parley palette: ink background, warm off-white text, gold keywords. */
	public static final SyntaxColors PARLEY_DARK = scheme("Parley dark",
			"#0E0D0B #E8E2D0 #D0AA42 #F0E6C8 #7A7464 #D08A5A #8DB6C7 #E3B36A #C9C1AA #5A4818 #1A1813 #6E6858 #0A0908 #D0AA42");

	/** The ready-made schemes (from RSyntaxTextArea's, and the Parley dark one). */
	public static final List<SyntaxColors> SCHEMES = Collections.unmodifiableList(Arrays.asList(LIGHT, PARLEY_DARK,
			scheme("Dark",
					"#293134 #A9B7C6 #93C763 #E0E2E4 #66747B #EC7600 #AE9FBF #FFCD22 #E8E2B7 #404E51 #2F393C #81969A #293134 #C1CBC2"),
			scheme("Eclipse",
					"#FFFFFF #000000 #7F0055 #000000 #3F7F5F #2900FF #FF9900 #000000 #000000 #B8CFE5 #E8F2FE #787878 #FFFFFF #000000"),
			scheme("IDEA",
					"#FFFFFF #000000 #000080 #000000 #808080 #008000 #810CA8 #0000FF #000000 #C4D4F0 #FFFFD7 #787878 #FFFFFF #000000"),
			scheme("Monokai",
					"#2E2E2E #F9F9F9 #F92672 #52E3F6 #6C6C6C #E3ED77 #FD971F #AE81FF #F92672 #4C4C4C #383A38 #81969A #2E2E2E #D2D2D2"),
			scheme("Visual Studio",
					"#FFFFFF #000000 #0000FF #000000 #008000 #A31515 #FF9900 #000000 #000000 #B8CFE5 #E8F2FE #2B91AF #FFFFFF #000000"),
			scheme("Druid",
					"#001C00 #66AA66 #885522 #44AAEE #325232 #009000 #EEAACC #EEAACC #66AA66 #143014 #102C10 #66AA66 #001C00 #88EE88")));

	private final String name;
	private final int[] rgb;

	private SyntaxColors(String name, int[] rgb) {
		this.name = name;
		this.rgb = rgb;
	}

	private static SyntaxColors scheme(String name, String hexes) {
		return new SyntaxColors(name, parse(hexes.split(" ")));
	}

	/**
	 * The syntax colors the colors that match a theme start from: light, or Parley dark for a dark
	 * theme. (A theme with no colors of its own gets {@link #matching} colors.)
	 */
	public static SyntaxColors defaults(boolean darkTheme) {
		return darkTheme ? PARLEY_DARK : LIGHT;
	}

	/** How much of a light theme's light color is in the background. */
	static final double TINT = 0.18;

	/**
	 * Colors that match a theme: the background a light tint of the theme's light color (a dark
	 * theme: a shade darker than its fields), the line numbers' background twice as much, the
	 * selection and the caret's line from its accent, and the text, line numbers and caret in its
	 * colors. The syntax colors are {@link #defaults(boolean)}', darkened or lightened where they
	 * would be hard to read on that background. Named {@link #MATCH}.
	 *
	 * @param start the theme's light color (the start of its gradient), 0xRRGGBB
	 * @param accent its accent
	 * @param field its text fields' background
	 * @param fieldText their text
	 * @param dark whether it is a dark theme
	 */
	public static SyntaxColors matching(int start, int accent, int field, int fieldText, boolean dark) {
		int[] c = defaults(dark).rgb();
		int bg;
		int gutter;
		if( dark ) {
			bg = mix(field, 0x000000, 0.25);
			gutter = mix(field, 0x000000, 0.45);
			c[CURRENT_LINE] = mix(bg, accent, 0.08);
		} else {
			bg = mix(0xFFFFFF, start, TINT);
			gutter = mix(0xFFFFFF, start, TINT * 2);
			c[CURRENT_LINE] = mix(bg, accent, 0.12);
		}
		c[BACKGROUND] = bg;
		c[GUTTER] = gutter;
		c[SELECTION] = mix(bg, accent, 0.35);
		c[TEXT] = readable(fieldText, bg, 7, dark);
		c[LINE_NUMBERS] = readable(accent, gutter, 3, dark);
		c[CARET] = readable(accent, bg, 4.5, dark);
		for(int role : new int[] {KEYWORDS, COMMANDS, STRINGS, VARIABLES, NUMBERS, OPERATORS}) {
			c[role] = readable(c[role], bg, 4.5, dark);
		}
		c[COMMENTS] = readable(c[COMMENTS], bg, 3.5, dark);
		return new SyntaxColors(MATCH, c);
	}

	/** Between two colors (0xRRGGBB): t = 0 is a, 1 is b. */
	static int mix(int a, int b, double t) {
		int ret = 0;
		for(int shift = 16; shift >= 0; shift -= 8) {
			double v = (a >> shift & 0xFF) * (1 - t) + (b >> shift & 0xFF) * t;
			ret |= (int) Math.round(v) << shift;
		}
		return ret;
	}

	/** The contrast between two colors (0xRRGGBB), 1 to 21, as WCAG has it. */
	static double contrast(int a, int b) {
		double x = luminance(a), y = luminance(b);
		return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05);
	}

	private static double luminance(int c) {
		double ret = 0;
		double[] weights = {0.2126, 0.7152, 0.0722};
		for(int i = 0; i < 3; i++) {
			double v = (c >> (16 - 8 * i) & 0xFF) / 255.0;
			ret += weights[i] * (v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4));
		}
		return ret;
	}

	/** A color made darker (on a light background) or lighter (on a dark one) until it has this contrast. */
	static int readable(int color, int background, double contrast, boolean darkBackground) {
		int toward = darkBackground ? 0xFFFFFF : 0x000000;
		for(int step = 0; step <= 50; step++) {
			int c = mix(color, toward, step / 50.0);
			if( contrast(c, background) >= contrast ) {
				return c;
			}
		}
		return toward;
	}

	/**
	 * A custom set of colors.
	 *
	 * @param rgb a color (0xRRGGBB) for each of {@link #ROLES}
	 */
	public static SyntaxColors custom(int[] rgb) {
		if( rgb == null || rgb.length != ROLES.size() ) {
			throw new IllegalArgumentException("Need " + ROLES.size() + " colors");
		}
		int[] copy = new int[rgb.length];
		for(int i = 0; i < copy.length; i++) {
			copy[i] = rgb[i] & 0xFFFFFF;
		}
		return new SyntaxColors(CUSTOM, copy);
	}

	/** The ready-made scheme with this name, or null. */
	public static SyntaxColors named(String name) {
		for(SyntaxColors s : SCHEMES) {
			if( s.name.equalsIgnoreCase(name == null ? "" : name.trim()) ) {
				return s;
			}
		}
		return null;
	}

	// ---------------------------------------------------------------- in a theme

	/**
	 * The colors from a theme's settings ({@link #KEYS} to #RRGGBB); a color missing or not a color
	 * is the default's.
	 *
	 * @param values the settings, keys without the prefix
	 * @param darkTheme whether the theme is dark, for the defaults
	 */
	public static SyntaxColors fromValues(Map<String, String> values, boolean darkTheme) {
		return fromValues(values, defaults(darkTheme));
	}

	/**
	 * The colors from a theme's settings ({@link #KEYS} to #RRGGBB); a color missing or not a color
	 * is the default's.
	 *
	 * @param values the settings, keys without the prefix
	 * @param defaults the colors for none of its own ({@link #matching}, say)
	 */
	public static SyntaxColors fromValues(Map<String, String> values, SyntaxColors defaults) {
		int[] c = defaults.rgb();
		boolean own = false;
		for(int i = 0; i < c.length; i++) {
			String v = values == null ? null : values.get(KEYS.get(i));
			if( v != null && v.trim().matches("#[0-9A-Fa-f]{6}") ) {
				c[i] = Integer.parseInt(v.trim().substring(1), 16);
				own = true;
			}
		}
		if( !own ) {
			return defaults;
		}
		SyntaxColors ret = custom(c);
		for(SyntaxColors s : SCHEMES) {
			if( Arrays.equals(s.rgb, ret.rgb) ) {
				return s;
			}
		}
		return ret;
	}

	/**
	 * The colors of a theme, from all its settings ({@code Theme.getExtras()}, keys with prefixes).
	 *
	 * @param extras the theme's settings
	 * @param darkTheme whether the theme is dark
	 */
	public static SyntaxColors ofTheme(Map<String, String> extras, boolean darkTheme) {
		return fromValues(own(extras), darkTheme);
	}

	/**
	 * The colors of a theme, from all its settings ({@code Theme.getExtras()}, keys with prefixes).
	 *
	 * @param extras the theme's settings
	 * @param defaults the colors for a theme with none of its own ({@link #matching}, say)
	 */
	public static SyntaxColors ofTheme(Map<String, String> extras, SyntaxColors defaults) {
		return fromValues(own(extras), defaults);
	}

	/** True if a theme's settings ({@code Theme.getExtras()}) have syntax colors of their own. */
	public static boolean hasColors(Map<String, String> extras) {
		return !own(extras).isEmpty();
	}

	private static Map<String, String> own(Map<String, String> extras) {
		Map<String, String> ret = new LinkedHashMap<>();
		String prefix = EXTENSION_ID + ".";
		if( extras != null ) {
			for(Map.Entry<String, String> e : extras.entrySet()) {
				if( e.getKey().startsWith(prefix) ) {
					ret.put(e.getKey().substring(prefix.length()), e.getValue());
				}
			}
		}
		return ret;
	}

	/** These colors as a theme's settings: {@link #KEYS} to #RRGGBB. */
	public Map<String, String> toValues() {
		Map<String, String> ret = new LinkedHashMap<>();
		for(int i = 0; i < rgb.length; i++) {
			ret.put(KEYS.get(i), toHex(rgb[i]));
		}
		return ret;
	}

	// ---------------------------------------------------------------- the colors

	public String getName() {
		return name;
	}

	public boolean isCustom() {
		return CUSTOM.equals(name);
	}

	/** True for colors made from a theme's ({@link #matching}). */
	public boolean isMatch() {
		return MATCH.equals(name);
	}

	/** One color, 0xRRGGBB. */
	public int rgb(int role) {
		return rgb[role];
	}

	/** All the colors, 0xRRGGBB (a copy). */
	public int[] rgb() {
		return rgb.clone();
	}

	/** One color as #RRGGBB. */
	public String hex(int role) {
		return toHex(rgb(role));
	}

	/** A color, 0xRRGGBB, as #RRGGBB. */
	public static String toHex(int rgb) {
		return String.format("#%06X", rgb & 0xFFFFFF);
	}

	/** These colors with one changed: a custom set. */
	public SyntaxColors withColor(int role, int color) {
		int[] c = rgb();
		c[role] = color;
		return custom(c);
	}

	/** True when the background is dark (so the light text and gutter go with it). */
	public boolean isDark() {
		int c = rgb(BACKGROUND);
		double lum = (0.299 * (c >> 16 & 0xFF) + 0.587 * (c >> 8 & 0xFF) + 0.114 * (c & 0xFF)) / 255;
		return lum < 0.5;
	}

	private static int[] parse(String[] hexes) {
		if( hexes.length != ROLES.size() ) {
			throw new IllegalArgumentException("Need " + ROLES.size() + " colors, not " + hexes.length);
		}
		int[] ret = new int[hexes.length];
		for(int i = 0; i < ret.length; i++) {
			String h = hexes[i].trim();
			if( !h.matches("#[0-9A-Fa-f]{6}") ) {
				throw new IllegalArgumentException("\"" + h + "\" isn't a color (#RRGGBB)");
			}
			ret[i] = Integer.parseInt(h.substring(1), 16);
		}
		return ret;
	}

	// ---------------------------------------------------------------- JavaFX

	/** The style class the JavaFX IDE puts on its root when it uses {@link #css()}. */
	public static final String CSS_CLASS = "syntax-colors";

	/**
	 * A stylesheet giving the JavaFX IDE's script editor these colors (for a root with
	 * {@link #CSS_CLASS}). Its rules are more specific than the IDE's own, light or dark. The JavaFX
	 * editor doesn't mark commands, numbers, operators or the caret's line, so those colors aren't used there.
	 */
	public String css() {
		String p = ".root." + CSS_CLASS + " .script-editor";
		StringBuilder b = new StringBuilder();
		b.append(p).append(" { -fx-background-color: ").append(hex(BACKGROUND)).append("; }\n");
		// .text first: a keyword is .text too, and the later of two rules as specific wins
		b.append(p).append(" .text { -fx-fill: ").append(hex(TEXT)).append("; }\n");
		String[][] spans = {{"keyword", "" + KEYWORDS}, {"comment", "" + COMMENTS}, {"string", "" + STRINGS},
			{"variable", "" + VARIABLES}, {"number", "" + NUMBERS}};
		for(String[] s : spans) {
			b.append(p).append(" .").append(s[0]).append(" { -fx-fill: ").append(hex(Integer.parseInt(s[1])))
			.append("; }\n");
		}
		b.append(p).append(" .caret { -fx-stroke: ").append(hex(CARET)).append("; }\n");
		b.append(p).append(" .selection { -fx-fill: ").append(hex(SELECTION)).append("; }\n");
		b.append(p).append(" .lineno { -fx-background-color: ").append(hex(GUTTER)).append("; -fx-text-fill: ")
		.append(hex(LINE_NUMBERS)).append("; }\n");
		// the line a debugged script is stopped on: light or dark to go with the background
		b.append(p).append(" .current-line { -fx-background-color: ").append(isDark() ? "#33401C" : "#D9F2D9")
		.append("; }\n");
		return b.toString();
	}

	@Override
	public boolean equals(Object o) {
		if( !(o instanceof SyntaxColors) ) {
			return false;
		}
		SyntaxColors s = (SyntaxColors) o;
		return name.equals(s.name) && Arrays.equals(rgb, s.rgb);
	}

	@Override
	public int hashCode() {
		return name.hashCode() * 31 + Arrays.hashCode(rgb);
	}

	@Override
	public String toString() {
		return name;
	}
}
