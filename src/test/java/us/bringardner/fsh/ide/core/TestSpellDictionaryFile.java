package us.bringardner.fsh.ide.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.fife.com.swabunga.spell.engine.GenericSpellDictionary;
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
}
