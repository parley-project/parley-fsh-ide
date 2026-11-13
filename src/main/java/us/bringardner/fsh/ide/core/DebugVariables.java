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
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import us.bringardner.fsh.Console;
import us.bringardner.fsh.FshList;
import us.bringardner.fsh.ShellContext;

/** A paused script's variables, for a debugger's variables view in any UI. */
public final class DebugVariables {

	private DebugVariables() {
	}

	/** The variables, by name. */
	public static List<Variable> list(Map<String,Object> variables) {
		List<Variable> ret = new ArrayList<>();
		if( variables != null ) {
			for(Map.Entry<String,Object> e : new TreeMap<>(variables).entrySet()) {
				ret.add(new Variable(e.getKey(), e.getValue()));
			}
		}
		return ret;
	}

	/**
	 * True for a variable the script inherited from the environment and hasn't changed
	 * (a debugger can leave these out, so the script's own variables stand out).
	 */
	public static boolean isInheritedEnvironment(String name, Object value) {
		String env = environment(name);
		return env != null && env.equals(value == null ? null : String.valueOf(value));
	}

	/**
	 * The value of the variable spelled exactly name in the environment a script is given, or
	 * null: the login shell's (see ShellEnvironment; an IDE opened from the Finder doesn't have
	 * those itself), else the IDE's own. (System.getenv(name) ignores case on Windows, where it
	 * made a script's own path look like the environment's Path.)
	 */
	static String environment(String name) {
		String ret = ShellEnvironment.known().get(name);
		if( ret != null ) {
			return ret;
		}
		ret = System.getenv(name);
		if( ret == null ) {
			return null;
		}
		for(String key : System.getenv().keySet()) {
			if( key.equals(name)) {
				return ret;
			}
		}
		return null;
	}

	// the variables a new shell starts with (IFS, PS1, OPTIND ...) and their values
	private static Map<String,String> shellDefaults;

	private static synchronized Map<String,String> shellDefaults() {
		if( shellDefaults == null ) {
			Map<String,String> m = new java.util.HashMap<>();
			try {
				for(Map.Entry<String,Object> e : new ShellContext(new Console()).getVariables().entrySet()) {
					m.put(e.getKey(), String.valueOf(e.getValue()));
				}
			} catch (RuntimeException e) {
				// no defaults to leave out
			}
			shellDefaults = m;
		}
		return shellDefaults;
	}

	/**
	 * True for one of the shell's own variables (IFS, PS1, OPTIND ...) that still has the value a
	 * new shell gives it. One the script has changed isn't (that's worth seeing).
	 */
	public static boolean isShellDefault(String name, Object value) {
		String def = shellDefaults().get(name);
		return def != null && !name.startsWith("$") && def.equals(String.valueOf(value));
	}

	/**
	 * The variables, by name. Unless all is true, without the ones inherited unchanged from the
	 * environment and the shell's own still at their defaults, so the script's own stand out.
	 */
	public static List<Variable> list(Map<String,Object> variables, boolean all) {
		List<Variable> ret = new ArrayList<>();
		for(Variable v : list(variables)) {
			if( all || !(isInheritedEnvironment(v.getName(), v.getValue()) || isShellDefault(v.getName(), v.getValue()))) {
				ret.add(v);
			}
		}
		return ret;
	}

	// variables fsh sets itself while a script runs, that a new shell doesn't have yet
	private static final java.util.Set<String> SET_BY_FSH = java.util.Set.of("OLDPWD", "PIPESTATUS", "BASH_REMATCH",
			// the ones ShellContext.getVariables() adds while they have a value
			"SHELLOPTS", "BASHOPTS", "BASH_ALIASES", "BASH_CMDS", "BASH_ARGC", "BASH_ARGV", "BASH_LINENO", "BASH_SOURCE", "DIRSTACK", "FUNCNAME");

	/**
	 * True for a variable fsh defines (the shell's own, such as IFS, PS1, OPTIND or BASH_LINENO, the
	 * ones inherited from the environment, and $0), whatever its value; false for the script's own,
	 * including its loop variables and its positional parameters $1, $2 ...
	 */
	public static boolean isFshDefined(String name) {
		if( name.startsWith("$")) {
			return name.equals("$0");
		}
		return shellDefaults().containsKey(name) || environment(name) != null || SET_BY_FSH.contains(name);
	}

	/** The variables, by name, that the script defined (or fsh did, if fshDefined is true). */
	public static List<Variable> list(Map<String,Object> variables, boolean all, boolean fshDefined) {
		List<Variable> ret = new ArrayList<>();
		for(Variable v : list(variables, all)) {
			if( isFshDefined(v.getName()) == fshDefined ) {
				ret.add(v);
			}
		}
		return ret;
	}

