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

import us.bringardner.fsh.debug.Breakpoint;
import us.bringardner.fsh.debug.ShellEnvironment;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import us.bringardner.fsh.ide.core.RemoteDebug.Mode;

/**
 * Debug Remote's three ways, against what would be on the other machine: fsh-loupe's adapter
 * listening on a port (FshAdapterMain --listen), and a shell started with
 * fsh --wait-for-debugger. Both are in this JVM, reached over the loopback address.
 */
public class TestRemoteDebug {

	private static final String SCRIPT = "x=1\nfor i in 1 2 3\ndo\n  x=$((x+i))\ndone\necho \"x is $x [$WHO] $1\"\nexit 3\n";

	@TempDir
	Path dir;

	private final BlockingQueue<String> log = new LinkedBlockingQueue<>();
	private final BlockingQueue<Map<String,Object>> variables = new LinkedBlockingQueue<>();
	private final StringBuilder output = new StringBuilder();
	private AdapterSession session;
	// what would be on the other machine: closed (and waited for) after each test
	private final List<RemoteSide> remotes = new java.util.ArrayList<>();
	private RemoteSide waiting;

	private final AdapterSession.Listener listener = new AdapterSession.Listener() {
		@Override
		public void paused(int line, String reason, Map<String,Object> vars) {
			variables.add(vars);
			log.add("paused "+line+" "+reason);
		}

		@Override
		public void output(String category, String text) {
			synchronized (output) {
				output.append(category).append(':').append(text);
			}
		}

		@Override
		public void breakpoint(Breakpoint bp, String status, int line, String message) {
		}

		@Override
		public void finished(int exitCode, boolean killed, Exception error) {
			log.add("finished "+exitCode+(killed ? " killed" : "")+(error == null ? "" : " "+error));
		}
	};

	/** Closes the session, the listening adapter and the waiting shell, and waits for their threads. */
	@AfterEach
	public void close() throws Exception {
		ShellEnvironment.setForTests(null);
		if( session != null ) {
			session.close();
		}
		for(RemoteSide r : remotes) {
			r.close();
		}
	}

	/** FshAdapterMain --listen 0 */
	private int listen() throws IOException {
		RemoteSide r = RemoteSide.listening();
		remotes.add(r);
		return r.port();
	}

	/** fsh --wait-for-debugger 0 script arg */
	private int waitingShell(Path script) throws Exception {
		waiting = RemoteSide.waiting(script);
		remotes.add(waiting);
		return waiting.port();
	}

	private String next() throws Exception {
		String s = log.poll(20, TimeUnit.SECONDS);
		assertNotNull(s, "nothing happened");
		return s;
	}

	private static Breakpoint breakpoint(int line) {
		Breakpoint bp = new Breakpoint();
		bp.setLine(line);
		bp.reset();
		return bp;
	}

	private String output() {
		synchronized (output) {
			return output.toString();
		}
	}

	@Test
	public void theEditorsScriptRunsThereWithThatMachinesEnvironment() throws Exception {
		// (this machine's shell environment is not the other's: it isn't sent)
		ShellEnvironment.setForTests(Map.of("WHO", "tony"));
		RemoteDebug how = new RemoteDebug("127.0.0.1", listen(), Mode.EDITOR_SCRIPT, "");
		session = how.connect(listener);
		AdapterSession.Started started = session.start(how, SCRIPT, "/home/me/demo.fsh", List.of("arg"), List.of(breakpoint(5)));
		assertFalse(started.isStopped());
		assertNull(started.getText());
		assertNull(started.getPath());

		assertEquals("paused 5 breakpoint", next());
		assertEquals("7", variables.poll().get("x"));
		assertEquals("8", session.evaluate("$((x+1))"));
		session.resume().get(10, TimeUnit.SECONDS);
		assertEquals("finished 3", next());
		assertTrue(output().contains("stdout:x is 7 [] arg"), output());
	}

