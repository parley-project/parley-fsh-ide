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

import java.util.prefs.Preferences;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The script editors' syntax colors, and how they're kept. */
public class TestSyntaxColors {

	private Preferences prefs;

	@BeforeEach
	public void node() {
		prefs = Preferences.userRoot().node("us/bringardner/fsh/ide/test/syntax-" + System.nanoTime());
	}

	@AfterEach
	public void removeNode() throws Exception {
		prefs.removeNode();
	}

	@Test
	public void theDefaultFollowsTheTheme() {
		SyntaxColors c = SyntaxColors.load(prefs);
		assertTrue(c.isFollowTheme());
		assertSame(SyntaxColors.LIGHT, c.resolve(false));
		assertSame(SyntaxColors.PARLEY_DARK, c.resolve(true));
		assertNull(c.rgb());
		assertThrows(IllegalStateException.class, ()->c.rgb(SyntaxColors.TEXT));
		assertSame(SyntaxColors.followTheme(), SyntaxColors.load(null));
	}

	@Test
	public void theSchemesHaveEveryColor() {
		assertEquals(8, SyntaxColors.SCHEMES.size());
		for(SyntaxColors s : SyntaxColors.SCHEMES) {
			assertEquals(SyntaxColors.ROLES.size(), s.rgb().length, s.getName());
			assertSame(s, s.resolve(true), "a scheme is its own colors");
			assertSame(s, SyntaxColors.named(s.getName().toUpperCase()));
		}
		assertFalse(SyntaxColors.LIGHT.isDark());
		assertTrue(SyntaxColors.PARLEY_DARK.isDark());
		assertTrue(SyntaxColors.named("Monokai").isDark());
		assertNull(SyntaxColors.named("Nope"));
		assertNull(SyntaxColors.named(null));
		assertEquals("#D0AA42", SyntaxColors.PARLEY_DARK.hex(SyntaxColors.KEYWORDS));
	}

	@Test
	public void changingAColorMakesACustomSet() {
		SyntaxColors c = SyntaxColors.named("Eclipse").withColor(SyntaxColors.STRINGS, 0xFF123456);
		assertTrue(c.isCustom());
		assertEquals(0x123456, c.rgb(SyntaxColors.STRINGS), "only RGB is kept");
		assertEquals(SyntaxColors.named("Eclipse").rgb(SyntaxColors.KEYWORDS), c.rgb(SyntaxColors.KEYWORDS));
		assertEquals(0x7F0055, SyntaxColors.named("Eclipse").rgb(SyntaxColors.KEYWORDS), "the scheme is unchanged");
		int[] rgb = c.rgb();
		rgb[0] = 0;
		assertEquals(0xFFFFFF, c.rgb(0), "a copy");
		assertThrows(IllegalArgumentException.class, ()->SyntaxColors.custom(new int[3]));
		assertThrows(IllegalArgumentException.class, ()->SyntaxColors.custom(null));
	}

	@Test
	public void theChoiceIsKept() throws Exception {
		SyntaxColors.named("Monokai").save(prefs);
		assertSame(SyntaxColors.named("Monokai"), SyntaxColors.load(prefs));
		assertNull(prefs.get(SyntaxColors.PREF_COLORS, null));

		SyntaxColors custom = SyntaxColors.LIGHT.withColor(SyntaxColors.KEYWORDS, 0x00C0FF);
		custom.save(prefs);
		assertEquals(custom, SyntaxColors.load(prefs));
		assertTrue(prefs.get(SyntaxColors.PREF_COLORS, "").startsWith("#FFFFFF,#000000,#00C0FF,"));

		SyntaxColors.followTheme().save(prefs);
		assertTrue(SyntaxColors.load(prefs).isFollowTheme());
		assertNull(prefs.get(SyntaxColors.PREF_SCHEME, null));
	}

	@Test
	public void whatDoesntMakeSenseFollowsTheTheme() {
		prefs.put(SyntaxColors.PREF_SCHEME, "No such scheme");
		assertTrue(SyntaxColors.load(prefs).isFollowTheme());
		prefs.put(SyntaxColors.PREF_SCHEME, SyntaxColors.CUSTOM);
		prefs.put(SyntaxColors.PREF_COLORS, "#FFFFFF,#000000");
		assertTrue(SyntaxColors.load(prefs).isFollowTheme(), "too few colors");
		prefs.put(SyntaxColors.PREF_COLORS, "blue" + ",#000000".repeat(SyntaxColors.ROLES.size() - 1));
		assertTrue(SyntaxColors.load(prefs).isFollowTheme(), "not a color");
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
