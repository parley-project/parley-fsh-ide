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
package us.bringardner.fsh.studio.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import us.bringardner.fsh.Console;
import us.bringardner.fsh.debug.Breakpoint;
import us.bringardner.fsh.loupe.FshAdapterMain;

/**
 * For the IDEs' Debug Remote tests: what would run on the other machine, in this JVM on the
 * loopback address. Either fsh-loupe's adapter listening on a port ({@link #listening()}, as
 * FshAdapterMain --listen), or a shell started with fsh --wait-for-debugger
 * ({@link #waiting(Path)}). Close it when the test is done: it waits for its thread to end.
 */
public final class RemoteSide implements AutoCloseable {

	private ServerSocket adapter;
	private CompletableFuture<Void> served;
	private CompletableFuture<Integer> shell;
	private final ByteArrayOutputStream out = new ByteArrayOutputStream();
	private final ByteArrayOutputStream err = new ByteArrayOutputStream();
	private int port;

	private RemoteSide() {
	}

	/** FshAdapterMain --listen 0 */
	public static RemoteSide listening() throws IOException {
		RemoteSide ret = new RemoteSide();
		ServerSocket server = FshAdapterMain.open("0");
		ret.adapter = server;
		ret.port = server.getLocalPort();
		ret.served = CompletableFuture.runAsync(() -> {
			try {
				FshAdapterMain.serve(server);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		});
		return ret;
	}

	/** fsh --wait-for-debugger 0 script arg */
	public static RemoteSide waiting(Path script) throws Exception {
		RemoteSide ret = new RemoteSide();
		Console.exitJvm = false;
		CompletableFuture<Integer> shell = new CompletableFuture<>();
		ret.shell = shell;
		Thread t = new Thread(() -> {
			try {
				Console c = new Console();
				c.setStdOut(new PrintStream(ret.out, true));
				c.setStdErr(new PrintStream(ret.err, true));
				c.setStdIn(new ByteArrayInputStream(new byte[0]));
				shell.complete(c.execute("--wait-for-debugger", "0", script.toString(), "arg"));
			} catch (Throwable e) {
				shell.completeExceptionally(e);
			}
		}, "test-shell");
		t.setDaemon(true);
		t.start();
		Pattern port = Pattern.compile("waiting for a debugger on port (\\d+)");
		long end = System.currentTimeMillis()+20000;
		for(;;) {
			Matcher m = port.matcher(ret.err());
			if( m.find()) {
				ret.port = Integer.parseInt(m.group(1));
				return ret;
			}
			if( shell.isDone() || System.currentTimeMillis() > end ) {
				throw new AssertionError("the shell isn't waiting for a debugger: "+ret.err());
			}
			Thread.sleep(20);
		}
	}

	public int port() {
		return port;
	}

	/** What the waiting shell's script wrote. */
	public String out() {
		return new String(out.toByteArray(), StandardCharsets.UTF_8);
	}

	public String err() {
		return new String(err.toByteArray(), StandardCharsets.UTF_8);
	}

	/** The waiting shell's status, once its script has ended. */
	public int status() throws Exception {
		return shell.get(20, TimeUnit.SECONDS);
	}

	@Override
	public void close() throws Exception {
		if( adapter != null ) {
			adapter.close();
			served.get(20, TimeUnit.SECONDS);
		}
		if( shell != null ) {
			if( !shell.isDone()) {
				// a test that failed before it attached: the shell would wait for ever (with its port and
				// thread), so a debugger attaches and lets it run to its end
				release();
			}
			shell.get(20, TimeUnit.SECONDS);
		}
	}

	private void release() {
		RemoteDebug how = new RemoteDebug("127.0.0.1", port, RemoteDebug.Mode.ATTACH, "");
		try (AdapterSession s = how.connect(new AdapterSession.Listener() {
			@Override
			public void paused(int line, String reason, java.util.Map<String,Object> variables) {
			}

			@Override
			public void output(String category, String text) {
			}

			@Override
			public void breakpoint(Breakpoint bp, String status, int line, String message) {
			}

			@Override
			public void finished(int exitCode, boolean killed, Exception error) {
			}
		})) {
			s.start(how, "", "", java.util.List.of(), java.util.List.of());
			// (closing the session lets it run on)
		} catch (Exception e) {
			// a debugger attached already (the port is closed): it runs on when that one goes
		}
	}
}
