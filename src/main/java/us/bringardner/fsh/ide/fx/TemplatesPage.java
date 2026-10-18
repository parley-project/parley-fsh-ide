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

import java.util.prefs.Preferences;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import us.bringardner.fsh.ide.EditorThemeExtension;
import us.bringardner.fsh.ide.core.Configuration;
import us.bringardner.fsh.ide.core.EditorFont;
import us.bringardner.fsh.ide.core.Template;
import us.bringardner.fsh.ide.core.TemplateList;
import us.bringardner.fx.dialog.FxSettingsPage;
import us.bringardner.swing.gradient.Theme;

/**
 * The Templates tab of Settings in the JavaFX IDE, as in the Swing one: the templates offered as you
 * type, to change, add to or delete, with Restore Defaults to put the built-in ones back. The
 * changes are kept when the dialog's OK is pressed; the editors read the templates each time they
 * complete, so they offer them at once.
 */
class TemplatesPage implements FxSettingsPage {

	/** The id it is registered under in fx-widgets' FxSettingsPages. */
	static final String ID = "fsh-ide.templates";

	/** What a template's code can have, shown under it. */
	static final String HINT = "${name} is a field to fill in (fields with the same name change together), "
			+ "${cursor} is where the caret goes, and $$ is a $.";

	private final TemplateList templates = new TemplateList(Configuration.getInstance().getTemplates());
	private final ListView<Template> list = new ListView<>();
	private final TextField name = new TextField();
	private final TextField description = new TextField();
	private final ScriptPreview code = new ScriptPreview();
	private final Label check = new Label(" ");
	private final Button deleteButton = new Button("Delete");
	private final BorderPane pane = new BorderPane();
	/** True while the fields are being filled in from the list, so it isn't taken as an edit. */
	private boolean updating;

	TemplatesPage() {
		build();
		fill();
		if( !list.getItems().isEmpty() ) {
			list.getSelectionModel().select(0);
		}
	}

	@Override
	public String getTitle() {
		return "Templates";
	}

	@Override
	public Node getNode() {
		return pane;
	}

	@Override
	public String problem() {
		for(int i = 0; i < templates.size(); i++) {
			String p = TemplateList.problem(templates.get(i));
			if( p != null ) {
				list.getSelectionModel().select(i);
				return p;
			}
		}
		return null;
	}

	@Override
	public void ok() throws Exception {
		if( !templates.isChanged() ) {
			return;
		}
		Configuration c = Configuration.getInstance();
		c.setTemplates(templates.toSave());
		c.save();
	}

	// ---------------------------------------------------------------- actions

	/** New: a template to fill in, selected, with its name ready to type over. */
	void actionNew() {
		String n = "new-template";
		for(int i = 2; contains(n); i++) {
			n = "new-template-" + i;
		}
		int at = templates.add(new Template(n, "", ""));
		fill();
		list.getSelectionModel().select(at);
		list.scrollTo(at);
		name.requestFocus();
		name.selectAll();
	}

	/** Delete: the selected template, once the user says so. */
	void actionDelete() {
		int i = list.getSelectionModel().getSelectedIndex();
		if( i < 0 ) {
			return;
		}
		Template t = templates.get(i);
		String message = "Delete the template \"" + t.getName() + "\"?"
				+ (Configuration.isBuiltIn(t) ? "\nRestore Defaults brings back a built-in one." : "");
		if( confirm("Delete Template", message) ) {
			delete(i);
		}
	}

	/** Deletes the template at i (no questions), and selects the one after it. */
	void delete(int i) {
		templates.remove(i);
		fill();
		if( !list.getItems().isEmpty() ) {
			list.getSelectionModel().select(Math.min(i, list.getItems().size() - 1));
		} else {
			show(null);
		}
	}

	/** Restore Defaults: the built-in templates as they come, once the user says so. */
	void actionRestoreDefaults() {
		if( confirm("Restore Defaults", "Put the built-in templates back as they come?\nYour own templates stay.") ) {
			restoreDefaults();
		}
	}

	void restoreDefaults() {
		templates.restoreDefaults();
		fill();
		if( !list.getItems().isEmpty() ) {
			list.getSelectionModel().select(0);
		}
	}

