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
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Document;


public class FindDialog extends JDialog {

	class FoundEntry {
		String key;
		Object curSelection;
		int start;
		int end;
	}
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private final JPanel contentPanel = new JPanel();
	//private final ButtonGroup dirButtonGroup = new ButtonGroup();
	private final ButtonGroup scopeButtonGroup = new ButtonGroup();

	private DefaultComboBoxModel<String> find = new DefaultComboBoxModel<String>(new String[0] );
	private DefaultComboBoxModel<String> replace = new DefaultComboBoxModel<String>(new String[0]);
	private JTextArea text;
	private JCheckBox chckbxCaseSensative;
	private JCheckBox chckbxWholeWords;
	private JCheckBox chckbxRegularExpressions;
	private JCheckBox chckbxWrapSearch;
	
	private JRadioButton rdbtnAll;
	private JRadioButton rdbtnSelectedLines;
	private int currentPos = 0;
	private Document doc;
	private DefaultHighlighter.DefaultHighlightPainter highlightPainter =
			new DefaultHighlighter.DefaultHighlightPainter(Color.lightGray);
	private FoundEntry curSelection;
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

		JComboBox<String> comboBox = new JComboBox<String>();
		comboBox.setEditable(true);
		comboBox.setMaximumRowCount(20);
		comboBox.setModel(find);
		comboBox.setBounds(67, 11, 212, 20);
		contentPanel.add(comboBox);

		JLabel lblReplace = new JLabel("Replace:");
		lblReplace.setBounds(12, 47, 61, 14);
		contentPanel.add(lblReplace);

		JComboBox<String> comboBox_1 = new JComboBox<String>();
		comboBox_1.setMaximumRowCount(20);
		comboBox_1.setEditable(true);
		comboBox_1.setModel(replace);
		comboBox_1.setBounds(67, 42, 212, 20);
		contentPanel.add(comboBox_1);

		JPanel panel_1 = new JPanel();
		panel_1.setBorder(new TitledBorder(null, "Scope", TitledBorder.LEADING, TitledBorder.TOP, null, null));
		panel_1.setBounds(22, 83, 215, 75);
		contentPanel.add(panel_1);
		panel_1.setLayout(null);

		rdbtnAll = new JRadioButton("All");
		rdbtnAll.setSelected(true);
		scopeButtonGroup.add(rdbtnAll);
		rdbtnAll.setBounds(17, 24, 93, 23);
		panel_1.add(rdbtnAll);

		rdbtnSelectedLines = new JRadioButton("Selected Lines");
		scopeButtonGroup.add(rdbtnSelectedLines);
		rdbtnSelectedLines.setBounds(17, 49, 147, 23);
		panel_1.add(rdbtnSelectedLines);

		JPanel panel_2 = new JPanel();
		panel_2.setBorder(new TitledBorder(null, "Options", TitledBorder.LEADING, TitledBorder.TOP, null, null));
		panel_2.setBounds(12, 183, 253, 107);
		contentPanel.add(panel_2);
		panel_2.setLayout(null);

