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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** A script debugged on the fsh adapter in this JVM, as the IDE does it. */
public class TestLoupeSession {

	/** What the session told the listener, in order. */
	private final BlockingQueue<String> log = new LinkedBlockingQueue<>();
	private final BlockingQueue<Map<String,Object>> variables = new LinkedBlockingQueue<>();
	private final StringBuilder output = new StringBuilder();
	private LoupeSession session;

	private final LoupeSession.Listener listener = new LoupeSession.Listener() {
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

	@AfterEach
	public void close() {
		if( session != null ) {
			session.close();
		}
	}

	private Breakpoint breakpoint(int line) {
		Breakpoint bp = new Breakpoint();
		bp.setLine(line);
		bp.reset();
		return bp;
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

	private static final String SCRIPT = "x=1\nfor i in 1 2 3\ndo\n  x=$((x+i))\ndone\necho \"x is $x\"\n";

	@Test
	public void stopsAtABreakpointAndRunsToTheEnd() throws Exception {
		session = LoupeSession.inProcess(listener);
		Breakpoint bp = breakpoint(3);	// editor line 3 is the 4th: x=$((x+i))
		session.start(SCRIPT, "demo.fsh", List.of(), List.of(bp));

		assertEquals("paused 3 breakpoint", next());
		Map<String,Object> vars = variables.poll();
		assertEquals("1", vars.get("x"));
		assertEquals("1", vars.get("i"));
		assertTrue(session.isPaused());
		assertEquals("11", session.evaluate("$((x+10))"));

		session.resume().get(10, TimeUnit.SECONDS);
		assertEquals("paused 3 breakpoint", next());
		assertEquals("2", variables.poll().get("x"));
		session.syncBreakpoints(List.of());
		session.resume().get(10, TimeUnit.SECONDS);
		assertEquals("finished 0", next());
		synchronized (output) {
			assertTrue(output.toString().contains("stdout:x is 7"), output.toString());
		}
	}

	@Test
	public void stepsAndChangesAVariable() throws Exception {
		session = LoupeSession.inProcess(listener);
		session.start(SCRIPT, "demo.fsh", List.of(), List.of(breakpoint(0)));
		assertEquals("paused 0 breakpoint", next());
		session.stepOver().get(10, TimeUnit.SECONDS);
		assertEquals("paused 1 step", next());
		Map<String,Object> vars = session.setVariable("x", "100");
		assertEquals("100", vars.get("x"));
		session.syncBreakpoints(List.of());
		session.resume().get(10, TimeUnit.SECONDS);
		assertEquals("finished 0", next());
		synchronized (output) {
			assertTrue(output.toString().contains("x is 106"), output.toString());
		}
	}

	@Test
	public void terminateEndsAStoppedScript() throws Exception {
		session = LoupeSession.inProcess(listener);
		session.start(SCRIPT, "demo.fsh", List.of(), List.of(breakpoint(1)));
		assertEquals("paused 1 breakpoint", next());
		session.terminate().get(10, TimeUnit.SECONDS);
		assertEquals("finished -1 killed", next());
	}

	@Test
	public void anUnplaceableBreakpointIsReported() throws Exception {
		session = LoupeSession.inProcess(listener);
		session.start("echo hi\n", "demo.fsh", List.of(), List.of(breakpoint(40)));
		String s = log.poll(20, TimeUnit.SECONDS);
		// (no code at or after line 41; the script runs on and ends)
		while( s != null && !s.startsWith("breakpoint rejected")) {
			s = log.poll(20, TimeUnit.SECONDS);
		}
		assertNotNull(s);
	}

	@Test
	public void cantAskWhileRunning() throws Exception {
		session = LoupeSession.inProcess(listener);
		assertThrows(IllegalStateException.class, ()->session.evaluate("$x"));
		assertThrows(IllegalStateException.class, ()->session.setVariable("x", "1"));
	}
}
