package us.bringardner.fsh.ide.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** A mark keeps to its text as text around it changes, like a Swing Position. */
public class MarkOffsetsTest {

	@Test
	public void beforeTheMarkPushesItAlong() {
		assertEquals(15, MarkOffsets.adjust(10, 2, 0, 5));
		assertEquals(7, MarkOffsets.adjust(10, 2, 3, 0));
		assertEquals(12, MarkOffsets.adjust(10, 2, 3, 5));
	}

	@Test
	public void afterTheMarkLeavesIt() {
		assertEquals(10, MarkOffsets.adjust(10, 11, 0, 5));
		assertEquals(10, MarkOffsets.adjust(10, 11, 4, 0));
	}

	@Test
	public void insertingAtTheMarkPushesIt() {
		// a new line typed at the start of a breakpoint's line moves the breakpoint down with its statement
		assertEquals(11, MarkOffsets.adjust(10, 10, 0, 1));
	}

	@Test
	public void removingAroundTheMarkPullsItBack() {
		assertEquals(4, MarkOffsets.adjust(10, 4, 8, 0));
		assertEquals(4, MarkOffsets.adjust(10, 4, 8, 2));
		// a replacement that starts at the mark keeps it where it was
		assertEquals(10, MarkOffsets.adjust(10, 10, 3, 1));
		// removing exactly up to the mark moves it to where the removal was
		assertEquals(4, MarkOffsets.adjust(10, 4, 6, 0));
	}
}
