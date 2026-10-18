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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** The script editors' syntax colors, as a theme keeps them. */
public class TestSyntaxColors {

	@Test
	public void aThemeWithoutColorsGetsTheDefaults() {
		assertSame(SyntaxColors.LIGHT, SyntaxColors.defaults(false));
		assertSame(SyntaxColors.PARLEY_DARK, SyntaxColors.defaults(true));
		assertSame(SyntaxColors.LIGHT, SyntaxColors.ofTheme(Map.of(), false));
		assertSame(SyntaxColors.PARLEY_DARK, SyntaxColors.ofTheme(Map.of("other-app.x", "1"), true));
		assertSame(SyntaxColors.PARLEY_DARK, SyntaxColors.ofTheme(null, true));
		assertFalse(SyntaxColors.hasColors(Map.of("other-app.x", "1")));
		assertFalse(SyntaxColors.hasColors(null));
	}

	@Test
	public void theSchemesHaveEveryColor() {
		assertEquals(8, SyntaxColors.SCHEMES.size());
		assertEquals(SyntaxColors.ROLES.size(), SyntaxColors.KEYS.size());
		for(SyntaxColors s : SyntaxColors.SCHEMES) {
			assertEquals(SyntaxColors.ROLES.size(), s.rgb().length, s.getName());
			assertSame(s, SyntaxColors.named(s.getName().toUpperCase()));
			assertFalse(s.isCustom());
		}
		assertFalse(SyntaxColors.LIGHT.isDark());
		assertTrue(SyntaxColors.PARLEY_DARK.isDark());
		assertTrue(SyntaxColors.named("Monokai").isDark());
		assertNull(SyntaxColors.named("Nope"));
		assertNull(SyntaxColors.named(null));
		assertEquals("#D0AA42", SyntaxColors.PARLEY_DARK.hex(SyntaxColors.KEYWORDS));
	}

	@Test
	public void aThemesColorsAreItsSettings() {
		SyntaxColors custom = SyntaxColors.named("Monokai").withColor(SyntaxColors.KEYWORDS, 0x00C0FF);
		Map<String, String> values = custom.toValues();
		assertEquals("#00C0FF", values.get("keywords"));
		assertEquals("#2E2E2E", values.get("background"));
		assertEquals(custom, SyntaxColors.fromValues(values, false));

		Map<String, String> extras = new HashMap<>();
		values.forEach((k, v)->extras.put(SyntaxColors.EXTENSION_ID + "." + k, v));
		extras.put("other-app.x", "1");
		assertTrue(SyntaxColors.hasColors(extras));
		assertEquals(custom, SyntaxColors.ofTheme(extras, false));

		// a scheme's colors are that scheme
		assertSame(SyntaxColors.named("Druid"), SyntaxColors.fromValues(SyntaxColors.named("Druid").toValues(), false));
	}

	@Test
	public void missingOrBadColorsAreTheDefaults() {
		SyntaxColors c = SyntaxColors.fromValues(Map.of("keywords", "#123456", "strings", "red"), true);
		assertTrue(c.isCustom());
		assertEquals(0x123456, c.rgb(SyntaxColors.KEYWORDS));
		assertEquals(SyntaxColors.PARLEY_DARK.rgb(SyntaxColors.STRINGS), c.rgb(SyntaxColors.STRINGS));
		assertEquals(SyntaxColors.PARLEY_DARK.rgb(SyntaxColors.BACKGROUND), c.rgb(SyntaxColors.BACKGROUND));
		assertSame(SyntaxColors.LIGHT, SyntaxColors.fromValues(Map.of("strings", "red"), false));
		assertSame(SyntaxColors.LIGHT, SyntaxColors.fromValues(null, false));
	}

	@Test
	public void changingAColorMakesACustomSet() {
		SyntaxColors c = SyntaxColors.named("Eclipse").withColor(SyntaxColors.STRINGS, 0xFF123456);
		assertTrue(c.isCustom());
		assertEquals(0x123456, c.rgb(SyntaxColors.STRINGS), "only RGB is kept");
		assertEquals(0x7F0055, SyntaxColors.named("Eclipse").rgb(SyntaxColors.KEYWORDS), "the scheme is unchanged");
		int[] rgb = c.rgb();
		rgb[0] = 0;
		assertEquals(0xFFFFFF, c.rgb(0), "a copy");
		assertThrows(IllegalArgumentException.class, ()->SyntaxColors.custom(new int[3]));
		assertThrows(IllegalArgumentException.class, ()->SyntaxColors.custom(null));
	}

	@Test
	public void theJavaFxStylesheetHasTheColors() {
		SyntaxColors c = SyntaxColors.named("Monokai").withColor(SyntaxColors.KEYWORDS, 0x00C0FF);
		String css = c.css();
		assertTrue(css.contains(".root.syntax-colors .script-editor .keyword { -fx-fill: #00C0FF; }"), css);
		assertTrue(css.contains(".script-editor { -fx-background-color: #2E2E2E; }"), css);
		assertTrue(css.contains(".current-line { -fx-background-color: #33401C; }"), "dark: a dark debug line");
		assertTrue(css.indexOf(" .text {") < css.indexOf(" .keyword {"), "keywords after plain text");
		assertTrue(SyntaxColors.LIGHT.css().contains(".current-line { -fx-background-color: #D9F2D9; }"));
	}

	@Test
	public void equalityIsNameAndColors() {
		assertEquals(SyntaxColors.LIGHT.withColor(1, 2), SyntaxColors.LIGHT.withColor(1, 2));
		assertEquals(SyntaxColors.LIGHT.withColor(1, 2).hashCode(), SyntaxColors.LIGHT.withColor(1, 2).hashCode());
		assertFalse(SyntaxColors.LIGHT.equals(SyntaxColors.LIGHT.withColor(1, 2)));
		assertFalse(SyntaxColors.LIGHT.equals("Light"));
		assertEquals("Light", SyntaxColors.LIGHT.toString());
		assertEquals("#00C0FF", SyntaxColors.toHex(0xFF00C0FF));
	}
}
