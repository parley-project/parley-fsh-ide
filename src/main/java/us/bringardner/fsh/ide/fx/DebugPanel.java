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
import java.util.function.Consumer;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import us.bringardner.fsh.ide.core.Breakpoint;
import us.bringardner.fsh.ide.core.CompileError;
import us.bringardner.fsh.ide.core.DebugVariables;
import us.bringardner.fsh.ide.core.SyntaxNode;
import us.bringardner.fsh.ide.core.Variable;
import us.bringardner.fsh.ide.core.Watchpoint;

/**
 * The debugger's views: the paused script's variables, the script's own and fsh's on their own
 * tabs (values can be changed while it's paused), the breakpoints, the variable watchpoints, and the
 * log of statements run.
 */
public class DebugPanel extends TabPane {

	/**
	 * The columns fill the table and the last one takes what is left (JavaFX 20's
	 * CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN); before JavaFX 20, the columns share it.
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static javafx.util.Callback<TableView.ResizeFeatures, Boolean> flexLastColumn() {
		try {
			return (javafx.util.Callback) TableView.class.getField("CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN").get(null);
		} catch (ReflectiveOperationException e) {
			return TableView.CONSTRAINED_RESIZE_POLICY;
		}
	}

	/** Log text kept; older text is removed from the top. */
	static final int MAX_LOG_LENGTH = 200_000;

	// all the paused script's variables; the tabs show those that pass the filter
	private Map<String,Object> allVariables;
	private boolean variablesEditable;
	// one filter for both tabs (each has a field for it)
	private final javafx.beans.property.StringProperty filterText = new javafx.beans.property.SimpleStringProperty("");
	// the script's own variables (loop variables and $1, $2 ... included), and fsh's
	private final VariableView userVariables = new VariableView("The script's own variables show here while it is paused.");
	private final VariableView fshVariables = new VariableView("fsh's variables show here while a script is paused.");
	private final ObservableList<Breakpoint> breakpoints = FXCollections.observableArrayList();
	private final TableView<Breakpoint> breakpointTable = new TableView<>(breakpoints);
	private final TextArea log = new TextArea();
	private final SyntaxTreeView syntax = new SyntaxTreeView();
	private Consumer<Variable> onEditVariable = v->{};
	private Consumer<Breakpoint> onBreakpointChanged = b->{};
	private Consumer<Breakpoint> onEditBreakpoint = b->{};
	/** what the Breakpoints tab, its rows and its Edit... button say a breakpoint can be (as the Swing IDE's) */
	static final String EDIT_BREAKPOINT_HINT = Breakpoint.EDIT_HINT;
	private final javafx.scene.control.Button editBreakpoint = new javafx.scene.control.Button("Edit...");

	/** Asks for a watchpoint: the variable (filled in) and Read / Write. Empty if canceled. */
	interface WatchpointAsker {
		java.util.Optional<Watchpoint> ask(String variable, boolean read, boolean write);
	}

	// the debug session's watchpoints (the script reads the list as it runs); the table shows them
	private List<Watchpoint> watchpoints = new java.util.concurrent.CopyOnWriteArrayList<>();
	private final ObservableList<Watchpoint> watchpointRows = FXCollections.observableArrayList();
	private final TableView<Watchpoint> watchpointTable = new TableView<>(watchpointRows);
	private Tab watches;
	private WatchpointAsker askWatchpoint = (variable, read, write)->
		WatchpointDialog.ask(getScene() == null ? null : getScene().getWindow(), variable, read, write);

