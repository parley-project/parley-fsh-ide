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
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;

public class GoToDialog extends JDialog {

	private static final long serialVersionUID = 1L;
	private final JPanel contentPanel = new JPanel();
	private JTextField textField;
	private boolean cancled = false;

	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
		try {
			GoToDialog dialog = new GoToDialog();
			dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
			dialog.setVisible(true);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Create the dialog.
	 */
	public GoToDialog() {
		setUndecorated(true);
		setModalityType(ModalityType.APPLICATION_MODAL);
		setBounds(100, 100, 180, 49);
		getContentPane().setLayout(new FlowLayout(FlowLayout.LEFT, 5, 5));
		FlowLayout fl_contentPanel = new FlowLayout();
		fl_contentPanel.setAlignment(FlowLayout.LEFT);
		contentPanel.setLayout(fl_contentPanel);
		contentPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
		getContentPane().add(contentPanel);
		{
			JLabel lblLine = new JLabel("Line:");
			contentPanel.add(lblLine);
		}
		{
			textField = new JTextField();
			textField.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					dispose();
				}
			});
			textField.addKeyListener(new KeyAdapter() {
				@Override
				public void keyTyped(KeyEvent e) {
					if( e.getKeyChar() == KeyEvent.VK_ESCAPE) {
						cancled = true;
						dispose();
					}
				}
			});
			
			contentPanel.add(textField);
			textField.setColumns(10);
		}

		// the gradient look (swing-widgets' GradientLook), as in parley-files-viewer
		GradientLook.apply(this);
	}
	
	public int getLine( ) {
		int ret = -1;
		setVisible(true);
		if( !cancled ) {
			try {
				ret = Integer.parseInt(textField.getText());
			} catch (Exception e) {
			}
		}
		return ret;
	}

}
