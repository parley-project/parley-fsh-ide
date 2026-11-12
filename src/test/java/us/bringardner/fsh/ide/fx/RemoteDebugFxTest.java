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
package us.bringardner.fsh.ide.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javafx.scene.Node;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.stage.Stage;
import us.bringardner.fsh.ide.core.Breakpoint;
import us.bringardner.fsh.ide.core.Configuration;
import us.bringardner.fsh.ide.core.RemoteDebug;
import us.bringardner.fsh.ide.core.RemoteDebug.Mode;
import us.bringardner.fsh.ide.core.RemoteSide;

/** Run &gt; Debug Remote... in the JavaFX IDE: the dialog, and each of its three ways against a "remote" side in this JVM. */
public class RemoteDebugFxTest {

	@TempDir
	static Path home;

	@TempDir
	Path dir;

	@BeforeAll
	public static void quietHome() {
		System.setProperty("user.home", home.toString());
	}

	private static final String THERE = "x=1\nfor i in 1 2 3\ndo\n  x=$((x+i))\ndone\necho \"x is $x $1\"\nexit 3\n";

	static class TestWindow extends IdeWindow {
		final List<String> problems = new java.util.concurrent.CopyOnWriteArrayList<>();
		RemoteDebug answer;
		RemoteDebug asked;

		TestWindow() {
			super(new Stage(), new IdeWindowTest.MemoryPreferences());
		}

		@Override
		protected boolean confirm(String question) {
			return true;
		}

		@Override
		protected void report(String title, Throwable error) {
			problems.add(title+": "+error);
		}

		@Override
		protected java.util.Optional<RemoteDebug> askRemoteDebug(RemoteDebug last) {
			asked = last;
			return java.util.Optional.ofNullable(answer);
		}
	}

	private static TestWindow window(String code) throws Exception {
		return Fx.call(()->{
			TestWindow w = new TestWindow();
			w.setCode(code, null);
			return w;
		});
	}

	private static void click(TestWindow w, String text) throws Exception {
		Fx.run(()->{
			for(MenuItem m : w.runMenu(0).getItems()) {
				if( text.equals(m.getText())) {
					assertFalse(m.isDisable(), text+" is greyed");
					m.fire();
					return;
				}
			}
			throw new AssertionError("no "+text);
		});
	}

	@Test
	public void theDialogGivesSettingsOnlyWhenTheyAreUsable() throws Exception {
		Fx.run(()->{
			RemoteDebugDialog d = new RemoteDebugDialog(new RemoteDebug());
			Node debug = d.getDialogPane().lookupButton(RemoteDebugDialog.DEBUG);
			assertEquals("Debug Remote", d.getTitle());
			assertEquals("4711", d.port.getText());
			assertTrue(d.editorScript.isSelected());
			assertTrue(debug.isDisable(), "no machine yet");
			assertEquals("", d.problem.getText());
			assertTrue(d.path.isDisabled(), "a path is for a script file there");

			d.host.setText("build1");
			assertFalse(debug.isDisable());
			d.port.setText("port");
			assertTrue(debug.isDisable());
			assertEquals("The port is a number from 1 to 65535", d.problem.getText());
			d.port.setText("5000");
			assertFalse(debug.isDisable());

			d.remoteFile.setSelected(true);
			assertFalse(d.path.isDisabled());
			assertTrue(debug.isDisable());
			assertEquals("Give the script's path on that machine", d.problem.getText());
			assertEquals(RemoteDebug.hint(Mode.REMOTE_FILE), d.hint.getText());
			d.path.setText("/opt/x.fsh");
			assertFalse(debug.isDisable());
			RemoteDebug r = d.settings();
			assertEquals("build1:5000", r.where());
			assertEquals(Mode.REMOTE_FILE, r.getMode());
			assertEquals("/opt/x.fsh", r.getPath());
			// Debug gives them; anything else (Cancel, closing the window) gives nothing
			assertEquals("build1", d.getResultConverter().call(RemoteDebugDialog.DEBUG).getHost());
			assertNull(d.getResultConverter().call(javafx.scene.control.ButtonType.CANCEL));
			assertNull(d.getResultConverter().call(null));

			d.attach.setSelected(true);
			assertTrue(d.path.isDisabled());
			assertFalse(debug.isDisable(), "attach needs no path");
			assertEquals(RemoteDebug.hint(Mode.ATTACH), d.hint.getText());

			// what was used last comes back
			RemoteDebugDialog again = new RemoteDebugDialog(new RemoteDebug("h", 22, Mode.ATTACH, "/p"));
			assertEquals("h", again.host.getText());
			assertEquals("22", again.port.getText());
			assertTrue(again.attach.isSelected());
			assertEquals("/p", again.path.getText());
		});
	}

