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
import us.bringardner.fsh.ide.core.Watchpoint;

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
	// while no script runs: what the next run starts with (the fsh Variables tab shows these), or null
	private Map<String,Object> startVariables;
	/** what the fsh Variables tab says while it shows what the next run starts with */
	static final String START_VARIABLES_CAPTION = "No script is running: this is what the next run starts with.";
	static final String START_VARIABLES_HINT = "<html>The shell's own variables, the environment's and $0, as the next run gets them.<br>"
			+"What the startup file FSH_ENV names sets isn't here: that file runs when the script does.</html>";
	private final javax.swing.JLabel startCaption = new javax.swing.JLabel(START_VARIABLES_CAPTION);

	// the script's own variables (loop variables and $1, $2 ... included), and fsh's
	private final VariableTable userVariables = new VariableTable();
	private final VariableTable fshVariables = new VariableTable();

	// shows a clicked variable (as "name = value") in the IDE's text area
	private Consumer<String> onShowVariable = s->{};

	/** A variable's value as text ("" for null). */
	static String valueText(Object value) {
		return value == null ? "" : String.valueOf(value);
	}

	/** A variable as the text area shows it: "name = value". */
	static String describe(Variable v) {
		return v.getName()+" = "+valueText(v.getValue())+"\n";
	}

	/**
	 * The value a dialog's text sets: one of the type the variable has now (see DebugVariables).
	 *
	 * @throws IllegalArgumentException with why, for text that isn't a value of that type
	 */
	static Object parseValue(Object oldValue, String text) {
		return DebugVariables.parseValue(oldValue, text);
	}

	/**
	 * A table's column headings in the theme's header colors: its header text on its header
	 * background. (The look and feel's own renderer takes only the text's color from the theme: on
	 * the Mac it draws the system's light heading behind it, where the theme's white header text
	 * couldn't be read.) With the arrow of the column the table is sorted by, as the look's has.
	 */
	static class HeadingRenderer extends DefaultTableCellRenderer {
		private static final long serialVersionUID = 1L;
		// the column (the model's) whose heading is at the left, or -1: the others are centered
		private final int leadingColumn;

		HeadingRenderer(int leadingColumn) {
			this.leadingColumn = leadingColumn;
		}

		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
			super.getTableCellRendererComponent(table, value, false, false, row, column);
			javax.swing.table.JTableHeader header = table.getTableHeader();
			// (read each time, so a theme chosen while the window is open shows)
			java.awt.Color background = javax.swing.UIManager.getColor("TableHeader.background");
			java.awt.Color foreground = javax.swing.UIManager.getColor("TableHeader.foreground");
			if( background == null || foreground == null ) {
				// no theme: the look's own pair
				background = header.getBackground();
				foreground = header.getForeground();
			}
			setOpaque(true);
			setBackground(background);
			setForeground(foreground);
			setFont(header.getFont());
			int modelColumn = table.convertColumnIndexToModel(column);
			setHorizontalAlignment(modelColumn == leadingColumn ? javax.swing.SwingConstants.LEADING : javax.swing.SwingConstants.CENTER);
			setHorizontalTextPosition(javax.swing.SwingConstants.LEADING);
			setIcon(sortIcon(table, modelColumn));
			// a line under the headings and between them, in the text's color so it shows on the background
			setBorder(javax.swing.BorderFactory.createCompoundBorder(
					javax.swing.BorderFactory.createMatteBorder(0, 0, 1, 1, foreground),
					javax.swing.BorderFactory.createEmptyBorder(2, 6, 2, 6)));
			return this;
		}

		// the arrow for the column the table is sorted by first, or null
		private static javax.swing.Icon sortIcon(JTable table, int modelColumn) {
			if( table.getRowSorter() == null || table.getRowSorter().getSortKeys().isEmpty()) {
				return null;
			}
			javax.swing.RowSorter.SortKey key = table.getRowSorter().getSortKeys().get(0);
			if( key.getColumn() != modelColumn ) {
				return null;
			}
			switch (key.getSortOrder()) {
			case ASCENDING: return javax.swing.UIManager.getIcon("Table.ascendingSortIcon");
			case DESCENDING: return javax.swing.UIManager.getIcon("Table.descendingSortIcon");
			default: return null;
			}
		}
	}

	/**
	 * One tab of variables: Name and Value. The table is as wide as its widest value (it
	 * scrolls sideways when that's wider than the tab), a click shows the variable in the text
	 * area, and a double click opens its value in an editor.
	 */
	private class VariableTable {
		private static final int VALUE_COLUMN = 1;
		private final String [] columnNames = {"Name","Value"};
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
				case 1: return val.getValue();
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
			// the Value column can be very wide: its heading at the left, where it can be seen
			table.getTableHeader().setDefaultRenderer(new HeadingRenderer(VALUE_COLUMN));
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

				@Override
				public void mousePressed(MouseEvent e) {
					showMenu(e);
				}

				@Override
				public void mouseReleased(MouseEvent e) {
					showMenu(e);
				}

				// right-click: watch the variable
				private void showMenu(MouseEvent e) {
					int viewRow = table.rowAtPoint(e.getPoint());
					if( !e.isPopupTrigger() || viewRow < 0 ) {
						return;
					}
					table.setRowSelectionInterval(viewRow, viewRow);
					Variable v = rows.get(table.convertRowIndexToModel(viewRow));
					javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
					javax.swing.JMenuItem watch = new javax.swing.JMenuItem("Watch "+v.getName()+"...");
					watch.setEnabled(Watchpoint.isValidName(v.getName()));
					watch.addActionListener(a->addWatchpoint(v.getName()));
					menu.add(watch);
					menu.show(table, e.getX(), e.getY());
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
				Component heading = header.getTableCellRendererComponent(table, column.getHeaderValue(), false, false, -1, col);
				int width = heading.getPreferredSize().width;
				javax.swing.Icon arrow = javax.swing.UIManager.getIcon("Table.ascendingSortIcon");
				if( arrow != null && heading instanceof javax.swing.JLabel && ((javax.swing.JLabel) heading).getIcon() == null ) {
					// room for the arrow the heading gets when the table is sorted by this column
					width += arrow.getIconWidth()+((javax.swing.JLabel) heading).getIconTextGap();
				}
				for(int row=0; row < table.getRowCount(); row++ ) {
					Component c = table.prepareRenderer(table.getCellRenderer(row, col), row, col);
					width = Math.max(width, c.getPreferredSize().width);
				}
				width += 2*table.getIntercellSpacing().width + 8;
				if( table.convertColumnIndexToModel(col) == VALUE_COLUMN ) {
					width = Math.max(width, 200);
				}
				column.setPreferredWidth(width);
			}
			table.revalidate();
		}
	}

	/** what the Breakpoints tab, its table and its Edit... button say a breakpoint can be */
	static final String EDIT_BREAKPOINT_HINT = Breakpoint.EDIT_HINT;
	private JTable breakpointsTable;
	private final JButton editBreakpointButton = new JButton("Edit...");

	/** The breakpoint's dialog (Edit... or a double-click), at x, y on the screen. */
	private void editBreakpoint(int row, int x, int y) {
		if( row < 0 || row >= breakpoints.size()) {
			return;
		}
		Breakpoint bp = breakpoints.get(row);
		BreakpointPropertiesDialog d = new BreakpointPropertiesDialog();
		d.showDialog(bp, breakpointsTable, x, y);
		if( d.isDelete()) {
			// the editor's breakpoint listener refreshes this table
			editorPane.removeBreakpoint(bp);
		} else {
			breakpointsTableModel.fireTableDataChanged();
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

			case 2: ret = bp.stopsText();
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
	private JTabbedPane tabbedPane;
	private JPanel watchPanel;

	// the debugger's watchpoints (the DebugSession's list: the script reads it as it runs)
	private List<Watchpoint> watchpoints = new java.util.concurrent.CopyOnWriteArrayList<>();
	private final String [] watchpointsColumnNames = {"Enabled","Variable","Read","Write"};
	private final AbstractTableModel watchpointsTableModel = new AbstractTableModel() {
		private static final long serialVersionUID = 1L;

		@Override
		public String getColumnName(int col) {
			return watchpointsColumnNames[col];
		}

		@Override
		public int getColumnCount() {
			return watchpointsColumnNames.length;
		}

		@Override
		public int getRowCount() {
			return watchpoints.size();
		}

		@Override
		public Object getValueAt(int row, int col) {
			Watchpoint wp = watchpoints.get(row);
			switch (col) {
			case 0: return wp.isEnabled();
			case 1: return wp.getVariable();
			case 2: return wp.isRead();
			case 3: return wp.isWrite();
			default:
				throw new IllegalArgumentException("Unexpected value: " + col);
			}
		}

		@Override
		public Class<?> getColumnClass(int col) {
			return col == 1 ? String.class : Boolean.class;
		}

		@Override
		public boolean isCellEditable(int row, int col) {
			return col != 1;
		}

		@Override
		public void setValueAt(Object value, int row, int col) {
			if( !(value instanceof Boolean) || row >= watchpoints.size()) {
				return;
			}
			Watchpoint wp = watchpoints.get(row);
			boolean b = (Boolean) value;
			switch (col) {
			case 0: wp.setEnabled(b); break;
			case 2: wp.setRead(b); break;
			case 3: wp.setWrite(b); break;
			default:
			}
			fireTableRowsUpdated(row, row);
		}
	};
	private final JTable watchpointsTable = new JTable(watchpointsTableModel);

	/**
	 * Create the panel.
	 */
	public DebugVariablePanel() {
		setLayout(new BorderLayout(0, 0));

		tabbedPane = new JTabbedPane(JTabbedPane.TOP);
		tabbedPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
		add(tabbedPane, BorderLayout.CENTER);

		tabbedPane.addTab("User Variables", null, userVariables.scrollPane, "The script's own variables, its loop variables and $1, $2 ...");
		// (over the table, only while it shows what the next run starts with)
		JPanel fshPanel = new JPanel(new BorderLayout(0, 0));
		startCaption.setToolTipText(START_VARIABLES_HINT);
		startCaption.setBorder(javax.swing.BorderFactory.createEmptyBorder(3, 6, 3, 6));
		startCaption.setVisible(false);
		fshPanel.add(startCaption, BorderLayout.NORTH);
		fshPanel.add(fshVariables.scrollPane, BorderLayout.CENTER);
		tabbedPane.addTab("fsh Variables", null, fshPanel, "The shell's own variables, the environment's, and $0");

		JScrollPane breakpointsScrollPane = new JScrollPane();
		JPanel panel = new JPanel();

		tabbedPane.addTab("Breakpoints", null, panel, EDIT_BREAKPOINT_HINT);
		panel.setLayout(new BorderLayout(0, 0));

		JTable btable = new JTable(breakpointsTableModel);
		btable.getTableHeader().setDefaultRenderer(new HeadingRenderer(-1));
		breakpointsTable = btable;
		btable.setToolTipText(EDIT_BREAKPOINT_HINT);
		btable.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if( e.getClickCount() == 2 && btable.getSelectedRow() >= 0 ) {
					editBreakpoint(btable.getSelectedRow(), e.getXOnScreen(), e.getYOnScreen());
				}
			}
		});
		breakpointsScrollPane.setViewportView(btable);
		panel.add(breakpointsScrollPane,BorderLayout.CENTER);

		JPanel panel_1 = new JPanel();
		panel.add(panel_1, BorderLayout.NORTH);

		// the dialog a double-click opens, for the selected breakpoint: so the table shows it can be changed
		editBreakpointButton.setToolTipText(EDIT_BREAKPOINT_HINT);
		editBreakpointButton.setEnabled(false);
		btable.getSelectionModel().addListSelectionListener(e->editBreakpointButton.setEnabled(btable.getSelectedRow() >= 0));
		editBreakpointButton.addActionListener(e->{
			java.awt.Point p = editBreakpointButton.getLocationOnScreen();
			editBreakpoint(btable.getSelectedRow(), p.x, p.y+editBreakpointButton.getHeight());
		});
		panel_1.add(editBreakpointButton);

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

		// watchpoints: stop when the script reads or writes a variable
		watchPanel = new JPanel(new BorderLayout(0, 0));
		JPanel watchButtons = new JPanel();
		JButton addWatch = new JButton("Add...");
		addWatch.setToolTipText("Watch a variable: stop when the script reads it, writes it, or both");
		addWatch.addActionListener(e->addWatchpoint(""));
		watchButtons.add(addWatch);
		JButton removeWatch = new JButton("Remove");
		removeWatch.addActionListener(e->removeSelectedWatchpoints());
		watchButtons.add(removeWatch);
		watchPanel.add(watchButtons, BorderLayout.NORTH);
		watchpointsTable.getColumnModel().getColumn(0).setMaxWidth(70);
		watchpointsTable.getColumnModel().getColumn(2).setMaxWidth(60);
		watchpointsTable.getColumnModel().getColumn(3).setMaxWidth(60);
		watchpointsTable.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				int row = watchpointsTable.rowAtPoint(e.getPoint());
				if( e.getClickCount() == 2 && row >= 0 && watchpointsTable.columnAtPoint(e.getPoint()) == 1 ) {
					editWatchpoint(watchpoints.get(row));
				}
			}
		});
		watchpointsTable.getTableHeader().setDefaultRenderer(new HeadingRenderer(-1));
		watchPanel.add(new JScrollPane(watchpointsTable), BorderLayout.CENTER);
		tabbedPane.addTab("Watchpoints", null, watchPanel, "Stop when the script reads or writes a variable");

		JScrollPane treeScrollPane = new JScrollPane();
		tabbedPane.addTab("AS Tree", null, treeScrollPane, null);
		treeView = new FshTreeViewPanel();
		treeScrollPane.setViewportView(treeView);
	}

	/**
	 * The watchpoints this shows and changes: the debug session's list, so changes are seen by a
	 * script that is running.
	 */
	public void setWatchpoints(List<Watchpoint> list) {
		watchpoints = list;
		watchpointsTableModel.fireTableDataChanged();
	}

	/**
	 * Asks for a variable to watch (name filled in) and how; adds the watchpoint, or changes the
	 * one there is for that variable.
	 */
	void addWatchpoint(String name) {
		// (the tab shows, so what's added is seen)
		tabbedPane.setSelectedComponent(watchPanel);
		Watchpoint existing = null;
		for(Watchpoint wp : watchpoints) {
			if( wp.getVariable().equals(Watchpoint.normalize(name))) {
				existing = wp;
			}
		}
		if( existing != null ) {
			editWatchpoint(existing);
			return;
		}
		Watchpoint wp = askWatchpoint(this, name, false, true);
		if( wp == null ) {
			return;
		}
		for(Watchpoint other : watchpoints) {
			if( other.getVariable().equals(wp.getVariable())) {
				other.setRead(wp.isRead());
				other.setWrite(wp.isWrite());
				other.setEnabled(true);
				watchpointsTableModel.fireTableDataChanged();
				return;
			}
		}
		watchpoints.add(wp);
		watchpointsTableModel.fireTableDataChanged();
	}

	private void editWatchpoint(Watchpoint wp) {
		Watchpoint changed = askWatchpoint(this, wp.getVariable(), wp.isRead(), wp.isWrite());
		if( changed == null ) {
			return;
		}
		wp.setVariable(changed.getVariable());
		wp.setRead(changed.isRead());
		wp.setWrite(changed.isWrite());
		watchpointsTableModel.fireTableDataChanged();
	}

	private void removeSelectedWatchpoints() {
		int [] rows = watchpointsTable.getSelectedRows();
		List<Watchpoint> remove = new ArrayList<>();
		for(int r : rows) {
			remove.add(watchpoints.get(watchpointsTable.convertRowIndexToModel(r)));
		}
		watchpoints.removeAll(remove);
		watchpointsTableModel.fireTableDataChanged();
	}

	/**
	 * The watchpoint dialog: the variable's name and Read / Write. Null unless OK is pressed
	 * (closing it any other way cancels).
	 */
	static Watchpoint askWatchpoint(Component parent, String name, boolean read, boolean write) {
		javax.swing.JTextField nameField = new javax.swing.JTextField(name, 20);
		JCheckBox readBox = new JCheckBox("Read", read);
		JCheckBox writeBox = new JCheckBox("Write", write);
		JPanel panel = new JPanel(new java.awt.GridBagLayout());
		java.awt.GridBagConstraints c = new java.awt.GridBagConstraints();
		c.anchor = java.awt.GridBagConstraints.WEST;
		c.insets = new java.awt.Insets(2, 2, 2, 6);
		panel.add(new javax.swing.JLabel("Variable:"), c);
		c.gridx = 1;
		c.fill = java.awt.GridBagConstraints.HORIZONTAL;
		panel.add(nameField, c);
		c.gridx = 0;
		c.gridy = 1;
		c.fill = java.awt.GridBagConstraints.NONE;
		panel.add(new javax.swing.JLabel("Stop on:"), c);
		JPanel boxes = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));
		boxes.add(readBox);
		boxes.add(writeBox);
		c.gridx = 1;
		panel.add(boxes, c);
		while( true ) {
			int ret = javax.swing.JOptionPane.showConfirmDialog(parent, panel, "Watchpoint",
					javax.swing.JOptionPane.OK_CANCEL_OPTION, javax.swing.JOptionPane.PLAIN_MESSAGE);
			if( ret != javax.swing.JOptionPane.OK_OPTION ) {
				return null;
			}
			String error = watchpointError(nameField.getText(), readBox.isSelected(), writeBox.isSelected());
			if( error == null ) {
				return new Watchpoint(nameField.getText(), readBox.isSelected(), writeBox.isSelected());
			}
			javax.swing.JOptionPane.showMessageDialog(parent, error, "Watchpoint", javax.swing.JOptionPane.WARNING_MESSAGE);
		}
	}

	/** What's wrong with a watchpoint's settings, or null if they're fine. */
	static String watchpointError(String name, boolean read, boolean write) {
		if( !Watchpoint.isValidName(name)) {
			return "Enter a variable's name (letters, digits and _, not starting with a digit).";
		}
		if( !read && !write ) {
			return "Choose Read, Write or both.";
		}
		return null;
	}

	/** Called with "name = value" when a variable is clicked. */
	public void setOnShowVariable(Consumer<String> handler) {
		onShowVariable = handler == null ? s->{} : handler;
	}

	/**
	 * Opens a variable's value in a multi-line editor until what is typed there is a value of the
	 * variable's type (it's said why it isn't) or the editor is cancelled.
	 *
	 * @return the text to set, or null for none
	 */
	private String askValue(Component parent, Variable v) {
		// (an array one element to a line; one that can't be changed that way is only shown)
		String problem = DebugVariables.editProblem(v.getValue());
		String title = problem == null ? v.getName() : v.getName()+" (can't be changed here: "+problem+")";
		String shown = DebugVariables.editText(v.getValue());
		while( true ) {
			String text = new VariableValueDialog(parent, title, shown, ctx != null && problem == null).showDialog();
			if( text == null || ctx == null ) {
				return null;
			}
			try {
				parseValue(v.getValue(), text);
				return text;
			} catch (IllegalArgumentException e) {
				javax.swing.JOptionPane.showMessageDialog(parent, e.getMessage(), v.getName(), javax.swing.JOptionPane.WARNING_MESSAGE);
				// again, with what was typed
				shown = text;
			}
		}
	}

	/** Opens a variable's value in an editor and sets what is typed there, as a value of the variable's type. */
	private void editVariable(Component parent, Variable v) {
		String text = askValue(parent, v);
		if( text == null || ctx == null ) {
			return;
		}
		try {
			// a positional parameter for $1 ..., else a variable
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
		// (a finished script's have no context: shown, but not to be edited)
		Map<String,Object> vars = variableSnapshot;
		userVariables.setRows(DebugVariables.list(vars, true, false));
		fshVariables.setRows(DebugVariables.list(startVariables != null ? startVariables : vars, true, true));
		startCaption.setVisible(startVariables != null);
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
		// (the script's own, or none: not what the next run starts with)
		this.startVariables = null;
		this.editorPane = editorPane;
		createNodes();
	}

	public void setContext(EditorPanel editorPane, ShellContext ctx ) {
		setContext(editorPane, ctx, ctx == null ? null : ctx.getVariables());
	}

	/**
	 * While no script runs: the fsh Variables tab shows these, what the next run starts with (see
	 * ScriptRun.startVariables), under a line that says so, until setContext. They can't be
	 * changed here. The User Variables tab keeps what it shows.
	 */
	public void setStartVariables(Map<String,Object> variables) {
		// (no script is running: nothing to change a variable of)
		ctx = null;
		startVariables = variables;
		createNodes();
	}

	public void updateTree(SyntaxNode tree, String string) {
		treeView.setTree(tree,string);
	}

	// for tests
	JTable userTable() { return userVariables.table; }
	JTable fshTable() { return fshVariables.table; }
	/** True while the fsh Variables tab shows (and says it shows) what the next run starts with. */
	boolean showsStartVariables() { return startVariables != null && startCaption.isVisible(); }
	JTable watchpointsTable() { return watchpointsTable; }
	JTable breakpointsTable() { return breakpointsTable; }
	JButton editBreakpointButton() { return editBreakpointButton; }
}
