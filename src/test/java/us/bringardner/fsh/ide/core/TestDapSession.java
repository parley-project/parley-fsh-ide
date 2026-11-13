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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import us.bringardner.fsh.ide.core.RemoteDebug.Protocol;

/**
 * The IDE as a DAP client: Debug Remote's three ways, speaking the Debug Adapter Protocol to
 * fsh-loupe's adapter (which takes Loupe or DAP on the port it listens on, and on the port a
 * script waits on).
 */
public class TestDapSession {

	private static final String SCRIPT = "x=1\nfor i in 1 2 3\ndo\n  x=$((x+i))\ndone\nname=\"a b\"\necho \"x is $x [$name] $1\"\nexit 3\n";

	@TempDir
	Path dir;

	private final BlockingQueue<String> log = new LinkedBlockingQueue<>();
	private final BlockingQueue<Map<String,Object>> variables = new LinkedBlockingQueue<>();
	private final StringBuilder output = new StringBuilder();
	private AdapterSession session;
	private RemoteSide remote;

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
			log.add("breakpoint "+status+" "+line);
		}

		@Override
		public void finished(int exitCode, boolean killed, Exception error) {
			log.add("finished "+exitCode+(killed ? " killed" : "")+(error == null ? "" : " "+error));
		}
	};

	/** Closes the session and the remote side (which waits for its threads). */
	@AfterEach
	public void close() throws Exception {
		if( session != null ) {
			session.close();
		}
		if( remote != null ) {
			remote.close();
		}
	}

	/** The next thing that isn't a breakpoint report. */
	private String next() throws Exception {
		for(;;) {
			String s = log.poll(20, TimeUnit.SECONDS);
			assertNotNull(s, "nothing happened");
			if( !s.startsWith("breakpoint ")) {
				return s;
			}
		}
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
	public void theEditorsScriptOverDap() throws Exception {
		remote = RemoteSide.listening();
		RemoteDebug how = new RemoteDebug("127.0.0.1", remote.port(), Mode.EDITOR_SCRIPT, "", Protocol.DAP);
		session = how.connect(listener);
		assertTrue(session instanceof DapSession);
		AdapterSession.Started started = session.start(how, SCRIPT, "/home/me/demo.fsh", List.of("arg"), List.of(breakpoint(6)));
		assertFalse(started.isStopped());

		assertEquals("paused 6 breakpoint", next());
		Map<String,Object> vars = variables.poll();
		// values as the IDE shows them: no quotes round a string
		assertEquals("7", vars.get("x"));
		assertEquals("a b", vars.get("name"));
		assertTrue(session.isPaused());
		assertEquals("8", session.evaluate("$((x+1))"));
		assertEquals("100", session.setVariable("x", "100").get("x"));
		session.resume().get(10, TimeUnit.SECONDS);
		assertEquals("finished 3", next());
		assertTrue(output().contains("stdout:x is 100 [a b] arg"), output());
	}

	@Test
	public void conditionsHitCountsAndSteps() throws Exception {
		remote = RemoteSide.listening();
		RemoteDebug how = new RemoteDebug("127.0.0.1", remote.port(), Mode.EDITOR_SCRIPT, "", Protocol.DAP);
		session = how.connect(listener);
		Breakpoint bp = breakpoint(3);
		bp.setConditional(true);
		bp.setCondition("[ $i -ge 2 ]");
		session.start(how, SCRIPT, "demo.fsh", List.of(), List.of(bp));
		assertEquals("paused 3 breakpoint", next());
		assertEquals("2", variables.poll().get("i"));
		session.stepOver().get(10, TimeUnit.SECONDS);
		assertTrue(next().startsWith("paused "));
		variables.poll();
		// no breakpoints any more: it runs to the end
		session.syncBreakpoints(List.of());
		session.resume().get(10, TimeUnit.SECONDS);
		assertEquals("finished 3", next());
	}

	@Test
	public void aScriptFileThereOverDapHasNoTextToShow() throws Exception {
		Path script = dir.resolve("there.fsh");
		Files.writeString(script, SCRIPT);
		remote = RemoteSide.listening();
		RemoteDebug how = new RemoteDebug("127.0.0.1", remote.port(), Mode.REMOTE_FILE, script.toString(), Protocol.DAP);
		session = how.connect(listener);
		AdapterSession.Started started = session.start(how, "echo the editor's", "editor.fsh", List.of("arg"), List.of(breakpoint(0)));
		assertTrue(started.isStopped());
		assertEquals(script.toString(), started.getPath());
		assertEquals(0, started.getLine());
		// parley-loupe's bridge has no DAP source request: the session says so, and goes on
		assertNull(started.getText());
		assertTrue(started.getNote().contains("doesn't give a script's text over DAP"), started.getNote());
		assertNull(log.poll(200, TimeUnit.MILLISECONDS), "the stop at the start is the answer, not an event");

		session.syncBreakpoints(List.of(breakpoint(6)));
		session.resume().get(10, TimeUnit.SECONDS);
		assertEquals("paused 6 breakpoint", next());
		assertEquals("7", variables.poll().get("x"));
		session.resume().get(10, TimeUnit.SECONDS);
		assertEquals("finished 3", next());
		assertTrue(output().contains("stdout:x is 7 [a b] arg"), output());
	}

	@Test
	public void attachesOverDapAndClosingLeavesTheScriptRunning() throws Exception {
		Path script = dir.resolve("waits.fsh");
		Files.writeString(script, SCRIPT);
		remote = RemoteSide.waiting(script);
		RemoteDebug how = new RemoteDebug("127.0.0.1", remote.port(), Mode.ATTACH, "", Protocol.DAP);
		session = how.connect(listener);
		AdapterSession.Started started = session.start(how, "", "editor.fsh", List.of(), List.of());
		assertTrue(started.isStopped());
		assertEquals(script.toAbsolutePath().normalize().toString(), started.getPath());
		assertEquals(0, started.getLine());
		assertEquals("", remote.out(), "nothing has run");
		session.stepOver().get(10, TimeUnit.SECONDS);
		assertEquals("paused 1 step", next());
		assertEquals("1", variables.poll().get("x"));

		// the debugger goes: the script runs on, to its own end
		session.close();
		assertEquals(3, remote.status());
		assertTrue(remote.out().startsWith("x is 7 [a b] arg"), remote.out());
	}

	@Test
	public void terminateOverDapEndsAnAttachedScript() throws Exception {
		Path script = dir.resolve("waits.fsh");
		Files.writeString(script, SCRIPT);
		remote = RemoteSide.waiting(script);
		RemoteDebug how = new RemoteDebug("127.0.0.1", remote.port(), Mode.ATTACH, "", Protocol.DAP);
		session = how.connect(listener);
		assertTrue(session.start(how, "", "editor.fsh", List.of(), List.of()).isStopped());
		session.terminate();
		assertTrue(next().startsWith("finished "), "the end of the script is told");
		remote.status();
		assertEquals("", remote.out(), "no command ran");
	}

	@Test
	public void quotesComeOffAString() {
		assertEquals("a b", DapSession.unquote("\"a b\""));
		assertEquals("say \"hi\"\n\tback\\slash", DapSession.unquote("\"say \\\"hi\\\"\\n\\tback\\\\slash\""));
		assertEquals("7", DapSession.unquote("7"));
		assertEquals("array[3]", DapSession.unquote("array[3]"));
		assertEquals("", DapSession.unquote("\"\""));
		assertEquals("\"", DapSession.unquote("\""));
		assertEquals("", DapSession.unquote(null));
	}

	@Test
	public void theProtocolIsKeptWithTheSettings() throws Exception {
		Configuration config = Configuration.createDefault();
		assertEquals(Protocol.LOUPE, config.getRemoteDebug().getProtocol());
		config.setRemoteDebug(new RemoteDebug("h", 1, Mode.ATTACH, "", Protocol.DAP));
		java.io.File file = dir.resolve("Config.xml").toFile();
		config.save(file);
		assertEquals(Protocol.DAP, Configuration.load(file).getRemoteDebug().getProtocol());
	}
}
