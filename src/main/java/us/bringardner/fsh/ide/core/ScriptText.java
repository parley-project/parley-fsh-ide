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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;


import us.bringardner.fsh.syntax.Ast;
import us.bringardner.parley.files.FileSource;

/** Text helpers for scripts: cleaning pasted text, arguments, reading and writing files. */
public class ScriptText {

	private static final int MAX_STATEMENT_TEXT = 120;

	private ScriptText() {
	}

	/**
	 * text without the invisible characters that come with text copied from web pages and
	 * documents and break scripts: control characters other than tab, newline and carriage
	 * return, zero-width spaces and byte order marks. A non-breaking space becomes a space.
	 * Other characters, including non-ASCII ones, are kept. null stays null.
	 */
	/**
	 * Code from part of a script (a selection), placed on its own lines: blank lines go in front,
	 * so $LINENO, error messages and the debugger give the script's line numbers.
	 * @param editorLine the line (0-based) the code starts on
	 */
	public static String atLine(String code, int editorLine) {
		if( code == null || editorLine <= 0 ) {
			return code;
		}
		StringBuilder b = new StringBuilder(editorLine+code.length());
		for(int i=0; i < editorLine; i++) {
			b.append('\n');
		}
		return b.append(code).toString();
	}

	public static String clean(String text) {
		if( text == null ) {
			return null;
		}
		StringBuilder ret = new StringBuilder(text.length());
		for(int idx=0,sz=text.length(); idx < sz; idx++) {
			char c = text.charAt(idx);
			if( c == ' ') {
				ret.append(' ');
			} else if( c == '\t' || c == '\n' || c == '\r'
					|| (c >= ' ' && c != 0x7f && !(c >= 0x80 && c < 0xa0)
					&& c != '​' && c != '‌' && c != '‍' && c != '﻿')) {
				ret.append(c);
			}
		}
		return ret.toString();
	}

	/** Script arguments separated by white space; none for a blank string. */
	public static List<String> splitArguments(String text) {
		List<String> ret = new ArrayList<>();
		if( text != null ) {
			for(String a : text.trim().split("\\s+")) {
				if( !a.isEmpty()) {
					ret.add(a);
				}
			}
		}
		return ret;
	}

	/**
	 * The command's first line of source, cut short; doesn't build the text of its whole body.
	 * @param source the text node was read from
	 */
	public static String statementText(Ast.Node node, String source) {
		if( node == null || source == null || node.start < 0 || node.end <= node.start || node.start >= source.length()) {
			return "";
		}
		int stop = Math.min(node.end, source.length());
		int end = Math.min(stop, node.start+MAX_STATEMENT_TEXT);
		String ret = source.substring(node.start, end);
		int nl = ret.indexOf('\n');
		if( nl >= 0 ) {
			ret = ret.substring(0, nl)+" ...";
		} else if( end < stop ) {
			ret += " ...";
		}
		return ret;
	}

	/** The file's text, read as UTF-8. */
	public static String read(FileSource file) throws IOException {
		try(InputStream in = file.getInputStream()) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	/** Writes text, cleaned, as UTF-8. */
	public static void write(FileSource file, String text) throws IOException {
		try (OutputStream out = file.getOutputStream()){
			out.write(clean(text).getBytes(StandardCharsets.UTF_8));
		}
	}

	/**
	 * The variable at offset in a script's text, for a debugger's right-click menu, or null: the
	 * name the offset is in (or just after), when the text shows it is a variable: it follows $
	 * or ${ (${#name} and ${!name} too), it is being set (name=, name+=, name[), or known says
	 * it is one (the names of the paused script's variables, say). A positional parameter is given
	 * as it's written: $1.
	 */
	public static String variableAt(String text, int offset, java.util.function.Predicate<String> known) {
		if( text == null || offset < 0 || offset > text.length()) {
			return null;
		}
		int start = offset;
		while( start > 0 && isNameChar(text.charAt(start-1))) {
			start--;
		}
		int end = offset;
		while( end < text.length() && isNameChar(text.charAt(end))) {
			end++;
		}
		if( start == end ) {
			return null;
		}
		String name = text.substring(start, end);
		// what is in front of it: $, ${, ${# or ${!
		int before = start;
		if( before > 1 && (text.charAt(before-1) == '#' || text.charAt(before-1) == '!') && text.charAt(before-2) == '{') {
			before--;
		}
		if( before > 0 && text.charAt(before-1) == '{') {
			before--;
		}
		boolean dollar = before > 0 && text.charAt(before-1) == '$' && (before == start || text.charAt(before) == '{');
		if( name.matches("[0-9]+")) {
			return dollar ? "$"+name : null;
		}
		if( !name.matches("[A-Za-z_][A-Za-z_0-9]*")) {
			return null;
		}
		boolean set = text.startsWith("=", end) && !text.startsWith("==", end) || text.startsWith("+=", end) || text.startsWith("[", end);
		// (a word of a command, such as the b of a=b, isn't being set: it must start its word)
		boolean startsWord = start == 0 || " \t\n;&|(".indexOf(text.charAt(start-1)) >= 0;
		if( dollar || set && startsWord || known != null && known.test(name)) {
			return name;
		}
		return null;
	}

	private static boolean isNameChar(char c) {
		return c == '_' || c >= '0' && c <= '9' || c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z';
	}
}
