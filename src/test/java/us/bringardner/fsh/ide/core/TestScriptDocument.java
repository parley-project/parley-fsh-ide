package us.bringardner.fsh.ide.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import us.bringardner.parley.files.FileSource;
import us.bringardner.parley.files.FileSourceFactory;

/**
 * A script and its run settings: the file holds only the code, the settings are kept in the
 * user's store, and settings lines saved by earlier versions are read and then dropped.
 */
public class TestScriptDocument {

	@TempDir
	Path dir;

	private RunSettingsStore store() {
		return new RunSettingsStore(dir.resolve("config").resolve("run-settings").toFile());
	}

	private FileSource file(String name, String text) throws Exception {
		Path p = dir.resolve(name);
		Files.writeString(p, text);
		return FileSourceFactory.getDefaultFactory().createFileSource(p.toString());
	}

	private String read(String name) throws Exception {
		return Files.readString(dir.resolve(name));
	}

	// ---- reading the settings lines of earlier versions

	@Test
	public void readsTheSettingsLinesOfEarlierVersions() {
		ScriptDocument doc = ScriptDocument.parse("#BjlIdeScriptArgs=a b\n#BjlIdeScriptIn=/tmp/in\n"
				+ "#BjlIdeScriptOut=/tmp/out\n#BjlIdeScriptErr=/tmp/err\necho hi\n");
		assertTrue(doc.hasLegacySettings());
		assertEquals(new RunSettings("a b", "/tmp/in", "/tmp/out", "/tmp/err"), doc.settings());
		assertEquals("echo hi\n", doc.body());
	}

	@Test
	public void settingsLinesCanBeAnywhere() {
		ScriptDocument doc = ScriptDocument.parse("echo a\n#BjlIdeScriptArgs=x\necho b");
		assertEquals("x", doc.arguments());
		assertEquals("echo a\necho b", doc.body());
	}

	@Test
	public void aScriptWithoutThemIsItsCodeExactly() {
		for(String text : new String[] {"echo hi", "echo hi\n", "a\r\nb\r\n", "\n\n# just comments\n"}) {
			ScriptDocument doc = ScriptDocument.parse(text);
			assertFalse(doc.hasLegacySettings());
			assertEquals(text, doc.body());
			assertEquals(RunSettings.NONE, doc.settings());
		}
		assertEquals("", ScriptDocument.parse("").body());
	}

	// ---- saving and opening

	@Test
	public void savingWritesOnlyTheCodeAndKeepsTheSettingsInTheStore() throws Exception {
		RunSettingsStore store = store();
		FileSource f = file("run.sh", "");
		assertNull(new ScriptDocument("echo $1\n", new RunSettings("a b", "", "/tmp/out", "")).save(f, store));
		assertEquals("echo $1\n", read("run.sh"));
		assertEquals(new RunSettings("a b", "", "/tmp/out", ""), store.get(f));

		ScriptDocument opened = ScriptDocument.open(f, read("run.sh"), store);
		assertEquals("echo $1\n", opened.body());
		assertEquals("a b", opened.arguments());
		assertEquals("/tmp/out", opened.stdOut());
	}

	@Test
	public void nothingIsKeptForAScriptWithoutSettings() throws Exception {
		RunSettingsStore store = store();
		FileSource f = file("plain.sh", "");
		new ScriptDocument("echo hi\n", RunSettings.NONE).save(f, store);
		assertEquals(0, store.size());
		// not even a file
		assertFalse(store.getFile().exists());
	}

	@Test
	public void clearedSettingsAreRemovedWhenTheScriptIsSaved() throws Exception {
		// arguments, save; open, clear them, save: nothing is left of them
		RunSettingsStore store = store();
		FileSource f = file("args.sh", "");
		new ScriptDocument("echo $1\n", new RunSettings("first", "", "", "")).save(f, store);
		assertEquals(1, store.size());

		ScriptDocument opened = ScriptDocument.open(f, read("args.sh"), store);
		assertEquals("first", opened.arguments());
		new ScriptDocument(opened.body(), RunSettings.NONE).save(f, store);
		assertEquals(0, store.size());
		assertEquals(RunSettings.NONE, ScriptDocument.open(f, read("args.sh"), store).settings());
	}

	@Test
	public void savingAScriptOfAnEarlierVersionMovesItsSettingsLinesToTheStore() throws Exception {
		RunSettingsStore store = store();
		FileSource f = file("old.sh", "#BjlIdeScriptArgs=x y\n#BjlIdeScriptOut=/tmp/o\necho $1\n");
		ScriptDocument opened = ScriptDocument.open(f, read("old.sh"), store);
		assertTrue(opened.hasLegacySettings());
		assertEquals("echo $1\n", opened.body());
		assertEquals(new RunSettings("x y", "", "/tmp/o", ""), opened.settings());

		opened.save(f, store);
		assertEquals("echo $1\n", read("old.sh"));
		assertEquals(new RunSettings("x y", "", "/tmp/o", ""), store.get(f));
		assertFalse(ScriptDocument.open(f, read("old.sh"), store).hasLegacySettings());
	}

	@Test
	public void settingsLinesInTheFileWinOverTheStore() throws Exception {
		// (an earlier version saved it again since)
		RunSettingsStore store = store();
		FileSource f = file("both.sh", "#BjlIdeScriptArgs=from-file\necho\n");
		store.put(f, new RunSettings("from-store", "", "", ""));
		assertEquals("from-file", ScriptDocument.open(f, read("both.sh"), store).arguments());
	}

	@Test
	public void sameCodeAndSettingsAreEqual() {
		ScriptDocument fromLines = ScriptDocument.parse("#BjlIdeScriptArgs=a\necho\n");
		assertEquals(new ScriptDocument("echo\n", new RunSettings("a", null, " ", "")), fromLines);
		assertFalse(fromLines.equals(new ScriptDocument("echo\n", RunSettings.NONE)));
	}
}
