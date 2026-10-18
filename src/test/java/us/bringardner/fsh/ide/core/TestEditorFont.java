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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.prefs.Preferences;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The editors' and console's font, apart from the appearance font. */
public class TestEditorFont {

	private Preferences prefs;

	@BeforeEach
	public void node() {
		prefs = Preferences.userRoot().node("us/bringardner/fsh/ide/test/font-" + System.nanoTime());
	}

	@AfterEach
	public void removeNode() throws Exception {
		prefs.removeNode();
	}

	@Test
	public void theDefaultIsWhatTheEditorsHaveAlwaysHad() {
		EditorFont f = EditorFont.defaults();
		assertNull(f.getFamily());
		assertEquals(13, f.getSize());
		assertTrue(f.isDefault());
		assertSame(EditorFont.defaults(), EditorFont.load(prefs));
		assertSame(EditorFont.defaults(), EditorFont.load(null));
		assertEquals("Monospaced 13", f.toString());
	}

	@Test
	public void biggerAndSmallerGoInSteps() {
		EditorFont f = EditorFont.defaults();
		int[] up = {14, 15, 16, 18, 20, 22, 24, 28, 32, 36, 40, 44, 48, 48};
		for(int size : up) {
			f = f.bigger();
			assertEquals(size, f.getSize());
		}
		int[] down = {44, 40, 36, 32, 28, 24, 22, 20, 18, 16, 15, 14, 13, 12, 11, 10, 9, 8, 8};
		for(int size : down) {
			f = f.smaller();
			assertEquals(size, f.getSize());
		}
		assertEquals(13, f.withFamily("Menlo").withSize(30).resetSize().getSize());
		assertEquals("Menlo", f.withFamily(" Menlo ").getFamily());
		assertNull(f.withFamily("  ").getFamily());
		assertEquals(EditorFont.MAX_SIZE, EditorFont.of(null, 400).getSize());
		assertEquals(EditorFont.MIN_SIZE, EditorFont.of(null, 1).getSize());
	}

	@Test
	public void itIsKeptInPreferences() {
		EditorFont f = EditorFont.of("Menlo", 16);
		f.save(prefs);
		assertEquals(f, EditorFont.load(prefs));
		assertEquals(f.hashCode(), EditorFont.load(prefs).hashCode());
		EditorFont.defaults().save(prefs);
		assertNull(prefs.get(EditorFont.PREF_FAMILY, null));
		assertNull(prefs.get(EditorFont.PREF_SIZE, null));
		assertFalse(f.equals("Menlo 16"));
		assertFalse(f.isDefault());
	}

	@Test
	public void theJavaFxStylesheet() {
		String css = EditorFont.of("Menlo", 16).css();
		assertTrue(css.contains(".root.editor-font .script-editor, .root.editor-font .console { -fx-font-family: \"Menlo\", monospace; -fx-font-size: 16px; }"), css);
		assertTrue(css.contains(".lineno { -fx-font-family: \"Menlo\", monospace; -fx-font-size: 14px; }"), css);
		assertTrue(EditorFont.of(null, 8).css().contains("-fx-font-family: monospace; -fx-font-size: 8px;"));
		assertTrue(EditorFont.of(null, 8).css().contains(".lineno { -fx-font-family: monospace; -fx-font-size: 8px; }"));
	}
}
