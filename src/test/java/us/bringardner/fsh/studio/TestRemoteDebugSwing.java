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
package us.bringardner.fsh.studio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.GraphicsEnvironment;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import us.bringardner.fsh.debug.Breakpoint;
import us.bringardner.fsh.studio.FshStudio;
import us.bringardner.fsh.studio.RemoteDebugDialog;
import us.bringardner.fsh.studio.core.Configuration;
import us.bringardner.fsh.studio.core.RemoteDebug;
import us.bringardner.fsh.studio.core.RemoteSide;
import us.bringardner.fsh.studio.core.RemoteDebug.Mode;
import us.bringardner.fsh.debug.ScriptDocument;

/** Run &gt; Debug Remote... in the Swing IDE: the dialog, and each of its three ways against a "remote" side in this JVM. */
public class TestRemoteDebugSwing {

	@TempDir
	static Path home;

	@TempDir
	Path dir;

	@BeforeAll
	public static void quietHome() {
		System.setProperty("user.home", home.toString());
		// no recent script of the user's: the window shows only the script the test gives it
		FshStudio.usePreferences(new MemoryPreferences());
	}

	private static final String THERE = "x=1\nfor i in 1 2 3\ndo\n  x=$((x+i))\ndone\necho \"x is $x $1\"\nexit 3\n";

	static class TestIde extends FshStudio {
		private static final long serialVersionUID = 1L;
		final List<String> problems = new java.util.concurrent.CopyOnWriteArrayList<>();
		RemoteDebug answer;
		RemoteDebug asked;

		@Override
		public void showError(Throwable e, String msg) {
			problems.add(msg+": "+e);
		}

		@Override
		protected boolean confirm(String question) {
			return true;
		}

		@Override
		protected RemoteDebug askRemoteDebug(RemoteDebug last) {
			asked = last;
			return answer;
		}
	}

	private final List<TestIde> windows = new ArrayList<>();

	/** Disposes every window a test opened. */
	@AfterEach
	public void dispose() throws Exception {
		SwingUtilities.invokeAndWait(()->{
			for(TestIde w : windows) {
				w.dispose();
			}
		});
	}

	private static <T> T onEdt(Callable<T> code) throws Exception {
		Object[] ret = new Object[1];
		Throwable[] failed = new Throwable[1];
		SwingUtilities.invokeAndWait(()->{
			try {
				ret[0] = code.call();
			} catch (Throwable e) {
				failed[0] = e;
			}
		});
		if( failed[0] instanceof Exception ) {
			throw (Exception) failed[0];
		}
		if( failed[0] != null ) {
			throw (Error) failed[0];
		}
		@SuppressWarnings("unchecked")
		T t = (T) ret[0];
		return t;
	}

	private static void waitFor(Callable<Boolean> condition, String what) throws Exception {
		long end = System.currentTimeMillis()+20_000;
		while( !onEdt(condition)) {
			assertTrue(System.currentTimeMillis() < end, what);
			Thread.sleep(20);
		}
	}

