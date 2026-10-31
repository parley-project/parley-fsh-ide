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

import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Window;
import us.bringardner.fsh.ide.core.Variable;

/**
 * A debugged script's variable's value in a multi-line editor. While the script is paused, OK
 * (or Ctrl/Cmd+Enter) gives what is typed; otherwise the value can only be read.
 */
public class VariableDialog extends Dialog<String> {

	final TextArea text = new TextArea();

	public VariableDialog(Variable v, boolean editable) {
		setTitle(v.getName());
		setResizable(true);
		text.setText(DebugPanel.valueText(v.getValue()));
		text.setEditable(editable);
		text.setPrefColumnCount(60);
		text.setPrefRowCount(12);
		text.setStyle("-fx-font-family: monospace;");
		getDialogPane().setContent(text);
		if( editable ) {
			getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
			// Enter adds a line, so Ctrl+Enter (Cmd+Enter on the Mac) is OK
			text.addEventFilter(KeyEvent.KEY_PRESSED, e->{
				if( e.getCode() == KeyCode.ENTER && e.isShortcutDown()) {
					e.consume();
					((Button) getDialogPane().lookupButton(ButtonType.OK)).fire();
				}
			});
		} else {
			getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
		}
		setResultConverter(b->b == ButtonType.OK ? text.getText() : null);
		setOnShown(e->{
			text.requestFocus();
			text.positionCaret(0);
		});
	}

	/** Shows the dialog for v and waits: the text typed, or empty for Cancel (or a read-only value). */
	public static Optional<String> edit(Window owner, Variable v, boolean editable) {
		VariableDialog d = new VariableDialog(v, editable);
		if( owner != null ) {
			d.initOwner(owner);
		}
		FxBrand.apply(d);
		return d.showAndWait();
	}
}
