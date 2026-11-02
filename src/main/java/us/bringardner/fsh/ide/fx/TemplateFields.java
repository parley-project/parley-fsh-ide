/**
*	Copyright 2024 Tony Bringardner
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
import java.util.function.BooleanSupplier;

import org.fxmisc.richtext.CodeArea;

import javafx.event.EventHandler;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import us.bringardner.fsh.ide.core.TemplateText;
import us.bringardner.fsh.ide.core.TemplateText.Choice;

/**
 * The fields of a template just inserted, as the Swing IDE's autocompletion has them: Tab goes
 * to the next field (after the last one, to the end of the template), Shift+Tab to the one before,
 * Escape stops. A field with choices ("${|-e : exists|-f : a regular file}") shows them in a list
 * under it when it's the current field: Up and Down choose, Enter (or a click) puts the choice in
 * and goes on to the next field, Escape closes the list; typing replaces the field as usual. Typing
 * outside the fields, or moving the caret out of the template, stops.
 */
public class TemplateFields {

	private final CodeArea area;
	/** whether something else (the completion list) has the keys now */
	private BooleanSupplier busy = ()->false;
	/** the fields, [start, end) in the text, kept up to date as the text changes */
	private final List<int[]> fields = new ArrayList<>();
	private final List<List<Choice>> choices = new ArrayList<>();
	/** where the template starts and ends */
	private int start;
	private int end;
	private int current = -1;
	private boolean active;
	/** our own change (a choice put in) */
	private boolean changing;

	private final Popup popup = new Popup();
	final ListView<Choice> list = new ListView<>();
	private final EventHandler<KeyEvent> keys = this::key;

	public TemplateFields(CodeArea area) {
		this.area = area;
		area.plainTextChanges().subscribe(c->changed(c.getPosition(), c.getRemoved().length(), c.getInserted().length()));
		area.caretPositionProperty().addListener((o, was, is)->caretMoved(is));
		area.addEventFilter(KeyEvent.KEY_PRESSED, keys);
		list.setPrefSize(440, 200);
		list.setCellFactory(l->new ListCell<>() {
			@Override
			protected void updateItem(Choice c, boolean empty) {
				super.updateItem(c, empty);
				setText(empty || c == null ? null : text(c));
			}
		});
		list.setOnMouseClicked(e->{
			Choice c = list.getSelectionModel().getSelectedItem();
			if( e.getButton() == MouseButton.PRIMARY && c != null ) {
				pick(c);
			}
		});
		// the list's window gets the editor window's keys first (as the completion list's does)
		popup.addEventFilter(KeyEvent.KEY_PRESSED, keys);
		VBox box = new VBox(list);
		box.getStyleClass().add("completion-popup");
		box.setStyle("-fx-background-color: white; -fx-border-color: #b0b0b0; -fx-padding: 2;");
		popup.getContent().add(box);
		popup.setAutoHide(true);
	}

	/** A choice as the list shows it: its value and what it means. */
	static String text(Choice c) {
		return c.label.isEmpty() ? c.value : c.value + " — " + c.label;
	}

	/** Something else that, while it's true, has Tab, Enter and Escape (the completion list). */
	public void setBusy(BooleanSupplier busy) {
		this.busy = busy == null ? ()->false : busy;
	}

	/**
	 * Starts on e, just put in at at: its first field is chosen. Nothing to do without fields, or
	 * when the template says where the caret goes.
	 */
	public void start(int at, TemplateText.Expansion e) {
		stop();
		if( e.fields.isEmpty() || e.cursor >= 0 ) {
			return;
		}
		for(int i = 0; i < e.fields.size(); i++) {
			int[] f = e.fields.get(i);
			fields.add(new int[] {at+f[0], at+f[1]});
			choices.add(e.choices.get(i));
		}
		start = at;
		end = at+e.text.length();
		active = true;
		select(0);
	}

	public void stop() {
		active = false;
		fields.clear();
		choices.clear();
		current = -1;
		popup.hide();
	}

	public boolean isActive() {
		return active;
	}

	/** the current field, [start, end) */
	int[] current() {
		return active ? fields.get(current).clone() : null;
	}

