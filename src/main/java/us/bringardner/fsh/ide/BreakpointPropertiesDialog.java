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
import us.bringardner.fsh.ide.core.Breakpoint;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;

public class BreakpointPropertiesDialog extends JDialog {

	private static final long serialVersionUID = 1L;
	private final JPanel contentPanel = new JPanel();
	private JTextField hitCountTextField;
	private boolean cancel;
	private JTextArea conditionalTextArea;
	private JCheckBox enabledCheckBox;
	private JCheckBox hitCountCheckBox;
	private JCheckBox conditionalCheckBox;
	private JButton deleteButton;
	private Component horizontalStrut;
	private boolean delete;

	public boolean isDelete() {
		return delete;
	}

	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
		try {
			BreakpointPropertiesDialog dialog = new BreakpointPropertiesDialog();
			dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
			dialog.setVisible(true);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Create the dialog.
	 */
	public BreakpointPropertiesDialog() {
		setBounds(100, 100, 211, 249);
		getContentPane().setLayout(new BorderLayout());
		contentPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
		getContentPane().add(contentPanel, BorderLayout.CENTER);
		contentPanel.setLayout(null);

		enabledCheckBox = new JCheckBox("Enabled");
		enabledCheckBox.setBounds(6, 6, 128, 23);
		contentPanel.add(enabledCheckBox);

		hitCountCheckBox = new JCheckBox("Hit Count");
		hitCountCheckBox.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				hitCountTextField.setVisible(hitCountCheckBox.isSelected());
			}
		});
		hitCountCheckBox.setBounds(6, 42, 128, 23);
		contentPanel.add(hitCountCheckBox);

		hitCountTextField = new JTextField();
		hitCountTextField.setHorizontalAlignment(SwingConstants.RIGHT);
		hitCountTextField.setVisible(false);
		hitCountTextField.setBounds(113, 41, 56, 26);
		contentPanel.add(hitCountTextField);
		hitCountTextField.setColumns(10);

		conditionalCheckBox = new JCheckBox("Conditional");
		conditionalCheckBox.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				conditionalTextArea.setVisible(conditionalCheckBox.isSelected());
			}
		});
		conditionalCheckBox.setBounds(6, 75, 128, 23);
		contentPanel.add(conditionalCheckBox);

		conditionalTextArea = new JTextArea();
		conditionalTextArea.setBounds(6, 110, 173, 71);
		conditionalTextArea.setVisible(false);
		contentPanel.add(conditionalTextArea);

		JPanel buttonPane = new JPanel();
		buttonPane.setLayout(new FlowLayout(FlowLayout.CENTER));
		getContentPane().add(buttonPane, BorderLayout.SOUTH);

		JButton okButton = new JButton("");
		okButton.setToolTipText("Save");
		okButton.setIcon(new ImageIcon(BreakpointPropertiesDialog.class.getResource("/img/smile20x20.png")));
		okButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				dispose();
			}
		});
		buttonPane.add(okButton);
		getRootPane().setDefaultButton(okButton);


		JButton cancelButton = new JButton("");
		cancelButton.setToolTipText("Cancel");
		cancelButton.setIcon(new ImageIcon(BreakpointPropertiesDialog.class.getResource("/img/eclipe_delete_edit.png")));
		cancelButton.addActionListener(new ActionListener() {
			

			public void actionPerformed(ActionEvent e) {
				cancel = true;
				dispose();
			}
		});
		
		horizontalStrut = Box.createHorizontalStrut(20);
		buttonPane.add(horizontalStrut);
		
		deleteButton = new JButton("");
		deleteButton.setToolTipText("Delete");
		deleteButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				delete = true;
				dispose();
			}
		});
		deleteButton.setIcon(new ImageIcon(BreakpointPropertiesDialog.class.getResource("/img/trashcan-red-20x22.png")));
		deleteButton.setSelectedIcon(null);
		buttonPane.add(deleteButton);
		
		Component horizontalStrut_1 = Box.createHorizontalStrut(20);
		buttonPane.add(horizontalStrut_1);
		cancelButton.setActionCommand("Cancel");
		buttonPane.add(cancelButton);
		
		contentPanel. addKeyListener(new KeyAdapter() {
			@Override
			public void keyTyped(KeyEvent e) {
				System.out.println(e.getKeyCode());
				if( e.getKeyCode() == KeyEvent.VK_ESCAPE) {// 6 == F
					cancel = true;
					dispose();
				}
			}
		});

		// the gradient look (swing-widgets' GradientLook), as in parley-files-viewer
		GradientLook.apply(this);
	}
	
	/**
	 * 
	 * @param bp
	 * @param parent
	 * @param y 
	 * @param x 
	 * @return true is the user did not cancel action
	 */
	public boolean showDialog(Breakpoint bp, Component parent, int x, int y) {
		cancel = false;
		enabledCheckBox.setSelected(bp.isEnabled(false));
		hitCountCheckBox.setSelected(bp.isHitCount());
		hitCountTextField.setText(""+bp.getHitCount());
		hitCountTextField.setVisible(bp.isHitCount());
		conditionalCheckBox.setSelected(bp.isConditional());
		conditionalTextArea.setText(bp.getCondition());
		conditionalTextArea.setVisible(bp.isConditional());
		setModal(true);
		setLocation(x, y);
		setVisible(true);
		if( !cancel ) {
			bp.setEnabled(enabledCheckBox.isSelected());
			bp.setHitCount(hitCountCheckBox.isSelected());
			if(hitCountCheckBox.isSelected() ) {
				bp.setHitCount(Integer.parseInt(hitCountTextField.getText().trim()));
			} else {
				bp.setHitCount(-1);
			}
			bp.setConditional(conditionalCheckBox.isSelected());
			bp.setCondition(conditionalTextArea.getText());
		}
		
		return !cancel;
	}
}
