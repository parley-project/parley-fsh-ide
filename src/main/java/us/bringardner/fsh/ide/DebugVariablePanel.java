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

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;


import us.bringardner.fsh.ShellContext;
import us.bringardner.fsh.ide.core.SyntaxNode;
import us.bringardner.fsh.ide.core.Variable;


public class DebugVariablePanel extends JPanel  {

	private static final long serialVersionUID = 1L;
	private ShellContext ctx ;	
	private Map<String,Object> variableSnapshot;

	
	private String [] variablesColumnNames = {"Name","Type","Value"};
	private List<Variable> variables = new ArrayList<Variable>();		
	private DefaultTableModel variableTableModel = new DefaultTableModel() {

		private static final long serialVersionUID = 1L;

		public String getColumnName(int col) {
			return variablesColumnNames[col];
		}

		public int getColumnCount() { 
			return variablesColumnNames.length; 
		}

		public int getRowCount() { 
			return variables.size(); 
		}



		public Object getValueAt(int row, int col) {
			Variable val = variables.get(row);
			switch (col) {
			case 0: return val.getName();
			case 1: return typeName(val.getValue());
			case 2: return val.getValue();
			default:
				throw new IllegalArgumentException("Unexpected value: " + col);
			}
		}

		public boolean isCellEditable(int row, int col){
			boolean ret = col == 2;

			return ret; 
		}	    

		public void setValueAt(Object aValue, int row, int column) {
			if( column == 2) {
				Variable val = variables.get(row);
				String name = ""+val.getName();
				if( name.startsWith("$")) {
					try {
						int pos = Integer.parseInt(name.substring(1));
						
						List<Object> vals = ctx.getAllPositionalParameters();
						vals.set(pos, aValue);
						ctx.console.setPositionalParameters(true, vals);
						
					} catch (Exception e) {						
					} 
				} else {
					ctx.setVariable(name, aValue);
				}
				// the script is paused, so its variables can be read here
				variableSnapshot = ctx.getVariables();
				createNodes();
			} 
		}



	};


	/** Simple class name of a variable's value (the Type column), or "" for null. */
	static String typeName(Object value) {
		if( value == null ) {
			return "";
		}
		return value.getClass().getSimpleName();
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



		JScrollPane variableScrollPane = new JScrollPane();
		tabbedPane.addTab("Variables", null, variableScrollPane, null);

		JTable vtable = new JTable(variableTableModel)

		{
			private static final long serialVersionUID = 1L;

			@Override
			public TableCellEditor getCellEditor(int row, int column) {
				if( column == 2 ) {
					Variable var = variables.get(row);
					Object val = var.getValue();
					if (val instanceof Boolean) {
						Boolean bool = (Boolean) val;
						
						JCheckBox checkBox = new JCheckBox();
						checkBox.setEnabled(true);
						checkBox.setOpaque(true);
						checkBox.setSelected(bool) ;
						return new DefaultCellEditor(checkBox);
					}
					
				}
				return super.getCellEditor(row, column);
			}
		};

		vtable.setAutoCreateRowSorter(true);
		variableScrollPane.setViewportView(vtable);

		vtable.getColumn("Value").setCellRenderer(new DefaultTableCellRenderer() {
			private static final long serialVersionUID = 1L;

			public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column){
				if (column == 2 && value instanceof Boolean) {
					JCheckBox checkBox = new JCheckBox();
					checkBox.setEnabled(true);
					checkBox.setOpaque(true);
					checkBox.setSelected(((Boolean)value).booleanValue()) ;
					return checkBox;		
				} 
				return super.getTableCellRendererComponent(vtable, value, isSelected, hasFocus, row, column);

			}
		});		
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

	

	protected void actionEnableAll(boolean b) {
		for(Breakpoint bo : breakpoints) {
			bo.setEnabled(b);
		}
		breakpointsTableModel.fireTableDataChanged();

	}

	private void createNodes() {
		
		List<Variable> tmp1=new ArrayList<Variable>();
		if( ctx != null && variableSnapshot != null ) {
			for(Map.Entry<String, Object> e: variableSnapshot.entrySet()) {
				tmp1.add(new Variable(e.getKey(),e.getValue()));
			}
		}
		variables = tmp1;
		variableTableModel.fireTableDataChanged();
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
}