	private TestIde window(String code) throws Exception {
		Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "needs a screen");
		return onEdt(()->{
			TestIde f = new TestIde();
			windows.add(f);
			f.setSize(900, 600);
			f.setVisible(true);
			f.setDocument(ScriptDocument.parse(code), null);
			return f;
		});
	}

	private static void click(FshStudio w, String text) throws Exception {
		onEdt(()->{
			for(Component c : w.runPopupItems(0)) {
				if( c instanceof JMenuItem && ((JMenuItem) c).getText().equals(text)) {
					assertTrue(c.isEnabled(), text+" is greyed");
					((JMenuItem) c).doClick();
					return null;
				}
			}
			throw new AssertionError("no "+text);
		});
	}

	@Test
	public void theDialogGivesSettingsOnlyWhenTheyAreUsable() throws Exception {
		Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "needs a screen");
		onEdt(()->{
			RemoteDebugDialog d = new RemoteDebugDialog(null, new RemoteDebug());
			try {
				assertEquals("Debug Remote", d.getTitle());
				assertEquals("4711", d.port.getText());
				assertTrue(d.editorScript.isSelected());
				assertFalse(d.debugButton.isEnabled(), "no machine yet");
				assertEquals(" ", d.problem.getText());
				assertFalse(d.path.isEnabled(), "a path is for a script file there");

				d.host.setText("build1");
				assertTrue(d.debugButton.isEnabled());
				d.port.setText("port");
				assertFalse(d.debugButton.isEnabled());
				assertEquals("The port is a number from 1 to 65535", d.problem.getText());
				d.port.setText("5000");

				d.remoteFile.doClick();
				assertTrue(d.path.isEnabled());
				assertFalse(d.debugButton.isEnabled());
				assertEquals("Give the script's path on that machine", d.problem.getText());
				assertEquals(RemoteDebug.hint(Mode.REMOTE_FILE), d.hint.getText());
				d.path.setText("/opt/x.fsh");
				assertTrue(d.debugButton.isEnabled());
				// nothing until Debug is pressed
				assertNull(d.getResult());
				// Loupe unless DAP is chosen; choosing it says what it costs
				assertTrue(d.loupe.isSelected());
				assertEquals(RemoteDebug.Protocol.LOUPE, d.settings().getProtocol());
				d.dap.doClick();
				assertEquals(RemoteDebug.hint(Mode.REMOTE_FILE)+"\n"+RemoteDebug.hint(RemoteDebug.Protocol.DAP), d.hint.getText());
				d.debugButton.doClick();
				RemoteDebug r = d.getResult();
				assertEquals(RemoteDebug.Protocol.DAP, r.getProtocol());
				assertEquals("build1:5000", r.where());
				assertEquals(Mode.REMOTE_FILE, r.getMode());
				assertEquals("/opt/x.fsh", r.getPath());
			} finally {
				d.dispose();
			}

			// Cancel, and closing the window, give nothing even when the fields are fine
			RemoteDebugDialog c = new RemoteDebugDialog(null, new RemoteDebug("h", 22, Mode.ATTACH, "/p"));
			try {
				assertEquals("h", c.host.getText());
				assertEquals("22", c.port.getText());
				assertTrue(c.attach.isSelected());
				assertFalse(c.path.isEnabled());
				assertTrue(c.debugButton.isEnabled(), "attach needs no path");
				c.cancelButton.doClick();
				assertNull(c.getResult());
			} finally {
				c.dispose();
			}
			RemoteDebugDialog x = new RemoteDebugDialog(null, new RemoteDebug("h", 22, Mode.ATTACH, ""));
			x.dispatchEvent(new java.awt.event.WindowEvent(x, java.awt.event.WindowEvent.WINDOW_CLOSING));
			assertNull(x.getResult());
			x.dispose();
			return null;
		});
	}

	@Test
	public void theRunMenuHasDebugRemoteAfterDebug() throws Exception {
		TestIde w = window("echo hi\n");
		List<String> run = onEdt(()->{
			List<String> ret = new ArrayList<>();
			for(int i=0; i < w.getJMenuBar().getMenuCount(); i++ ) {
				JMenu m = w.getJMenuBar().getMenu(i);
				if( m != null && "Run".equals(m.getText())) {
					for(Component c : m.getMenuComponents()) {
						ret.add(c instanceof JMenuItem ? ((JMenuItem) c).getText() : "-");
					}
				}
			}
			return ret;
		});
		assertEquals(List.of("Run", "Debug", "Debug Remote...", "Stop"), run.subList(0, 4));
		// canceled: nothing runs, and the dialog was offered what the configuration has
		onEdt(()->{
			w.actionDebugRemote();
			return null;
		});
		assertNotNull(w.asked);
		assertFalse(onEdt(w::isScriptRunning));
	}

	@Test
	public void theEditorsScriptDebuggedThere() throws Exception {
		try (RemoteSide remote = RemoteSide.listening()) {
			TestIde w = window("x=1\nx=2\necho \"x is $x\"\n");
			RemoteDebug how = new RemoteDebug("127.0.0.1", remote.port(), Mode.EDITOR_SCRIPT, "");
			w.answer = how;
			onEdt(()->{
				w.editor().addBreakpoint(2);
				w.actionDebugRemote();
				return null;
			});
			waitFor(w::isScriptPaused, "it never stopped at the breakpoint: "+w.problems);
			assertFalse(onEdt(w::showsRemoteScript), "it is the editor's own script");
			assertFalse(onEdt(()->w.editor().isEditable()));
			// Show Value asks the adapter
			onEdt(()->{
				w.showValue("x");
				return null;
			});
			waitFor(()->w.logText().contains("x = 2"), "no value shown: "+w.logText());
			click(w, "Resume");
			waitFor(()->!w.isScriptRunning(), "it never finished");
			assertTrue(onEdt(w::logText).contains("exitCode = 0"), onEdt(w::logText));
			assertEquals(List.of(), w.problems);
			assertTrue(onEdt(()->w.editor().isEditable()));
			// kept for the next time
			assertEquals(how.where(), Configuration.getInstance().getRemoteDebug().where());
		}
	}

	@Test
	public void aScriptFileThereIsShownAndTheUsersOwnPutBack() throws Exception {
		Path script = dir.resolve("there.fsh");
		Files.writeString(script, THERE);
		try (RemoteSide remote = RemoteSide.listening()) {
			TestIde w = window("echo mine\necho more\n");
			onEdt(()->{
				Breakpoint bp = w.editor().addBreakpoint(1);
				bp.setConditional(true);
				bp.setCondition("[ 1 = 1 ]");
				return null;
			});
			String ownTitle = onEdt(w::getTitle);
			RemoteDebug how = new RemoteDebug("127.0.0.1", remote.port(), Mode.REMOTE_FILE, script.toString());
			onEdt(()->{
				w.debugRemote(how);
				return null;
			});
			waitFor(w::isScriptPaused, "it never stopped at its first command: "+w.problems);

			assertTrue(onEdt(w::showsRemoteScript));
			assertEquals(THERE, onEdt(()->w.editor().getText()));
			assertEquals(script+" on 127.0.0.1:"+remote.port(), onEdt(w::getTitle));
			assertFalse(onEdt(()->w.editor().isEditable()));
			// the document is still the user's own: it is what a save or the exit question is about
			assertEquals("echo mine\necho more\n", onEdt(()->w.getDocument().body()));
			assertFalse(onEdt(w::hasChanged));
			assertTrue(onEdt(()->w.editor().getBreakpoints().isEmpty()), "the user's breakpoints are not this script's");

			onEdt(()->w.editor().addBreakpoint(5));
			click(w, "Resume");
			waitFor(()->w.isScriptPaused() && w.logText().contains("Debugging on"), "it never stopped at line 6");
			onEdt(()->{
				w.showValue("x");
				return null;
			});
			waitFor(()->w.logText().contains("x = 7"), "not at the breakpoint after the loop: "+w.logText());
			click(w, "Resume");
			waitFor(()->!w.isScriptRunning(), "it never finished");
			assertTrue(onEdt(w::logText).contains("exitCode = 3"), onEdt(w::logText));

			// the user's own script is back, as it was
			assertFalse(onEdt(w::showsRemoteScript));
			assertEquals("echo mine\necho more\n", onEdt(()->w.editor().getText()));
			assertFalse(onEdt(w::hasChanged));
			waitFor(()->ownTitle.equals(w.getTitle()), "the title is "+w.getTitle());
			Breakpoint back = onEdt(()->w.editor().getBreakpoint(1));
			assertNotNull(back);
			assertTrue(back.isConditional());
			assertEquals("[ 1 = 1 ]", back.getCondition());
			assertEquals(1, onEdt(()->w.editor().getBreakpoints().size()).intValue());
			assertTrue(onEdt(()->w.editor().isEditable()));
			assertEquals(List.of(), w.problems);
		}
	}

	@Test
	public void attachesToAWaitingScriptAndStopEndsIt() throws Exception {
		Path script = dir.resolve("waits.fsh");
		Files.writeString(script, THERE);
		try (RemoteSide remote = RemoteSide.waiting(script)) {
			TestIde w = window("echo mine\n");
			RemoteDebug how = new RemoteDebug("127.0.0.1", remote.port(), Mode.ATTACH, "");
			onEdt(()->{
				w.debugRemote(how);
				return null;
			});
			waitFor(w::isScriptPaused, "it never attached: "+w.problems);
			assertTrue(onEdt(w::showsRemoteScript));
			assertEquals(THERE, onEdt(()->w.editor().getText()));
			assertEquals("", remote.out(), "nothing has run");
			click(w, "Step Over");
			waitFor(()->w.isScriptPaused() && w.showDebugView(true).userTable().getModel().getRowCount() > 0, "it never stepped");
			click(w, "Stop");
			waitFor(()->!w.isScriptRunning(), "it never stopped");
			assertEquals("echo mine\n", onEdt(()->w.editor().getText()));
			assertEquals(List.of(), w.problems);
			remote.status();
			assertEquals("", remote.out(), "the script was ended before it wrote anything");
		}
	}

	/** Over DAP: the editor's script, debugged there through the IDE's DAP client. */
	@Test
	public void theEditorsScriptOverDap() throws Exception {
		try (RemoteSide remote = RemoteSide.listening()) {
			TestIde w = window("x=1\nx=2\necho \"x is $x\"\n");
			onEdt(()->{
				w.editor().addBreakpoint(2);
				w.debugRemote(new RemoteDebug("127.0.0.1", remote.port(), Mode.EDITOR_SCRIPT, "", RemoteDebug.Protocol.DAP));
				return null;
			});
			waitFor(w::isScriptPaused, "it never stopped at the breakpoint: "+w.problems);
			onEdt(()->{
				w.showValue("x");
				return null;
			});
			waitFor(()->w.logText().contains("x = 2"), "no value shown: "+w.logText());
			click(w, "Resume");
			waitFor(()->!w.isScriptRunning(), "it never finished");
			assertTrue(onEdt(w::logText).contains("exitCode = 0"), onEdt(w::logText));
			assertEquals(List.of(), w.problems);
		}
	}

	@Test
	public void whatCanNotStartIsReportedAndNothingIsLeftRunning() throws Exception {
		TestIde w = window("echo mine\n");
		int port;
		try (ServerSocket s = new ServerSocket(0)) {
			port = s.getLocalPort();
		}
		onEdt(()->{
			w.debugRemote(new RemoteDebug("127.0.0.1", port, Mode.EDITOR_SCRIPT, ""));
			return null;
		});
		waitFor(()->!w.isScriptRunning(), "still running");
		assertEquals(1, w.problems.size());
		assertTrue(w.problems.get(0).startsWith("Can't connect to 127.0.0.1:"+port), w.problems.toString());

		w.problems.clear();
		try (RemoteSide remote = RemoteSide.listening()) {
			onEdt(()->{
				w.debugRemote(new RemoteDebug("127.0.0.1", remote.port(), Mode.REMOTE_FILE, dir.resolve("none.fsh").toString()));
				return null;
			});
			waitFor(()->!w.problems.isEmpty(), "nothing reported");
			waitFor(()->!w.isScriptRunning(), "still running");
			assertTrue(w.problems.get(0).startsWith("Debugging couldn't start on 127.0.0.1:"+remote.port()) && w.problems.get(0).contains("none.fsh"),
					w.problems.toString());
			assertEquals("echo mine\n", onEdt(()->w.editor().getText()));
			assertTrue(onEdt(()->w.editor().isEditable()));
		}
	}
}