	/**
	 * The value an editor's text sets: one of the type the variable has now (oldValue's), so a
	 * number stays a number and an array stays an array.
	 * <ul>
	 * <li>a Boolean: true or false</li>
	 * <li>a whole number (Long, Integer, Short, Byte, BigInteger) or another number (Double, Float,
	 * BigDecimal): that number</li>
	 * <li>an array: an element for each line (see editText); an element keeps its index while the
	 * number of lines is the number of elements, otherwise they are numbered from 0</li>
	 * <li>an associative array: an element for each key=value line</li>
	 * <li>anything else (text, or nothing yet): the text</li>
	 * </ul>
	 *
	 * @throws IllegalArgumentException with why, for text that isn't a value of that type
	 */
	public static Object parseValue(Object oldValue, String text) {
		if( oldValue instanceof Boolean ) {
			String t = text.trim();
			if( t.equalsIgnoreCase("true") || t.equalsIgnoreCase("false")) {
				return Boolean.valueOf(t);
			}
			throw new IllegalArgumentException("It's true or false, not "+oneLine(t));
		}
		if( oldValue instanceof Number ) {
			return parseNumber((Number) oldValue, text.trim());
		}
		if( oldValue instanceof List<?> || oldValue instanceof Map<?,?> ) {
			if( text.equals(editText(oldValue))) {
				// nothing was changed
				return ShellContext.copyValue(oldValue);
			}
			String lines = text.replace("\r\n", "\n").replace('\r', '\n');
			if( lines.endsWith("\n")) {
				// (the line break that ends the last line isn't one more, empty, element)
				lines = lines.substring(0, lines.length()-1);
			}
			List<String> list = lines.isEmpty() ? List.of() : java.util.Arrays.asList(lines.split("\n", -1));
			return oldValue instanceof Map<?,?> ? parseMap((Map<?,?>) oldValue, list) : parseArray((List<?>) oldValue, list);
		}
		return text;
	}

	// text as a number of the class old is
	private static Number parseNumber(Number old, String t) {
		boolean whole = old instanceof Long || old instanceof Integer || old instanceof Short || old instanceof Byte
				|| old instanceof java.math.BigInteger;
		try {
			if( old instanceof Long ) {
				return Long.valueOf(t);
			} else if( old instanceof Integer ) {
				return Integer.valueOf(t);
			} else if( old instanceof Short ) {
				return Short.valueOf(t);
			} else if( old instanceof Byte ) {
				return Byte.valueOf(t);
			} else if( old instanceof java.math.BigInteger ) {
				return new java.math.BigInteger(t);
			} else if( old instanceof Float ) {
				return Float.valueOf(t);
			} else if( old instanceof java.math.BigDecimal ) {
				return new java.math.BigDecimal(t);
			}
			return Double.valueOf(t);
		} catch (NumberFormatException e) {
			if( whole && t.matches("[-+]?[0-9]+")) {
				throw new IllegalArgumentException("It's too big for this variable: "+oneLine(t));
			}
			throw new IllegalArgumentException("It's a "+(whole ? "whole number" : "number")+", not "+oneLine(t));
		}
	}

	// an array of the lines: at the indexes old has while there are as many, else from 0
	private static Object parseArray(List<?> old, List<String> lines) {
		if( !(old instanceof FshList)) {
			return new ArrayList<Object>(lines);
		}
		List<Integer> indexes = ((FshList) old).getIndexes();
		FshList ret = new FshList();
		for(int i=0; i < lines.size(); i++ ) {
			ret.set(indexes.size() == lines.size() ? indexes.get(i) : i, lines.get(i));
		}
		return ret;
	}

	// a map of the key=value lines, of the class old is (which says how the shell orders it)
	private static Object parseMap(Map<?,?> old, List<String> lines) {
		Map<Object,Object> ret = old instanceof TreeMap<?,?> ? new TreeMap<>() : new java.util.LinkedHashMap<>();
		for(int i=0; i < lines.size(); i++ ) {
			String line = lines.get(i);
			int eq = line.indexOf('=');
			if( eq < 1 ) {
				throw new IllegalArgumentException("Line "+(i+1)+" isn't key=value: "+oneLine(line));
			}
			ret.put(line.substring(0, eq), line.substring(eq+1));
		}
		return ret;
	}

	/**
	 * The text an editor shows for a value, and parseValue reads back: an array's elements one on
	 * each line, an associative array's as key=value lines, anything else as its text ("" for null).
	 */
	public static String editText(Object value) {
		StringBuilder ret = new StringBuilder();
		if( value instanceof List<?> ) {
			for(Object o : (List<?>) value) {
				ret.append(ret.length() > 0 ? "\n" : "").append(o == null ? "" : String.valueOf(o));
			}
			return ret.toString();
		}
		if( value instanceof Map<?,?> ) {
			for(Map.Entry<?,?> e : ((Map<?,?>) value).entrySet()) {
				ret.append(ret.length() > 0 ? "\n" : "").append(e.getKey()).append('=').append(e.getValue() == null ? "" : String.valueOf(e.getValue()));
			}
			return ret.toString();
		}
		return value == null ? "" : String.valueOf(value);
	}

