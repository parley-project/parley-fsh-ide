/**
*	Copyright 2026 Tony Bringardner
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
package us.bringardner.fsh.studio;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

/**
 * A debugged script's variable's value in a multi-line editor. While the script is paused, OK
 * (or Ctrl/Cmd+Enter) sets what is typed; otherwise the value can only be read.
 */
public class VariableValueDialog extends JDialog {

	private static final long serialVersionUID = 1L;

	private final JTextArea textArea;
	private String result;

	/**
	 * @param editable false to only show the value (the script isn't paused)
	 */
	public VariableValueDialog(Component parent, String name, String value, boolean editable) {
		super(parent == null ? null : SwingUtilities.getWindowAncestor(parent), name, ModalityType.APPLICATION_MODAL);
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);

		textArea = new JTextArea(value, 12, 60);
		textArea.setEditable(editable);
		textArea.setTabSize(4);
		textArea.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, textArea.getFont().getSize()));
		JScrollPane scroll = new JScrollPane(textArea);
		scroll.setBorder(BorderFactory.createEmptyBorder(6, 6, 0, 6));
		getContentPane().add(scroll, BorderLayout.CENTER);

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		if( editable ) {
			JButton ok = new JButton("OK");
			ok.addActionListener(e->ok());
			JButton cancel = new JButton("Cancel");
			cancel.addActionListener(e->dispose());
			buttons.add(ok);
			buttons.add(cancel);
		} else {
			JButton close = new JButton("Close");
			close.addActionListener(e->dispose());
			buttons.add(close);
		}
		getContentPane().add(buttons, BorderLayout.SOUTH);

		JComponent root = getRootPane();
		root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel");
		root.getActionMap().put("cancel", new AbstractAction() {
			private static final long serialVersionUID = 1L;
			@Override
			public void actionPerformed(java.awt.event.ActionEvent e) {
				dispose();
			}
		});
		if( editable ) {
			// Enter adds a line, so Ctrl+Enter (Cmd+Enter on the Mac) is OK
			for(int mask : new int[] {InputEvent.CTRL_DOWN_MASK, InputEvent.META_DOWN_MASK}) {
				textArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, mask), "ok");
			}
			textArea.getActionMap().put("ok", new AbstractAction() {
				private static final long serialVersionUID = 1L;
				@Override
				public void actionPerformed(java.awt.event.ActionEvent e) {
					ok();
				}
			});
		}

		pack();
		Window owner = getOwner();
		setLocationRelativeTo(owner == null ? parent : owner);
	}

	private void ok() {
		result = textArea.getText();
		dispose();
	}

	/** Shows the dialog; the text typed, or null for Cancel (or a read-only value). */
	public String showDialog() {
		result = null;
		textArea.setCaretPosition(0);
		setVisible(true);
		return result;
	}

	// for tests
	JTextArea textArea() { return textArea; }
}
