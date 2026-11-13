package us.bringardner.fsh.studio.core;

import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * The IDE's preferences (recent files, window layout...) used to live under the
 * package node of us.bringardner.shell.ide, before BjlShell became fsh. The first
 * time a class asks for its node and the new node is empty, the old values are copied
 * over, so the move doesn't lose them. The old node is left as it is.
 */
public final class LegacyPreferences {

	/** The node userNodeForPackage gave the IDE classes before the rename. */
	static final String OLD_NODE = "/us/bringardner/shell/ide";

	private LegacyPreferences() {
	}

	/** Preferences.userNodeForPackage(c), filled from the old node the first time. */
	public static Preferences forPackage(Class<?> c) {
		Preferences now = Preferences.userNodeForPackage(c);
		copyIfEmpty(Preferences.userRoot(), OLD_NODE, now);
		return now;
	}

	/** Copies every key of root's node at oldPath into target, if target has no keys yet. */
	static void copyIfEmpty(Preferences root, String oldPath, Preferences target) {
		try {
			if( target.keys().length > 0 || !root.nodeExists(oldPath)) {
				return;
			}
			Preferences old = root.node(oldPath);
			for (String key : old.keys()) {
				String value = old.get(key, null);
				if( value != null) {
					target.put(key, value);
				}
			}
			target.flush();
		} catch (BackingStoreException | IllegalStateException e) {
			// preferences are a convenience: start empty rather than fail
		}
	}
}
