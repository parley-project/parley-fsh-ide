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
package us.bringardner.fsh.ide;

import us.bringardner.fsh.ide.core.Breakpoint;
import us.bringardner.fsh.ide.core.DebugVariables;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.ScrollPaneConstants;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;

import us.bringardner.fsh.ShellContext;
import us.bringardner.fsh.ide.core.SyntaxNode;
import us.bringardner.fsh.ide.core.Variable;


public class DebugVariablePanel extends JPanel  {

	private static final long serialVersionUID = 1L;
	private ShellContext ctx ;
	private Map<String,Object> variableSnapshot;

	// the script's own variables (loop variables and $1, $2 ... included), and fsh's
	private final VariableTable userVariables = new VariableTable();
	private final VariableTable fshVariables = new VariableTable();

	// shows a clicked variable (as "name = value") in the IDE's text area
	private Consumer<String> onShowVariable = s->{};

	/** Simple class name of a variable's value (the Type column), or "" for null. */
	static String typeName(Object value) {
		if( value == null ) {
			return "";
		}
		return value.getClass().getSimpleName();
	}

	/** A variable's value as text ("" for null). */
	static String valueText(Object value) {
		return value == null ? "" : String.valueOf(value);
	}

	/** A variable as the text area shows it: "name = value". */
	static String describe(Variable v) {
		return v.getName()+" = "+valueText(v.getValue())+"\n";
	}

	/**
	 * The value a dialog's text sets: true or false for a Boolean variable, otherwise the text.
	 */
	static Object parseValue(Object oldValue, String text) {
		if( oldValue instanceof Boolean ) {
			String t = text.trim();
			if( t.equalsIgnoreCase("true") || t.equalsIgnoreCase("false")) {
				return Boolean.valueOf(t);
			}
		}
		return text;
	}

	/**
	 * One tab of variables: Name, Type and Value. The table is as wide as its widest value (it
	 * scrolls sideways when that's wider than the tab), a click shows the variable in the text
	 * area, and a double click opens its value in an editor.
	 */
	private class VariableTable {
		private final String [] columnNames = {"Name","Type","Value"};
		private List<Variable> rows = new ArrayList<>();
		private final AbstractTableModel model = new AbstractTableModel() {
			private static final long serialVersionUID = 1L;

			@Override
			public String getColumnName(int col) {
				return columnNames[col];
			}

			@Override
			public int getColumnCount() {
				return columnNames.length;
			}

			@Override
			public int getRowCount() {
				return rows.size();
			}

			@Override
			public Object getValueAt(int row, int col) {
				Variable val = rows.get(row);
				switch (col) {
				case 0: return val.getName();
				case 1: return typeName(val.getValue());
				case 2: return val.getValue();
				default:
					throw new IllegalArgumentException("Unexpected value: " + col);
				}
			}

			@Override
			public boolean isCellEditable(int row, int col) {
				// values are changed in the editor a double click opens
				return false;
			}
		};

		private final JTable table = new JTable(model) {
			private static final long serialVersionUID = 1L;

			// the columns' own widths together
			private int columnsWidth() {
				int ret = 0;
				for(int i=0, n=getColumnModel().getColumnCount(); i < n; i++ ) {
					ret += getColumnModel().getColumn(i).getPreferredWidth();
				}
				return ret;
			}

			// fill the tab, unless the columns need more room: then scroll sideways
			@Override
			public boolean getScrollableTracksViewportWidth() {
				return getParent() != null && columnsWidth() < getParent().getWidth();
			}

			@Override
			public java.awt.Dimension getPreferredSize() {
				java.awt.Dimension d = super.getPreferredSize();
				d.width = columnsWidth();
				return d;
			}

			// each column at its width, and the Value column takes any room left over
			@Override
			public void doLayout() {
				javax.swing.table.TableColumnModel cm = getColumnModel();
				int n = cm.getColumnCount();
				if( n == 0 ) {
					return;
				}
				TableColumn resizing = getTableHeader() == null ? null : getTableHeader().getResizingColumn();
				if( resizing != null ) {
					// a column dragged wider or narrower keeps that width
					resizing.setPreferredWidth(resizing.getWidth());
				}
				int extra = Math.max(0, getWidth()-columnsWidth());
				for(int i=0; i < n; i++ ) {
					TableColumn c = cm.getColumn(i);
					c.setWidth(c.getPreferredWidth()+(i == n-1 ? extra : 0));
				}
			}
		};

