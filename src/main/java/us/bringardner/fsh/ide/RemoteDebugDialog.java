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
package us.bringardner.fsh.ide;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import us.bringardner.fsh.ide.core.RemoteDebug;
import us.bringardner.fsh.ide.core.RemoteDebug.Mode;

/**
 * Run &gt; Debug Remote...: the machine and port, the protocol, what to debug there, and (for
 * a script file there) its path; as the JavaFX IDE's dialog. Debug gives the settings; closing it any other
 * way gives nothing.
 */
public class RemoteDebugDialog extends JDialog {

	private static final long serialVersionUID = 1L;

	final JTextField host = new JTextField(22);
	final JTextField port = new JTextField(6);
	final JRadioButton editorScript = new JRadioButton(Mode.EDITOR_SCRIPT.getLabel());
	final JRadioButton remoteFile = new JRadioButton(Mode.REMOTE_FILE.getLabel());
	final JRadioButton attach = new JRadioButton(Mode.ATTACH.getLabel());
	final JTextField path = new JTextField(22);
	final JRadioButton loupe = new JRadioButton(RemoteDebug.Protocol.LOUPE.getLabel());
	final JRadioButton dap = new JRadioButton(RemoteDebug.Protocol.DAP.getLabel());
	final JTextArea hint = note("");
	final JLabel problem = new JLabel(" ");
	final JButton debugButton = new JButton("Debug");
	final JButton cancelButton = new JButton("Cancel");
	private RemoteDebug result;

	public RemoteDebugDialog(Window owner, RemoteDebug last) {
		super(owner, "Debug Remote", ModalityType.APPLICATION_MODAL);
		// closing the dialog any way but Debug is Cancel
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		host.setText(last.getHost());
		host.setToolTipText("The machine's name or address");
		port.setText(String.valueOf(last.getPort()));
		path.setText(last.getPath());
		path.setToolTipText("The script's path on that machine");
		ButtonGroup modes = new ButtonGroup();
		for(JRadioButton b : new JRadioButton[] {editorScript, remoteFile, attach}) {
			modes.add(b);
			b.setOpaque(false);
			b.addActionListener(e->check());
		}
		button(last.getMode()).setSelected(true);
		ButtonGroup protocols = new ButtonGroup();
		JPanel protocol = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		protocol.setOpaque(false);
		for(JRadioButton b : new JRadioButton[] {loupe, dap}) {
			protocols.add(b);
			b.setOpaque(false);
			b.addActionListener(e->check());
			protocol.add(b);
		}
		(last.getProtocol() == RemoteDebug.Protocol.DAP ? dap : loupe).setSelected(true);

		JPanel fields = new JPanel(new GridBagLayout());
		fields.setOpaque(false);
		fields.setBorder(BorderFactory.createEmptyBorder(10, 10, 4, 10));
		GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(4, 4, 4, 4);
		c.anchor = GridBagConstraints.WEST;
		row(fields, c, 0, "Machine:", host);
		row(fields, c, 1, "Port:", port);
		JPanel what = new JPanel();
		what.setOpaque(false);
		what.setLayout(new javax.swing.BoxLayout(what, javax.swing.BoxLayout.Y_AXIS));
		what.add(editorScript);
		what.add(remoteFile);
		what.add(attach);
		row(fields, c, 2, "Protocol:", protocol);
		row(fields, c, 3, "Debug:", what);
		row(fields, c, 4, "Script's path:", path);
		c.gridx = 0;
		c.gridwidth = 2;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.weightx = 1;
		c.gridy = 5;
		fields.add(hint, c);
		c.gridy = 6;
		fields.add(note(RemoteDebug.WARNING), c);
		c.gridy = 7;
		fields.add(problem, c);

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		buttons.setOpaque(false);
		buttons.add(cancelButton);
		buttons.add(debugButton);
		debugButton.addActionListener(e->{
			RemoteDebug now = settings();
			if( now.problem() == null ) {
				result = now;
				dispose();
			}
		});
		cancelButton.addActionListener(e->dispose());
		getRootPane().setDefaultButton(debugButton);
		getRootPane().registerKeyboardAction(e->dispose(), javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
				javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);

		getContentPane().setLayout(new BorderLayout());
		getContentPane().add(fields, BorderLayout.CENTER);
		getContentPane().add(buttons, BorderLayout.SOUTH);

		DocumentListener typed = new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e) {
				check();
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				check();
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				check();
			}
		};
		host.getDocument().addDocumentListener(typed);
		port.getDocument().addDocumentListener(typed);
		path.getDocument().addDocumentListener(typed);
		check();
		// the gradient look (swing-widgets' GradientLook), as in parley-files-viewer
		us.bringardner.swing.gradient.GradientLook.apply(this);
		pack();
		setLocationRelativeTo(owner);
	}

	private static void row(JPanel panel, GridBagConstraints c, int y, String label, java.awt.Component field) {
		c.gridy = y;
		c.gridx = 0;
		c.gridwidth = 1;
		c.fill = GridBagConstraints.NONE;
		c.weightx = 0;
		panel.add(new JLabel(label), c);
		c.gridx = 1;
		panel.add(field, c);
	}

	/** Text that wraps, looking like a label. */
	private static JTextArea note(String text) {
		JTextArea a = new JTextArea(text, text.isEmpty() ? 5 : 3, 38);
		a.setLineWrap(true);
		a.setWrapStyleWord(true);
		a.setEditable(false);
		a.setFocusable(false);
		a.setOpaque(false);
		a.setBorder(null);
		a.setFont(new JLabel().getFont());
		return a;
	}

	JRadioButton button(Mode mode) {
		switch(mode) {
		case REMOTE_FILE:
			return remoteFile;
		case ATTACH:
			return attach;
		default:
			return editorScript;
		}
	}

	/** Debug is there to press only when the fields make sense; why not is said (but not for the empty machine everyone starts with). */
	private void check() {
		RemoteDebug now = settings();
		String p = now.problem();
		problem.setText(p == null || now.getHost().isEmpty() ? " " : p);
		debugButton.setEnabled(p == null);
		path.setEnabled(remoteFile.isSelected());
		String more = RemoteDebug.hint(now.getProtocol());
		hint.setText(RemoteDebug.hint(now.getMode())+(more.isEmpty() ? "" : "\n"+more));
	}

	/** What the fields hold now (a port that isn't one is 0, which {@link RemoteDebug#problem()} reports). */
	RemoteDebug settings() {
		Mode mode = remoteFile.isSelected() ? Mode.REMOTE_FILE : attach.isSelected() ? Mode.ATTACH : Mode.EDITOR_SCRIPT;
		return new RemoteDebug(host.getText(), Math.max(0, RemoteDebug.parsePort(port.getText())), mode, path.getText(),
				dap.isSelected() ? RemoteDebug.Protocol.DAP : RemoteDebug.Protocol.LOUPE);
	}

	/** The settings Debug was pressed with, or null. */
	public RemoteDebug getResult() {
		return result;
	}

	/** Shows the dialog and waits: the settings to debug with, or null if it was canceled. */
	public static RemoteDebug ask(Window owner, RemoteDebug last) {
		RemoteDebugDialog d = new RemoteDebugDialog(owner, last);
		d.setVisible(true);
		return d.getResult();
	}
}
