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
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import us.bringardner.fsh.ide.core.Breakpoint;
import us.bringardner.fsh.ide.core.CompileError;
import us.bringardner.fsh.ide.core.DebugVariables;
import us.bringardner.fsh.ide.core.SyntaxNode;
import us.bringardner.fsh.ide.core.Variable;

/**
 * The debugger's views: the paused script's variables (values can be changed while it's
 * paused), the breakpoints, and the log of statements run.
 */
public class DebugPanel extends TabPane {

	/** Log text kept; older text is removed from the top. */
	static final int MAX_LOG_LENGTH = 200_000;

	private final ObservableList<Variable> variables = FXCollections.observableArrayList();
	// all the paused script's variables; the table shows those that pass the check box and filter
	private Map<String,Object> allVariables;
	private final CheckBox showEnvironment = new CheckBox("Shell & environment");
	private final TextField filter = new TextField();
	private final TableView<Variable> variableTable = new TableView<>(variables);
	private final ObservableList<Breakpoint> breakpoints = FXCollections.observableArrayList();
	private final TableView<Breakpoint> breakpointTable = new TableView<>(breakpoints);
	private final TextArea log = new TextArea();
	private final SyntaxTreeView syntax = new SyntaxTreeView();
	private BiConsumer<Variable,String> onSetVariable = (v, s)->{};
	private Consumer<Breakpoint> onBreakpointChanged = b->{};
	private Consumer<Breakpoint> onEditBreakpoint = b->{};

	public DebugPanel() {
		// variables
		TableColumn<Variable,String> name = new TableColumn<>("Name");
		name.setCellValueFactory(c->new ReadOnlyStringWrapper(c.getValue().getName()));
		name.setPrefWidth(120);
		TableColumn<Variable,String> type = new TableColumn<>("Type");
		type.setCellValueFactory(c->new ReadOnlyStringWrapper(DebugVariables.typeName(c.getValue().getValue())));
		type.setPrefWidth(70);
		TableColumn<Variable,String> value = new TableColumn<>("Value");
		value.setCellValueFactory(c->new ReadOnlyStringWrapper(String.valueOf(c.getValue().getValue())));
		value.setCellFactory(TextFieldTableCell.forTableColumn());
		value.setOnEditCommit(e->onSetVariable.accept(e.getRowValue(), e.getNewValue()));
		value.setPrefWidth(200);
		variableTable.getColumns().add(name);
		variableTable.getColumns().add(type);
		variableTable.getColumns().add(value);
		variableTable.setEditable(false);
		variableTable.setPlaceholder(new Label("The variables show here while a script is paused."));
		variableTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

		// breakpoints
		TableColumn<Breakpoint,Boolean> enabled = new TableColumn<>("On");
		enabled.setCellValueFactory(c->{
			Breakpoint bp = c.getValue();
			SimpleBooleanProperty p = new SimpleBooleanProperty(bp.isEnabled(false));
			p.addListener((o, was, is)->{
				bp.setEnabled(is);
				onBreakpointChanged.accept(bp);
			});
			return p;
		});
		enabled.setCellFactory(CheckBoxTableCell.forTableColumn(enabled));
		enabled.setEditable(true);
		enabled.setPrefWidth(36);
		TableColumn<Breakpoint,String> line = new TableColumn<>("Line");
		line.setCellValueFactory(c->new ReadOnlyStringWrapper(""+(c.getValue().getLine()+1)));
		line.setPrefWidth(50);
		TableColumn<Breakpoint,String> detail = new TableColumn<>("Stops");
		detail.setCellValueFactory(c->new ReadOnlyStringWrapper(describe(c.getValue())));
		breakpointTable.getColumns().add(enabled);
		breakpointTable.getColumns().add(line);
		breakpointTable.getColumns().add(detail);
		breakpointTable.setEditable(true);
		breakpointTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
		breakpointTable.setPlaceholder(new Label("Click in the editor's margin to add a breakpoint."));
		breakpointTable.setRowFactory(t->{
			TableRow<Breakpoint> row = new TableRow<>();
			row.setOnMouseClicked(e->{
				if( e.getClickCount() == 2 && !row.isEmpty()) {
					onEditBreakpoint.accept(row.getItem());
				}
			});
			return row;
		});

		log.setEditable(false);
		log.getStyleClass().add("debug-log");

		filter.setPromptText("Filter by name");
		filter.textProperty().addListener((o, was, is)->showVariables());
		showEnvironment.setTooltip(new javafx.scene.control.Tooltip("Also show the variables inherited from the environment, and the shell's own at their defaults"));
		showEnvironment.selectedProperty().addListener((o, was, is)->showVariables());
		HBox options = new HBox(8, filter, showEnvironment);
		options.setPadding(new Insets(4));
		options.setStyle("-fx-alignment: center-left;");
		HBox.setHgrow(filter, Priority.ALWAYS);
		Tab vars = new Tab("Variables", new BorderPane(variableTable, options, null, null, null));
		Tab bps = new Tab("Breakpoints", breakpointTable);
		Tab logTab = new Tab("Log", log);
		Tab syntaxTab = new Tab("Syntax", syntax);
		for(Tab t : new Tab[] {vars, bps, logTab, syntaxTab}) {
			t.setClosable(false);
		}
		getTabs().addAll(vars, bps, logTab, syntaxTab);
	}

