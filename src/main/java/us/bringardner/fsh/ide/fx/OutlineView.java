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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntConsumer;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import us.bringardner.fsh.ide.core.ScriptOutline;

/**
 * The script's outline beside the editor: its functions (with the variables each sets) and its
 * variables. A click on one (or Enter) goes to its line; a variable's right-click menu watches it.
 */
public class OutlineView extends BorderPane {

	static final String FUNCTIONS = "Functions";
	static final String VARIABLES = "Variables";

	// a group's name, or a function or variable
	private final TreeView<Object> tree = new TreeView<>(new TreeItem<>());
	// what the user collapsed (groups by name, functions as name()), kept as the outline is rebuilt
	private final Set<String> collapsed = new HashSet<>();
	private boolean rebuilding;
	private IntConsumer onGoToLine = line->{};
	private java.util.function.Consumer<String> onWatchVariable = name->{};
	private ScriptOutline outline = ScriptOutline.of(null);

	public OutlineView() {
		Label title = new Label("Outline");
		title.setPadding(new Insets(3, 6, 3, 6));
		setTop(title);
		tree.setShowRoot(false);
		tree.setCellFactory(t->new TreeCell<>() {
			// right-click on a variable: watch it
			private final javafx.scene.control.MenuItem watch = new javafx.scene.control.MenuItem();
			private final javafx.scene.control.ContextMenu menu = new javafx.scene.control.ContextMenu(watch);
			{
				watch.setOnAction(e->{
					String name = variableName(getItem());
					if( name != null ) {
						onWatchVariable.accept(name);
					}
				});
			}

			@Override
			protected void updateItem(Object o, boolean empty) {
				super.updateItem(o, empty);
				String name = empty ? null : variableName(o);
				if( name != null ) {
					watch.setText("Watch "+name+"...");
					setContextMenu(menu);
				} else {
					setContextMenu(null);
				}
				if( empty || o == null ) {
					setText(null);
				} else if( o instanceof ScriptOutline.Item ) {
					ScriptOutline.Item item = (ScriptOutline.Item) o;
					setText(item.label()+"  : "+item.getLine());
				} else {
					setText(String.valueOf(o));
				}
			}
		});
		tree.setOnMouseClicked(e->{
			if( e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 1 ) {
				// not on a group's arrow (that opens and closes it)
				if( e.getTarget() instanceof javafx.scene.Node && isArrow((javafx.scene.Node) e.getTarget())) {
					return;
				}
				goToSelected();
			}
		});
		tree.setOnKeyPressed(e->{
			if( e.getCode() == KeyCode.ENTER ) {
				goToSelected();
			}
		});
		setCenter(tree);
		setPrefWidth(200);
		setMinWidth(80);
	}

	private static boolean isArrow(javafx.scene.Node n) {
		for(javafx.scene.Node k = n; k != null; k = k.getParent()) {
			if( k.getStyleClass().contains("tree-disclosure-node")) {
				return true;
			}
			if( k instanceof TreeCell ) {
				return false;
			}
		}
		return false;
	}

	/** Called with a (1-based) line chosen in the outline. */
	public void setOnGoToLine(IntConsumer handler) {
		onGoToLine = handler == null ? line->{} : handler;
	}

	/** Called with a variable's name when Watch is chosen on its right-click menu. */
	public void setOnWatchVariable(java.util.function.Consumer<String> handler) {
		onWatchVariable = handler == null ? name->{} : handler;
	}

	/** A variable's name, or null for a function or a group. */
	static String variableName(Object o) {
		if( o instanceof ScriptOutline.Item && !((ScriptOutline.Item) o).isFunction()) {
			return ((ScriptOutline.Item) o).getName();
		}
		return null;
	}

	/** Chooses Watch on the item's right-click menu (for tests). */
	void watch(Object o) {
		String name = variableName(o);
		if( name != null ) {
			onWatchVariable.accept(name);
		}
	}

	private void goToSelected() {
		TreeItem<Object> item = tree.getSelectionModel().getSelectedItem();
		if( item != null && item.getValue() instanceof ScriptOutline.Item ) {
			onGoToLine.accept(((ScriptOutline.Item) item.getValue()).getLine());
		}
	}

	private static String key(Object o) {
		return o instanceof ScriptOutline.Item ? ((ScriptOutline.Item) o).label() : String.valueOf(o);
	}

	/** Shows a script's outline (an empty one for none). */
	public void setOutline(ScriptOutline outline) {
		this.outline = outline == null ? ScriptOutline.of(null) : outline;
		rebuilding = true;
		try {
			TreeItem<Object> root = new TreeItem<>();
			group(root, FUNCTIONS, this.outline.getFunctions());
			group(root, VARIABLES, this.outline.getVariables());
			tree.setRoot(root);
		} finally {
			rebuilding = false;
		}
	}

	private void group(TreeItem<Object> root, String name, List<ScriptOutline.Item> items) {
		if( items.isEmpty()) {
			return;
		}
		TreeItem<Object> group = item(name);
		for(ScriptOutline.Item it : items) {
			TreeItem<Object> node = item(it);
			for(ScriptOutline.Item v : it.getChildren()) {
				node.getChildren().add(item(v));
			}
			group.getChildren().add(node);
		}
		root.getChildren().add(group);
	}

	// open unless the user closed it; remembers when the user opens or closes it
	private TreeItem<Object> item(Object value) {
		TreeItem<Object> ret = new TreeItem<>(value);
		ret.setExpanded(!collapsed.contains(key(value)));
		ret.expandedProperty().addListener((o, was, is)->{
			if( !rebuilding && !ret.isLeaf()) {
				if( is ) {
					collapsed.remove(key(value));
				} else {
					collapsed.add(key(value));
				}
			}
		});
		return ret;
	}

	// for tests
	ScriptOutline outline() { return outline; }
	TreeView<Object> treeView() { return tree; }
}
