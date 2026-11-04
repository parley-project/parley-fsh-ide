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
import us.bringardner.fsh.ide.core.BreakpointHelp;

/** A breakpoint's properties: on or off, a condition, a hit count; or delete it. */
public class BreakpointDialog extends Dialog<BreakpointDialog.Result> {

	public enum Result { CHANGED, DELETE, CANCELED }

	public static final ButtonType DELETE = new ButtonType("Delete", ButtonData.LEFT);

	private final Breakpoint bp;
	final CheckBox enabled = new CheckBox("Enabled");
	final CheckBox conditional = new CheckBox("Stop when the expression is true:");
	final TextField condition = new TextField();
	final CheckBox hitCount = new CheckBox("Stop from hit number:");
	final Spinner<Integer> hits = new Spinner<>(1, 1_000_000, 1);

	public BreakpointDialog(Breakpoint bp) {
		this.bp = bp;
		setTitle("Breakpoint on line "+(bp.getLine()+1));
		enabled.setSelected(bp.isEnabled(false));
		conditional.setSelected(bp.isConditional());
		condition.setText(bp.getCondition());
		condition.setPromptText(Breakpoint.CONDITION_EXAMPLE);
		condition.setPrefColumnCount(24);
		condition.disableProperty().bind(conditional.selectedProperty().not());
		hitCount.setSelected(bp.isHitCount());
		hits.getValueFactory().setValue(Math.max(1, bp.getHitCount()));
		hits.setEditable(true);
		hitCount.setTooltip(new javafx.scene.control.Tooltip(Breakpoint.HIT_COUNT_HINT));
		hits.setTooltip(new javafx.scene.control.Tooltip(Breakpoint.HIT_COUNT_HINT));
		hits.disableProperty().bind(hitCount.selectedProperty().not());

		GridPane grid = new GridPane();
		grid.setHgap(8);
		grid.setVgap(8);
		grid.setPadding(new Insets(10));
		// the line's code, and the info button: how the condition and the hit count stop it
		javafx.scene.control.Button info = FxIcons.iconOnly(new javafx.scene.control.Button(), "info.png", BreakpointHelp.TITLE);
		info.setOnAction(e->showHelp());
		javafx.scene.layout.Region gap = new javafx.scene.layout.Region();
		javafx.scene.layout.HBox.setHgrow(gap, javafx.scene.layout.Priority.ALWAYS);
		javafx.scene.layout.HBox top = new javafx.scene.layout.HBox(8, new Label(bp.getCode()), gap, info);
		top.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
		grid.add(top, 0, 0, 2, 1);
		infoButton = info;
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

	/** the info button (for the tests) */
	javafx.scene.control.Button infoButton;

	/**
	 * The info button: how a breakpoint's condition and hit count stop it, as the Debug Adapter
	 * Protocol says, with the specification's reference (and a link that opens it).
	 */
	static void showHelp() {
		javafx.scene.control.Alert a = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
		a.setTitle(BreakpointHelp.TITLE);
		a.setHeaderText(BreakpointHelp.TITLE);
		a.setGraphic(new javafx.scene.image.ImageView(FxIcons.image("info@2x.png")));
		javafx.scene.control.TextArea text = new javafx.scene.control.TextArea(BreakpointHelp.TEXT);
		text.setEditable(false);
		text.setWrapText(true);
		text.setPrefColumnCount(60);
		text.setPrefRowCount(22);
		javafx.scene.control.Hyperlink link = new javafx.scene.control.Hyperlink("Open the Specification");
		link.setOnAction(e->new Thread(()->{
			try {
				java.awt.Desktop.getDesktop().browse(new java.net.URI(BreakpointHelp.SPEC_URL));
			} catch (Exception ex) {
				// (the reference is in the text)
			}
		}, "open-spec").start());
		a.getDialogPane().setContent(new javafx.scene.layout.VBox(6, text, link));
		a.setResizable(true);
		a.showAndWait();
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
		FxBrand.apply(d);
		return d.showAndWait().orElse(Result.CANCELED);
	}
}
