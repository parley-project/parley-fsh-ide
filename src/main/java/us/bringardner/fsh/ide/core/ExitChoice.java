package us.bringardner.fsh.ide.core;

/**
 * The answer when an IDE window is closed (or the IDE quits) with unsaved changes. Closing the
 * question any way but one of its buttons counts as {@link #CANCEL}.
 */
public enum ExitChoice {
	/** Keep the window open, with its changes. */
	CANCEL,
	/** Close without saving. */
	DISCARD,
	/** Save, then close (stays open if the save doesn't happen). */
	SAVE
}
