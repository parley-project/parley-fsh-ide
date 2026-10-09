package us.bringardner.fsh.ide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.fife.com.swabunga.spell.engine.SpellDictionary;
import org.fife.ui.rsyntaxtextarea.spell.SpellingParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import us.bringardner.fsh.ide.core.SpellChecking;

/** The Swing editor's spelling shares the user's words with the JavaFX IDE (~/.fsh-ide/words.txt). */
public class TestSwingSpelling {

	@TempDir
	static Path home;

	@BeforeAll
	public static void setUp() throws Exception {
		System.setProperty("java.awt.headless", "true");
		System.setProperty("user.home", home.toString());
		// a word added earlier, say from the JavaFX IDE
		Path words = home.resolve(".fsh-ide").resolve("words.txt");
		Files.createDirectories(words.getParent());
		Files.writeString(words, "fshide\n");
	}

	@Test
	public void readsTheUsersWords() throws Exception {
		SpellDictionary dict = SpellChecking.english().get(60, TimeUnit.SECONDS);
		assertTrue(dict.isCorrect("fshide"));
	}

	@Test
	public void addsToTheUsersWords() throws Exception {
		SpellDictionary dict = SpellChecking.english().get(60, TimeUnit.SECONDS);
		SpellingParser parser = FshIDETextArea.spellingParser(dict);
		// with a user dictionary, the parser offers "Add to dictionary", and adds there
		assertEquals(SpellChecking.userWordsFile(), parser.getUserDictionary());
		assertTrue(parser.getAllowAdd());

		// what the "Add to dictionary" link does: add to the user dictionary
		java.lang.reflect.Field sc = SpellingParser.class.getDeclaredField("sc");
		sc.setAccessible(true);
		Object checker = sc.get(parser);
		checker.getClass().getMethod("addToDictionary", String.class).invoke(checker, "wroldwide");
		assertTrue(Files.readString(SpellChecking.userWordsFile().toPath()).contains("wroldwide"));
		assertTrue(Files.readString(SpellChecking.userWordsFile().toPath()).contains("fshide"));
	}
}
