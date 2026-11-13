package us.bringardner.fsh.studio.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.prefs.Preferences;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import us.bringardner.fsh.studio.core.LegacyPreferences;

/** The IDE's preferences survive the move from us.bringardner.shell.ide to us.bringardner.fsh.studio. */
public class TestLegacyPreferences {

	private Preferences base;

	@BeforeEach
	public void setUp() {
		base = Preferences.userRoot().node("fshTestLegacyPreferences" + System.nanoTime());
	}

	@AfterEach
	public void tearDown() throws Exception {
		base.removeNode();
	}

	@Test
	public void copiesOldValuesIntoAnEmptyNode() throws Exception {
		Preferences old = base.node("old");
		old.put("recent0", "/tmp/a.sh");
		old.putInt("width", 800);
		Preferences now = base.node("new");

		LegacyPreferences.copyIfEmpty(base, "old", now);

		assertEquals("/tmp/a.sh", now.get("recent0", null));
		assertEquals(800, now.getInt("width", 0));
		assertEquals("/tmp/a.sh", old.get("recent0", null), "the old node is left as it is");
	}

	@Test
	public void leavesANodeThatAlreadyHasValues() throws Exception {
		base.node("old").put("recent0", "/tmp/old.sh");
		Preferences now = base.node("new");
		now.put("recent0", "/tmp/new.sh");

		LegacyPreferences.copyIfEmpty(base, "old", now);

		assertEquals("/tmp/new.sh", now.get("recent0", null));
	}

	@Test
	public void noOldNodeIsFine() throws Exception {
		Preferences now = base.node("new");
		LegacyPreferences.copyIfEmpty(base, "missing", now);
		assertNull(now.get("recent0", null));
	}
}
