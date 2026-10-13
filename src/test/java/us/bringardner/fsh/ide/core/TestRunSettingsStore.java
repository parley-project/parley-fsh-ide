package us.bringardner.fsh.ide.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import us.bringardner.parley.files.FileSource;
import us.bringardner.parley.files.FileSourceFactory;

/** The user's store of scripts' arguments and redirects, and how it keeps itself small. */
public class TestRunSettingsStore {

	private static final long DAY = 24L*60*60*1000;

	@TempDir
	Path dir;

	private final AtomicLong now = new AtomicLong(1_800_000_000_000L);

	private RunSettingsStore store() {
		return new RunSettingsStore(dir.resolve("config").resolve("run-settings").toFile(), now::get);
	}

	private String script(String name) throws Exception {
		Path p = dir.resolve(name);
		Files.writeString(p, "echo\n");
		return p.toFile().getCanonicalPath();
	}

	private static RunSettings args(String a) {
		return new RunSettings(a, "", "", "");
	}

	@Test
	public void keepsSettingsAcrossInstances() throws Exception {
		String a = script("a.sh");
		RunSettings s = new RunSettings("one \"two three\"", "/in put", "/tmp/out\tfile", "C:\\err");
		store().put(a, s);
		assertEquals(s, store().get(a));
		assertEquals(RunSettings.NONE, store().get(script("other.sh")));
	}

	@Test
	public void emptySettingsRemoveTheEntryAndAnEmptyStoreHasNoFile() throws Exception {
		RunSettingsStore store = store();
		String a = script("a.sh");
		store.put(a, args("x"));
		assertTrue(store.getFile().exists());
		store.put(a, RunSettings.NONE);
		assertEquals(0, store.size());
		assertFalse(store.getFile().exists());
	}

	@Test
	public void nothingIsWrittenForAScriptThatNeverHadSettings() throws Exception {
		RunSettingsStore store = store();
		store.put(script("a.sh"), RunSettings.NONE);
		store.get(script("b.sh"));
		assertFalse(store.getFile().getParentFile().exists(), "no file, folder or lock");
	}

	@Test
	public void entriesNotUsedForTheMaximumAgeGo() throws Exception {
		RunSettingsStore store = store();
		String old = script("old.sh");
		String used = script("used.sh");
		store.put(old, args("o"));
		store.put(used, args("u"));
		now.addAndGet(100*DAY);
		// using a script keeps it
		assertEquals(args("u"), store.get(used));
		now.addAndGet(100*DAY);
		assertEquals(1, store.prune());
		assertEquals(RunSettings.NONE, store.get(old));
		assertEquals(args("u"), store.get(used));
	}

	@Test
	public void anyChangeDropsWhatsPastTheLimits() throws Exception {
		RunSettingsStore store = store();
		store.setLimits(30, 3);
		for(int i=1; i <= 5; i++) {
			store.put(script(i+".sh"), args(""+i));
			now.addAndGet(1000);
		}
		// the least recently used went
		assertEquals(3, store.size());
		assertEquals(RunSettings.NONE, store.get(script("1.sh")));
		assertEquals(args("5"), store.get(script("5.sh")));

		now.addAndGet(31*DAY);
		store.put(script("new.sh"), args("n"));
		assertEquals(1, store.size());
	}

	@Test
	public void pruneDropsLocalScriptsThatAreGone() throws Exception {
		RunSettingsStore store = store();
		String kept = script("kept.sh");
		String gone = script("gone.sh");
		store.put(kept, args("k"));
		store.put(gone, args("g"));
		// a remote script isn't looked for
		store.put("sftp://tony@server:22/home/tony/remote.sh", args("r"));
		new File(gone).delete();
		assertEquals(1, store.prune());
		assertEquals(args("k"), store.get(kept));
		assertEquals(args("r"), store.get("sftp://tony@server:22/home/tony/remote.sh"));
	}

	@Test
	public void clearForgetsEverything() throws Exception {
		RunSettingsStore store = store();
		store.put(script("a.sh"), args("a"));
		store.put(script("b.sh"), args("b"));
		store.clear();
		assertEquals(0, store.size());
		assertFalse(store.getFile().exists());
	}

	@Test
	public void aScriptIsKnownByItsCanonicalPath() throws Exception {
		script("x.sh");
		Files.createDirectories(dir.resolve("sub"));
		FileSource viaDots = FileSourceFactory.getDefaultFactory().createFileSource(dir.resolve("sub/../x.sh").toString());
		assertEquals(dir.resolve("x.sh").toFile().getCanonicalPath(), RunSettingsStore.keyOf(viaDots));
	}

	@Test
	public void noPasswordIsKept() {
		assertEquals("ftp://tony@host:21", RunSettingsStore.withoutPassword("ftp://tony:secret@host:21"));
		assertEquals("sftp://tony@host:22", RunSettingsStore.withoutPassword("sftp://tony@host:22"));
		assertEquals("fileproxy://", RunSettingsStore.withoutPassword("fileproxy://"));
	}

	@Test
	public void aBrokenLineIsSkipped() throws Exception {
		RunSettingsStore store = store();
		String a = script("a.sh");
		store.put(a, args("a"));
		Files.writeString(store.getFile().toPath(), Files.readString(store.getFile().toPath())+"garbage\nx\ty\n");
		assertEquals(args("a"), store.get(a));
		assertEquals(1, store.size());
	}
}
