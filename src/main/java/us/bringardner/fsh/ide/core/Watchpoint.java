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

/**
 * A variable watchpoint: while debugging, the script stops (as at a breakpoint) when it reads
 * the variable, writes it (sets or unsets it), or either. An array's watchpoint also sees its
 * elements (a[1]=x writes a). Read by the script's thread, so its fields are volatile.
 */
public class Watchpoint {

	private volatile String variable;
	private volatile boolean read;
	private volatile boolean write;
	private volatile boolean enabled = true;

	/**
	 * @param variable the variable's name ($x, or a[1] for an array, are taken as x and a)
	 * @param read stop when the script reads it
	 * @param write stop when the script sets or unsets it
	 */
	public Watchpoint(String variable, boolean read, boolean write) {
		setVariable(variable);
		this.read = read;
		this.write = write;
	}

	/** The variable's name as it is watched: without a leading $ (or ${ }) and without a subscript. */
	public static String normalize(String name) {
		String ret = name == null ? "" : name.trim();
		if( ret.startsWith("${") && ret.endsWith("}")) {
			ret = ret.substring(2, ret.length()-1);
		} else if( ret.startsWith("$")) {
			ret = ret.substring(1);
		}
		int b = ret.indexOf('[');
		if( b > 0 ) {
			ret = ret.substring(0, b);
		}
		return ret.trim();
	}

	/** True for a name a variable can have (a letter or _, then letters, digits or _). */
	public static boolean isValidName(String name) {
		return normalize(name).matches("[A-Za-z_][A-Za-z_0-9]*");
	}

	public String getVariable() {
		return variable;
	}

	public void setVariable(String variable) {
		this.variable = normalize(variable);
	}

	public boolean isRead() {
		return read;
	}

	public void setRead(boolean read) {
		this.read = read;
	}

	public boolean isWrite() {
		return write;
	}

	public void setWrite(boolean write) {
		this.write = write;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	/** True if the script should stop for this access (name as fsh gives it: a[1] is a). */
	public boolean matches(String name, boolean isWrite) {
		if( !enabled || !(isWrite ? write : read)) {
			return false;
		}
		return variable.equals(normalize(name));
	}

	/** What it stops on: "read", "write" or "read/write" ("never" with neither). */
	public String accessText() {
		return read && write ? "read/write" : read ? "read" : write ? "write" : "never";
	}

	@Override
	public String toString() {
		return variable+" ("+accessText()+")";
	}
}
