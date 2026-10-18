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

import java.util.ArrayList;
import java.util.List;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Spinner;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import us.bringardner.fsh.ide.core.EditorFont;
import us.bringardner.fsh.ide.core.SyntaxColors;
import us.bringardner.fx.gradient.GradientLook;
import us.bringardner.swing.gradient.Theme;

/**
 * View &gt; Editor Font...: the font of the script editors and the console, as in the Swing IDE. A
 * fixed-width family (or the standard monospaced font) and a size, with a preview in the editors'
 * colors. OK keeps it; Cancel changes nothing.
 */
class EditorFontDialog {

	/** The first entry of the list: the platform's standard monospaced font (no family of its own). */
	static final String STANDARD = "Standard monospaced";

	private final Stage stage = new Stage();
	private final ListView<String> families = new ListView<>();
	private final Spinner<Integer> size = new Spinner<>(EditorFont.MIN_SIZE, EditorFont.MAX_SIZE, EditorFont.DEFAULT_SIZE);
	private final ScriptPreview preview = new ScriptPreview();
	private final SyntaxColors colors;
	private EditorFont chosen;
	private boolean accepted;
	private boolean updating;

	EditorFontDialog(Window owner, EditorFont start) {
		stage.setTitle("Editor Font");
		stage.initModality(Modality.APPLICATION_MODAL);
		if( owner != null ) {
			stage.initOwner(owner);
		}
		Theme t = Theme.current();
		colors = SyntaxColors.ofTheme(t.getExtras(), t.isDark());
		build();
		select(start);
	}

	/**
	 * Shows the dialog and waits.
	 *
	 * @return the font chosen, or null if canceled
	 */
	static EditorFont choose(Window owner, EditorFont start) {
		EditorFontDialog d = new EditorFontDialog(owner, start);
		d.stage.showAndWait();
		return d.accepted ? d.chosen : null;
	}

	Stage getStage() {
		return stage;
	}

	EditorFont getChosen() {
		return chosen;
	}

	boolean isAccepted() {
		return accepted;
	}

	/** Shows a font: its family selected (or the standard one) and its size. */
	void select(EditorFont f) {
		updating = true;
		String family = f.getFamily();
		if( family != null && !families.getItems().contains(family) ) {
			// a family not among the fixed-width ones found: still offered
			families.getItems().add(1, family);
		}
		families.getSelectionModel().select(family == null ? STANDARD : family);
		families.scrollTo(families.getSelectionModel().getSelectedIndex());
		size.getValueFactory().setValue(f.getSize());
		updating = false;
		chosen = f;
		preview.show(colors, chosen);
	}

	void ok() {
		accepted = true;
		stage.close();
	}

	void cancel() {
		accepted = false;
		stage.close();
	}

	ListView<String> familyList() {
		return families;
	}

	Spinner<Integer> sizeSpinner() {
		return size;
	}

	ScriptPreview preview() {
		return preview;
	}

	private void changed() {
		if( updating ) {
			return;
		}
		String family = families.getSelectionModel().getSelectedItem();
		chosen = EditorFont.of(family == null || STANDARD.equals(family) ? null : family, size.getValue());
		preview.show(colors, chosen);
	}

	/** The families whose letters are all as wide: "iii" as wide as "WWW". */
	static List<String> fixedWidthFamilies() {
		List<String> ret = new ArrayList<>();
		for(String f : Font.getFamilies()) {
			Font font = Font.font(f, 13);
			if( width("iiiiii", font) > 0 && Math.abs(width("iiiiii", font) - width("WWWWWW", font)) < 0.5 ) {
				ret.add(f);
			}
		}
		return ret;
	}

	private static double width(String s, Font f) {
		Text t = new Text(s);
		t.setFont(f);
		return t.getLayoutBounds().getWidth();
	}

	private void build() {
		families.getItems().add(STANDARD);
		families.getItems().addAll(fixedWidthFamilies());
		families.setPrefSize(220, 260);
		families.getSelectionModel().selectedItemProperty().addListener((o, was, is)->changed());
		size.setEditable(true);
		size.setPrefWidth(80);
		size.valueProperty().addListener((o, was, is)->changed());
		HBox sizeRow = new HBox(6, new Label("Size:"), size);
		sizeRow.setAlignment(Pos.CENTER_LEFT);
		VBox left = new VBox(6, new Label("Font (fixed-width):"), families, sizeRow);
		VBox.setVgrow(families, Priority.ALWAYS);

		preview.setPrefSize(440, 260);
		VBox right = new VBox(6, new Label("Preview"), preview);
		VBox.setVgrow(preview, Priority.ALWAYS);

		Button reset = new Button("Standard font");
		reset.setTooltip(new Tooltip("The standard monospaced font at " + EditorFont.DEFAULT_SIZE + " points"));
		reset.setOnAction(e->select(EditorFont.defaults()));
		Button cancelButton = new Button("Cancel");
		cancelButton.setCancelButton(true);
		cancelButton.setOnAction(e->cancel());
		Button okButton = new Button("OK");
		okButton.setDefaultButton(true);
		okButton.setOnAction(e->ok());
		Region spring = new Region();
		HBox.setHgrow(spring, Priority.ALWAYS);
		HBox buttons = new HBox(8, spring, reset, cancelButton, okButton);

		BorderPane content = new BorderPane();
		content.setPadding(new Insets(14, 16, 10, 16));
		content.setLeft(left);
		BorderPane.setMargin(left, new Insets(0, 12, 0, 0));
		content.setCenter(right);
		content.setBottom(buttons);
		BorderPane.setMargin(buttons, new Insets(10, 0, 0, 0));
		Scene scene = new Scene(content);
		GradientLook.apply(scene);
		stage.setScene(scene);
	}
}
