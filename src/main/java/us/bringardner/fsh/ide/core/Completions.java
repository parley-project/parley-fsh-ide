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
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What can be typed to finish the word before the caret: templates (if, for, case ...) and
 * the variables the script sets. For any UI's autocompletion.
 */
public final class Completions {

	/** One choice: what's shown, and what it inserts. */
	public static final class Completion {
		public final String label;
		public final String description;
		/** A template's code (with ${fields}), or plain text for a variable. */
		public final String code;
		public final boolean template;

		Completion(String label, String description, String code, boolean template) {
			this.label = label;
			this.description = description;
			this.code = code;
			this.template = template;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	// a variable assigned (name=), looped over (for name in), read (read [-r] a b) or declared
	private static final Pattern ASSIGNED = Pattern.compile("(?m)(?:^|[\\s;(])([A-Za-z_][A-Za-z0-9_]*)(?:\\[[^\\]]*\\])?\\+?=");
	private static final Pattern FOR = Pattern.compile("\\b(?:for|select)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s+in\\b");
	// read's options that take an argument (-a -d -i -n -N -p -t -u) take it; the names stay on the line
	private static final Pattern READ = Pattern.compile("(?m)\\bread\\b((?:[ \\t]+-[A-Za-z]*[adinNptu][ \\t]+(?:'[^'\\n]*'|\"[^\"\\n]*\"|\\S+)|[ \\t]+-[A-Za-z]+)*)((?:[ \\t]+[A-Za-z_][A-Za-z0-9_]*)+)");
	private static final Pattern DECLARED = Pattern.compile("(?m)\\b(?:local|export|declare|readonly|typeset)\\b(?:[ \\t]+-[A-Za-z]+)*((?:[ \\t]+[A-Za-z_][A-Za-z0-9_]*)+)");

	private Completions() {
	}

	/** Whether c can be part of a word to complete (as the Swing IDE: letters, digits, _ . # $). */
	public static boolean isWordChar(char c) {
		return Character.isLetterOrDigit(c) || c == '_' || c == '.' || c == '#' || c == '$';
	}

	/** The word before caret in text ("" if there's none). */
	public static String prefix(String text, int caret) {
		int start = caret;
		while( start > 0 && isWordChar(text.charAt(start-1))) {
			start--;
		}
		return text.substring(start, caret);
	}

	/** The variables script sets, by name. */
	public static List<String> variables(String script) {
		TreeSet<String> names = new TreeSet<>();
		Matcher m = ASSIGNED.matcher(script);
		while( m.find()) {
			names.add(m.group(1));
		}
		m = FOR.matcher(script);
		while( m.find()) {
			names.add(m.group(1));
		}
		for(Pattern p : new Pattern[] {READ, DECLARED}) {
			m = p.matcher(script);
			while( m.find()) {
				String list = m.group(m.groupCount());
				for(String n : list.trim().split("\\s+")) {
					if( !n.isEmpty()) {
						names.add(n);
					}
				}
			}
		}
		return new ArrayList<>(names);
	}

	/**
	 * The completions for prefix: with "$", the script's variables ("$name"); otherwise the
	 * templates whose name starts with it (ignoring case), then the variables. An empty prefix
	 * offers everything.
	 */
	public static List<Completion> matching(String prefix, Collection<Template> templates, String script) {
		List<Completion> ret = new ArrayList<>();
		String p = prefix.toLowerCase(Locale.ROOT);
		boolean dollar = p.startsWith("$");
		if( !dollar ) {
			for(Template t : templates) {
				String name = t.getName() == null ? "" : t.getName();
				if( name.toLowerCase(Locale.ROOT).startsWith(p)) {
					String d = t.getDescription() == null || t.getDescription().isBlank() ? name : t.getDescription();
					String code = t.getCode() == null || t.getCode().isBlank() ? name : t.getCode();
					ret.add(new Completion(name, d, code, true));
				}
			}
		}
		String bare = dollar ? p.substring(1).replace("{", "") : p;
		for(String v : variables(script)) {
			if( v.toLowerCase(Locale.ROOT).startsWith(bare) && !(dollar ? "$"+v : v).equals(prefix)) {
				ret.add(new Completion(dollar ? "$"+v : v, "variable", (dollar ? "$$" : "")+v, false));
			}
		}
		return ret;
	}
}
