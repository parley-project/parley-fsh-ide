package us.bringardner.fsh.ide.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.fife.com.swabunga.spell.engine.SpellDictionary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The blocks an editor can fold, and spell checking comments and quoted text. */
public class TestFoldingAndSpelling {

	private static String regions(String script) {
		return FoldRegions.find(script).stream().map(Object::toString).collect(Collectors.joining(" "));
	}

	@Test
	public void blocks() {
		String script = String.join("\n",
				"for i in 1 2; do",            // 0
				"  if [ $i = 1 ]; then",       // 1
				"    echo one",                 // 2
				"  elif true; then",            // 3
				"    echo for while done",      // 4  words, not keywords
				"  else",                       // 5
				"    :",                        // 6
				"  fi",                         // 7
				"done",                         // 8
				"case $x in",                   // 9
				"  a) echo a ;;",               // 10
				"esac",                         // 11
				"greet() {",                    // 12
				"  # if this were code, it would open a block",  // 13
				"  echo \"while { here\"",      // 14
				"}",                            // 15
				"function other {",             // 16
				"  while read l",               // 17
				"  do echo $l; done",           // 18
				"}");                           // 19
		assertEquals("0-8 1-7 9-11 12-15 16-19 17-18", regions(script));
	}

	@Test
	public void oneLinersAndUnfinishedBlocksDontFold() {
		assertEquals("", regions("if true; then echo x; fi\nfor i in 1; do :; done"));
		assertEquals("", regions("while true\ndo\n  echo still typing"));
		assertEquals("", regions(""));
	}

	@Test
	public void checksCommentsAndQuotedText() {
		Set<String> known = Set.of("this", "is", "a", "comment", "hello", "world");
		String script = "# this is a commnet\necho \"helo $USER world\" --verbos /usr/lcoal 'wrold' NASA camelCase\nmisspeled=1\n";
		List<String> words = SpellChecking.misspelled(script, known::contains).stream()
				.map(r->script.substring(r[0], r[1])).collect(Collectors.toList());
		// code (the option, the path, the variable name) isn't checked; nor are acronyms and camelCase
		assertEquals(List.of("commnet", "helo", "wrold"), words);
	}

	@Test
	public void theEnglishDictionary(@TempDir Path home) throws Exception {
		String was = System.getProperty("user.home");
		System.setProperty("user.home", home.toString());
		try {
			SpellDictionary dict = SpellChecking.english().get(60, TimeUnit.SECONDS);
			assertTrue(dict.isCorrect("hello"));
			assertTrue(dict.isCorrect("directory"));
			assertFalse(dict.isCorrect("wrold"));
			assertTrue(SpellChecking.suggestions(dict, "wrold", 5).contains("world"), SpellChecking.suggestions(dict, "wrold", 5).toString());

			SpellChecking.addUserWord(dict, "fshide");
			assertTrue(dict.isCorrect("fshide"));
			assertEquals(List.of("fshide"), Files.readAllLines(SpellChecking.userWordsFile().toPath()));
		} finally {
			System.setProperty("user.home", was);
		}
	}
}
