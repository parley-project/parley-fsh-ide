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
import java.util.function.IntConsumer;

import javafx.scene.control.Label;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import us.bringardner.fsh.ide.core.CompileError;
import us.bringardner.fsh.ide.core.SyntaxNode;

/**
 * The script as fsh parses it: a tree of its commands, loops, pipelines and so on. Double-click
 * (or Enter on) a node to go to its line. The Swing IDE draws the same tree as a diagram.
 */
public class SyntaxTreeView extends BorderPane {

	private final TreeView<SyntaxNode> tree = new TreeView<>();
	private final Label errors = new Label();
	private IntConsumer onGoToLine = line->{};

	public SyntaxTreeView() {
		tree.setShowRoot(false);
		tree.setCellFactory(t->new TreeCell<>() {
			@Override
			protected void updateItem(SyntaxNode n, boolean empty) {
				super.updateItem(n, empty);
				setText(empty || n == null ? null : label(n));
			}
		});
		tree.setOnMouseClicked(e->{
			if( e.getClickCount() == 2 ) {
				goToSelected();
			}
		});
		tree.setOnKeyPressed(k->{
			if( k.getCode() == KeyCode.ENTER ) {
				goToSelected();
			}
		});
		errors.setWrapText(true);
		errors.setStyle("-fx-text-fill: #c62828; -fx-padding: 4;");
		setCenter(tree);
		setBottom(errors);
	}

	/** "kind: text", the text cut to its first line and 60 characters. */
	static String label(SyntaxNode n) {
		String text = n.getText() == null ? "" : n.getText();
		if( text.trim().length() <= 2 && n.getChildCount() > 1 ) {
			// just an operator (a pipeline's "|", say): show what it joins
			StringBuilder sb = new StringBuilder();
			for(SyntaxNode kid : n.getChildren()) {
				String t = kid.getText() == null ? "" : kid.getText().trim();
				if( !t.isEmpty()) {
					sb.append(sb.length() == 0 ? "" : " "+(text.trim().isEmpty() ? ";" : text.trim())+" ").append(t);
				}
			}
			text = sb.toString();
		}
		int nl = text.indexOf('\n');
		if( nl >= 0 ) {
			text = text.substring(0, nl)+" ...";
		}
		if( text.length() > 60 ) {
			text = text.substring(0, 57)+"...";
		}
		return text.isEmpty() ? n.getKind() : n.getKind()+": "+text;
	}

	private void goToSelected() {
		TreeItem<SyntaxNode> item = tree.getSelectionModel().getSelectedItem();
		if( item != null && item.getValue() != null && item.getValue().getLine() > 0 ) {
			onGoToLine.accept(item.getValue().getLine());
		}
	}

	/** Shows root (null for none) and the syntax errors found. */
	public void setTree(SyntaxNode root, List<CompileError> found) {
		TreeItem<SyntaxNode> item = root == null ? new TreeItem<>() : build(root);
		item.setExpanded(true);
		tree.setRoot(item);
		StringBuilder sb = new StringBuilder();
		for(CompileError e : found) {
			sb.append("Line ").append(e.line).append(": ").append(e.msg).append('\n');
		}
		errors.setText(sb.toString().trim());
		errors.setVisible(!found.isEmpty());
		errors.setManaged(!found.isEmpty());
	}

	private static TreeItem<SyntaxNode> build(SyntaxNode n) {
		TreeItem<SyntaxNode> item = new TreeItem<>(n);
		for(SyntaxNode kid : n.getChildren()) {
			item.getChildren().add(build(kid));
		}
		return item;
	}

	/** Called with a (1-based) line to go to. */
	public void setOnGoToLine(IntConsumer handler) {
		onGoToLine = handler;
	}

	TreeView<SyntaxNode> treeView() {
		return tree;
	}
}