	@Test
	public void theRunMenuHasDebugRemoteAfterDebug() throws Exception {
		TestWindow w = window("echo hi\n");
		List<String> run = Fx.call(()->{
			List<String> ret = new ArrayList<>();
			javafx.scene.control.MenuBar bar = (javafx.scene.control.MenuBar) w.stage().getScene().lookup(".menu-bar");
			for(Menu m : bar.getMenus()) {
				if( "Run".equals(m.getText())) {
					for(MenuItem i : m.getItems()) {
						ret.add(i.getText());
					}
				}
			}
			return ret;
		});
		assertEquals(List.of("Run", "Debug", "Debug Remote...", "Stop"), run.subList(0, 4));
		// canceled: nothing runs, and the dialog was offered what the configuration has
		Fx.run(w::debugRemote);
		assertNotNull(w.asked);
		assertFalse(Fx.call(w::isRunning));
	}

	@Test
	public void theEditorsScriptDebuggedThere() throws Exception {
		try (RemoteSide remote = RemoteSide.listening()) {
			TestWindow w = window("x=1\nx=2\necho \"x is $x\"\n");
			RemoteDebug how = new RemoteDebug("127.0.0.1", remote.port(), Mode.EDITOR_SCRIPT, "");
			w.answer = how;
			Fx.run(()->{
				w.editor().addBreakpoint(2);
				w.debugRemote();
			});
			Fx.waitFor(w::isPaused);
			assertEquals(2, Fx.call(()->w.editor().getCurrentLine()));
			assertFalse(Fx.call(w::showsRemoteScript), "it is the editor's own script");
			assertFalse(Fx.call(()->w.editor().isEditable()));
			click(w, "Resume");
			Fx.waitFor(()->!w.isRunning());
			Fx.waitFor(()->w.console().getArea().getText().contains("x is 2"));
			assertEquals("Finished, exit code 0", w.statusText());
			assertEquals(List.of(), w.problems);
			// kept for the next time
			RemoteDebug kept = Configuration.getInstance().getRemoteDebug();
			assertEquals(how.where(), kept.where());
			Fx.run(()->w.stage().hide());
		}
	}

	@Test
	public void aScriptFileThereIsShownAndTheUsersOwnPutBack() throws Exception {
		Path script = dir.resolve("there.fsh");
		Files.writeString(script, THERE);
		try (RemoteSide remote = RemoteSide.listening()) {
			TestWindow w = window("echo saved\n");
			Fx.run(()->{
				// the user's own script: changed since it was opened, with a breakpoint that has a condition
				w.editor().getCodeArea().replaceText("echo mine\necho unsaved\n");
				Breakpoint bp = w.editor().addBreakpoint(1);
				bp.setConditional(true);
				bp.setCondition("[ 1 = 1 ]");
				w.argumentsField().setText("arg");
			});
			assertTrue(Fx.call(w::hasChanged));
			String ownTitle = Fx.call(()->w.stage().getTitle());
			RemoteDebug how = new RemoteDebug("127.0.0.1", remote.port(), Mode.REMOTE_FILE, script.toString());
			Fx.run(()->w.debugRemote(how));
			Fx.waitFor(w::isPaused);

			// the script there, stopped before its first command
			assertTrue(Fx.call(w::showsRemoteScript));
			assertEquals(THERE, Fx.call(()->w.editor().getText()));
			assertEquals(0, Fx.call(()->w.editor().getCurrentLine()));
			assertEquals(script+" on 127.0.0.1:"+remote.port()+" - fsh-ide", Fx.call(()->w.stage().getTitle()));
			assertFalse(Fx.call(()->w.editor().isEditable()));
			assertTrue(w.statusText().startsWith("Stopped before the first command"), w.statusText());
			// the document is still the user's own: it is what a save or the exit question is about
			assertEquals("echo mine\necho unsaved\n", Fx.call(()->w.getDocument().body()));
			assertTrue(Fx.call(w::hasChanged));
			assertTrue(Fx.call(()->w.editor().getBreakpoints().isEmpty()), "the user's breakpoints are not this script's");

			Fx.run(()->w.editor().addBreakpoint(5));
			click(w, "Resume");
			Fx.waitFor(()->w.isPaused() && w.editor().getCurrentLine() == 5);
			click(w, "Resume");
			Fx.waitFor(()->!w.isRunning());
			Fx.waitFor(()->w.console().getArea().getText().contains("x is 7 arg"));
			assertEquals("Finished, exit code 3", w.statusText());

			// the user's own script is back, as it was
			assertFalse(Fx.call(w::showsRemoteScript));
			assertEquals("echo mine\necho unsaved\n", Fx.call(()->w.editor().getText()));
			assertEquals(ownTitle, Fx.call(()->w.stage().getTitle()));
			assertTrue(Fx.call(w::hasChanged));
			Breakpoint back = Fx.call(()->w.editor().getBreakpoint(1));
			assertNotNull(back);
			assertTrue(back.isConditional());
			assertEquals("[ 1 = 1 ]", back.getCondition());
			assertEquals(1, Fx.call(()->w.editor().getBreakpoints().size()));
			assertTrue(Fx.call(()->w.editor().isEditable()));
			assertEquals(List.of(), w.problems);
			Fx.run(()->w.stage().hide());
		}
	}

