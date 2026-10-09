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
		String env = System.getenv(name);
		return env != null && env.equals(value == null ? null : String.valueOf(value));
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

	/** Simple class name of a variable's value (a Type column), or "" for null. */
	public static String typeName(Object value) {
		return value == null ? "" : value.getClass().getSimpleName();
	}

	/**
	 * Sets a variable of a paused script: a positional parameter for "$1", "$2" ..., otherwise
	 * a shell variable. Call it only while the script is paused (its thread is waiting).
	 *
	 * @throws IllegalArgumentException if name is "$n" for a parameter the script doesn't have
	 */
	public static void set(ShellContext ctx, String name, Object value) {
		if( name.startsWith("$")) {
			int pos;
			try {
				pos = Integer.parseInt(name.substring(1));
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException(name+" can't be set");
			}
			List<Object> values = ctx.getAllPositionalParameters();
			if( pos < 0 || pos >= values.size()) {
				throw new IllegalArgumentException("There's no "+name);
			}
			values.set(pos, value);
			ctx.console.setPositionalParameters(true, values);
		} else {
			ctx.setVariable(name, value);
		}
	}
}
