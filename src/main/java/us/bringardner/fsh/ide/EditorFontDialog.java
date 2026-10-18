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
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.util.prefs.Preferences;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.EmptyBorder;

import org.fife.ui.rtextarea.RTextScrollPane;

import us.bringardner.fsh.ide.core.EditorFont;
import us.bringardner.swing.gradient.GradientLook;

/**
 * View &gt; Editor Font...: the font of the script editors and the console. A fixed-width family (or
 * the standard monospaced font) and a size, with a preview in the editors' colors. OK puts it on the
 * open editors and keeps it; Cancel changes nothing.
 */
public class EditorFontDialog extends JDialog {

	private static final long serialVersionUID = 1L;

	/** The first entry of the list: the platform's standard monospaced font (no family of its own). */
	static final String STANDARD = "Standard monospaced";

	private final DefaultListModel<String> families = new DefaultListModel<>();
	private final JList<String> familyList = new JList<>(families);
	private final JSpinner size = new JSpinner(new SpinnerNumberModel(EditorFont.DEFAULT_SIZE, EditorFont.MIN_SIZE,
			EditorFont.MAX_SIZE, 1));
	private final EditorThemeExtension.PreviewArea preview = new EditorThemeExtension.PreviewArea();
	private EditorFont chosen;
	private boolean accepted;
	private boolean updating;

	public EditorFontDialog(Window owner, EditorFont start) {
		super(owner, "Editor Font", ModalityType.APPLICATION_MODAL);
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		build();
		select(start);
		pack();
		setLocationRelativeTo(owner);
	}

	/**
	 * Shows the dialog and waits; OK puts the font on the open editors and keeps it.
	 *
	 * @return true if OK
	 */
	public static boolean showDialog(Window owner, Preferences prefs) {
		EditorFontDialog d = new EditorFontDialog(owner, EditorFonts.current());
		d.setVisible(true);
		if( d.isAccepted() ) {
			EditorFonts.choose(d.getChosen(), prefs);
		}
		return d.isAccepted();
	}

	public EditorFont getChosen() {
		return chosen;
	}

	public boolean isAccepted() {
		return accepted;
	}

	/** Shows a font: its family selected (or the standard one) and its size. */
	public void select(EditorFont f) {
		chosen = f;
		updating = true;
		String family = f.getFamily();
		if( family != null && !families.contains(family) ) {
			// a family not among the fixed-width ones found: still offered
			families.add(1, family);
		}
		familyList.setSelectedValue(family == null ? STANDARD : family, true);
		size.setValue(f.getSize());
		updating = false;
		showPreview();
	}

	public void ok() {
		accepted = true;
		dispose();
	}

	public void cancel() {
		accepted = false;
		dispose();
	}

	/** The family list, for tests. */
	JList<String> familyList() {
		return familyList;
	}

	/** The size, for tests. */
	JSpinner sizeSpinner() {
		return size;
	}

	/** The preview, for tests. */
	EditorThemeExtension.PreviewArea getPreview() {
		return preview;
	}

	private void changed() {
		if( updating ) {
			return;
		}
		String family = familyList.getSelectedValue();
		chosen = EditorFont.of(family == null || STANDARD.equals(family) ? null : family, (Integer) size.getValue());
		showPreview();
	}

	private void showPreview() {
		preview.setFont(EditorFonts.font(chosen));
		EditorColors.apply(preview, EditorColors.current());
	}

	private void build() {
		JPanel content = new JPanel(new BorderLayout(12, 10));
		content.setBorder(new EmptyBorder(14, 16, 10, 16));
		setContentPane(content);

		families.addElement(STANDARD);
		for(String f : EditorFonts.fixedWidthFamilies()) {
			families.addElement(f);
		}
		familyList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		familyList.setVisibleRowCount(12);
		familyList.addListSelectionListener(e->changed());
		JScrollPane familyScroll = new JScrollPane(familyList);
		familyScroll.setPreferredSize(new Dimension(220, 260));
		JPanel left = new JPanel(new BorderLayout(0, 6));
		left.add(new JLabel("Font (fixed-width):"), BorderLayout.NORTH);
		left.add(familyScroll, BorderLayout.CENTER);
		JPanel sizeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		sizeRow.add(new JLabel("Size:"));
		size.addChangeListener(e->changed());
		sizeRow.add(size);
		left.add(sizeRow, BorderLayout.SOUTH);
		content.add(left, BorderLayout.WEST);

		JPanel right = new JPanel(new BorderLayout(0, 6));
		right.add(new JLabel("Preview"), BorderLayout.NORTH);
		preview.setText(EditorThemeExtension.SAMPLE);
		preview.setCaretPosition(0);
		RTextScrollPane scroll = new RTextScrollPane(preview);
		scroll.setPreferredSize(new Dimension(440, 260));
		right.add(scroll, BorderLayout.CENTER);
		content.add(right, BorderLayout.CENTER);

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		JButton reset = new JButton("Standard font");
		reset.setToolTipText("The standard monospaced font at " + EditorFont.DEFAULT_SIZE + " points");
		reset.addActionListener(e->select(EditorFont.defaults()));
		JButton cancelButton = new JButton("Cancel");
		cancelButton.addActionListener(e->cancel());
		JButton okButton = new JButton("OK");
		okButton.addActionListener(e->ok());
		buttons.add(reset);
		buttons.add(cancelButton);
		buttons.add(okButton);
		content.add(buttons, BorderLayout.SOUTH);
		getRootPane().setDefaultButton(okButton);
		getRootPane().registerKeyboardAction(e->cancel(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
				JComponent.WHEN_IN_FOCUSED_WINDOW);
		GradientLook.apply(this);
	}
}