	@Test
	public void attachesToAWaitingScriptAndStopEndsIt() throws Exception {
		Path script = dir.resolve("waits.fsh");
		Files.writeString(script, THERE);
		try (RemoteSide remote = RemoteSide.waiting(script)) {
			TestWindow w = window("echo mine\n");
			RemoteDebug how = new RemoteDebug("127.0.0.1", remote.port(), Mode.ATTACH, "");
			// (redirects are for a script the IDE starts: they don't stop an attach)
			Fx.run(()->{
				w.stdOutField().setText("out.txt");
				w.debugRemote(how);
			});
			Fx.waitFor(w::isPaused);
			assertTrue(Fx.call(w::showsRemoteScript));
			assertEquals(THERE, Fx.call(()->w.editor().getText()));
			assertEquals("", remote.out(), "nothing has run");
			click(w, "Step Over");
			Fx.waitFor(()->w.isPaused() && w.editor().getCurrentLine() == 1);
			click(w, "Stop");
			Fx.waitFor(()->!w.isRunning());
			assertTrue(w.statusText().startsWith("Stopped"), w.statusText());
			assertEquals("echo mine\n", Fx.call(()->w.editor().getText()));
			assertEquals(List.of(), w.problems);
			remote.status();
			assertEquals("", remote.out(), "the script was ended before it wrote anything");
			Fx.run(()->w.stage().hide());
		}
	}

	@Test
	public void whatCanNotStartIsReportedAndNothingIsLeftRunning() throws Exception {
		TestWindow w = window("echo mine\n");
		int port;
		try (ServerSocket s = new ServerSocket(0)) {
			port = s.getLocalPort();
		}
		// nothing listens there
		Fx.run(()->w.debugRemote(new RemoteDebug("127.0.0.1", port, Mode.EDITOR_SCRIPT, "")));
		Fx.waitFor(()->!w.isRunning());
		assertEquals(1, w.problems.size());
		assertTrue(w.problems.get(0).startsWith("Can't connect to 127.0.0.1:"+port), w.problems.toString());

		// an adapter listens, but the path isn't there
		w.problems.clear();
		try (RemoteSide remote = RemoteSide.listening()) {
			Fx.run(()->w.debugRemote(new RemoteDebug("127.0.0.1", remote.port(), Mode.REMOTE_FILE, dir.resolve("none.fsh").toString())));
			Fx.waitFor(()->!w.problems.isEmpty());
			Fx.waitFor(()->!w.isRunning());
			assertTrue(w.problems.get(0).startsWith("Debugging couldn't start on 127.0.0.1:"+remote.port()) && w.problems.get(0).contains("none.fsh"),
					w.problems.toString());
			assertEquals("echo mine\n", Fx.call(()->w.editor().getText()));
			assertTrue(Fx.call(()->w.editor().isEditable()));
		}
		Fx.run(()->w.stage().hide());
	}
}
