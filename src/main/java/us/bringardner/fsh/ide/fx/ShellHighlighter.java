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
package us.bringardner.fsh.ide.fx;

import java.util.Collection;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

/**
 * Colours shell script text: comments, strings, variables and keywords. A quick scan with
 * regular expressions, good enough for colouring (the syntax check uses fsh's parser).
 */
final class ShellHighlighter {

	private static final String KEYWORDS = "if|then|else|elif|fi|for|in|while|until|do|done|case|esac|function"
			+ "|select|return|break|continue|local|export|declare|readonly|time|coproc";

	private static final Pattern PATTERN = Pattern.compile(
			// a # starts a comment at the start of a word, not in $# or ${x#y}
			"(?<COMMENT>(?:^|(?<=[\\s;(|&]))#[^\\n]*)"
			+ "|(?<STRING>\"(?:\\\\.|[^\"\\\\])*\"?|'[^']*'?)"
			+ "|(?<VARIABLE>\\$\\{[^}\\n]*\\}?|\\$[A-Za-z_][A-Za-z0-9_]*|\\$[0-9#?@*$!-])"
			+ "|(?<KEYWORD>\\b(?:" + KEYWORDS + ")\\b)",
			Pattern.MULTILINE);

	private ShellHighlighter() {
	}

	/** The style class for each stretch of text ("comment", "string", "variable", "keyword", or none). */
	static StyleSpans<Collection<String>> compute(String text) {
		Matcher m = PATTERN.matcher(text);
		StyleSpansBuilder<Collection<String>> spans = new StyleSpansBuilder<>();
		int last = 0;
		while( m.find()) {
			String style = m.group("COMMENT") != null ? "comment"
					: m.group("STRING") != null ? "string"
					: m.group("VARIABLE") != null ? "variable"
					: "keyword";
			spans.add(Collections.emptyList(), m.start()-last);
			spans.add(Collections.singleton(style), m.end()-m.start());
			last = m.end();
		}
		spans.add(Collections.emptyList(), text.length()-last);
		return spans.create();
	}
}