	/**
	 * Why a value can't be changed as text, or null when it can: an array with a line break in an
	 * element (a line is an element), or an associative array with one in a key or a value, or an =
	 * in a key.
	 */
	public static String editProblem(Object value) {
		if( value instanceof List<?> ) {
			for(Object o : (List<?>) value) {
				if( hasLineBreak(o)) {
					return "an element has a line break in it";
				}
			}
		} else if( value instanceof Map<?,?> ) {
			for(Map.Entry<?,?> e : ((Map<?,?>) value).entrySet()) {
				if( hasLineBreak(e.getKey()) || hasLineBreak(e.getValue())) {
					return "an element has a line break in it";
				}
				if( String.valueOf(e.getKey()).contains("=") || String.valueOf(e.getKey()).isEmpty()) {
					return "a key is empty or has an = in it";
				}
			}
		}
		return null;
	}

	private static boolean hasLineBreak(Object o) {
		String s = String.valueOf(o);
		return s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0;
	}

	/**
	 * What a debugger shows for text chosen in a paused script: a variable's name (x, or as the
	 * script writes it, $x or ${x}) gives its value, an array's or a map's as the variables view
	 * shows it; other text with a $ in it (such as ${a[1]}, $1 or $HOME/bin) is expanded as the
	 * shell expands it between double quotes. Nothing of the script's changes: it's expanded in a
	 * copy of the script's variables (so ${x:=1} or $((i++)) sets nothing), and text that would
	 * run a command ($( ), ` `, &lt;( ) or &gt;( )) is refused. Call it only while the script is
	 * paused.
	 *
	 * @throws IllegalArgumentException with why there's no value to show
	 */
	public static String valueOf(ShellContext ctx, String text) {
		String t = text == null ? "" : text.trim();
		if( t.isEmpty()) {
			throw new IllegalArgumentException("Nothing to show the value of");
		}
		if( Watchpoint.isValidName(t) && !t.contains("[")) {
			String name = Watchpoint.normalize(t);
			Map<String,Object> all = ctx.getVariables();
			if( !all.containsKey(name)) {
				throw new IllegalArgumentException(name+" isn't set");
			}
			return String.valueOf(all.get(name));
		}
		String expression = expression(t);
		try {
			return us.bringardner.fsh.exec.Executor.expandWord(ctx.isolatedSubShell(), expression);
		} catch (RuntimeException e) {
			throw new IllegalArgumentException("Can't show "+oneLine(t)+": "+(e.getMessage() == null ? e.toString() : e.getMessage()));
		}
	}

	/**
	 * Text chosen in a script, as what to expand for its value where a debugger doesn't have the
	 * script's context (Loupe's evaluate): ${name} for a variable's name, otherwise the text itself.
	 *
	 * @throws IllegalArgumentException for text with no $ in it that isn't a name, and for text
	 *         that would run a command ($( ), ` `, &lt;( ) or &gt;( )), as valueOf
	 */
	public static String expression(String text) {
		String t = text == null ? "" : text.trim();
		if( t.isEmpty()) {
			throw new IllegalArgumentException("Nothing to show the value of");
		}
		if( t.contains("`") || t.contains("<(") || t.contains(">(") || t.matches("(?s).*\\$\\((?!\\().*")) {
			throw new IllegalArgumentException("Its value isn't shown, since that would run a command: "+oneLine(t));
		}
		if( t.matches("[A-Za-z_][A-Za-z_0-9]*")) {
			return "${"+t+"}";
		}
		if( !t.contains("$")) {
			throw new IllegalArgumentException("Not a variable: "+oneLine(t));
		}
		return t;
	}

	private static String oneLine(String text) {
		String ret = text.replace("\r\n", "⏎").replace('\n', '⏎').replace('\r', '⏎');
		return ret.length() > 60 ? ret.substring(0, 57)+"..." : ret;
	}

	/** Simple class name of a variable's value (a Type column), or "" for null. */
	public static String typeName(Object value) {
		return value == null ? "" : value.getClass().getSimpleName();
	}

	/**
	 * Sets a variable of a paused script: a positional parameter for "$1", "$2" ... (the running
	 * function's, in a function), otherwise a shell variable. Call it only while the script is
	 * paused (its thread is waiting).
	 *
	 * @throws IllegalArgumentException if name is "$n" for a parameter the script (or function)
	 *         doesn't have, or $0 (which, as in bash, can't be set)
	 */
	public static void set(ShellContext ctx, String name, Object value) {
		if( name.startsWith("$")) {
			int pos;
			try {
				pos = Integer.parseInt(name.substring(1));
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException(name+" can't be set");
			}
			if( pos == 0 ) {
				throw new IllegalArgumentException("$0 can't be set");
			}
			// $1 ... of the running function, or of the script outside one
			List<Object> values = new java.util.ArrayList<>(ctx.getPositionalParameterValues());
			if( pos < 1 || pos > values.size()) {
				throw new IllegalArgumentException("There's no "+name);
			}
			values.set(pos-1, value);
			ctx.setPositionalParameterValues(values);
		} else {
			ctx.setVariable(name, value);
		}
	}
}
