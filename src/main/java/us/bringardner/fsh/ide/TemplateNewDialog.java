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

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

public class TemplateNewDialog extends JDialog {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private final JPanel contentPanel = new JPanel();
	protected boolean cancel;
	private TemplatePanel templatePanel;

	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
		try {
			TemplateNewDialog dialog = new TemplateNewDialog();
			dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
			dialog.setVisible(true);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Create the dialog.
	 */
	public TemplateNewDialog() {
		//setIconImage(IEditor.getOpenScadImage());
		setLocationRelativeTo(null);
		setBounds(100, 100, 831, 341);
		getContentPane().setLayout(new BorderLayout());
		contentPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
		getContentPane().add(contentPanel, BorderLayout.CENTER);
		contentPanel.setLayout(new BorderLayout(0, 0));
		{
			templatePanel = new TemplatePanel();
			templatePanel.setTemplate(new Template());
			contentPanel.add(templatePanel);
		}
		{
			JPanel buttonPane = new JPanel();
			buttonPane.setLayout(new FlowLayout(FlowLayout.RIGHT));
			getContentPane().add(buttonPane, BorderLayout.SOUTH);
			{
				JButton okButton = new JButton("OK");
				okButton.addActionListener(new ActionListener() {
					public void actionPerformed(ActionEvent e) {
						Template tmp = templatePanel.getTemplate();
						String name = tmp.getName();
						if( name != null) {
							name = name.trim();
						}
						if(name == null || name.isEmpty()) {
							JOptionPane.showMessageDialog(TemplateNewDialog.this, "Name is a required Field", "Warning", JOptionPane.WARNING_MESSAGE);
							return;
						}

						cancel = false;
						dispose();
					}
				});
				okButton.setActionCommand("OK");
				buttonPane.add(okButton);
				getRootPane().setDefaultButton(okButton);
			}
			{
				JButton cancelButton = new JButton("Cancel");
				cancelButton.addActionListener(new ActionListener() {
					public void actionPerformed(ActionEvent e) {
						cancel = true;
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
	
	public boolean showDialog() {
		setModal(true);
		setLocationRelativeTo(null);
		setVisible(true);
		
		return !cancel;
	}
	
	public Template getTemplate() {
		return templatePanel.getTemplate();
	}
	

}