	@Test
	public void aScriptFileThereStopsAtItsFirstCommandWithItsText() throws Exception {
		Path script = dir.resolve("there.fsh");
		Files.writeString(script, SCRIPT);
		RemoteDebug how = new RemoteDebug("localhost", listen(), Mode.REMOTE_FILE, script.toString());
		session = how.connect(listener);
		// (the editor's own script and breakpoints are not this script's: they are not used)
		AdapterSession.Started started = session.start(how, "echo the editor's", "editor.fsh", List.of("arg"), List.of(breakpoint(0)));
		assertTrue(started.isStopped());
		assertEquals(SCRIPT, started.getText());
		assertEquals(script.toString(), started.getPath());
		assertEquals(0, started.getLine());
		assertNotNull(started.getVariables());
		assertTrue(session.isPaused());
		assertNull(log.poll(200, TimeUnit.MILLISECONDS), "the stop at the start is the answer, not an event");

		// the user sets a breakpoint in the text shown, and lets it run
		session.syncBreakpoints(List.of(breakpoint(5)));
		session.resume().get(10, TimeUnit.SECONDS);
		assertEquals("paused 5 breakpoint", next());
		assertEquals("7", variables.poll().get("x"));
		session.resume().get(10, TimeUnit.SECONDS);
		assertEquals("finished 3", next());
		assertTrue(output().contains("stdout:x is 7 [] arg"), output());
	}

	@Test
	public void aPathThatIsNotThereIsSaidSo() throws Exception {
		RemoteDebug how = new RemoteDebug("127.0.0.1", listen(), Mode.REMOTE_FILE, dir.resolve("none.fsh").toString());
		session = how.connect(listener);
		Exception e = assertThrows(Exception.class, () -> session.start(how, "", "editor.fsh", List.of(), List.of()));
		assertTrue(e.getMessage().contains("Can't read") && e.getMessage().contains("none.fsh"), e.getMessage());
	}

	@Test
	public void attachesToAScriptThatWaits() throws Exception {
		Path script = dir.resolve("waits.fsh");
		Files.writeString(script, SCRIPT);
		RemoteDebug how = new RemoteDebug("127.0.0.1", waitingShell(script), Mode.ATTACH, "");
		session = how.connect(listener);
		AdapterSession.Started started = session.start(how, "echo the editor's", "editor.fsh", List.of(), List.of());
		assertTrue(started.isStopped());
		assertEquals(SCRIPT, started.getText());
		assertEquals(script.toAbsolutePath().normalize().toString(), started.getPath());
		assertEquals(0, started.getLine());
		assertEquals("", waiting.out(), "nothing has run");

		session.syncBreakpoints(List.of(breakpoint(5)));
		session.stepOver().get(10, TimeUnit.SECONDS);
		assertEquals("paused 1 step", next());
		assertEquals("1", variables.poll().get("x"));
		session.resume().get(10, TimeUnit.SECONDS);
		assertEquals("paused 5 breakpoint", next());
		session.resume().get(10, TimeUnit.SECONDS);
		assertEquals("finished 3", next());
		assertEquals(3, waiting.status());
		// its output is the shell's own (the WHO is this JVM's environment's, if any)
		assertTrue(waiting.out().startsWith("x is 7 ["), waiting.out());
		assertEquals("", output());
	}

	@Test
	public void closingTheSessionLeavesAnAttachedScriptRunning() throws Exception {
		Path script = dir.resolve("waits.fsh");
		Files.writeString(script, SCRIPT);
		RemoteDebug how = new RemoteDebug("127.0.0.1", waitingShell(script), Mode.ATTACH, "");
		session = how.connect(listener);
		assertTrue(session.start(how, "", "editor.fsh", List.of(), List.of()).isStopped());
		session.close();
		assertEquals(3, waiting.status());
		assertTrue(waiting.out().startsWith("x is 7 ["), waiting.out());
	}

