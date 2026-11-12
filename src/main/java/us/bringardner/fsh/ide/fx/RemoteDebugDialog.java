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

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import us.bringardner.fsh.ide.core.RemoteDebug;
import us.bringardner.fsh.ide.core.RemoteDebug.Mode;

/**
 * Run &gt; Debug Remote...: the machine and port, what to debug there, and (for a script file
 * there) its path. Debug gives the settings; closing it any other way gives nothing.
 */
public class RemoteDebugDialog extends Dialog<RemoteDebug> {

	public static final ButtonType DEBUG = new ButtonType("Debug", ButtonData.OK_DONE);

	/** What the dialog says about the connection (the Swing dialog says the same). */
	public static final String WARNING = RemoteDebug.WARNING;

	final TextField host = new TextField();
	final TextField port = new TextField();
	final ToggleGroup modes = new ToggleGroup();
	final RadioButton editorScript = new RadioButton(Mode.EDITOR_SCRIPT.getLabel());
	final RadioButton remoteFile = new RadioButton(Mode.REMOTE_FILE.getLabel());
	final RadioButton attach = new RadioButton(Mode.ATTACH.getLabel());
	final TextField path = new TextField();
	final Label hint = new Label();
	final Label problem = new Label();

	public RemoteDebugDialog(RemoteDebug last) {
		setTitle("Debug Remote");
		host.setText(last.getHost());
		host.setPromptText("name or address");
		host.setPrefColumnCount(22);
		port.setText(String.valueOf(last.getPort()));
		port.setPrefColumnCount(6);
		path.setText(last.getPath());
		path.setPromptText("/path/on/that/machine/script.fsh");
		for(RadioButton b : new RadioButton[] {editorScript, remoteFile, attach}) {
			b.setToggleGroup(modes);
		}
		button(last.getMode()).setSelected(true);
		path.disableProperty().bind(remoteFile.selectedProperty().not());
		hint.setWrapText(true);
		hint.setMaxWidth(440);
		problem.setWrapText(true);
		problem.setMaxWidth(440);
		problem.setStyle("-fx-text-fill: -fx-accent;");
		Label warning = new Label(WARNING);
		warning.setWrapText(true);
		warning.setMaxWidth(440);

		GridPane grid = new GridPane();
		grid.setHgap(8);
		grid.setVgap(8);
		grid.setPadding(new Insets(10));
		grid.add(new Label("Machine:"), 0, 0);
		grid.add(host, 1, 0);
		grid.add(new Label("Port:"), 0, 1);
		grid.add(port, 1, 1);
		grid.add(new Label("Debug:"), 0, 2);
		grid.add(new VBox(6, editorScript, remoteFile, attach), 1, 2);
		grid.add(new Label("Script's path:"), 0, 3);
		grid.add(path, 1, 3);
		grid.add(hint, 0, 4, 2, 1);
		grid.add(warning, 0, 5, 2, 1);
		grid.add(problem, 0, 6, 2, 1);
		getDialogPane().setContent(grid);
		getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, DEBUG);

		Node debug = getDialogPane().lookupButton(DEBUG);
		Runnable check = ()->{
			RemoteDebug now = settings();
			String p = now.problem();
			// (an empty machine is where everyone starts: the button says enough)
			problem.setText(p == null || now.getHost().isEmpty() ? "" : p);
			debug.setDisable(p != null);
			hint.setText(RemoteDebug.hint(now.getMode()));
		};
		host.textProperty().addListener((o, a, b)->check.run());
		port.textProperty().addListener((o, a, b)->check.run());
		path.textProperty().addListener((o, a, b)->check.run());
		modes.selectedToggleProperty().addListener((o, a, b)->check.run());
		check.run();
		// closing the dialog any way but Debug is Cancel
		setResultConverter(b->b == DEBUG && settings().problem() == null ? settings() : null);
		javafx.application.Platform.runLater(host::requestFocus);
	}

	RadioButton button(Mode mode) {
		switch(mode) {
		case REMOTE_FILE:
			return remoteFile;
		case ATTACH:
			return attach;
		default:
			return editorScript;
		}
	}

	/** What the fields hold now (a port that isn't one is 0, which {@link RemoteDebug#problem()} reports). */
	RemoteDebug settings() {
		Mode mode = remoteFile.isSelected() ? Mode.REMOTE_FILE : attach.isSelected() ? Mode.ATTACH : Mode.EDITOR_SCRIPT;
		return new RemoteDebug(host.getText(), Math.max(0, RemoteDebug.parsePort(port.getText())), mode, path.getText());
	}

	/** Shows the dialog and waits: the settings to debug with, or empty if it was canceled. */
	public static java.util.Optional<RemoteDebug> ask(Window owner, RemoteDebug last) {
		RemoteDebugDialog d = new RemoteDebugDialog(last);
		if( owner != null ) {
			d.initOwner(owner);
		}
		FxBrand.apply(d);
		return d.showAndWait();
	}
}
