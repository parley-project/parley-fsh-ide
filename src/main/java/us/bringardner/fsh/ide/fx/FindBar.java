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

import java.util.List;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.control.Labeled;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import us.bringardner.fsh.ide.core.TextSearch;

/**
 * Find and replace in the script, in a bar under the editor (the Swing IDE has a dialog):
 * matching case, whole words, regular expressions, wrapping round the end.
 */
public class FindBar extends VBox {

	private final ScriptEditor editor;
	final TextField find = new TextField();
	final TextField replace = new TextField();
	final CheckBox matchCase = new CheckBox("Match case");
	final CheckBox wholeWords = new CheckBox("Whole words");
	final CheckBox regex = new CheckBox("Regex");
	final CheckBox wrap = new CheckBox("Wrap");
	private final Label result = new Label();
	private final HBox replaceRow;

	public FindBar(ScriptEditor editor) {
		this.editor = editor;
		getStyleClass().add("find-bar");
		setSpacing(4);
		setPadding(new Insets(4, 6, 4, 6));
		wrap.setSelected(true);
		find.setPromptText("Find");
		replace.setPromptText("Replace with");
		find.setPrefColumnCount(20);
		replace.setPrefColumnCount(20);

		Button next = new Button("Next");
		next.setOnAction(e->findNext(true));
		Button previous = new Button("Previous");
		previous.setOnAction(e->findNext(false));
		Button close = new Button("✕");
		close.setOnAction(e->hide());
		// wraps when the window is narrow; buttons, boxes and labels keep their full width
		FlowPane findRow = new FlowPane(6, 4, new Label("Find:"), find, next, previous, matchCase, wholeWords, regex, wrap, result);
		findRow.setStyle("-fx-alignment: center-left;");
		findRow.prefWrapLengthProperty().bind(widthProperty().subtract(60));

		Button replaceOne = new Button("Replace");
		replaceOne.setOnAction(e->replace(false));
		Button replaceFind = new Button("Replace & Find");
		replaceFind.setOnAction(e->replace(true));
		Button replaceAll = new Button("Replace All");
		replaceAll.setOnAction(e->replaceAll());
		replaceRow = new HBox(6, new Label("Replace:"), replace, replaceOne, replaceFind, replaceAll);
		replaceRow.setStyle("-fx-alignment: center-left;");

		HBox top = new HBox(findRow, close);
		HBox.setHgrow(findRow, Priority.ALWAYS);
		getChildren().addAll(top, replaceRow);
		for(javafx.scene.Node n : new javafx.scene.Node[] {next, previous, close, matchCase, wholeWords, regex, wrap,
				replaceOne, replaceFind, replaceAll}) {
			((Labeled) n).setMinWidth(Region.USE_PREF_SIZE);
		}
		for(javafx.scene.Node n : findRow.getChildren()) {
			if( n instanceof Label ) {
				((Label) n).setMinWidth(Region.USE_PREF_SIZE);
			}
		}
		for(javafx.scene.Node n : replaceRow.getChildren()) {
			if( n instanceof Label ) {
				((Label) n).setMinWidth(Region.USE_PREF_SIZE);
			}
		}

		find.setOnKeyPressed(k->{
			if( k.getCode() == KeyCode.ENTER ) {
				findNext(!k.isShiftDown());
				k.consume();
			} else if( k.getCode() == KeyCode.ESCAPE ) {
				hide();
				k.consume();
			}
		});
		replace.setOnKeyPressed(k->{
			if( k.getCode() == KeyCode.ENTER ) {
				replace(true);
				k.consume();
			} else if( k.getCode() == KeyCode.ESCAPE ) {
				hide();
				k.consume();
			}
		});
		for(CheckBox c : new CheckBox[] {matchCase, wholeWords, regex}) {
			c.selectedProperty().addListener((o, was, is)->result.setText(""));
		}
		setShown(false, false);
	}

	private void setShown(boolean shown, boolean withReplace) {
		setVisible(shown);
		setManaged(shown);
		replaceRow.setVisible(withReplace);
		replaceRow.setManaged(withReplace);
	}

	/** Shows the bar (with the replace row if withReplace), starting with the selected text. */
	public void show(boolean withReplace) {
		String selected = editor.getSelectedText();
		if( selected != null && !selected.contains("\n")) {
			find.setText(selected);
		}
		setShown(true, withReplace);
		find.requestFocus();
		find.selectAll();
		result.setText("");
	}

	public void hide() {
		setShown(false, false);
		editor.getCodeArea().requestFocus();
	}

	TextSearch.Options options() {
		return new TextSearch.Options(matchCase.isSelected(), wholeWords.isSelected(), regex.isSelected(), wrap.isSelected());
	}

	/**
	 * Selects the next match after the selection (or the previous one before it).
	 * @return true if one was found
	 */
	public boolean findNext(boolean forward) {
		String query = find.getText();
		if( query.isEmpty()) {
			return false;
		}
		var area = editor.getCodeArea();
		int from = forward ? area.getSelection().getEnd() : area.getSelection().getStart();
		try {
			TextSearch.Match m = TextSearch.find(area.getText(), query, from, forward, options());
			if( m == null ) {
				result.setText("Not found");
				return false;
			}
			editor.select(m.start, m.end);
			int n = TextSearch.count(area.getText(), query, options());
			result.setText((m.wrapped ? "Wrapped. " : "")+n+(n == 1 ? " match" : " matches"));
			return true;
		} catch (IllegalArgumentException e) {
			result.setText("Bad regular expression");
			return false;
		}
	}

	/** Replaces the selection if it's a match (then finds the next one if andFind). */
	public void replace(boolean andFind) {
		var area = editor.getCodeArea();
		String selected = area.getSelectedText();
		String query = find.getText();
		try {
			int start = area.getSelection().getStart();
			TextSearch.Match m = selected.isEmpty() ? null : TextSearch.find(area.getText(), query, start, true, options());
			if( m != null && m.start == start && m.end == area.getSelection().getEnd()) {
				String with = TextSearch.replacementFor(selected, query, replace.getText(), options());
				area.replaceText(m.start, m.end, with);
				area.selectRange(m.start, m.start+with.length());
			}
			if( andFind || m == null ) {
				findNext(true);
			}
		} catch (IllegalArgumentException e) {
			result.setText("Bad regular expression");
		}
	}

	/**
	 * Replaces every match, one by one from the last (so the rest of the text, and breakpoints,
	 * stay where they are).
	 * @return how many were replaced
	 */
	public int replaceAll() {
		var area = editor.getCodeArea();
		String query = find.getText();
		try {
			String text = area.getText();
			List<TextSearch.Match> all = TextSearch.findAll(text, query, options());
			for(int i=all.size()-1; i >= 0; i--) {
				TextSearch.Match m = all.get(i);
				area.replaceText(m.start, m.end, TextSearch.replacementFor(text.substring(m.start, m.end), query, replace.getText(), options()));
			}
			result.setText("Replaced "+all.size());
			return all.size();
		} catch (IllegalArgumentException e) {
			result.setText("Bad regular expression");
			return 0;
		}
	}

	String resultText() {
		return result.getText();
	}
}