	// for tests
	boolean isShowingChoices() {
		return popup.isShowing();
	}

	/** Makes field i the current one: its text selected, and its choices shown if it has any. */
	void select(int i) {
		current = i;
		int[] f = fields.get(i);
		changing = true;
		try {
			area.selectRange(f[0], f[1]);
		} finally {
			changing = false;
		}
		area.requestFollowCaret();
		List<Choice> list = choices.get(i);
		if( list == null || list.isEmpty()) {
			popup.hide();
		} else {
			showChoices(list, f);
		}
	}

	private void showChoices(List<Choice> items, int[] f) {
		list.getItems().setAll(items);
		String now = area.getText(f[0], f[1]);
		int at = 0;
		for(int n = 0; n < items.size(); n++) {
			if( items.get(n).value.equals(now)) {
				at = n;
				break;
			}
		}
		list.getSelectionModel().select(at);
		list.scrollTo(at);
		place(f, true);
	}

	/**
	 * Shows the list under field f. Text just put in has no place on the screen until it's laid
	 * out: then the caret's place, else once more after the layout.
	 */
	private void place(int[] f, boolean again) {
		if( popup.isShowing() || !active || area.getScene() == null || area.getScene().getWindow() == null
				|| !area.getScene().getWindow().isShowing()) {
			return;
		}
		java.util.Optional<javafx.geometry.Bounds> at = area.getCharacterBoundsOnScreen(f[0], Math.max(f[0]+1, f[1]));
		if( at.isEmpty()) {
			at = area.getCaretBounds();
		}
		if( at.isPresent()) {
			popup.show(area, at.get().getMinX(), at.get().getMaxY()+2);
		} else if( again ) {
			javafx.application.Platform.runLater(()->{
				if( active && fields.get(current) == f ) {
					place(f, false);
				}
			});
		}
	}

	/** The next field, or after the last one, the end of the template (and that's all). */
	void next() {
		if( current+1 < fields.size()) {
			select(current+1);
		} else {
			int at = end;
			stop();
			area.moveTo(at);
		}
	}

	void previous() {
		select(Math.max(0, current-1));
	}

	/** Puts c in the current field, and goes on to the next one. */
	void pick(Choice c) {
		int[] f = fields.get(current);
		changing = true;
		try {
			area.replaceText(f[0], f[1], c.value);
		} finally {
			changing = false;
		}
		popup.hide();
		next();
	}

	/** A change in a field moves the ones after it; anywhere else, that's the end. */
	private void changed(int pos, int removed, int inserted) {
		if( !active ) {
			return;
		}
		int delta = inserted-removed;
		for(int i = 0; i < fields.size(); i++) {
			int[] f = fields.get(i);
			if( f[0] <= pos && pos+removed <= f[1]) {
				f[1] += delta;
				for(int k = i+1; k < fields.size(); k++) {
					fields.get(k)[0] += delta;
					fields.get(k)[1] += delta;
				}
				end += delta;
				if( !changing ) {
					popup.hide();
				}
				return;
			}
		}
		stop();
	}

	private void caretMoved(int caret) {
		if( active && !changing && (caret < start || caret > end)) {
			stop();
		}
	}

	private void key(KeyEvent e) {
		if( !active || e.isConsumed() || busy.getAsBoolean()) {
			return;
		}
		boolean other = e.isControlDown() || e.isAltDown() || e.isMetaDown();
		if( popup.isShowing()) {
			switch (e.getCode()) {
			case UP:
				list.getSelectionModel().selectPrevious();
				e.consume();
				return;
			case DOWN:
				list.getSelectionModel().selectNext();
				e.consume();
				return;
			case ENTER:
				Choice c = list.getSelectionModel().getSelectedItem();
				if( c != null ) {
					pick(c);
					e.consume();
				}
				return;
			case ESCAPE:
				popup.hide();
				e.consume();
				return;
			default:
				break;
			}
		}
		if( e.getCode() == KeyCode.TAB && !other ) {
			if( e.isShiftDown()) {
				previous();
			} else {
				next();
			}
			e.consume();
		} else if( e.getCode() == KeyCode.ESCAPE ) {
			stop();
			e.consume();
		}
	}
}
