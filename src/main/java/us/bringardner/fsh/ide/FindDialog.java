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
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.AbstractButton;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;

import us.bringardner.fsh.ide.core.TextSearch;


/**
 * Edit > Find... and Replace...: finds forward or back, replaces one or all (TextAreaFind, with
 * the same matching as the JavaFX IDE's find bar). A match is selected in the editor. With
 * Selected Lines, only the lines that were selected are searched (shown highlighted).
 */
public class FindDialog extends JDialog {

	private static final long serialVersionUID = 1L;
	private final JPanel contentPanel = new JPanel();
	private final ButtonGroup scopeButtonGroup = new ButtonGroup();

	private DefaultComboBoxModel<String> find = new DefaultComboBoxModel<String>(new String[0] );
	private DefaultComboBoxModel<String> replace = new DefaultComboBoxModel<String>(new String[0]);
	private JComboBox<String> findBox;
	private JComboBox<String> replaceBox;
	private JCheckBox chckbxCaseSensitive;
	private JCheckBox chckbxWholeWords;
	private JCheckBox chckbxRegularExpressions;
	private JCheckBox chckbxWrapSearch;

	private JRadioButton rdbtnAll;
	private JRadioButton rdbtnSelectedLines;
	/** the search, on the editor the dialog was last shown for */
	private TextAreaFind search;
	private JLabel resultLabel;
	private AbstractButton btnReplace;
	private JButton btnReplaceFind;

	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
		try {
			FindDialog dialog = new FindDialog();
			dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
			SwingUtilities.invokeLater(()->{
				dialog.setVisible(true);	
			});
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}