		chckbxCaseSensative = new JCheckBox("Case Sensative");
		chckbxCaseSensative.setBounds(6, 28, 113, 23);
		panel_2.add(chckbxCaseSensative);

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
				find();
			}
		});
		btnFind.setBounds(37, 292, 81, 23);
		contentPanel.add(btnFind);

		btnReplaceFind = new JButton("Replace / Find");
		btnReplaceFind.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				findAndReplace();
			}
		});
		btnReplaceFind.setEnabled(false);
		btnReplaceFind.setBounds(136, 292, 101, 23);
		contentPanel.add(btnReplaceFind);

		btnReplace = new JButton("Replace");
		btnReplace.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				replace();
			}
		});
		btnReplace.setEnabled(false);
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
						if( curSelection != null ) {
							text.getHighlighter().removeHighlight(curSelection.curSelection);
						}
						resultLabel.setText("");
						currentPos = 0;
						curSelection = null;
						dispose();
					}
				});
				cancelButton.setActionCommand("Cancel");
				buttonPane.add(cancelButton);
			}
		}

		// the gradient look (swing-widgets' GradientLook), as in parley-files-viewer
		GradientLook.apply(this);
	}

	protected void replaceAll() {
		currentPos = 0;
		curSelection = null;
		int cnt = 0;
		while( find()) {
			replace();
			cnt++;
		}
		if( cnt > 0 ) {
			resultLabel.setText("replaced "+cnt+" instances");
		}

	}


	protected void replace() {
		if( curSelection != null && curSelection.start > 0 && curSelection.end > curSelection.start) {
			Object obj = replace.getSelectedItem();
			if( obj == null ) {
				obj = "";
			}
			String rep = obj.toString();
			if( replace.getIndexOf(rep) < 0 ) {
				replace.insertElementAt(rep, 0);
			}

			// remove then add
			try {
				doc.remove(curSelection.start, curSelection.end - curSelection.start);
				doc.insertString(curSelection.start, rep, null);
				curSelection = null;
			} catch (BadLocationException e) {

			}

		}	

	}


	protected boolean findAndReplace() {
		replace();
		boolean ret = find();
		return ret;
	}


	public boolean find() {
		boolean ret = false;
		if( curSelection != null ) {
			text.getHighlighter().removeHighlight(curSelection.curSelection);
			curSelection = null;
		}
		resultLabel.setText("");

		String f = (String) find.getSelectedItem();
		if( find.getIndexOf(f) < 0 ) {
			find.insertElementAt(f, 0);
		}

		try {

			String data = null;
			if( rdbtnSelectedLines.isSelected()) {
				data = text.getSelectedText();
			} else {
				data = doc.getText(0, doc.getLength());
			}
			if( chckbxRegularExpressions.isSelected() || !chckbxRegularExpressions.isSelected()) {
				//\b
				ret = findRx(f, data);
			} else {
				ret = findIndex(f,data);
			}

		} catch (BadLocationException e) {
		}


		btnReplace.setEnabled(ret);
		btnReplaceFind.setEnabled(ret);
		return ret;

	}


	private boolean findIndex(String f, String data)  {
		boolean ret = false;
		if( !chckbxCaseSensative.isSelected() ) {
			data = data.toLowerCase();
			f = f.toLowerCase();
		}

		int idx = data.indexOf(f, currentPos);
		if( idx < 0 ) {
			if( chckbxWrapSearch.isSelected() ) {
				currentPos = 0;
				resultLabel.setText("Wrapped Search");
			}
			idx = data.indexOf(f, currentPos);
		}
		if( idx >= 0 ) {
			try {
				curSelection = new FoundEntry();//
				curSelection.curSelection = text.getHighlighter().addHighlight(idx, idx+f.length(),highlightPainter);
				curSelection.start = idx;
				curSelection.start = idx+f.length();
				curSelection.key = f;
				idx+=f.length();
				ret = true;
			} catch (BadLocationException e) {
				e.printStackTrace();
			}
		} else {
			resultLabel.setText("No found");
		}
		currentPos = idx;
		return ret;
	}


	private boolean findRx(String f, String data) throws BadLocationException {
		int op = Pattern.MULTILINE | Pattern.UNIX_LINES;

		if( !chckbxCaseSensative.isSelected() ) {
			op |= Pattern.CASE_INSENSITIVE;
		}
		if( !chckbxRegularExpressions.isSelected() ) {
			// no rx so make f literal
			f = escape(f);
		}
		if(chckbxWholeWords.isSelected()) {
			f = "\\b"+f+"\\b";
		}

		//System.out.println("op="+op+" f="+f);
		Pattern p = Pattern.compile(f, op);
		Matcher matcher = p.matcher(data);

		if (matcher.find()) {
			do {
				int start = matcher.start(0);
				if( start > currentPos) {
					int end = matcher.end();
					curSelection = new FoundEntry(); 
					curSelection.curSelection = text.getHighlighter().addHighlight(start, end,highlightPainter);
					curSelection.start = start;
					curSelection.end = end;
					curSelection.key = f;
					currentPos= end;
					text.setCaretPosition(start);
					resultLabel.setText("start="+start+" end="+end);
					return true;
				} else {
					resultLabel.setText("start="+start);
				}

			} while(matcher.find());

		} 
		resultLabel.setText("Not found");
		currentPos=-1;

		return false;
	}


	private String escape(String f) {
		StringBuilder  ret = new StringBuilder();
		for(byte b : f.getBytes()) {
			switch (b) {
			case '\\':
			case '.':
			case '*':
			case '?':
			case '+':
			case '^':
			case '&':
			case '(':
			case ')':
			case '{':
			case '}':
			case '[':
			case ']':

				ret.append('\\');
				break;

			default:
				break;
			}
			ret.append((char)b);
		}

		return ret.toString();
	}


	public void showDialog(JTextArea sqlEditorPane) {
		this.text = sqlEditorPane;
		doc = text.getDocument();
		currentPos = text.getCaretPosition();
		String sel = text.getSelectedText();
		if( sel != null && sel.length()>0) {
			find.setSelectedItem(sel);
			currentPos = text.getSelectionStart()-1;

			find();
		}
		setAlwaysOnTop(true);

		setVisible(true);



	}
}
