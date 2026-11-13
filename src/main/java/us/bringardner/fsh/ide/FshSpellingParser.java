/**
*	Copyright 2024 Tony Bringardner
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
package us.bringardner.fsh.ide;

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.swing.text.BadLocationException;
import javax.swing.text.Element;

import org.fife.com.swabunga.spell.engine.SpellDictionary;
import org.fife.ui.rsyntaxtextarea.RSyntaxDocument;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.parser.AbstractParser;
import org.fife.ui.rsyntaxtextarea.parser.DefaultParseResult;
import org.fife.ui.rsyntaxtextarea.parser.DefaultParserNotice;
import org.fife.ui.rsyntaxtextarea.parser.ParseResult;
import org.fife.ui.rsyntaxtextarea.parser.ParserNotice;

import us.bringardner.fsh.ide.core.SpellChecking;

/**
 * Spell checking for the editor: the words
 * SpellChecking.misspelled finds in the comments and quoted text, against the one dictionary
 * SpellChecking.english() reads (with the user's words in ~/.fsh-ide/words.txt), underlined; the
 * right-click menu offers suggestions, "Ignore" (until the IDE closes) and "Add to Dictionary".
 */
class FshSpellingParser extends AbstractParser {

	private final RSyntaxTextArea area;
	private final SpellDictionary dictionary;
	private final Set<String> ignored = ConcurrentHashMap.newKeySet();
	private final DefaultParseResult result = new DefaultParseResult(this);
	/** false: nothing is checked (Edit > Check Spelling) */
	private volatile boolean checking = true;
	/** the misspelled words of the last parse, each [start, end) */
	private volatile List<int[]> misspelled = new ArrayList<>();

	FshSpellingParser(RSyntaxTextArea area, SpellDictionary dictionary) {
		this.area = area;
		this.dictionary = dictionary;
	}

	@Override
	public ParseResult parse(RSyntaxDocument doc, String style) {
		result.clearNotices();
		Element root = doc.getDefaultRootElement();
		result.setParsedLines(0, root.getElementCount()-1);
		if( !checking ) {
			misspelled = new ArrayList<>();
			return result;
		}
		List<int[]> found;
		String text;
		try {
			text = doc.getText(0, doc.getLength());
		} catch (BadLocationException e) {
			misspelled = new ArrayList<>();
			return result;
		}
		found = SpellChecking.misspelled(text, w->ignored.contains(w) || dictionary.isCorrect(w));
		for(int[] r : found) {
			final String word = text.substring(r[0], r[1]);
			DefaultParserNotice notice = new DefaultParserNotice(this, "Misspelled: "+word, root.getElementIndex(r[0]), r[0], r[1]-r[0]) {
				@Override
				public Color getColor() {
					return underline();
				}
				@Override
				public String getToolTipText() {
					return null;
				}
			};
			notice.setLevel(ParserNotice.Level.INFO);
			result.addNotice(notice);
		}
		misspelled = found;
		return result;
	}

	/** The squiggle's colour, light or dark. */
	private Color underline() {
		Color bg = area.getBackground();
		boolean dark = bg != null && (bg.getRed()*299 + bg.getGreen()*587 + bg.getBlue()*114)/1000 < 128;
		return dark ? new Color(0xE06C5A) : new Color(0xd32f2f);
	}

	/** Turns checking on or off (the underlines come or go at once). */
	void setChecking(boolean on) {
		checking = on;
		area.forceReparsing(this);
	}

	boolean isChecking() {
		return checking;
	}

	/** The misspelled word at pos, as [start, end), or null. */
	int[] misspelledAt(int pos) {
		for(int[] r : misspelled) {
			if( pos >= r[0] && pos <= r[1] ) {
				return r;
			}
		}
		return null;
	}

	/** Up to max suggested spellings for word, best first. */
	List<String> suggestions(String word, int max) {
		return SpellChecking.suggestions(dictionary, word, max);
	}

	/** Leaves word alone from now on (until the IDE closes). */
	void ignore(String word) {
		ignored.add(word);
		area.forceReparsing(this);
	}

	/** Adds word to the user's dictionary (~/.fsh-ide/words.txt). */
	void addToDictionary(String word) throws IOException {
		SpellChecking.addUserWord(dictionary, word);
		area.forceReparsing(this);
	}
}
