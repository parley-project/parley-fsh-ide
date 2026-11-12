package us.bringardner.fsh.ide;

import java.util.HashMap;
import java.util.Map;
import java.util.prefs.AbstractPreferences;

/**
 * Preferences kept in memory, for the Swing IDE's tests (FshIDE.usePreferences): a test's
 * window starts with no recent script, so it shows only the script the test gives it, and
 * nothing a test does reaches the user's own preferences.
 */
public final class MemoryPreferences extends AbstractPreferences {
	private final Map<String, String> values = new HashMap<>();
	private final Map<String, MemoryPreferences> children = new HashMap<>();

	public MemoryPreferences() {
		this(null, "");
	}

	private MemoryPreferences(MemoryPreferences parent, String name) {
		super(parent, name);
	}

	@Override protected void putSpi(String key, String value) { values.put(key, value); }
	@Override protected String getSpi(String key) { return values.get(key); }
	@Override protected void removeSpi(String key) { values.remove(key); }
	@Override protected void removeNodeSpi() { }
	@Override protected String[] keysSpi() { return values.keySet().toArray(new String[0]); }
	@Override protected String[] childrenNamesSpi() { return children.keySet().toArray(new String[0]); }
	@Override protected AbstractPreferences childSpi(String name) { return children.computeIfAbsent(name, n->new MemoryPreferences(this, n)); }
	@Override protected void syncSpi() { }
	@Override protected void flushSpi() { }
}
