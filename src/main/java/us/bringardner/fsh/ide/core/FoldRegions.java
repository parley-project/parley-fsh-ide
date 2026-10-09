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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * The blocks of a script that an editor can fold: if ... fi, for/while/until/select ... done,
 * case ... esac and { ... }. Found by a quick scan (not fsh's parser, so a half-typed script
 * still folds): a keyword counts only where a command starts, and comments and quoted text
 * are skipped. Lines are 0-based.
 */
public final class FoldRegions {

	/** A block: its first line (where it's opened) and its last (where it's closed). */
	public static final class Region {
		public final int start;
		public final int end;

		Region(int start, int end) {
			this.start = start;
			this.end = end;
		}

		@Override
		public String toString() {
			return start+"-"+end;
		}
	}

	private FoldRegions() {
	}

	private static String closerFor(String opener) {
		switch (opener) {
		case "if": return "fi";
		case "for": case "while": case "until": case "select": return "done";
		case "case": return "esac";
		case "{": return "}";
		default: return null;
		}
	}

	/** The blocks that span more than one line, in the order they start. */
	public static List<Region> find(String text) {
		List<Region> ret = new ArrayList<>();
		// each open block: its closer and the line it started on
		Deque<Object[]> open = new ArrayDeque<>();
		String[] lines = text.split("\n", -1);
		boolean inSingle = false;
		boolean inDouble = false;
		for(int ln=0; ln < lines.length; ln++) {
			String line = lines[ln];
			boolean commandStart = !inSingle && !inDouble;
			int i = 0;
			while( i < line.length()) {
				char c = line.charAt(i);
				if( inSingle ) {
					if( c == '\'' ) {
						inSingle = false;
					}
					i++;
					continue;
				}
				if( inDouble ) {
					if( c == '\\' ) {
						i += 2;
						continue;
					}
					if( c == '"' ) {
						inDouble = false;
					}
					i++;
					continue;
				}
				if( c == '\'' ) {
					inSingle = true;
					commandStart = false;
					i++;
				} else if( c == '"' ) {
					inDouble = true;
					commandStart = false;
					i++;
				} else if( c == '#' && (i == 0 || Character.isWhitespace(line.charAt(i-1)) || line.charAt(i-1) == ';')) {
					break;
				} else if( c == '\\' ) {
					i += 2;
					commandStart = false;
				} else if( c == ';' || c == '&' || c == '|' || c == '(' || c == ')' || c == '!' ) {
					// a new command follows (a "()" after a function name too)
					commandStart = true;
					i++;
				} else if( Character.isWhitespace(c)) {
					i++;
				} else {
					int j = i;
					while( j < line.length() && !Character.isWhitespace(line.charAt(j))
							&& ";&|()<>\"'".indexOf(line.charAt(j)) < 0 ) {
						j++;
					}
					String word = line.substring(i, j);
					if( word.equals("{") && !commandStart ) {
						// a { on its own always opens a block (after "function name", say)
						open.push(new Object[] {"}", ln});
						commandStart = true;
					} else if( commandStart ) {
						String closer = closerFor(word);
						if( closer != null ) {
							open.push(new Object[] {closer, ln});
						} else if( !open.isEmpty() && word.equals(open.peek()[0])) {
							int start = (Integer) open.pop()[1];
							if( ln > start ) {
								ret.add(new Region(start, ln));
							}
						}
						// after these, another command starts; otherwise this one has started
						commandStart = word.equals("then") || word.equals("do") || word.equals("else")
								|| word.equals("elif") || word.equals("{") || word.equals("time")
								|| word.equals("!") || closer != null && !word.equals("for")
								&& !word.equals("case") && !word.equals("select");
					}
					i = Math.max(j, i+1);
				}
			}
		}
		ret.sort((a, b)->a.start != b.start ? Integer.compare(a.start, b.start) : Integer.compare(b.end, a.end));
		return ret;
	}
}