		private final JScrollPane scrollPane = new JScrollPane(table,
				ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
				ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);

		VariableTable() {
			table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
			table.setAutoCreateRowSorter(true);
			table.getColumn("Value").setCellRenderer(new DefaultTableCellRenderer() {
				private static final long serialVersionUID = 1L;

				@Override
				public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column){
					if (value instanceof Boolean) {
						JCheckBox checkBox = new JCheckBox();
						checkBox.setOpaque(true);
						checkBox.setSelected(((Boolean)value).booleanValue()) ;
						if( isSelected ) {
							checkBox.setBackground(table.getSelectionBackground());
						}
						return checkBox;
					}
					// one line in the table: the line breaks of a value show as ⏎
					String text = valueText(value).replace("\r\n", "⏎").replace('\n', '⏎').replace('\r', '⏎');
					return super.getTableCellRendererComponent(table, text, isSelected, hasFocus, row, column);
				}
			});
			table.addMouseListener(new MouseAdapter() {
				@Override
				public void mouseClicked(MouseEvent e) {
					int viewRow = table.rowAtPoint(e.getPoint());
					if( viewRow < 0 ) {
						return;
					}
					Variable v = rows.get(table.convertRowIndexToModel(viewRow));
					if( e.getClickCount() == 1 ) {
						onShowVariable.accept(describe(v));
					} else if( e.getClickCount() == 2 ) {
						editVariable(table, v);
					}
				}
			});
		}

		void setRows(List<Variable> list) {
			rows = list;
			model.fireTableDataChanged();
			fitColumns();
		}