	@Test
	public void theWrongWayForWhatListensIsExplained() throws Exception {
		// an adapter that starts scripts has no script waiting
		RemoteDebug attach = new RemoteDebug("127.0.0.1", listen(), Mode.ATTACH, "");
		session = attach.connect(listener);
		Exception e = assertThrows(IOException.class, () -> session.start(attach, "", "editor.fsh", List.of(), List.of()));
		assertTrue(e.getMessage().contains("No script is waiting") && e.getMessage().contains("--wait-for-debugger"), e.getMessage());
		session.close();

		// a waiting script is attached to, not given another script to run
		Path script = dir.resolve("waits.fsh");
		Files.writeString(script, "echo ran\n");
		int port = waitingShell(script);
		RemoteDebug file = new RemoteDebug("127.0.0.1", port, Mode.REMOTE_FILE, script.toString());
		session = file.connect(listener);
		e = assertThrows(IOException.class, () -> session.start(file, "", "editor.fsh", List.of(), List.of()));
		assertTrue(e.getMessage().contains("A script is waiting") && e.getMessage().contains(Mode.ATTACH.getLabel()), e.getMessage());
		session.close();
		RemoteDebug editor = new RemoteDebug("127.0.0.1", port, Mode.EDITOR_SCRIPT, "");
		session = editor.connect(listener);
		e = assertThrows(Exception.class, () -> session.start(editor, "echo mine", "editor.fsh", List.of(), List.of()));
		assertTrue(e.getMessage().contains("use attach"), e.getMessage());
		// (the launch that failed ended its target: the listener is told)
		assertEquals("finished -1", next());
		session.close();

		// (the script still waits: let it go)
		RemoteDebug go = new RemoteDebug("127.0.0.1", port, Mode.ATTACH, "");
		session = go.connect(listener);
		session.start(go, "", "", List.of(), List.of());
		session.resume().get(10, TimeUnit.SECONDS);
		assertEquals("finished 0", next());
	}

	@Test
	public void nothingListening() throws Exception {
		int port;
		try (ServerSocket s = new ServerSocket(0)) {
			port = s.getLocalPort();
		}
		RemoteDebug how = new RemoteDebug("127.0.0.1", port, Mode.EDITOR_SCRIPT, "");
		assertThrows(IOException.class, () -> how.connect(listener).close());
	}

	@Test
	public void whatIsMissingIsSaid() {
		RemoteDebug r = new RemoteDebug();
		assertEquals(RemoteDebug.DEFAULT_PORT, r.getPort());
		assertEquals(Mode.EDITOR_SCRIPT, r.getMode());
		assertEquals("Give the machine's name or address", r.problem());
		assertThrows(IOException.class, () -> r.connect(listener));
		r.setHost(" build1 ");
		assertNull(r.problem());
		assertEquals("build1:4711", r.where());
		r.setPort(0);
		assertEquals("The port is a number from 1 to 65535", r.problem());
		r.setPort(70000);
		assertNotNull(r.problem());
		r.setPort(22);
		r.setMode(Mode.REMOTE_FILE);
		assertEquals("Give the script's path on that machine", r.problem());
		r.setPath(" /opt/x.fsh ");
		assertNull(r.problem());
		assertEquals("/opt/x.fsh", r.getPath());
		// (attach needs no path)
		r.setPath("");
		r.setMode(Mode.ATTACH);
		assertNull(r.problem());
		r.setHost("::1");
		assertEquals("[::1]:22", r.where());

		assertEquals(4711, RemoteDebug.parsePort(" 4711 "));
		for(String bad : new String[] {"", "0", "65536", "-1", "port", null, "47 11"}) {
			assertEquals(-1, RemoteDebug.parsePort(bad), String.valueOf(bad));
		}
	}

	@Test
	public void theLastOneUsedIsKeptInTheConfiguration() throws Exception {
		File file = dir.resolve("Config.xml").toFile();
		Configuration config = Configuration.createDefault();
		// (never null, and a copy: changing what was read changes nothing kept)
		assertEquals("", config.getRemoteDebug().getHost());
		config.getRemoteDebug().setHost("changed");
		assertEquals("", config.getRemoteDebug().getHost());

		config.setRemoteDebug(new RemoteDebug("build1", 5000, Mode.REMOTE_FILE, "/opt/x.fsh"));
		config.save(file);
		RemoteDebug read = Configuration.load(file).getRemoteDebug();
		assertEquals("build1", read.getHost());
		assertEquals(5000, read.getPort());
		assertEquals(Mode.REMOTE_FILE, read.getMode());
		assertEquals("/opt/x.fsh", read.getPath());
		assertNull(Configuration.getLoadError());
	}
}
