package us.bringardner.fsh.ide.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** A watchpoint's name and what it stops on. */
public class TestWatchpoint {

	@Test
	public void namesAreTakenWithoutDollarOrSubscript() {
		assertEquals("x", Watchpoint.normalize("$x"));
		assertEquals("x", Watchpoint.normalize("${x}"));
		assertEquals("a", Watchpoint.normalize(" a[2] "));
		assertTrue(Watchpoint.isValidName("_n1"));
		assertFalse(Watchpoint.isValidName("1n"));
		assertFalse(Watchpoint.isValidName(""));
	}

	@Test
	public void matches() {
		Watchpoint wp = new Watchpoint("x", true, false);
		assertTrue(wp.matches("x", false));
		assertFalse(wp.matches("x", true));
		assertFalse(wp.matches("xy", false));
		assertTrue(wp.matches("x[1]", false));
		wp.setEnabled(false);
		assertFalse(wp.matches("x", false));
		assertEquals("read", wp.accessText());
		assertEquals("read/write", new Watchpoint("x", true, true).accessText());
	}
}