		// each column as wide as its widest cell (the Value column at least 200 pixels)
		private void fitColumns() {
			for(int col=0; col < table.getColumnCount(); col++ ) {
				TableColumn column = table.getColumnModel().getColumn(col);
				TableCellRenderer header = table.getTableHeader().getDefaultRenderer();
				int width = header.getTableCellRendererComponent(table, column.getHeaderValue(), false, false, -1, col).getPreferredSize().width;
				for(int row=0; row < table.getRowCount(); row++ ) {
					Component c = table.prepareRenderer(table.getCellRenderer(row, col), row, col);
					width = Math.max(width, c.getPreferredSize().width);
				}
				width += 2*table.getIntercellSpacing().width + 8;
				if( col == 2 ) {
					width = Math.max(width, 200);
				}
				column.setPreferredWidth(width);
			}
			table.revalidate();
		}
	}

	private String [] breakpointsColumnNames = {"Enabled","Line",""};
	private List<Breakpoint> breakpoints = new ArrayList<>();
	private DefaultTableModel breakpointsTableModel = new DefaultTableModel() {

		private static final long serialVersionUID = 1L;

		public String getColumnName(int col) {
			return breakpointsColumnNames[col].toString();
		}

		public int getColumnCount() {
			return breakpointsColumnNames.length;
		}

		public int getRowCount() {
			return breakpoints.size();
		}

		public Object getValueAt(int row, int col) {
			Breakpoint bp = breakpoints.get(row);
			Object ret = null;
			switch (col) {
			case 0: ret = bp.isEnabled(false);
			break;
			case 1: ret = "Line "+(bp.getLine()+1);
			break;

			case 2: ret = bp.isConditional()?bp.getCondition():bp.isHitCount()?"Hitcount "+bp.getHitCount():"";
			break;
			default:
				throw new IllegalArgumentException("Unexpected value: " + col);
			}

			return ret;
		}

		public java.lang.Class<?> getColumnClass(int columnIndex) {
			if( columnIndex==0) {
				return Boolean.class;
			}
			return String.class;

		};

		public boolean isCellEditable(int row, int col){
			if( col == 0 ) {
				return true;
			}
			return false;
		}

		public void setValueAt(Object aValue, int row, int column) {
			if( column == 0) {
				if (aValue instanceof Boolean) {
					Boolean b = (Boolean) aValue;
					breakpoints.get(row).setEnabled(b);
				}
			}
		};
	};

	private EditorPanel editorPane;
	private FshTreeViewPanel treeView;

	/**
	 * Create the panel.
	 */
	public DebugVariablePanel() {
		setLayout(new BorderLayout(0, 0));

		JTabbedPane tabbedPane = new JTabbedPane(JTabbedPane.TOP);
		tabbedPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
		add(tabbedPane, BorderLayout.CENTER);

		tabbedPane.addTab("User Variables", null, userVariables.scrollPane, "The script's own variables, its loop variables and $1, $2 ...");
		tabbedPane.addTab("fsh Variables", null, fshVariables.scrollPane, "The shell's own variables, the environment's, and $0");

		JScrollPane breakpointsScrollPane = new JScrollPane();
		JPanel panel = new JPanel();

		tabbedPane.addTab("Breakpoints", null, panel, null);
		panel.setLayout(new BorderLayout(0, 0));

		JTable btable = new JTable(breakpointsTableModel);
		btable.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if( e.getClickCount() == 2) {
					int row = btable.getSelectedRow();
					Breakpoint bp = breakpoints.get(row);
					BreakpointPropertiesDialog d = new BreakpointPropertiesDialog();
					d.showDialog(bp, btable, e.getXOnScreen(), e.getYOnScreen());
					if( d.isDelete()) {
						// the editor's breakpoint listener refreshes this table
						editorPane.removeBreakpoint(bp);
					} else {
						breakpointsTableModel.fireTableDataChanged();
					}
				}
			}
		});
		breakpointsScrollPane.setViewportView(btable);
		panel.add(breakpointsScrollPane,BorderLayout.CENTER);

		JPanel panel_1 = new JPanel();
		panel.add(panel_1, BorderLayout.NORTH);

		JButton btnNewButton = new JButton("Enable All");
		btnNewButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				actionEnableAll(true);
			}
		});
		panel_1.add(btnNewButton);

		JButton btnNewButton_1 = new JButton("Disable All");
		btnNewButton_1.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				actionEnableAll(false);
			}
		});
		panel_1.add(btnNewButton_1);

		JScrollPane treeScrollPane = new JScrollPane();
		tabbedPane.addTab("AS Tree", null, treeScrollPane, null);
		treeView = new FshTreeViewPanel();
		treeScrollPane.setViewportView(treeView);
	}

	/** Called with "name = value" when a variable is clicked. */
	public void setOnShowVariable(Consumer<String> handler) {
		onShowVariable = handler == null ? s->{} : handler;
	}

	/** Opens a variable's value in a multi-line editor and sets what is typed there. */
	private void editVariable(Component parent, Variable v) {
		VariableValueDialog d = new VariableValueDialog(parent, v.getName(), valueText(v.getValue()), ctx != null);
		String text = d.showDialog();
		if( text == null || ctx == null ) {
			return;
		}
		try {
			// the same as the JavaFX IDE: a positional parameter for $1 ..., else a variable
			DebugVariables.set(ctx, v.getName(), parseValue(v.getValue(), text));
		} catch (RuntimeException e) {
			// not one that can be set: leave it
		}
		// the script is paused, so its variables can be read here
		variableSnapshot = ctx.getVariables();
		createNodes();
	}

	protected void actionEnableAll(boolean b) {
		for(Breakpoint bo : breakpoints) {
			bo.setEnabled(b);
		}
		breakpointsTableModel.fireTableDataChanged();

	}

	private void createNodes() {
		Map<String,Object> vars = ctx == null ? null : variableSnapshot;
		userVariables.setRows(DebugVariables.list(vars, true, false));
		fshVariables.setRows(DebugVariables.list(vars, true, true));
		refreshBreakpoints();
	}

	/** Reloads the breakpoints table from the editor. */
	public void refreshBreakpoints() {
		List<Breakpoint> tmp = new ArrayList<Breakpoint>();
		if( editorPane != null ) {
			Map<Integer, Breakpoint> map = editorPane.getBreakpoints();
			for(Breakpoint bp : map.values()) {
				tmp.add(bp);
			}
		}
		breakpoints = tmp;
		breakpointsTableModel.fireTableDataChanged();

	}

	/**
	 * @param variables ctx's variables, read on the script's own thread (ctx.getVariables()),
	 *        since the script may still be changing them while this runs on the EDT
	 */
	public void setContext(EditorPanel editorPane, ShellContext ctx, Map<String,Object> variables ) {
		this.ctx = ctx;
		this.variableSnapshot = variables;
		this.editorPane = editorPane;
		createNodes();
	}

	public void setContext(EditorPanel editorPane, ShellContext ctx ) {
		setContext(editorPane, ctx, ctx == null ? null : ctx.getVariables());
	}

	public void updateTree(SyntaxNode tree, String string) {
		treeView.setTree(tree,string);
	}

	// for tests
	JTable userTable() { return userVariables.table; }
	JTable fshTable() { return fshVariables.table; }
}
