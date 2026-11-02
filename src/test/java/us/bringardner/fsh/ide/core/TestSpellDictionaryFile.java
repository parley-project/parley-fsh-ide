package us.bringardner.fsh.ide.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.fife.com.swabunga.spell.engine.GenericSpellDictionary;
import org.fife.com.swabunga.spell.engine.GenericTransformator;
import org.fife.com.swabunga.spell.engine.PropertyConfiguration;
import org.fife.com.swabunga.spell.engine.SpellDictionaryDichoDisk;
import org.fife.com.swabunga.spell.engine.SpellDictionaryDisk;
import org.fife.com.swabunga.spell.engine.SpellDictionaryHashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The word list a SpellDictionaryHashMap reads is closed at once: Windows will not delete a file that
 * is open, and the Jazzy original left it open until the reader was garbage collected (this project
 * has its own copy of the class). No garbage collection here, on purpose.
 */
public class TestSpellDictionaryFile {

	@TempDir
	Path dir;

	@Test
	public void theWordListIsClosedAfterItIsRead() throws Exception {
		File words = dir.resolve("words.txt").toFile();
		Files.write(words.toPath(), List.of("zorp", "quux"), StandardCharsets.UTF_8);
		SpellDictionaryHashMap dict = new SpellDictionaryHashMap(words);
		assertTrue(dict.isCorrect("zorp"));
		assertTrue(dict.isCorrect("quux"));
		dict.addDictionary(words);
		assertTrue(words.delete(), "the file is still open");
	}

	@Test
	public void addedWordsAreAppendedToTheFile() throws Exception {
		File words = dir.resolve("words.txt").toFile();
		Files.write(words.toPath(), List.of("zorp"), StandardCharsets.UTF_8);
		SpellDictionaryHashMap dict = new SpellDictionaryHashMap(words);
		assertTrue(dict.addWord("frobnicate"));
		assertTrue(dict.isCorrect("frobnicate"));
		assertEquals(List.of("zorp", "frobnicate"), Files.readAllLines(words.toPath(), StandardCharsets.UTF_8));
		assertTrue(words.delete(), "the file is still open");
	}

	@Test
	public void theGenericDictionaryClosesItsWordListToo() throws Exception {
		File words = dir.resolve("generic.txt").toFile();
		Files.write(words.toPath(), List.of("zorp", "quux"), StandardCharsets.UTF_8);
		GenericSpellDictionary dict = new GenericSpellDictionary(words);
		assertTrue(dict.isCorrect("zorp"));
		assertTrue(dict.addWord("frobnicate"));
		assertEquals(List.of("zorp", "quux", "frobnicate"), Files.readAllLines(words.toPath(), StandardCharsets.UTF_8));
		assertTrue(words.delete(), "the file is still open");
	}

	@Test
	public void thePhoneticFileIsClosed() throws Exception {
		File phonetic = dir.resolve("phonetic.txt").toFile();
		Files.write(phonetic.toPath(), List.of("version english", "alphabet abcdefghijklmnopqrstuvwxyz", "PH 1"), StandardCharsets.UTF_8);
		new GenericTransformator(phonetic);
		assertTrue(phonetic.delete(), "the file is still open");
		Files.write(phonetic.toPath(), List.of("version english", "alphabet abcdefghijklmnopqrstuvwxyz"), StandardCharsets.UTF_8);
		new GenericTransformator(phonetic, "UTF-8");
		assertTrue(phonetic.delete(), "the file is still open");
	}

	@Test
	public void theDichoDiskFileCanBeClosed() throws Exception {
		File words = dir.resolve("sorted.txt").toFile();
		Files.write(words.toPath(), List.of("apple", "banana", "cherry"), StandardCharsets.UTF_8);
		SpellDictionaryDichoDisk dict = new SpellDictionaryDichoDisk(words);
		// (the file has to be sorted by phonetic code to be searched; here it is only opened)
		dict.close();
		assertTrue(words.delete(), "the file is still open");
	}

	@Test
	public void theOnDiskDictionaryLeavesNoFileOpen() throws Exception {
		File base = dir.resolve("disk").toFile();
		File wordsDir = new File(base, "words");
		assertTrue(wordsDir.mkdirs());
		Files.write(new File(wordsDir, "w.txt").toPath(), List.of("apple", "banana", "cherry"), StandardCharsets.UTF_8);
		SpellDictionaryDisk dict = new SpellDictionaryDisk(base, null, true);
		assertTrue(dict.isCorrect("banana"));
		for(File f : new File(base, "db").listFiles()) {
			assertTrue(f.delete(), f + " is still open");
		}
		assertTrue(new File(wordsDir, "w.txt").delete());
	}

	@Test
	public void theConfigurationIsReadAndClosed() {
		assertTrue(new PropertyConfiguration().getInteger(org.fife.com.swabunga.spell.engine.Configuration.SPELL_THRESHOLD) > 0);
	}
}