	public DebugPanel() {
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
		breakpointTable.setColumnResizePolicy(flexLastColumn());
		breakpointTable.setPlaceholder(new Label("Click in the editor's margin to add a breakpoint."));
		breakpointTable.setRowFactory(t->{
			TableRow<Breakpoint> row = new TableRow<>();
			row.setOnMouseClicked(e->{
				if( e.getClickCount() == 2 && !row.isEmpty()) {
					onEditBreakpoint.accept(row.getItem());
				}
			});
			// (only on a row with a breakpoint)
			javafx.scene.control.Tooltip hint = new javafx.scene.control.Tooltip(EDIT_BREAKPOINT_HINT);
			row.itemProperty().addListener((o, was, is)->row.setTooltip(is == null ? null : hint));
			return row;
		});
		// the dialog a double-click opens, for the selected breakpoint: so the table shows it can be changed
		editBreakpoint.setTooltip(new javafx.scene.control.Tooltip(EDIT_BREAKPOINT_HINT));
		editBreakpoint.disableProperty().bind(breakpointTable.getSelectionModel().selectedItemProperty().isNull());
		editBreakpoint.setOnAction(e->{
			Breakpoint bp = breakpointTable.getSelectionModel().getSelectedItem();
			if( bp != null ) {
				onEditBreakpoint.accept(bp);
			}
		});
		HBox breakpointButtons = new HBox(8, editBreakpoint);
		breakpointButtons.setPadding(new Insets(4));
		BorderPane breakpointPane = new BorderPane(breakpointTable, breakpointButtons, null, null, null);

		// watchpoints
		watchpointTable.getColumns().add(checkColumn("On", Watchpoint::isEnabled, Watchpoint::setEnabled, 36));
		TableColumn<Watchpoint,String> variable = new TableColumn<>("Variable");
		variable.setCellValueFactory(c->new ReadOnlyStringWrapper(c.getValue().getVariable()));
		variable.setPrefWidth(140);
		watchpointTable.getColumns().add(variable);
		watchpointTable.getColumns().add(checkColumn("Read", Watchpoint::isRead, Watchpoint::setRead, 50));
		watchpointTable.getColumns().add(checkColumn("Write", Watchpoint::isWrite, Watchpoint::setWrite, 50));
		watchpointTable.setEditable(true);
		watchpointTable.getSelectionModel().setSelectionMode(javafx.scene.control.SelectionMode.MULTIPLE);
		watchpointTable.setColumnResizePolicy(flexLastColumn());
		watchpointTable.setPlaceholder(new Label("Add a variable to stop when the script reads or writes it."));
		watchpointTable.setRowFactory(t->{
			TableRow<Watchpoint> row = new TableRow<>();
			row.setOnMouseClicked(e->{
				if( e.getClickCount() == 2 && !row.isEmpty()) {
					editWatchpoint(row.getItem());
				}
			});
			return row;
		});
		javafx.scene.control.Button addWatch = new javafx.scene.control.Button("Add...");
		addWatch.setTooltip(new javafx.scene.control.Tooltip("Watch a variable: stop when the script reads it, writes it, or both"));
		addWatch.setOnAction(e->addWatchpoint(""));
		javafx.scene.control.Button removeWatch = new javafx.scene.control.Button("Remove");
		removeWatch.disableProperty().bind(javafx.beans.binding.Bindings.isEmpty(watchpointTable.getSelectionModel().getSelectedItems()));
		removeWatch.setOnAction(e->removeWatchpoints(new ArrayList<>(watchpointTable.getSelectionModel().getSelectedItems())));
		HBox watchButtons = new HBox(8, addWatch, removeWatch);
		watchButtons.setPadding(new Insets(4));
		BorderPane watchPane = new BorderPane(watchpointTable, watchButtons, null, null, null);

		log.setEditable(false);
		log.getStyleClass().add("debug-log");

		filterText.addListener((o, was, is)->showVariables());
		Tab user = new Tab("User Variables", userVariables.pane);
		user.setTooltip(new javafx.scene.control.Tooltip("The script's own variables, its loop variables and $1, $2 ..."));
		Tab fsh = new Tab("fsh Variables", fshVariables.pane);
		fsh.setTooltip(new javafx.scene.control.Tooltip("The shell's own variables, the environment's, and $0"));
		Tab bps = new Tab("Breakpoints", breakpointPane);
		bps.setTooltip(new javafx.scene.control.Tooltip(EDIT_BREAKPOINT_HINT));
		watches = new Tab("Watchpoints", watchPane);
		watches.setTooltip(new javafx.scene.control.Tooltip("Stop when the script reads or writes a variable"));
		Tab logTab = new Tab("Log", log);
		Tab syntaxTab = new Tab("Syntax", syntax);
		for(Tab t : new Tab[] {user, fsh, bps, watches, logTab, syntaxTab}) {
			t.setClosable(false);
		}
		getTabs().addAll(user, fsh, bps, watches, logTab, syntaxTab);
	}

	/** A column of check boxes for one of a watchpoint's switches. */
	private static TableColumn<Watchpoint,Boolean> checkColumn(String title, java.util.function.Predicate<Watchpoint> get,
			java.util.function.BiConsumer<Watchpoint,Boolean> set, double width) {
		TableColumn<Watchpoint,Boolean> ret = new TableColumn<>(title);
		ret.setCellValueFactory(c->{
			Watchpoint wp = c.getValue();
			SimpleBooleanProperty p = new SimpleBooleanProperty(get.test(wp));
			p.addListener((o, was, is)->set.accept(wp, is));
			return p;
		});
		ret.setCellFactory(CheckBoxTableCell.forTableColumn(ret));
		ret.setEditable(true);
		ret.setPrefWidth(width);
		return ret;
	}

