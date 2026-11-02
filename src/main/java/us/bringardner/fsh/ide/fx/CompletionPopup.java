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

import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

import org.fxmisc.richtext.CodeArea;

import javafx.event.EventHandler;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import us.bringardner.fsh.ide.core.Completions;
import us.bringardner.fsh.ide.core.Completions.Completion;
import us.bringardner.fsh.ide.core.Template;
import us.bringardner.fsh.ide.core.TemplateText;

/**
 * Autocompletion: the templates and variables that finish the word before the caret, in a
 * list under it. Typing goes on into the editor and narrows the list; Up and Down choose,
 * Enter or Tab inserts, Escape closes; then Tab goes from field to field of the template, and a
 * field with choices lists them ({@link TemplateFields}). (The Swing IDE uses RSyntaxTextArea's
 * autocompletion, with the same templates.)
 */
public class CompletionPopup {

	private final ScriptEditor editor;
	private final Supplier<Collection<Template>> templates;
	private final Popup popup = new Popup();
	final ListView<Completion> list = new ListView<>();
	private final Label description = new Label();
	private boolean active;
	private final EventHandler<KeyEvent> keys = this::key;

	public CompletionPopup(ScriptEditor editor, Supplier<Collection<Template>> templates) {
		this.editor = editor;
		this.templates = templates;
		// while the list is up, Tab, Enter and Escape are its, not the template fields'
		editor.getTemplateFields().setBusy(this::isActive);
		list.setPrefSize(440, 180);
		list.setCellFactory(l->new ListCell<>() {
			@Override
			protected void updateItem(Completion c, boolean empty) {
				super.updateItem(c, empty);
				setText(empty || c == null ? null : text(c));
			}
		});
		list.getSelectionModel().selectedItemProperty().addListener((o, was, is)->
			description.setText(is == null ? "" : is.description));
		// a click on one inserts it
		list.setOnMouseClicked(e->{
			Completion c = list.getSelectionModel().getSelectedItem();
			if( e.getButton() == javafx.scene.input.MouseButton.PRIMARY && c != null ) {
				apply(c);
			}
		});
		// JavaFX gives the editor window's keys to the list's window first, where the list took
		// Enter (so it never inserted): Up, Down, Enter, Tab and Escape are handled here (key());
		// the rest go on to the editor, where typing narrows the list. (The editor has them too, for
		// when the list's window isn't between: one that one of them handles is consumed.)
		popup.addEventFilter(KeyEvent.KEY_PRESSED, keys);
		description.setWrapText(true);
		description.setMaxWidth(440);
		VBox box = new VBox(list, description);
		box.getStyleClass().add("completion-popup");
		box.setStyle("-fx-background-color: white; -fx-border-color: #b0b0b0; -fx-padding: 2;");
		popup.getContent().add(box);
		popup.setAutoHide(true);
		popup.setOnHidden(e->stop());
		editor.getCodeArea().textProperty().addListener((o, was, is)->{
			if( active ) {
				refresh();
			}
		});		// Ctrl+Space in the editor itself: on macOS, Edit > Complete's Ctrl+Space is in the system
		// menu bar, which doesn't pass it on, so the menu's shortcut alone never showed the list
		editor.getCodeArea().addEventFilter(KeyEvent.KEY_PRESSED, e->{
			if( e.getCode() == javafx.scene.input.KeyCode.SPACE && e.isControlDown() && !e.isAltDown() && !e.isMetaDown()) {
				e.consume();
				show();
			}
		});
	}

	/** A completion as the list shows it: a template's name and, if it says more, its description. */
	static String text(Completion c) {
		if( !c.template ) {
			return c.label+"   (variable)";
		}
		boolean more = c.description != null && !c.description.isBlank() && !c.description.equals(c.label);
		return more ? c.label+" \u2014 "+c.description : c.label;
	}

	/** Shows what can finish the word before the caret (nothing happens if there's nothing). */
	public void show() {
		if( !active ) {
			active = true;
			editor.getCodeArea().addEventFilter(KeyEvent.KEY_PRESSED, keys);
		}
		refresh();
	}

	/** The word before the caret (with hyphens, for a template's name: {@link Completions#prefix(String, int, Collection)}). */
	String prefix() {
		CodeArea area = editor.getCodeArea();
		return Completions.prefix(area.getText(), area.getCaretPosition(), templates.get());
	}

	List<Completion> candidates() {
		return Completions.matching(prefix(), templates.get(), editor.getText());
	}

	private void refresh() {
		List<Completion> found = candidates();
		if( found.isEmpty()) {
			hide();
			return;
		}
		list.getItems().setAll(found);
		list.getSelectionModel().select(0);
		CodeArea area = editor.getCodeArea();
		if( !popup.isShowing() && area.getScene() != null && area.getScene().getWindow() != null
				&& area.getScene().getWindow().isShowing()) {
			area.getCaretBounds().ifPresent(b->popup.show(area, b.getMinX(), b.getMaxY()+2));
		}
	}

	private void key(KeyEvent e) {
		switch (e.getCode()) {
		case UP:
			list.getSelectionModel().selectPrevious();
			e.consume();
			break;
		case DOWN:
			list.getSelectionModel().selectNext();
			e.consume();
			break;
		case ENTER:
		case TAB:
			Completion c = list.getSelectionModel().getSelectedItem();
			if( c != null ) {
				apply(c);
				e.consume();
			}
			break;
		case ESCAPE:
			hide();
			e.consume();
			break;
		default:
			break;
		}
	}

	/** Puts c in place of the word before the caret. */
	void apply(Completion c) {
		CodeArea area = editor.getCodeArea();
		int caret = area.getCaretPosition();
		int start = caret-prefix().length();
		hide();
		editor.insert(start, caret, TemplateText.expand(c.code));
	}

	public void hide() {
		stop();
		popup.hide();
	}

	private void stop() {
		if( active ) {
			active = false;
			editor.getCodeArea().removeEventFilter(KeyEvent.KEY_PRESSED, keys);
		}
	}

	boolean isActive() {
		return active;
	}

	// for tests
	boolean isShowing() {
		return popup.isShowing();
	}
}
