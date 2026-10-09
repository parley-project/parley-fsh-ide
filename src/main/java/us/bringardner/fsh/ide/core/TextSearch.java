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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Find and replace in a script's text, for any UI: plain text or a regular expression,
 * matching case or not, whole words only, wrapping round the end or not.
 */
public final class TextSearch {

	/** How to search. */
	public static final class Options {
		public final boolean matchCase;
		public final boolean wholeWords;
		public final boolean regex;
		public final boolean wrap;

		public Options(boolean matchCase, boolean wholeWords, boolean regex, boolean wrap) {
			this.matchCase = matchCase;
			this.wholeWords = wholeWords;
			this.regex = regex;
			this.wrap = wrap;
		}
	}

	/** Where a match is: [start, end). */
	public static final class Match {
		public final int start;
		public final int end;
		/** True if the search went round the end (or the start, going back) to find it. */
		public final boolean wrapped;

		Match(int start, int end, boolean wrapped) {
			this.start = start;
			this.end = end;
			this.wrapped = wrapped;
		}

		@Override
		public String toString() {
			return "["+start+","+end+")"+(wrapped ? " wrapped" : "");
		}
	}

	private TextSearch() {
	}

	/** The pattern for query; an empty query matches nothing. @throws IllegalArgumentException for a bad regular expression */
	static Pattern pattern(String query, Options o) {
		String p = o.regex ? query : Pattern.quote(query);
		if( o.wholeWords ) {
			p = "(?<![\\w])(?:"+p+")(?![\\w])";
		}
		int flags = Pattern.MULTILINE | (o.matchCase ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
		return Pattern.compile(p, flags);
	}

	/**
	 * The next match of query in text, searching from from (forward) or before from (backward).
	 * Empty matches (a regular expression like "x*") are skipped.
	 * @return the match, or null if there's none
	 */
	public static Match find(String text, String query, int from, boolean forward, Options o) {
		if( query == null || query.isEmpty()) {
			return null;
		}
		Matcher m = pattern(query, o).matcher(text);
		from = Math.max(0, Math.min(from, text.length()));
		if( forward ) {
			Match r = firstFrom(m, from, text.length());
			if( r == null && o.wrap && from > 0 ) {
				r = firstFrom(m, 0, from);
				return r == null ? null : new Match(r.start, r.end, true);
			}
			return r;
		}
		Match r = lastBefore(m, 0, from);
		if( r == null && o.wrap && from < text.length()) {
			r = lastBefore(m, from, text.length());
			return r == null ? null : new Match(r.start, r.end, true);
		}
		return r;
	}

	private static Match firstFrom(Matcher m, int from, int limit) {
		int at = from;
		while( at <= limit && m.find(at)) {
			if( m.end() > limit ) {
				return null;
			}
			if( m.end() > m.start()) {
				return new Match(m.start(), m.end(), false);
			}
			at = m.end()+1;
		}
		return null;
	}

	private static Match lastBefore(Matcher m, int from, int limit) {
		Match last = null;
		int at = from;
		while( at < limit && m.find(at)) {
			if( m.end() > limit ) {
				break;
			}
			if( m.end() > m.start()) {
				last = new Match(m.start(), m.end(), false);
				at = m.end();
			} else {
				at = m.end()+1;
			}
		}
		return last;
	}

	/** Every (non-empty) match, in order. */
	public static java.util.List<Match> findAll(String text, String query, Options o) {
		java.util.List<Match> ret = new java.util.ArrayList<>();
		if( query == null || query.isEmpty()) {
			return ret;
		}
		Matcher m = pattern(query, o).matcher(text);
		while( m.find()) {
			if( m.end() > m.start()) {
				ret.add(new Match(m.start(), m.end(), false));
			}
		}
		return ret;
	}

	/** How many (non-empty) matches there are. */
	public static int count(String text, String query, Options o) {
		if( query == null || query.isEmpty()) {
			return 0;
		}
		int n = 0;
		Matcher m = pattern(query, o).matcher(text);
		while( m.find()) {
			if( m.end() > m.start()) {
				n++;
			}
		}
		return n;
	}

	/**
	 * What replaces the matched text: replacement as it is, or with $1 ... filled in for a
	 * regular expression.
	 */
	public static String replacementFor(String matched, String query, String replacement, Options o) {
		if( !o.regex ) {
			return replacement;
		}
		Matcher m = pattern(query, o).matcher(matched);
		if( !m.matches()) {
			return replacement;
		}
		StringBuilder sb = new StringBuilder();
		m.appendReplacement(sb, replacement);
		return sb.toString();
	}

	/** The result of replacing every match. */
	public static final class Replaced {
		public final String text;
		public final int count;

		Replaced(String text, int count) {
			this.text = text;
			this.count = count;
		}
	}

	/** Replaces every (non-empty) match of query in text. */
	public static Replaced replaceAll(String text, String query, String replacement, Options o) {
		if( query == null || query.isEmpty()) {
			return new Replaced(text, 0);
		}
		Matcher m = pattern(query, o).matcher(text);
		StringBuilder sb = new StringBuilder();
		int n = 0;
		while( m.find()) {
			if( m.end() == m.start()) {
				continue;
			}
			m.appendReplacement(sb, o.regex ? replacement : Matcher.quoteReplacement(replacement));
			n++;
		}
		m.appendTail(sb);
		return new Replaced(sb.toString(), n);
	}
}