	/**
	 * The watchpoints this shows and changes: the debug session's list, so changes are seen by
	 * a script that is running.
	 */
	public void setWatchpoints(List<Watchpoint> list) {
		watchpoints = list;
		watchpointRows.setAll(list);
	}

	/**
	 * Asks for a variable to watch (name filled in) and how, and adds the watchpoint; for a
	 * variable that's already watched, changes its watchpoint.
	 */
	void addWatchpoint(String name) {
		// (the tab shows, so what's added is seen)
		getSelectionModel().select(watches);
		for(Watchpoint wp : watchpoints) {
			if( wp.getVariable().equals(Watchpoint.normalize(name))) {
				editWatchpoint(wp);
				return;
			}
		}
		askWatchpoint.ask(name, false, true).ifPresent(wp->{
			for(Watchpoint other : watchpoints) {
				if( other.getVariable().equals(wp.getVariable())) {
					other.setRead(wp.isRead());
					other.setWrite(wp.isWrite());
					other.setEnabled(true);
					watchpointRows.setAll(watchpoints);
					watchpointTable.refresh();
					return;
				}
			}
			watchpoints.add(wp);
			watchpointRows.setAll(watchpoints);
		});
	}

	private void editWatchpoint(Watchpoint wp) {
		askWatchpoint.ask(wp.getVariable(), wp.isRead(), wp.isWrite()).ifPresent(changed->{
			wp.setVariable(changed.getVariable());
			wp.setRead(changed.isRead());
			wp.setWrite(changed.isWrite());
			watchpointTable.refresh();
		});
	}

	void removeWatchpoints(List<Watchpoint> list) {
		watchpoints.removeAll(list);
		watchpointRows.setAll(watchpoints);
	}

	/** How a breakpoint stops: its condition, hit count, or always (with its code). */
	static String describe(Breakpoint bp) {
		String stops = bp.stopsText();
		return stops.isEmpty() ? bp.getCode() : stops;
	}

	/** Shows a paused script's variables; they can be edited until {@link #clearVariables()} or a new set. */
	public void setVariables(Map<String,Object> vars, boolean editable) {
		allVariables = vars;
		variablesEditable = editable;
		showVariables();
	}

	private void showVariables() {
		String f = filterText.get() == null ? "" : filterText.get().trim().toLowerCase();
		userVariables.setRows(filtered(DebugVariables.list(allVariables, true, false), f));
		fshVariables.setRows(filtered(DebugVariables.list(allVariables, true, true), f));
	}

	private static List<Variable> filtered(List<Variable> list, String f) {
		List<Variable> ret = new ArrayList<>();
		for(Variable v : list) {
			if( f.isEmpty() || v.getName().toLowerCase().contains(f)) {
				ret.add(v);
			}
		}
		return ret;
	}

	/** Leaves the variables shown but read-only (the script isn't paused any more). */
	public void freezeVariables() {
		variablesEditable = false;
	}

	public void clearVariables() {
		allVariables = null;
		variablesEditable = false;
		showVariables();
		userVariables.detail.clear();
		fshVariables.detail.clear();
	}

	/** A variable's value as text ("" for null). */
	static String valueText(Object value) {
		return value == null ? "" : String.valueOf(value);
	}

	/** A variable as the pane under the table shows it: "name = value". */
	static String describe(Variable v) {
		return v.getName()+" = "+valueText(v.getValue());
	}

	/**
	 * One tab of variables: a filter, the Name, Type and Value table, and under it the clicked
	 * variable (name = value). The Value column is as wide as its widest value (the table scrolls
	 * sideways when that's wider than the tab); a double click opens the value in an editor.
	 */
	private class VariableView {
		final ObservableList<Variable> rows = FXCollections.observableArrayList();
		final TableView<Variable> table = new TableView<>(rows);
		final TableColumn<Variable,String> name = new TableColumn<>("Name");
		final TableColumn<Variable,String> type = new TableColumn<>("Type");
		final TableColumn<Variable,String> value = new TableColumn<>("Value");
		final TextField filter = new TextField();
		final TextArea detail = new TextArea();
		final BorderPane pane;

