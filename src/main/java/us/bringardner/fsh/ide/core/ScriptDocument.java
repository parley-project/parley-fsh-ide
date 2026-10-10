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
 * A script as saved by the IDE: its code, and the arguments and redirects it's run with,
 * which are kept in comment lines (#BjlIdeScriptArgs= and so on) at the top of the file.
 * The BjlShellIde names are kept so existing scripts keep their settings.
 *
 * @param body the code, without the settings lines
 * @param arguments script arguments, separated by white space
 * @param stdIn file standard input is read from, or ""
 * @param stdOut file standard output goes to, or ""
 * @param stdErr file standard error goes to, or ""
 */
public final class ScriptDocument {

	private final String body;
	private final String arguments;
	private final String stdIn;
	private final String stdOut;
	private final String stdErr;

	static final String SCRIPT_ARGS="#BjlIdeScriptArgs=";
	static final String SCRIPT_IN  ="#BjlIdeScriptIn=";
	static final String SCRIPT_OUT ="#BjlIdeScriptOut=";
	static final String SCRIPT_ERR ="#BjlIdeScriptErr=";

	public ScriptDocument(String body, String arguments, String stdIn, String stdOut, String stdErr) {
		this.body = body == null ? "" : body;
		this.arguments = arguments == null ? "" : arguments.trim();
		this.stdIn = stdIn == null ? "" : stdIn.trim();
		this.stdOut = stdOut == null ? "" : stdOut.trim();
		this.stdErr = stdErr == null ? "" : stdErr.trim();
	}

	public String body() {
		return body;
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

	@Override public boolean equals(Object x) {
		if (this == x) return true;
		if (!(x instanceof ScriptDocument)) return false;
		ScriptDocument o = (ScriptDocument) x;
		return body.equals(o.body) && arguments.equals(o.arguments) && stdIn.equals(o.stdIn)
				&& stdOut.equals(o.stdOut) && stdErr.equals(o.stdErr);
	}

	@Override public int hashCode() {
		return java.util.Objects.hash(body, arguments, stdIn, stdOut, stdErr);
	}

	@Override public String toString() {
		return "ScriptDocument[body=" + body + ", arguments=" + arguments + ", stdIn=" + stdIn
				+ ", stdOut=" + stdOut + ", stdErr=" + stdErr + "]";
	}

	/** Reads a saved script. Settings lines can be anywhere; each body line ends with a newline. */
	public static ScriptDocument parse(String text) {
		if( text.isEmpty()) {
			// a new script is empty, not one blank line
			return new ScriptDocument("", "", "", "", "");
		}
		String args="", in="", out="", err="";
		StringBuilder body = new StringBuilder();
		for(String line : text.split("\n")) {
			if( line.startsWith(SCRIPT_ARGS)) {
				args = line.substring(SCRIPT_ARGS.length());
			} else if( line.startsWith(SCRIPT_IN)) {
				in = line.substring(SCRIPT_IN.length());
			} else if( line.startsWith(SCRIPT_OUT)) {
				out = line.substring(SCRIPT_OUT.length());
			} else if( line.startsWith(SCRIPT_ERR)) {
				err = line.substring(SCRIPT_ERR.length());
			} else  {
				body.append(line).append('\n');
			}
		}
		return new ScriptDocument(body.toString(), args, in, out, err);
	}

	/** The text to save: the settings that are set, then the body. */
	public String toText() {
		StringBuilder buf = new StringBuilder();
		line(buf, SCRIPT_ARGS, arguments);
		line(buf, SCRIPT_IN, stdIn);
		line(buf, SCRIPT_OUT, stdOut);
		line(buf, SCRIPT_ERR, stdErr);
		return buf.append(body).toString();
	}

	private static void line(StringBuilder buf, String key, String value) {
		if( !value.isEmpty()) {
			buf.append(key).append(value).append('\n');
		}
	}
}
