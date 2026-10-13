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

import java.util.Objects;

/**
 * How a script is run: its arguments and the files its standard input, output and error are
 * redirected to ("" for none). Kept per user in a RunSettingsStore, not in the script.
 */
public final class RunSettings {

	/** No arguments, no redirects. */
	public static final RunSettings NONE = new RunSettings("", "", "", "");

	private final String arguments;
	private final String stdIn;
	private final String stdOut;
	private final String stdErr;

	/**
	 * @param arguments the arguments, separated by white space (as typed; see ScriptText.splitArguments)
	 * @param stdIn file standard input is read from, or "" (null is "")
	 */
	public RunSettings(String arguments, String stdIn, String stdOut, String stdErr) {
		this.arguments = clean(arguments);
		this.stdIn = clean(stdIn);
		this.stdOut = clean(stdOut);
		this.stdErr = clean(stdErr);
	}

	private static String clean(String s) {
		return s == null ? "" : s.trim();
	}

	public String arguments() {
		return arguments;
	}

	public String stdIn() {
		return stdIn;
	}

	public String stdOut() {
		return stdOut;
	}

	public String stdErr() {
		return stdErr;
	}

	/** True if there's nothing to keep: no arguments and no redirects. */
	public boolean isEmpty() {
		return arguments.isEmpty() && stdIn.isEmpty() && stdOut.isEmpty() && stdErr.isEmpty();
	}

	@Override
	public boolean equals(Object x) {
		if( this == x ) {
			return true;
		}
		if( !(x instanceof RunSettings)) {
			return false;
		}
		RunSettings o = (RunSettings) x;
		return arguments.equals(o.arguments) && stdIn.equals(o.stdIn) && stdOut.equals(o.stdOut) && stdErr.equals(o.stdErr);
	}

	@Override
	public int hashCode() {
		return Objects.hash(arguments, stdIn, stdOut, stdErr);
	}

	@Override
	public String toString() {
		return "RunSettings[arguments="+arguments+", stdIn="+stdIn+", stdOut="+stdOut+", stdErr="+stdErr+"]";
	}
}
