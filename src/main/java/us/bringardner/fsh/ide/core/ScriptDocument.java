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
import java.util.Objects;

import us.bringardner.parley.files.FileSource;

/**
 * A script being edited: its code, and the arguments and redirects it's run with.
 * <p>
 * The script file holds only the code; the settings are kept per user in a RunSettingsStore
 * (see open and save). Scripts saved by earlier versions (BjlShellIde, and fsh-ide before
 * this change) carry the settings in comment lines (#BjlIdeScriptArgs= and so on); those are
 * still read, and saving the script moves them to the store and leaves them out of the file.
 */
public final class ScriptDocument {

	// the comment lines earlier versions kept the settings in; read, never written
	static final String LEGACY_ARGS = "#BjlIdeScriptArgs=";
	static final String LEGACY_IN   = "#BjlIdeScriptIn=";
	static final String LEGACY_OUT  = "#BjlIdeScriptOut=";
	static final String LEGACY_ERR  = "#BjlIdeScriptErr=";

	private final String body;
	private final RunSettings settings;
	private final boolean legacy;

	public ScriptDocument(String body, RunSettings settings) {
		this(body, settings, false);
	}

	private ScriptDocument(String body, RunSettings settings, boolean legacy) {
		this.body = body == null ? "" : body;
		this.settings = settings == null ? RunSettings.NONE : settings;
		this.legacy = legacy;
	}

	/** The code (what the script file holds). */
	public String body() {
		return body;
	}

	public RunSettings settings() {
		return settings;
	}

	public String arguments() {
		return settings.arguments();
	}

	public String stdIn() {
		return settings.stdIn();
	}

	public String stdOut() {
		return settings.stdOut();
	}

	public String stdErr() {
		return settings.stdErr();
	}

	/** True if the text had settings lines from an earlier version (saving it removes them). */
	public boolean hasLegacySettings() {
		return legacy;
	}

	/** True for a settings line from an earlier version of the IDE. */
	public static boolean isLegacySettingsLine(String line) {
		return line.startsWith(LEGACY_ARGS) || line.startsWith(LEGACY_IN)
				|| line.startsWith(LEGACY_OUT) || line.startsWith(LEGACY_ERR);
	}

	/**
	 * Reads a script's text: the code, and the settings lines of an earlier version if it has
	 * any (they're left out of the code, wherever they are). Text without them is the code as
	 * it is, and the settings are NONE.
	 */
	public static ScriptDocument parse(String text) {
		if( text == null || text.isEmpty()) {
			return new ScriptDocument("", RunSettings.NONE);
		}
		String args = null, in = null, out = null, err = null;
		StringBuilder body = new StringBuilder(text.length());
		int from = 0;
		while( from < text.length()) {
			int nl = text.indexOf('\n', from);
			int end = nl < 0 ? text.length() : nl+1;
			String line = text.substring(from, nl < 0 ? text.length() : nl);
			if( line.startsWith(LEGACY_ARGS)) {
				args = line.substring(LEGACY_ARGS.length());
			} else if( line.startsWith(LEGACY_IN)) {
				in = line.substring(LEGACY_IN.length());
			} else if( line.startsWith(LEGACY_OUT)) {
				out = line.substring(LEGACY_OUT.length());
			} else if( line.startsWith(LEGACY_ERR)) {
				err = line.substring(LEGACY_ERR.length());
			} else {
				body.append(text, from, end);
			}
			from = end;
		}
		boolean legacy = args != null || in != null || out != null || err != null;
		if( !legacy ) {
			return new ScriptDocument(text, RunSettings.NONE);
		}
		return new ScriptDocument(body.toString(), new RunSettings(args, in, out, err), true);
	}

	/**
	 * A script read from file: its code, and its settings (from the store, or from the
	 * settings lines of an earlier version, which win if the file still has them).
	 * @param store the user's settings, or null to use only what's in the text
	 */
	public static ScriptDocument open(FileSource file, String text, RunSettingsStore store) throws IOException {
		ScriptDocument doc = parse(text);
		if( doc.legacy || file == null || store == null ) {
			return doc;
		}
		return new ScriptDocument(doc.body, store.get(file));
	}

	/**
	 * Writes the code to file and keeps the settings in the store; with none, any kept for the
	 * file are removed (so nothing is left from arguments since cleared).
	 * @return null, or a warning if the code was saved but the settings couldn't be kept
	 * @throws IOException if the code couldn't be written
	 */
	public String save(FileSource file, RunSettingsStore store) throws IOException {
		ScriptText.write(file, body);
		if( store == null ) {
			return null;
		}
		try {
			store.put(file, settings);
			return null;
		} catch (IOException | RuntimeException e) {
			return "The script was saved, but its arguments and redirects couldn't be kept in "
					+store.getFile()+": "+e.getMessage();
		}
	}

	/** Equal code and settings (where the settings came from doesn't matter). */
	@Override
	public boolean equals(Object x) {
		if( this == x ) {
			return true;
		}
		if( !(x instanceof ScriptDocument)) {
			return false;
		}
		ScriptDocument o = (ScriptDocument) x;
		return body.equals(o.body) && settings.equals(o.settings);
	}

	@Override
	public int hashCode() {
		return Objects.hash(body, settings);
	}

	@Override
	public String toString() {
		return "ScriptDocument[body="+body+", settings="+settings+(legacy ? ", from settings lines" : "")+"]";
	}
}
