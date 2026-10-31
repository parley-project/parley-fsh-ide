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

import java.util.Optional;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.Window;
import us.bringardner.fsh.ide.core.Watchpoint;

/**
 * A watchpoint's variable and what stops on it: Read, Write or both. OK stays off until the
 * name is a variable's and one of them is chosen; closing it any other way cancels.
 */
public class WatchpointDialog extends Dialog<Watchpoint> {

	final TextField name = new TextField();
	final CheckBox read = new CheckBox("Read");
	final CheckBox write = new CheckBox("Write");
	final Label problem = new Label();

	public WatchpointDialog(String variable, boolean isRead, boolean isWrite) {
		setTitle("Watchpoint");
		name.setText(variable == null ? "" : variable);
		name.setPromptText("count");
		name.setPrefColumnCount(20);
		read.setSelected(isRead);
		write.setSelected(isWrite);

		GridPane grid = new GridPane();
		grid.setHgap(8);
		grid.setVgap(8);
		grid.setPadding(new Insets(10));
		grid.add(new Label("Variable:"), 0, 0);
		grid.add(name, 1, 0);
		grid.add(new Label("Stop on:"), 0, 1);
		grid.add(new HBox(12, read, write), 1, 1);
		grid.add(problem, 0, 2, 2, 1);
		getDialogPane().setContent(grid);
		getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);

		Node ok = getDialogPane().lookupButton(ButtonType.OK);
		Runnable check = ()->{
			String error = error(name.getText(), read.isSelected(), write.isSelected());
			problem.setText(error == null ? "" : error);
			ok.setDisable(error != null);
		};
		name.textProperty().addListener((o, was, is)->check.run());
		read.selectedProperty().addListener((o, was, is)->check.run());
		write.selectedProperty().addListener((o, was, is)->check.run());
		check.run();

		setResultConverter(b->b == ButtonType.OK ? new Watchpoint(name.getText(), read.isSelected(), write.isSelected()) : null);
		setOnShown(e->name.requestFocus());
	}

	/** What's wrong with the settings, or null if they're fine. */
	static String error(String name, boolean read, boolean write) {
		if( !Watchpoint.isValidName(name)) {
			return "Enter a variable's name.";
		}
		if( !read && !write ) {
			return "Choose Read, Write or both.";
		}
		return null;
	}

	/** Shows the dialog and waits: the watchpoint chosen, or empty if it was canceled. */
	public static Optional<Watchpoint> ask(Window owner, String variable, boolean read, boolean write) {
		WatchpointDialog d = new WatchpointDialog(variable, read, write);
		if( owner != null ) {
			d.initOwner(owner);
		}
		FxBrand.apply(d);
		return d.showAndWait();
	}
}
