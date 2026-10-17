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

import us.bringardner.swing.gradient.GradientLook;
import us.bringardner.fsh.ide.core.Template;
import us.bringardner.fsh.ide.core.Configuration;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.DefaultTableModel;

public class TemplateFrame extends JFrame {


	private static final long serialVersionUID = 1L;
	private JPanel contentPane;	
	private boolean disposed = false;
	private JLabel statusLabel;
	private List<Template> templates = new ArrayList<Template>();
	JTable table = new JTable();
	private DefaultTableModel model = new DefaultTableModel() {

		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;
		String names[] = {"Name","Description","Code"};

		@Override
		public int getColumnCount() {
			return names.length;
		}

		@Override
		public String getColumnName(int column) {
			return names[column];
		}

		@Override
		public Class<?> getColumnClass(int columnIndex) {
			return String.class;
		}

		@Override
		public Object getValueAt(int row, int column) {
			Template t = templates.get(row);
			switch (column) {
			case 0: return t.getName();
			case 1: return t.getDescription();
			case 2: return t.getCode();
			default:

			}
			throw new IllegalArgumentException("Unexpected value: " + column);
		}

		public int getRowCount() {
			return templates.size();
		};

		public void setValueAt(Object aValue, int row, int column) {
			System.out.println("row="+row+" col="+column);
		};

		public boolean isCellEditable(int row, int column) {
			return false;
		};


	};
	private int selectedIndex=-1;
	private TemplatePanel templatePanel;
	private String original;

	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					TemplateFrame frame = new TemplateFrame();
					frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
					List<Template> t = Configuration.getInstance().getTemplates();
					frame.setTemplates(t);
					frame.setVisible(true);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}


	/**
	 * Create the frame.
	 */
	public TemplateFrame() {
		//setIconImage(IEditor.getOpenScadImage());
		setTemplates(Configuration.getInstance().getTemplates());

		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				disposed = true;
			}
		});
		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		setBounds(100, 100, 585, 639);
		setLocationRelativeTo(null);
		contentPane = new JPanel();
		contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
		contentPane.setLayout(new BorderLayout(0, 0));
		setContentPane(contentPane);

		JPanel controlPanel = new JPanel();
		contentPane.add(controlPanel, BorderLayout.NORTH);
		controlPanel.setLayout(new BorderLayout(0, 0));

		JPanel panel_2 = new JPanel();
		controlPanel.add(panel_2, BorderLayout.SOUTH);

		JButton btnCloseWithoutSaving = new JButton("Close without saving");
		panel_2.add(btnCloseWithoutSaving);
		
				JButton btnUndoChanges = new JButton("Undo changes");
				panel_2.add(btnUndoChanges);
				btnUndoChanges.addActionListener(new ActionListener() {
					public void actionPerformed(ActionEvent e) {
						actionUndoChanges();
					}
				});
		btnCloseWithoutSaving.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				closeWithoutSaving();
			}
		});

		JPanel panel_1 = new JPanel();
		controlPanel.add(panel_1, BorderLayout.NORTH);
		
				JButton btnNew = new JButton("New");
				panel_1.add(btnNew);
				btnNew.addActionListener(new ActionListener() {
					public void actionPerformed(ActionEvent e) {
						actionNew();
					}
				});
		
				JButton btnRestorDefaults = new JButton("Restore Defaults");
				panel_1.add(btnRestorDefaults);
				
						JButton btnSaveChanges = new JButton("Save Changes");
						panel_1.add(btnSaveChanges);
						btnSaveChanges.addActionListener(new ActionListener() {
							public void actionPerformed(ActionEvent e) {
								actionSaveChanges();
							}
						});
				btnRestorDefaults.addActionListener(new ActionListener() {
					public void actionPerformed(ActionEvent e) {
						actionRestoreDefaults();
					}
				});





		JPanel panel = new JPanel();
		contentPane.add(panel, BorderLayout.SOUTH);
		panel.setLayout(new BorderLayout(0, 0));

		statusLabel = new JLabel("");
		panel.add(statusLabel, BorderLayout.NORTH);


		templatePanel = new TemplatePanel();
		templatePanel.setToolTipText("");
		panel.add(templatePanel, BorderLayout.CENTER);
		templatePanel.setChangeListenewr((delete)->{

			if( delete ) {
				templates.remove(selectedIndex--);	
				if( selectedIndex<0 ) {
					selectedIndex=0;
				}
				table.setRowSelectionInterval(selectedIndex, selectedIndex);						
			} else {
				if( templatePanel.hasChanged()) {						
					templates.set(selectedIndex, templatePanel.getTemplate());
				}
			}
			build(true);
		});
		templatePanel.setPreferredSize(new Dimension(300, 150));

		JScrollPane templateTableScrollPane = new JScrollPane();
		contentPane.add(templateTableScrollPane, BorderLayout.CENTER);

		templateTableScrollPane.setViewportView(table);

		table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		table.getSelectionModel().addListSelectionListener((e)->{
			actionSelection(e);

		});
		table.setModel(model);

		new Thread(()->{
			while(true) {
				try {
					Thread.sleep(100);
				} catch (InterruptedException e1) {
				}

				if(hasChanged()) {
					setTitle("*Templates have changes");
				} else {
					setTitle("");
				}
			}
		}).start();

		// the gradient look (swing-widgets' GradientLook), as in parley-files-viewer
		GradientLook.apply(this);
	}

	protected void actionSelection(ListSelectionEvent e) {	
		
		if( e != null && !e.getValueIsAdjusting()) {
			selectedIndex  = table.getSelectedRow();
			if( selectedIndex >=0 && selectedIndex< templates.size()) {
				templatePanel.setTemplate(templates.get(selectedIndex));
			} else {
				templatePanel.setTemplate(null);
			}
		}
	}


	public void setTemplates(List<Template> newTemplates) {
		List<Template> tmp = new ArrayList<Template>();

		for (Template t : newTemplates) {
			tmp.add(t.copy());
		}
		Collections.sort(tmp, new Comparator<Template>() {
			@Override
			public int compare(Template o1, Template o2) {
				return o1.getName().compareTo(o2.getName());
			}
		});
		templates = tmp;
		original = toString(templates);
		build(false);
	}

	private String toString(List<Template> list) {
		StringBuilder ret = new StringBuilder();
		for (Template t : list) {
			ret.append(t.getName()+t.getDescription()+t.getCode()+"\n");
		}
		return ret.toString();
	}

	protected void actionNew() {
		TemplateNewDialog dialog = new TemplateNewDialog();
		if( dialog.showDialog()) {
			templates.add(dialog.getTemplate());
			build(true);
		}		
	}

	protected void closeWithoutSaving() {
		dispose();
	}

	public boolean hasChanged() {
		return !toString(templates).equals(original);
	}

	protected void actionSaveChanges() {
		Configuration config = Configuration.getInstance();
		config.setTemplates(templates);

		try {
			config.save();
			setTemplates(config.getTemplates());						
		} catch (IOException e) {
			logError(e, "Can't save your configuration.");
		}

	}

	private void logError(Throwable error, String title) {
		if(SwingUtilities.isEventDispatchThread()) {
			JOptionPane.showMessageDialog(this, error.toString(), title, JOptionPane.ERROR_MESSAGE);
		} else {
			SwingUtilities.invokeLater(new Runnable() {
				@Override
				public void run() {
					JOptionPane.showMessageDialog(TemplateFrame.this, error.toString(), title, JOptionPane.ERROR_MESSAGE);
				}
			});
		}
	}

	protected void actionRestoreDefaults() {
		setTemplates(Configuration.createDefault().getTemplates());		
	}

	protected void actionUndoChanges() {
		setTemplates(Configuration.getInstance().getTemplates());		
	}


	private void build(boolean sort) {
		if( sort ) {
			Collections.sort(templates, new Comparator<Template>() {
				@Override
				public int compare(Template o1, Template o2) {
					return o1.getName().compareTo(o2.getName());
				}
			});
		}
		
		int tmp= selectedIndex;
		model.fireTableDataChanged();
		selectedIndex = tmp;
		
		if( selectedIndex>=0 && selectedIndex<templates.size()) {
			table.setRowSelectionInterval(selectedIndex, selectedIndex);
		}
		
	}

	public boolean isDisposed() {
		return disposed;
	}

}
