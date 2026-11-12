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
package us.bringardner.fsh.ide;

import java.awt.Color;
import java.util.List;

import javax.swing.JTextArea;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Position;

import org.fife.ui.rtextarea.RTextArea;

import us.bringardner.fsh.ide.core.TextSearch;

/**
 * Find and replace in a Swing text area, for FindDialog: the matching is
 * core.TextSearch's. A match is selected in the text area. A scope (Selected Lines) limits
 * the search to the lines that were selected, and follows the text as it changes (it is shown with
 * a highlight).
 */
final class TextAreaFind {

	private static final DefaultHighlighter.DefaultHighlightPainter SCOPE_PAINTER =
			new DefaultHighlighter.DefaultHighlightPainter(Color.lightGray);

	private final JTextArea text;
	/** the highlight that shows the lines searched, or null for all the text */
	private Object scope;
	/**
	 * Where the scope starts: just after the character before it (null: the start of the text). A
	 * position at the start itself would move on when text is put in there, as a replacement is.
	 */
	private Position scopeAfter;
	/** where the scope ends */
	private Position scopeEnd;
	/** what the last operation found: "3 matches", "Not found", "Replaced 2" ... */
	private String result = "";

	TextAreaFind(JTextArea text) {
		this.text = text;
	}

	JTextArea textArea() {
		return text;
	}

	String result() {
		return result;
	}

	boolean hasScope() {
		return scope != null;
	}

	/**
	 * Selected Lines: the lines the selection is on, from the start of the first to the end of the
	 * last, highlighted; else none (all the text).
	 */
	void setScope(boolean selectedLines) {
		if( scope != null ) {
			text.getHighlighter().removeHighlight(scope);
			scope = null;
			scopeAfter = scopeEnd = null;
		}
		if( selectedLines ) {
			try {
				int start = text.getLineStartOffset(text.getLineOfOffset(text.getSelectionStart()));
				int end = text.getLineEndOffset(text.getLineOfOffset(text.getSelectionEnd()));
				scopeAfter = start == 0 ? null : text.getDocument().createPosition(start-1);
				scopeEnd = text.getDocument().createPosition(end);
				scope = text.getHighlighter().addHighlight(start, end, SCOPE_PAINTER);
			} catch (BadLocationException e) {
				// (the selection is in the text)
			}
		}
	}

	/** [start, end) of the text that is searched */
	int[] range() {
		int length = text.getDocument().getLength();
		if( scopeEnd != null ) {
			int end = Math.min(scopeEnd.getOffset(), length);
			int start = Math.min(scopeAfter == null ? 0 : scopeAfter.getOffset()+1, end);
			return new int[] {start, end};
		}
		return new int[] {0, length};
	}

	/** the highlight over the scope again, after a replacement */
	private void showScope() {
		if( scope != null ) {
			int[] r = range();
			try {
				text.getHighlighter().changeHighlight(scope, r[0], r[1]);
			} catch (BadLocationException e) {
				// (the range is in the text)
			}
		}
	}

	private String rangeText(int[] r) throws BadLocationException {
		return text.getDocument().getText(r[0], r[1]-r[0]);
	}

	/** selects [start, end), shown even while the dialog has the focus */
	private void select(int start, int end) {
		text.select(start, end);
		text.getCaret().setSelectionVisible(true);
	}

	/**
	 * Selects the next match after the selection, or (forward false) the one before it.
	 * @return true if one was found
	 */
	boolean find(String query, boolean forward, TextSearch.Options o) {
		result = "";
		if( query == null || query.isEmpty()) {
			return false;
		}
		try {
			int[] r = range();
			String in = rangeText(r);
			int from = (forward ? text.getSelectionEnd() : text.getSelectionStart()) - r[0];
			TextSearch.Match m = TextSearch.find(in, query, from, forward, o);
			if( m == null ) {
				result = "Not found";
				return false;
			}
			select(r[0]+m.start, r[0]+m.end);
			int n = TextSearch.count(in, query, o);
			result = (m.wrapped ? "Wrapped. " : "")+n+(n == 1 ? " match" : " matches");
			return true;
		} catch (IllegalArgumentException e) {
			result = "Bad regular expression";
		} catch (BadLocationException e) {
			// (the range is in the text)
		}
		return false;
	}

	/**
	 * Replaces the selection if it is a match (and then, andFind, finds the next one); if it isn't,
	 * finds the next match, to be replaced next time.
	 */
	void replace(String query, String with, boolean andFind, TextSearch.Options o) {
		result = "";
		if( query == null || query.isEmpty()) {
			return;
		}
		try {
			int[] r = range();
			String in = rangeText(r);
			int start = text.getSelectionStart();
			int end = text.getSelectionEnd();
			TextSearch.Match m = start == end || start < r[0] || end > r[1] ? null
					: TextSearch.find(in, query, start-r[0], true, o);
			if( m != null && r[0]+m.start == start && r[0]+m.end == end ) {
				String replacement = TextSearch.replacementFor(in.substring(m.start, m.end), query, with, o);
				text.replaceRange(replacement, start, end);
				showScope();
				select(start, start+replacement.length());
				if( !andFind ) {
					return;
				}
			}
			find(query, true, o);
		} catch (IllegalArgumentException e) {
			result = "Bad regular expression";
		} catch (BadLocationException e) {
			// (the range is in the text)
		}
	}

	/**
	 * Replaces every match (in the scope, if there is one), from the last one back, so the text
	 * before each, and its breakpoints, stays where it is; undone as one edit.
	 * @return how many were replaced
	 */
	int replaceAll(String query, String with, TextSearch.Options o) {
		result = "";
		if( query == null || query.isEmpty()) {
			return 0;
		}
		RTextArea atomic = text instanceof RTextArea ? (RTextArea) text : null;
		try {
			int[] r = range();
			String in = rangeText(r);
			List<TextSearch.Match> all = TextSearch.findAll(in, query, o);
			if( atomic != null ) {
				atomic.beginAtomicEdit();
			}
			try {
				for(int i = all.size()-1; i >= 0; i--) {
					TextSearch.Match m = all.get(i);
					text.replaceRange(TextSearch.replacementFor(in.substring(m.start, m.end), query, with, o),
							r[0]+m.start, r[0]+m.end);
				}
			} finally {
				if( atomic != null ) {
					atomic.endAtomicEdit();
				}
			}
			showScope();
			result = "Replaced "+all.size();
			return all.size();
		} catch (IllegalArgumentException e) {
			result = "Bad regular expression";
		} catch (BadLocationException e) {
			// (the range is in the text)
		}
		return 0;
	}
}
