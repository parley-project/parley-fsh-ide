/**
 *	Copyright 2026 Tony Bringardner
 *
 *	Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. 
 *	You may obtain a copy of the License at
 *
 *	http://www.apache.org/licenses/LICENSE-2.0
 *
 *	Unless required by applicable law or agreed to in writing, software distributed under the License is distributed 
 *	on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for 
 *	the specific language governing permissions and limitations under the License.
 */
package us.bringardner.fsh.ide.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

// Jazzy, the spelling engine inside RSyntaxTextArea's spellchecker: no UI of its own
import org.fife.com.swabunga.spell.engine.SpellDictionary;
import org.fife.com.swabunga.spell.engine.SpellDictionaryHashMap;
import org.fife.com.swabunga.spell.engine.Word;

/**
 * Spell checking a script's comments and quoted text, for either IDE: the English dictionary
 * (read once, in the background, and shared), the user's own words, the words to check, and
 * suggestions.
 */
public final class SpellChecking {

	/** The parts of the dictionary file used (as the Swing IDE always has). */
	private static final Set<String> PARTS = Set.of("eng_com.dic", "color.dic", "labeled.dic", "center.dic", "ize.dic", "yze.dic");
	private static CompletableFuture<SpellDictionary> english;

	// comments, and text in quotes (as the editor colours them)
	private static final Pattern CHECKED = Pattern.compile(
			"(?m)((?:^|(?<=[\\s;(|&]))#[^\\n]*)|(\"(?:\\\\.|[^\"\\\\])*\"?)|('[^']*'?)");
	// a plain word: not part of a variable, option, path, file name or other word
	private static final Pattern WORD = Pattern.compile("(?<![\\w$/.\\-{@])[A-Za-z]+(?:'[A-Za-z]+)?(?![\\w/.\\-}@])");

	private SpellChecking() {
	}

	/** The English dictionary, with the user's words; read in the background the first time it's asked for. */
	public static synchronized CompletableFuture<SpellDictionary> english() {
		if( english == null ) {
			english = CompletableFuture.supplyAsync(()->{
				try(InputStream in = SpellChecking.class.getResourceAsStream("/english_dic.zip")) {
					if( in == null ) {
						return null;
					}
					SpellDictionary dict = read(in);
					for(String w : userWords()) {
						dict.addWord(w);
					}
					return dict;
				} catch (IOException e) {
					return null;
				}
			});
		}
		return english;
	}

	static SpellDictionaryHashMap read(InputStream zip) throws IOException {
		SpellDictionaryHashMap dict = null;
		try(ZipInputStream zf = new ZipInputStream(zip)) {
			for(ZipEntry e = zf.getNextEntry(); e != null; e = zf.getNextEntry()) {
				if( PARTS.contains(e.getName())) {
					BufferedReader r = new BufferedReader(new InputStreamReader(zf, StandardCharsets.ISO_8859_1));
					if( dict == null ) {
						dict = new SpellDictionaryHashMap(r);
					} else {
						dict.addDictionary(r);
					}
				}
			}
		}
		return dict != null ? dict : new SpellDictionaryHashMap();
	}

	/** Where the user's own words are kept: ~/.fsh-ide/words.txt, one a line. */
	public static File userWordsFile() {
		return new File(new File(System.getProperty("user.home"), Configuration.DIR), "words.txt");
	}

	static List<String> userWords() {
		File f = userWordsFile();
		List<String> ret = new ArrayList<>();
		if( f.isFile()) {
			try {
				for(String w : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
					if( !w.isBlank()) {
						ret.add(w.trim());
					}
				}
			} catch (IOException e) {
				// none, then
			}
		}
		return ret;
	}

	/** Adds word to dict and to the user's words, so it's known from now on. */
	public static void addUserWord(SpellDictionary dict, String word) throws IOException {
		dict.addWord(word);
		File f = userWordsFile();
		f.getParentFile().mkdirs();
		Files.writeString(f.toPath(), word+"\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
	}

	/** True if word is worth checking: not an acronym (ALLCAPS) or a camelCase name. */
	static boolean checkable(String word) {
		if( word.length() < 2 ) {
			return false;
		}
		for(int i=1; i < word.length(); i++) {
			if( Character.isUpperCase(word.charAt(i))) {
				return false;
			}
		}
		return true;
	}

	/**
	 * The misspelled words in the comments and quoted text of script, each [start, end).
	 * @param isCorrect says whether a word is spelled right (a dictionary's isCorrect, say)
	 */
	public static List<int[]> misspelled(String script, Predicate<String> isCorrect) {
		List<int[]> ret = new ArrayList<>();
		Matcher part = CHECKED.matcher(script);
		while( part.find()) {
			String text = part.group();
			Matcher w = WORD.matcher(text);
			while( w.find()) {
				String word = w.group();
				if( checkable(word) && !isCorrect.test(word) && !isCorrect.test(word.toLowerCase())) {
					ret.add(new int[] {part.start()+w.start(), part.start()+w.end()});
				}
			}
		}
		return ret;
	}

	/** Up to max suggested spellings for word, best first. */
	public static List<String> suggestions(SpellDictionary dict, String word, int max) {
		List<String> ret = new ArrayList<>();
		for(Word w : dict.getSuggestions(word, 2)) {
			if( !ret.contains(w.getWord())) {
				ret.add(w.getWord());
			}
			if( ret.size() >= max ) {
				break;
			}
		}
		return ret;
	}
}
