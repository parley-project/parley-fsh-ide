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
public record ScriptDocument(String body, String arguments, String stdIn, String stdOut, String stdErr) {

	static final String SCRIPT_ARGS="#BjlIdeScriptArgs=";
	static final String SCRIPT_IN  ="#BjlIdeScriptIn=";
	static final String SCRIPT_OUT ="#BjlIdeScriptOut=";
	static final String SCRIPT_ERR ="#BjlIdeScriptErr=";

	public ScriptDocument {
		body = body == null ? "" : body;
		arguments = arguments == null ? "" : arguments.trim();
		stdIn = stdIn == null ? "" : stdIn.trim();
		stdOut = stdOut == null ? "" : stdOut.trim();
		stdErr = stdErr == null ? "" : stdErr.trim();
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
