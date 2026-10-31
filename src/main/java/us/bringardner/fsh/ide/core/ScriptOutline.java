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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A script's outline, for an Outline view in either IDE: its functions (each with the variables
 * it sets) and the variables the script sets outside them, in the order they first appear, each
 * with the (1-based) line it is defined, or first set, on.
 */
public final class ScriptOutline {

	/** A function or a variable, and its line (1-based). */
	public static final class Item {
		private final boolean function;
		private final String name;
		private final int line;
		private final List<Item> children;

		Item(boolean function, String name, int line, List<Item> children) {
			this.function = function;
			this.name = name;
			this.line = line;
			this.children = Collections.unmodifiableList(children);
		}

		public boolean isFunction() {
			return function;
		}

		public String getName() {
			return name;
		}

		/** The line (1-based) the function is defined on, or the variable first set on. */
		public int getLine() {
			return line;
		}

		/** A function's variables (none for a variable). */
		public List<Item> getChildren() {
			return children;
		}

		/** As the outline shows it: name() for a function, else the name. */
		public String label() {
			return function ? name+"()" : name;
		}

		@Override
		public String toString() {
			return label()+" "+line;
		}
	}

	private static final Pattern NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
	// commands whose arguments name variables (assignments among them are seen as assignments)
	private static final Set<String> DECLARING = Set.of("local", "declare", "typeset", "export", "readonly", "read");
	// read's options that take a value (read -p 'prompt' -t 5 name)
	private static final String READ_OPTIONS_WITH_VALUE = "adinNptu";

	private final List<Item> functions;
	private final List<Item> variables;

	private ScriptOutline(List<Item> functions, List<Item> variables) {
		this.functions = Collections.unmodifiableList(functions);
		this.variables = Collections.unmodifiableList(variables);
	}

	/** The script's functions, in order. */
	public List<Item> getFunctions() {
		return functions;
	}

	/** The variables the script sets outside its functions, in the order first set. */
	public List<Item> getVariables() {
		return variables;
	}

	public boolean isEmpty() {
		return functions.isEmpty() && variables.isEmpty();
	}

	/** The outline of a parsed script (ScriptParser.parse); empty for null. */
	public static ScriptOutline of(SyntaxNode tree) {
		List<Item> functions = new ArrayList<>();
		Map<String,Integer> variables = new LinkedHashMap<>();
		if( tree != null ) {
			collect(tree, variables, functions);
		}
		return new ScriptOutline(functions, items(variables));
	}

	private static List<Item> items(Map<String,Integer> variables) {
		List<Item> ret = new ArrayList<>();
		for(Map.Entry<String,Integer> e : variables.entrySet()) {
			ret.add(new Item(false, e.getKey(), e.getValue(), new ArrayList<>()));
		}
		return ret;
	}

	// variables set in node go in variables; functions (and the variables each sets) in functions
	private static void collect(SyntaxNode node, Map<String,Integer> variables, List<Item> functions) {
		String kind = node.getKind();
		if( kind.equals("function")) {
			// its own variables; a function defined inside it is listed as a function too
			Map<String,Integer> own = new LinkedHashMap<>();
			int index = functions.size();
			for(SyntaxNode c : node.getChildren()) {
				collect(c, own, functions);
			}
			functions.add(index, new Item(true, node.getText(), node.getLine(), items(own)));
			return;
		}
		if( kind.equals("assignment")) {
			add(variables, assignedName(node.getText()), node.getLine());
		} else if( kind.equals("for") || kind.equals("select")) {
			// for name in ... / select name in ... (not for (( ... )))
			String t = node.getText();
			int space = t.indexOf(' ');
			if( space > 0 ) {
				add(variables, t.substring(space+1).trim(), node.getLine());
			}
		} else if( kind.equals("command")) {
			declared(node, variables);
		}
		for(SyntaxNode c : node.getChildren()) {
			collect(c, variables, functions);
		}
	}

	// local x y, read -p 'prompt' name, getopts "ab:" opt ...: the variables named (not assigned)
	private static void declared(SyntaxNode command, Map<String,Integer> variables) {
		List<SyntaxNode> words = new ArrayList<>();
		for(SyntaxNode c : command.getChildren()) {
			if( c.getKind().equals("word") || c.getKind().equals("assignment")) {
				words.add(c);
			}
		}
		if( words.isEmpty() || !words.get(0).getKind().equals("word")) {
			return;
		}
		String cmd = words.get(0).getText();
		if( cmd.equals("getopts")) {
			if( words.size() > 2 && words.get(2).getKind().equals("word")) {
				add(variables, words.get(2).getText(), words.get(2).getLine());
			}
			return;
		}
		if( !DECLARING.contains(cmd)) {
			return;
		}
		boolean options = true;
		for(int i=1; i < words.size(); i++ ) {
			SyntaxNode w = words.get(i);
			if( !w.getKind().equals("word")) {
				// an assignment (local x=1), in its place among the names
				add(variables, assignedName(w.getText()), w.getLine());
				continue;
			}
			String t = w.getText();
			if( options && t.equals("--")) {
				options = false;
			} else if( options && t.startsWith("-") && t.length() > 1 ) {
				// read -p 'prompt': skip the option's value
				if( cmd.equals("read") && READ_OPTIONS_WITH_VALUE.indexOf(t.charAt(t.length()-1)) >= 0 ) {
					i++;
				}
			} else {
				options = false;
				add(variables, t, w.getLine());
			}
		}
	}

	/** The variable an assignment node sets: x for x=, x+= and x[i]=. */
	static String assignedName(String text) {
		int end = 0;
		while( end < text.length() && (Character.isLetterOrDigit(text.charAt(end)) || text.charAt(end) == '_')) {
			end++;
		}
		return text.substring(0, end);
	}

	private static void add(Map<String,Integer> variables, String name, int line) {
		if( name != null && NAME.matcher(name).matches() && !variables.containsKey(name)) {
			variables.put(name, line);
		}
	}
}
