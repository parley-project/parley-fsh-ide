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

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;

/**
 * Where and how Run &gt; Debug Remote debugs: the machine and port an adapter listens on, what
 * to debug there. The connection has no login and no encryption
 * (the adapter's side says so when it starts): it is for a network where everyone who can
 * reach the port is trusted. The last one used is kept in the {@link Configuration}.
 */
@XmlAccessorType(XmlAccessType.FIELD)
public final class RemoteDebug {

	/** What to debug on the remote machine. */
	public enum Mode {
		/** The script in the editor, run there (an adapter started with --listen). */
		EDITOR_SCRIPT("The script in the editor, run there"),
		/** A script file that is there, run there (an adapter started with --listen). */
		REMOTE_FILE("A script file on that machine"),
		/** A script started there with fsh --wait-for-debugger. */
		ATTACH("A script waiting there for a debugger");

		private final String label;

		Mode(String label) {
			this.label = label;
		}

		/** For a dialog. */
		public String getLabel() {
			return label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	/** What the dialogs say about the connection. */
	public static final String WARNING = "The connection has no login and no encryption: use it only on a network where"
			+" everyone who can reach the port is trusted.";

	/** What has to be running on the other machine for mode, for the dialogs. */
	public static String hint(Mode mode) {
		switch(mode == null ? Mode.EDITOR_SCRIPT : mode) {
		case REMOTE_FILE:
			return "On that machine: fsh-loupe's adapter, started with --listen address:port. The script is read and run"
					+" there, and shown here while it is debugged; it stops before its first command.";
		case ATTACH:
			return "On that machine: the script, started with fsh --wait-for-debugger address:port script. It is shown"
					+" here while it is debugged, and stops before its first command.";
		default:
			return "On that machine: fsh-loupe's adapter, started with --listen address:port. The script in the editor"
					+" is sent there and runs with that machine's files and environment.";
		}
	}

	/** The port fsh-loupe's examples use. */
	public static final int DEFAULT_PORT = 4711;

	private String host = "";
	private int port = DEFAULT_PORT;
	private Mode mode = Mode.EDITOR_SCRIPT;
	private String path = "";

	public RemoteDebug() {
	}

	public RemoteDebug(String host, int port, Mode mode, String path) {
		setHost(host);
		setPort(port);
		setMode(mode);
		setPath(path);
	}

	public String getHost() {
		return host == null ? "" : host;
	}

	public void setHost(String host) {
		this.host = host == null ? "" : host.trim();
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public Mode getMode() {
		return mode == null ? Mode.EDITOR_SCRIPT : mode;
	}

	public void setMode(Mode mode) {
		this.mode = mode == null ? Mode.EDITOR_SCRIPT : mode;
	}

	/** The script's path on the remote machine (for {@link Mode#REMOTE_FILE}). */
	public String getPath() {
		return path == null ? "" : path;
	}

	public void setPath(String path) {
		this.path = path == null ? "" : path.trim();
	}

	public RemoteDebug copy() {
		return new RemoteDebug(getHost(), port, getMode(), getPath());
	}

	/**
	 * The port a dialog's field holds.
	 * @return 1 to 65535, or -1 if text isn't one
	 */
	public static int parsePort(String text) {
		try {
			int p = Integer.parseInt(text == null ? "" : text.trim());
			return p >= 1 && p <= 65535 ? p : -1;
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	/** What is missing or wrong, for the user; or null if a session can be opened. */
	public String problem() {
		if( getHost().isEmpty()) {
			return "Give the machine's name or address";
		}
		if( port < 1 || port > 65535 ) {
			return "The port is a number from 1 to 65535";
		}
		if( getMode() == Mode.REMOTE_FILE && getPath().isEmpty()) {
			return "Give the script's path on that machine";
		}
		return null;
	}

	/** Connects to the adapter (10 seconds at most). */
	public AdapterSession connect(AdapterSession.Listener listener) throws IOException {
		String problem = problem();
		if( problem != null ) {
			throw new IOException(problem);
		}
		return LoupeSession.connect(getHost(), port, listener);
	}

	/** Such as "build1:4711". */
	public String where() {
		return (getHost().indexOf(':') >= 0 ? "["+getHost()+"]" : getHost())+":"+port;
	}
}
