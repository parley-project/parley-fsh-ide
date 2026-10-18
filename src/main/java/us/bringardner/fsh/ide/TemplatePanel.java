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

import us.bringardner.fsh.ide.core.Template;
import us.bringardner.fsh.ide.core.Configuration;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.TitledBorder;

import org.fife.ui.autocomplete.AutoCompletion;
import org.fife.ui.autocomplete.DefaultCompletionProvider;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;

public class TemplatePanel extends JPanel {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private JTextField nameTextField;
	private JTextField descriptionTextField;
	private JScrollPane scrollPane;
	private RSyntaxTextArea codeEditorPane;
	private AutoCompletion autoComplete;
	private Template template;
	private JButton deleteButton;
	interface ChangeListener {
		public void changed(boolean delete);
	}
	
	private ChangeListener changeListener;
	private JButton updateButton;
	/**
	 * Create the panel.
	 */
	
	public void setChangeListenewr(ChangeListener l) {
		changeListener = l;
	}
	
	public TemplatePanel() {
		setLayout(new BorderLayout(0, 0));
		JPanel panel = new JPanel();
		FlowLayout flowLayout = (FlowLayout) panel.getLayout();
		flowLayout.setAlignment(FlowLayout.LEFT);
		add(panel, BorderLayout.NORTH);
		
		nameTextField = new JTextField();
		nameTextField.setToolTipText("Name");
		nameTextField.addFocusListener(new FocusAdapter() {
			@Override
			public void focusGained(FocusEvent e) {
				actionFocusGained();
			}
			@Override
			public void focusLost(FocusEvent e) {
				actionFocusLost();
			}
		});
		panel.add(nameTextField);
		nameTextField.setColumns(10);
		
		descriptionTextField = new JTextField();
		descriptionTextField.addFocusListener(new FocusAdapter() {
			@Override
			public void focusGained(FocusEvent e) {
				actionFocusGained();
			}
			@Override
			public void focusLost(FocusEvent e) {
				actionFocusLost();
			}
		});
		descriptionTextField.setToolTipText("Description");
		panel.add(descriptionTextField);
		descriptionTextField.setColumns(20);
		
		deleteButton = new JButton("");
		deleteButton.setToolTipText("Delete Template");
		deleteButton.setIcon(new ImageIcon(TemplatePanel.class.getResource("/img/eclipe_delete_edit.png")));
		deleteButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				int res = JOptionPane.showConfirmDialog( TemplatePanel.this,"Are you sure?");
				if( res == JOptionPane.OK_OPTION) {					
					if( changeListener != null ) {
						changeListener.changed(true);
					}
				}
			}
		});
		panel.add(deleteButton);
		
		updateButton = new JButton("");
		updateButton.setToolTipText("Update Template");
		updateButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				
				if( changeListener != null ) {
					changeListener.changed(false);
					updateUI();
				}
			}
		});
		updateButton.setIcon(new ImageIcon(TemplatePanel.class.getResource("/img/update-20.png")));
		panel.add(updateButton);
		
		scrollPane = new JScrollPane();
		add(scrollPane, BorderLayout.CENTER);
		
		codeEditorPane = new RSyntaxTextArea();
		codeEditorPane.addFocusListener(new FocusAdapter() {
			@Override
			public void focusGained(FocusEvent e) {
				actionFocusGained();
			}
			@Override
			public void focusLost(FocusEvent e) {
				actionFocusLost();
			}
		});
		codeEditorPane.setBorder(new TitledBorder(null, "Code", TitledBorder.LEADING, TitledBorder.TOP, null, null));
		codeEditorPane.setToolTipText("Code");
		scrollPane.setViewportView(codeEditorPane);
		EditorColors.apply(codeEditorPane);
		
		
	}
	
	protected void actionFocusLost() {
		if( autoComplete != null ) {
			autoComplete.setCompletionProvider(new DefaultCompletionProvider());
		}
		if(hasChanged()) {
			if(changeListener != null ) {
				changeListener.changed(false);
				updateUI();
			}
		}		
	}

	protected void actionFocusGained() {
		createAutoComplete();		
	}

	protected void createAutoComplete()  {

		DefaultCompletionProvider provider = new DefaultCompletionProvider(){
			@Override
			protected boolean isValidChar(char ch) {
				return Character.isLetterOrDigit(ch) || ch=='_'  || ch=='.' || ch=='#';
			}
		};

		Configuration config = Configuration.getInstance();
		for(Template t : config.getTemplates()) {
			EditorPanel.addCompletion(provider, t);
		}
		
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				if(autoComplete == null ) {
					autoComplete = new AutoCompletion(provider); 
					autoComplete.setAutoCompleteEnabled(true);
					autoComplete.setParameterAssistanceEnabled(true);
					autoComplete.install(codeEditorPane);
				} else {
					autoComplete.setCompletionProvider(provider);
				}
			}
		});

	}

	public boolean hasChanged () {
		boolean ret = !(template.getName().equals(nameTextField.getText().trim()) 
				&& template.getCode().equals(codeEditorPane.getText().trim()))
				;
		if( !ret ) {
			String d = template.getDescription().trim();
			if( d != null) {
				ret = !d.equals(descriptionTextField.getText().trim());
			} else {
				ret = !descriptionTextField.getText().trim().isEmpty();
			}
		}
		
		return ret;
	}

	public Template getTemplate() {
		return new Template(nameTextField.getText().trim(), descriptionTextField.getText().trim(), codeEditorPane.getText().trim());
	}

	public void setTemplate(Template template) {
		
		if( template == null ) {
			template = new Template();
		}
		this.template = template;
		nameTextField.setText(template.getName());
		descriptionTextField.setText(template.getDescription());
		codeEditorPane.setText(template.getCode());
		updateUI();
	}
	
}