	/** How a breakpoint stops: its condition, hit count, or always (with its code). */
	static String describe(Breakpoint bp) {
		if( bp.isConditional() && !bp.getCondition().isBlank()) {
			return "when "+bp.getCondition();
		}
		if( bp.isHitCount() && bp.getHitCount() > 0 ) {
			return "on hit "+bp.getHitCount();
		}
		return bp.getCode();
	}

	/** Shows a paused script's variables; they can be edited until {@link #clearVariables()} or a new set. */
	public void setVariables(Map<String,Object> vars, boolean editable) {
		allVariables = vars;
		showVariables();
		variableTable.setEditable(editable);
	}

	private void showVariables() {
		String f = filter.getText() == null ? "" : filter.getText().trim().toLowerCase();
		List<Variable> list = new ArrayList<>();
		for(Variable v : DebugVariables.list(allVariables, showEnvironment.isSelected())) {
			if( f.isEmpty() || v.getName().toLowerCase().contains(f)) {
				list.add(v);
			}
		}
		variables.setAll(list);
	}

	/** Leaves the variables shown but read-only (the script isn't paused any more). */
	public void freezeVariables() {
		variableTable.setEditable(false);
	}

	public void clearVariables() {
		allVariables = null;
		variables.clear();
		variableTable.setEditable(false);
	}

	public void setBreakpoints(Collection<Breakpoint> list) {
		breakpoints.setAll(list);
		breakpointTable.refresh();
	}

	public void appendLog(String text) {
		log.appendText(text);
		int extra = log.getLength()-MAX_LOG_LENGTH;
		if( extra > 0 ) {
			log.deleteText(0, extra);
		}
	}

	public void clearLog() {
		log.clear();
	}

	/** Shows the script's syntax tree (null for none) and its syntax errors. */
	public void setSyntax(SyntaxNode tree, List<CompileError> errors) {
		syntax.setTree(tree, errors);
	}

	/** Called with a (1-based) line chosen in the syntax tree. */
	public void setOnGoToLine(java.util.function.IntConsumer handler) {
		syntax.setOnGoToLine(handler);
	}

	SyntaxTreeView syntaxView() {
		return syntax;
	}

	/** Called with a variable and the value typed for it (while the script is paused). */
	public void setOnSetVariable(BiConsumer<Variable,String> handler) { onSetVariable = handler; }

	/** Called when a breakpoint is switched on or off here. */
	public void setOnBreakpointChanged(Consumer<Breakpoint> handler) { onBreakpointChanged = handler; }

	/** Called when a breakpoint is double-clicked. */
	public void setOnEditBreakpoint(Consumer<Breakpoint> handler) { onEditBreakpoint = handler; }

	// for tests
	ObservableList<Variable> variables() { return variables; }
	ObservableList<Breakpoint> breakpoints() { return breakpoints; }
	String logText() { return log.getText(); }
	boolean variablesEditable() { return variableTable.isEditable(); }
	CheckBox environmentCheckBox() { return showEnvironment; }
	TextField filterField() { return filter; }
}
