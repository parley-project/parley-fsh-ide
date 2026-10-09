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
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.Window;
import us.bringardner.fsh.ide.core.Breakpoint;

/** A breakpoint's properties: on or off, a condition, a hit count; or delete it. */
public class BreakpointDialog extends Dialog<BreakpointDialog.Result> {

	public enum Result { CHANGED, DELETE, CANCELED }

	public static final ButtonType DELETE = new ButtonType("Delete", ButtonData.LEFT);

	private final Breakpoint bp;
	final CheckBox enabled = new CheckBox("Enabled");
	final CheckBox conditional = new CheckBox("Stop only when this command succeeds:");
	final TextField condition = new TextField();
	final CheckBox hitCount = new CheckBox("Stop only on hit number:");
	final Spinner<Integer> hits = new Spinner<>(1, 1_000_000, 1);

	public BreakpointDialog(Breakpoint bp) {
		this.bp = bp;
		setTitle("Breakpoint on line "+(bp.getLine()+1));
		enabled.setSelected(bp.isEnabled(false));
		conditional.setSelected(bp.isConditional());
		condition.setText(bp.getCondition());
		condition.setPromptText("[ $i -gt 3 ]");
		condition.setPrefColumnCount(24);
		condition.disableProperty().bind(conditional.selectedProperty().not());
		hitCount.setSelected(bp.isHitCount());
		hits.getValueFactory().setValue(Math.max(1, bp.getHitCount()));
		hits.setEditable(true);
		hits.disableProperty().bind(hitCount.selectedProperty().not());

		GridPane grid = new GridPane();
		grid.setHgap(8);
		grid.setVgap(8);
		grid.setPadding(new Insets(10));
		grid.add(new Label(bp.getCode()), 0, 0, 2, 1);
		grid.add(enabled, 0, 1, 2, 1);
		grid.add(conditional, 0, 2);
		grid.add(condition, 1, 2);
		grid.add(hitCount, 0, 3);
		grid.add(hits, 1, 3);
		getDialogPane().setContent(grid);
		getDialogPane().getButtonTypes().addAll(DELETE, ButtonType.CANCEL, ButtonType.OK);
		setResultConverter(b->{
			if( b == ButtonType.OK ) {
				apply();
				return Result.CHANGED;
			}
			return b == DELETE ? Result.DELETE : Result.CANCELED;
		});
	}

	/** Puts what's chosen into the breakpoint. */
	void apply() {
		bp.setEnabled(enabled.isSelected());
		bp.setConditional(conditional.isSelected() && !condition.getText().isBlank());
		bp.setCondition(condition.getText().trim());
		bp.setHitCount(hitCount.isSelected());
		bp.setHitCount(hitCount.isSelected() ? hits.getValue() : -1);
	}

	/** Shows the dialog for bp and waits. */
	public static Result edit(Window owner, Breakpoint bp) {
		BreakpointDialog d = new BreakpointDialog(bp);
		if( owner != null ) {
			d.initOwner(owner);
		}
		return d.showAndWait().orElse(Result.CANCELED);
	}
}
