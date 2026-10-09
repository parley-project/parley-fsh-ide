package us.bringardner.fsh.ide.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import us.bringardner.fsh.Console;

/** The JavaFX view of fsh's ConsoleIO. */
public class ConsoleViewTest {

	private static KeyEvent key(KeyCode code) {
		return new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false);
	}

	private static String styleAt(ConsoleView v, int pos) {
		var style = v.getArea().getStyleAtPosition(pos+1);
		return style.isEmpty() ? "" : style.iterator().next();
	}

	@Test
	public void outputAndErrors() throws Exception {
		ConsoleView v = Fx.call(ConsoleView::new);
		v.getStdOut().print("out ");
		v.getStdErr().print("err");
		Fx.waitFor(()->v.getArea().getText().equals("out err"));
		assertEquals("", Fx.call(()->styleAt(v, 0)));
		assertEquals("error", Fx.call(()->styleAt(v, 4)));
	}

	@Test
	public void typedLinesGoToStandardInput() throws Exception {
		ConsoleView v = Fx.call(ConsoleView::new);
		Fx.run(()->{
			v.getArea().appendText("hello there");
			v.keyPressed(key(KeyCode.ENTER));
		});
		byte[] buf = new byte[64];
		int n = v.getStdIn().read(buf);
		assertEquals("hello there\n", new String(buf, 0, n, StandardCharsets.UTF_8));
	}

	@Test
	public void outputCantBeDeleted() throws Exception {
		ConsoleView v = Fx.call(ConsoleView::new);
		v.getStdOut().print("output");
		Fx.waitFor(()->v.getArea().getText().equals("output"));
		KeyEvent back = key(KeyCode.BACK_SPACE);
		Fx.run(()->{
			v.getArea().moveTo(6);
			v.keyPressed(back);
		});
		assertTrue(back.isConsumed());
	}

	@Test
	public void outputArrivingWhileTypingKeepsTheTypedText() throws Exception {
		ConsoleView v = Fx.call(ConsoleView::new);
		Fx.run(()->v.getArea().appendText("half"));
		v.getStdOut().print("news\n");
		Fx.waitFor(()->v.getArea().getText().equals("news\nhalf"));
		assertEquals("half", Fx.call(v::inputLine));
	}

	@Test
	public void shellLinesWithHistory() throws Exception {
		ConsoleView v = Fx.call(ConsoleView::new);
		Console console = new Console();
		console.history.clear();
		console.addHistory("ls -l");
		console.addHistory("pwd");
		v.getIo().setPrompt("$ ");
		CompletableFuture<String> line = CompletableFuture.supplyAsync(()->{
			try {
				return v.getIo().readLine(console);
			} catch (Exception e) {
				return "ERR "+e;
			}
		});
		Fx.waitFor(()->v.getArea().getText().endsWith("$ "));
		Fx.run(()->{
			v.keyPressed(key(KeyCode.UP));
			v.keyPressed(key(KeyCode.UP));
		});
		assertEquals("ls -l", Fx.call(v::inputLine));
		Fx.run(()->v.keyPressed(key(KeyCode.ENTER)));
		assertEquals("ls -l", line.get(5, TimeUnit.SECONDS));
	}
}
