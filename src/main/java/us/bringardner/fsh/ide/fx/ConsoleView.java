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

import java.io.InputStream;
import java.io.PrintStream;

import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.StyleClassedTextArea;

import javafx.application.Platform;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import us.bringardner.fsh.ConsoleIO;
import us.bringardner.fsh.ConsoleSignal;

/**
 * The JavaFX console: shows what's written to its standard output (and standard error, in
 * red), and sends what's typed to the shell's line reader or, while a script runs, to its
 * standard input. The workings are fsh's {@link ConsoleIO}; this is its JavaFX view, as
 * fsh's ConsolePanel is its Swing view.
 */
public class ConsoleView extends StackPane implements ConsoleIO.View {

	/** Text kept; older text is removed from the top. */
	static final int MAX_LENGTH = 2_000_000;

	private final ConsoleIO io = new ConsoleIO(Platform::runLater, Platform::isFxApplicationThread);
	private final StyleClassedTextArea area = new StyleClassedTextArea();
	// where the line being typed starts; text before it is output and can't be edited
	private int inputStart;

	public ConsoleView() {
		area.getStyleClass().add("console");
		area.setWrapText(true);
		// what the user types has no style of its own, whatever it's next to
		area.setUseInitialStyleForInsertion(true);
		area.addEventFilter(KeyEvent.KEY_PRESSED, this::keyPressed);
		area.addEventFilter(KeyEvent.KEY_TYPED, e->{
			// typing goes on the input line
			if( area.getCaretPosition() < inputStart || area.getSelection().getStart() < inputStart ) {
				area.moveTo(area.getLength());
			}
		});
		getChildren().add(new VirtualizedScrollPane<>(area));
		io.setView(this);
	}

	void keyPressed(KeyEvent e) {
		KeyCode key = e.getCode();
		int pos = area.getCaretPosition();
		if( e.isControlDown()) {
			ConsoleSignal signal = key == KeyCode.C ? ConsoleSignal.Interupt
					: key == KeyCode.BACK_SLASH ? ConsoleSignal.Terminate
					: key == KeyCode.Z ? ConsoleSignal.Suspend
					: key == KeyCode.D ? ConsoleSignal.Quit
					: null;
			if( signal != null ) {
				e.consume();
				io.signal(signal);
				return;
			}
		}
		switch (key) {
		case BACK_SPACE:
		case LEFT:
			if( pos <= inputStart && area.getSelection().getLength() == 0 ) {
				e.consume();
			}
			break;
		case DELETE:
			if( pos < inputStart ) {
				e.consume();
			}
			break;
		case UP:
		case DOWN:
			e.consume();
			if( io.isReadingLine()) {
				String cmd = key == KeyCode.UP ? io.historyUp() : io.historyDown();
				if( cmd != null ) {
					setInputLine(cmd);
				}
			}
			break;
		case ENTER:
			e.consume();
			submit();
			break;
		default:
			break;
		}
	}

	/** Sends the line being typed (as Enter does). */
	void submit() {
		String line = inputLine();
		area.appendText("\n");
		if( line.endsWith("\\")) {
			// continued on the next line
			area.moveTo(area.getLength());
			return;
		}
		inputStart = area.getLength();
		area.moveTo(inputStart);
		io.submitLine(line);
	}

	String inputLine() {
		return area.getText(inputStart, area.getLength());
	}

	private void setInputLine(String text) {
		area.replaceText(inputStart, area.getLength(), text);
		area.moveTo(area.getLength());
	}

	private void appendStyled(String text, String style) {
		int start = area.getLength();
		area.appendText(text);
		if( style != null && !text.isEmpty()) {
			area.setStyleClass(start, start+text.length(), style);
		}
	}

	// ---- ConsoleIO.View

	@Override
	public void append(String text, boolean error) {
		// output goes before anything being typed, which stays on the input line
		String typed = inputLine();
		area.replaceText(inputStart, area.getLength(), "");
		appendStyled(text, error ? "error" : null);
		int extra = area.getLength() - MAX_LENGTH;
		if( extra > 0 ) {
			area.replaceText(0, extra, "");
		}
		inputStart = area.getLength();
		area.appendText(typed);
		area.moveTo(area.getLength());
		area.requestFollowCaret();
	}

	@Override
	public void startLine(String prompt, String text) {
		String all = area.getText();
		if( !all.isEmpty() && !all.endsWith("\n")) {
			area.appendText("\n");
		}
		appendStyled(prompt, "prompt");
		inputStart = area.getLength();
		area.appendText(text);
		area.moveTo(area.getLength());
		area.requestFollowCaret();
	}

	@Override
	public void clearText() {
		area.clear();
		inputStart = 0;
	}

	/** Clears the console, including output not shown yet. */
	public void clear() {
		io.clear();
	}

	public ConsoleIO getIo() {
		return io;
	}

	public PrintStream getStdOut() {
		return io.getStdOut();
	}

	public PrintStream getStdErr() {
		return io.getStdErr();
	}

	public InputStream getStdIn() {
		return io.getStdIn();
	}

	StyleClassedTextArea getArea() {
		return area;
	}
}
