package us.bringardner.fsh.studio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.awt.Rectangle;

import org.junit.jupiter.api.Test;



/** Saved window bounds. */
public class TestFshStudioBounds {

	private static final Rectangle[] SCREEN = { new Rectangle(0, 0, 1920, 1080) };

	@Test
	public void savedBounds() {
		assertEquals(new Rectangle(10, 20, 800, 600), FshStudio.parseBounds("10,20,800,600", SCREEN));
	}

	@Test
	public void badSavedBoundsAreIgnored() {
		assertNull(FshStudio.parseBounds(null, SCREEN));
		assertNull(FshStudio.parseBounds("", SCREEN));
		assertNull(FshStudio.parseBounds("10,20,800", SCREEN));
		assertNull(FshStudio.parseBounds("10,20,x,600", SCREEN));
		assertNull(FshStudio.parseBounds("10,20,0,0", SCREEN));
		// on a screen that has since been unplugged
		assertNull(FshStudio.parseBounds("3000,20,800,600", SCREEN));
	}

}