		VariableView(String placeholder) {
			name.setCellValueFactory(c->new ReadOnlyStringWrapper(c.getValue().getName()));
			name.setPrefWidth(120);
			type.setCellValueFactory(c->new ReadOnlyStringWrapper(DebugVariables.typeName(c.getValue().getValue())));
			type.setPrefWidth(70);
			// one line in the table: the line breaks of a value show as ⏎
			value.setCellValueFactory(c->new ReadOnlyStringWrapper(oneLine(valueText(c.getValue().getValue()))));
			value.setPrefWidth(200);
			// (fsh-ide.css puts its heading at the left)
			value.getStyleClass().add("variable-value");
			// at least the rest of the table's width, so a short value still fills it
			javafx.beans.InvalidationListener fill = o->value.setMinWidth(Math.max(80, table.getWidth()-name.getWidth()-type.getWidth()-18));
			table.widthProperty().addListener(fill);
			name.widthProperty().addListener(fill);
			type.widthProperty().addListener(fill);
			table.getColumns().add(name);
			table.getColumns().add(type);
			table.getColumns().add(value);
			table.setEditable(false);
			table.setPlaceholder(new Label(placeholder));
			table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
			table.setRowFactory(t->{
				TableRow<Variable> row = new TableRow<>();
				// right-click: watch the variable
				javafx.scene.control.MenuItem watch = new javafx.scene.control.MenuItem();
				watch.setOnAction(e->addWatchpoint(row.getItem().getName()));
				javafx.scene.control.ContextMenu menu = new javafx.scene.control.ContextMenu(watch);
				row.itemProperty().addListener((o, was, is)->{
					if( is == null ) {
						row.setContextMenu(null);
					} else {
						watch.setText("Watch "+is.getName()+"...");
						watch.setDisable(!Watchpoint.isValidName(is.getName()));
						row.setContextMenu(menu);
					}
				});
				row.setOnMouseClicked(e->{
					if( row.isEmpty()) {
						return;
					}
					detail.setText(describe(row.getItem()));
					if( e.getClickCount() == 2 ) {
						onEditVariable.accept(row.getItem());
					}
				});
				return row;
			});

			filter.setPromptText("Filter by name");
			filter.textProperty().bindBidirectional(filterText);
			HBox options = new HBox(8, filter);
			options.setPadding(new Insets(4));
			options.setStyle("-fx-alignment: center-left;");
			HBox.setHgrow(filter, Priority.ALWAYS);

			detail.setEditable(false);
			detail.setWrapText(true);
			detail.setPromptText("Click a variable to see its value here; double-click to change it.");
			detail.setStyle("-fx-font-family: monospace;");
			javafx.scene.control.SplitPane split = new javafx.scene.control.SplitPane(table, detail);
			split.setOrientation(javafx.geometry.Orientation.VERTICAL);
			split.setDividerPositions(0.75);
			pane = new BorderPane(split, options, null, null, null);
		}

		void setRows(List<Variable> list) {
			rows.setAll(list);
			// the Value column as wide as its widest value
			javafx.scene.text.Text measure = new javafx.scene.text.Text();
			double width = 200;
			for(Variable v : list) {
				measure.setText(oneLine(valueText(v.getValue())));
				width = Math.max(width, measure.getLayoutBounds().getWidth()+24);
			}
			value.setPrefWidth(width);
		}
	}

	private static String oneLine(String text) {
		return text.replace("\r\n", "⏎").replace('\n', '⏎').replace('\r', '⏎');
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

	/** Called with a variable that was double-clicked, to open its value in an editor. */
	public void setOnEditVariable(Consumer<Variable> handler) { onEditVariable = handler; }

	/** Called when a breakpoint is switched on or off here. */
	public void setOnBreakpointChanged(Consumer<Breakpoint> handler) { onBreakpointChanged = handler; }

	// for tests
	javafx.scene.control.Button editBreakpointButton() { return editBreakpoint; }
	TableView<Breakpoint> breakpointTable() { return breakpointTable; }

	/** Called when a breakpoint is double-clicked. */
	public void setOnEditBreakpoint(Consumer<Breakpoint> handler) { onEditBreakpoint = handler; }

	// for tests
	/** The variables shown on both tabs. */
	List<Variable> variables() {
		List<Variable> ret = new ArrayList<>(userVariables.rows);
		ret.addAll(fshVariables.rows);
		return ret;
	}
	ObservableList<Variable> userVariables() { return userVariables.rows; }
	ObservableList<Variable> fshVariables() { return fshVariables.rows; }
	ObservableList<Breakpoint> breakpoints() { return breakpoints; }
	ObservableList<Watchpoint> watchpointRows() { return watchpointRows; }
	void setWatchpointAsker(WatchpointAsker asker) { askWatchpoint = asker; }
	String logText() { return log.getText(); }
	public boolean variablesEditable() { return variablesEditable; }
	TextField filterField() { return userVariables.filter; }
	TableView<Variable> userTable() { return userVariables.table; }
	TableView<Variable> fshTable() { return fshVariables.table; }
	String userDetailText() { return userVariables.detail.getText(); }
}