	/**
	 * Create the dialog.
	 */
	public FindDialog() {
		//setIconImage(EditorPanel.getOpenScadImage());
		setBounds(100, 100, 331, 442);
		getContentPane().setLayout(new BorderLayout());
		contentPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
		getContentPane().add(contentPanel, BorderLayout.CENTER);
		contentPanel.setLayout(null);
		{
			JLabel lblFind = new JLabel("Find:");
			lblFind.setBounds(12, 11, 45, 25);
			contentPanel.add(lblFind);
		}

		findBox = new JComboBox<String>();
		findBox.setEditable(true);
		findBox.setMaximumRowCount(20);
		findBox.setModel(find);
		findBox.setBounds(67, 11, 212, 20);
		contentPanel.add(findBox);

		JLabel lblReplace = new JLabel("Replace:");
		lblReplace.setBounds(12, 47, 61, 14);
		contentPanel.add(lblReplace);

		replaceBox = new JComboBox<String>();
		replaceBox.setMaximumRowCount(20);
		replaceBox.setEditable(true);
		replaceBox.setModel(replace);
		replaceBox.setBounds(67, 42, 212, 20);
		contentPanel.add(replaceBox);

		JPanel panel_1 = new JPanel();
		panel_1.setBorder(new TitledBorder(null, "Scope", TitledBorder.LEADING, TitledBorder.TOP, null, null));
		panel_1.setBounds(22, 83, 215, 75);
		contentPanel.add(panel_1);
		panel_1.setLayout(null);

		rdbtnAll = new JRadioButton("All");
		rdbtnAll.setSelected(true);
		rdbtnAll.addActionListener(e->setScope(false));
		scopeButtonGroup.add(rdbtnAll);
		rdbtnAll.setBounds(17, 24, 93, 23);
		panel_1.add(rdbtnAll);

		rdbtnSelectedLines = new JRadioButton("Selected Lines");
		rdbtnSelectedLines.addActionListener(e->setScope(true));
		scopeButtonGroup.add(rdbtnSelectedLines);
		rdbtnSelectedLines.setBounds(17, 49, 147, 23);
		panel_1.add(rdbtnSelectedLines);

		JPanel panel_2 = new JPanel();
		panel_2.setBorder(new TitledBorder(null, "Options", TitledBorder.LEADING, TitledBorder.TOP, null, null));
		panel_2.setBounds(12, 183, 253, 107);
		contentPanel.add(panel_2);
		panel_2.setLayout(null);

		chckbxCaseSensitive = new JCheckBox("Case Sensitive");
		chckbxCaseSensitive.setBounds(6, 28, 113, 23);
		panel_2.add(chckbxCaseSensitive);

		chckbxWrapSearch = new JCheckBox("Wrap Search");
		chckbxWrapSearch.setSelected(true);
		chckbxWrapSearch.setBounds(135, 28, 97, 23);
		panel_2.add(chckbxWrapSearch);

		chckbxWholeWords = new JCheckBox("Whole words");
		chckbxWholeWords.setBounds(6, 54, 113, 23);
		panel_2.add(chckbxWholeWords);

		chckbxRegularExpressions = new JCheckBox("Regular Expressions");
		chckbxRegularExpressions.setBounds(6, 80, 166, 23);
		panel_2.add(chckbxRegularExpressions);

		JButton btnFind = new JButton("Find");
		btnFind.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				find(true);
			}
		});
		btnFind.setBounds(37, 292, 81, 23);
		contentPanel.add(btnFind);

		btnReplaceFind = new JButton("Replace / Find");
		btnReplaceFind.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				replace(true);
			}
		});
		btnReplaceFind.setBounds(136, 292, 101, 23);
		contentPanel.add(btnReplaceFind);

		btnReplace = new JButton("Replace");
		btnReplace.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				replace(false);
			}
		});
		btnReplace.setBounds(37, 326, 81, 23);
		contentPanel.add(btnReplace);

		JButton btnReplaceAll = new JButton("Replace All");
		btnReplaceAll.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				replaceAll();
			}
		});
		btnReplaceAll.setBounds(136, 326, 101, 23);
		contentPanel.add(btnReplaceAll);

		resultLabel = new JLabel("");
		resultLabel.setHorizontalAlignment(SwingConstants.CENTER);
		resultLabel.setBounds(22, 154, 243, 25);
		contentPanel.add(resultLabel);
		{
			JPanel buttonPane = new JPanel();
			buttonPane.setLayout(new FlowLayout(FlowLayout.CENTER));
			getContentPane().add(buttonPane, BorderLayout.SOUTH);
			{
				JButton cancelButton = new JButton("Close");
				cancelButton.addActionListener(new ActionListener() {
					public void actionPerformed(ActionEvent e) {
						close();
					}
				});
				cancelButton.setActionCommand("Cancel");
				buttonPane.add(cancelButton);
			}
		}

		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				close();
			}
		});

		// the gradient look (swing-widgets' GradientLook), as in parley-files-viewer
		GradientLook.apply(this);
	}

	/** Close: the Selected Lines highlight goes, and the dialog. */
	private void close() {
		setScope(false);
		resultLabel.setText("");
		dispose();
	}

	TextSearch.Options options() {
		return new TextSearch.Options(chckbxCaseSensitive.isSelected(), chckbxWholeWords.isSelected(),
				chckbxRegularExpressions.isSelected(), chckbxWrapSearch.isSelected());
	}

	/** what is typed in a box (an editable combo box's model has it only once Enter is pressed) */
	private static String typed(JComboBox<String> box) {
		Object o = box.getEditor().getItem();
		return o == null ? "" : o.toString();
	}

	/** s first in the box's list (once), and selected */
	private static void remember(DefaultComboBoxModel<String> model, String s) {
		if( s.isEmpty()) {
			return;
		}
		int at = model.getIndexOf(s);
		if( at > 0 ) {
			model.removeElementAt(at);
		}
		if( at != 0 ) {
			model.insertElementAt(s, 0);
		}
		model.setSelectedItem(s);
	}

	/** All or Selected Lines (the lines selected now) */
	private void setScope(boolean selectedLines) {
		if( search != null ) {
			search.setScope(selectedLines);
		}
		rdbtnSelectedLines.setSelected(selectedLines);
		rdbtnAll.setSelected(!selectedLines);
	}

	/** Find (and Edit > Find Next): the next match after the selection. */
	public boolean find() {
		return find(true);
	}

	/**
	 * Selects the next match after the selection, or (Edit > Find Previous) the one before it.
	 * @return true if one was found
	 */
	public boolean find(boolean forward) {
		if( search == null ) {
			return false;
		}
		String query = typed(findBox);
		remember(find, query);
		boolean ret = search.find(query, forward, options());
		resultLabel.setText(search.result());
		return ret;
	}

	/** Replace: the selection, if it is a match; then (Replace / Find) the next one. */
	private void replace(boolean andFind) {
		if( search == null ) {
			return;
		}
		String query = typed(findBox);
		String with = typed(replaceBox);
		remember(find, query);
		remember(replace, with);
		search.replace(query, with, andFind, options());
		resultLabel.setText(search.result());
	}

	/** Replace All */
	private void replaceAll() {
		if( search == null ) {
			return;
		}
		String query = typed(findBox);
		String with = typed(replaceBox);
		remember(find, query);
		remember(replace, with);
		search.replaceAll(query, with, options());
		resultLabel.setText(search.result());
	}

	/** Find...: the dialog on sqlEditorPane, with the selection (if it's on one line) to find. */
	public void showDialog(JTextArea sqlEditorPane) {
		showDialog(sqlEditorPane, false);
	}

	/**
	 * Find... or (replace) Replace...: the dialog on area, with the selection (if it's on one line)
	 * to find, and the cursor in the Find or the Replace box.
	 */
	public void showDialog(JTextArea area, boolean replace) {
		if( search == null || search.textArea() != area ) {
			setScope(false);
			search = new TextAreaFind(area);
		}
		String sel = area.getSelectedText();
		if( sel != null && !sel.isEmpty() && sel.indexOf('\n') < 0 ) {
			findBox.getEditor().setItem(sel);
		}
		setAlwaysOnTop(true);
		setVisible(true);
		JComboBox<String> box = replace ? replaceBox : findBox;
		SwingUtilities.invokeLater(()->{
			box.getEditor().getEditorComponent().requestFocusInWindow();
			box.getEditor().selectAll();
		});
	}
}