	/** Asks; tests override it. */
	protected boolean confirm(String title, String message) {
		Alert a = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.OK, ButtonType.CANCEL);
		if( pane.getScene() != null ) {
			a.initOwner(pane.getScene().getWindow());
		}
		a.setTitle(title);
		a.setHeaderText(null);
		return a.showAndWait().filter(ButtonType.OK::equals).isPresent();
	}

	TemplateList templates() {
		return templates;
	}

	ListView<Template> list() {
		return list;
	}

	TextField nameField() {
		return name;
	}

	TextField descriptionField() {
		return description;
	}

	ScriptEditor codeEditor() {
		return code.editor();
	}

	Label checkLabel() {
		return check;
	}

	// ---------------------------------------------------------------- the parts

	private boolean contains(String n) {
		return templates.items().stream().anyMatch(t->n.equals(t.getName()));
	}

	private void fill() {
		updating = true;
		list.getItems().setAll(templates.items());
		updating = false;
	}

	/** Shows a template in the fields (null: none, and they're disabled). */
	private void show(Template t) {
		updating = true;
		name.setText(t == null ? "" : t.getName());
		description.setText(t == null ? "" : t.getDescription());
		code.editor().setText(t == null ? "" : t.getCode());
		code.editor().getCodeArea().moveTo(0);
		name.setDisable(t == null);
		description.setDisable(t == null);
		code.setDisable(t == null);
		deleteButton.setDisable(t == null);
		updating = false;
		showCheck(t);
	}

	private void showCheck(Template t) {
		String c = t == null ? null : TemplateList.check(t);
		String p = t == null ? null : TemplateList.problem(t);
		check.setText(p != null ? "⚠ " + p : c == null ? (t == null ? " " : "✓ The code parses")
				: "⚠ " + c + " (fine if it's only part of a script)");
	}

	/** The fields changed: the selected template becomes what they say. */
	private void edited() {
		int i = list.getSelectionModel().getSelectedIndex();
		if( updating || i < 0 ) {
			return;
		}
		Template t = new Template(name.getText().trim(), description.getText().trim(), code.editor().getText());
		int at = templates.set(i, t);
		updating = true;
		list.getItems().setAll(templates.items());
		list.getSelectionModel().select(at);
		updating = false;
		showCheck(t);
	}

	private void build() {
		pane.setPadding(new Insets(10));

		list.setPrefSize(300, 380);
		list.setCellFactory(v->new ListCell<>() {
			@Override
			protected void updateItem(Template t, boolean empty) {
				super.updateItem(t, empty);
				if( empty || t == null ) {
					setText(null);
					setGraphic(null);
					return;
				}
				Text n = new Text(t.getName());
				n.setStyle("-fx-font-weight: bold;");
				n.getStyleClass().add("template-name");
				String d = t.getDescription() == null || t.getDescription().isBlank() || t.getDescription().equals(t.getName())
						? "" : "  " + t.getDescription();
				Text desc = new Text(d);
				desc.setOpacity(0.65);
				n.fillProperty().bind(textFillProperty());
				desc.fillProperty().bind(textFillProperty());
				setText(null);
				setGraphic(new TextFlow(n, desc));
			}
		});
		list.getSelectionModel().selectedIndexProperty().addListener((o, was, is)->{
			if( !updating ) {
				int i = is.intValue();
				show(i < 0 || i >= templates.size() ? null : templates.get(i));
			}
		});

		Button newButton = new Button("New");
		newButton.setTooltip(new Tooltip("Add a template"));
		newButton.setOnAction(e->actionNew());
		deleteButton.setTooltip(new Tooltip("Delete the selected template"));
		deleteButton.setOnAction(e->actionDelete());
		Button restore = new Button("Restore Defaults");
		restore.setTooltip(new Tooltip("Put the built-in templates back as they come; your own stay"));
		restore.setOnAction(e->actionRestoreDefaults());
		HBox listButtons = new HBox(6, newButton, deleteButton, restore);
		VBox left = new VBox(6, list, listButtons);
		VBox.setVgrow(list, Priority.ALWAYS);
		pane.setLeft(left);
		BorderPane.setMargin(left, new Insets(0, 12, 0, 0));

		name.setTooltip(new Tooltip("What you type to use the template"));
		description.setTooltip(new Tooltip("Shown in the list of completions"));
		code.editor().getCodeArea().setEditable(true);
		code.setPrefSize(460, 300);
		code.show(EditorThemeExtension.colorsOf(Theme.current()),
				EditorFont.load(Preferences.userRoot().node(IdeWindow.PREFERENCES_NODE)));

		GridPane form = new GridPane();
		form.setHgap(8);
		form.setVgap(6);
		form.add(new Label("Name"), 0, 0);
		form.add(name, 1, 0);
		form.add(new Label("Description"), 0, 1);
		form.add(description, 1, 1);
		GridPane.setHgrow(name, Priority.ALWAYS);
		GridPane.setHgrow(description, Priority.ALWAYS);
		form.add(new Label("Code"), 0, 2, 2, 1);
		form.add(code, 0, 3, 2, 1);
		GridPane.setVgrow(code, Priority.ALWAYS);
		GridPane.setHgrow(code, Priority.ALWAYS);
		form.add(check, 0, 4, 2, 1);
		Label hint = new Label(HINT);
		hint.setWrapText(true);
		hint.setMaxWidth(460);
		hint.setStyle("-fx-font-style: italic;");
		form.add(hint, 0, 5, 2, 1);
		pane.setCenter(form);

		name.textProperty().addListener((o, was, is)->edited());
		description.textProperty().addListener((o, was, is)->edited());
		code.editor().getCodeArea().textProperty().addListener((o, was, is)->edited());
	}
}
