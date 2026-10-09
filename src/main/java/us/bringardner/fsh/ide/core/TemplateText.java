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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Expands a template's code for insertion: "${name}" becomes the text "name", a field the
 * user fills in; "${cursor}" is where the caret goes; "$$" is a "$". (The same syntax the
 * Swing IDE's autocompletion uses.)
 */
public final class TemplateText {

	/** A template's code, expanded. */
	public static final class Expansion {
		public final String text;
		/** The fields, in order, each [start, end) in text. */
		public final List<int[]> fields;
		/** Where the caret goes, or -1 (then: on the first field, else after the text). */
		public final int cursor;

		Expansion(String text, List<int[]> fields, int cursor) {
			this.text = text;
			this.fields = Collections.unmodifiableList(fields);
			this.cursor = cursor;
		}
	}

	private TemplateText() {
	}

	public static Expansion expand(String code) {
		StringBuilder out = new StringBuilder();
		List<int[]> fields = new ArrayList<>();
		int cursor = -1;
		int i = 0;
		while( i < code.length()) {
			if( code.startsWith("$$", i)) {
				out.append('$');
				i += 2;
			} else if( code.startsWith("${", i)) {
				int close = code.indexOf('}', i+2);
				if( close < 0 ) {
					out.append(code, i, code.length());
					break;
				}
				String name = code.substring(i+2, close);
				if( name.equals("cursor")) {
					cursor = out.length();
				} else {
					fields.add(new int[] {out.length(), out.length()+name.length()});
					out.append(name);
				}
				i = close+1;
			} else {
				out.append(code.charAt(i++));
			}
		}
		return new Expansion(out.toString(), fields, cursor);
	}
}
