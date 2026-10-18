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
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.util.prefs.Preferences;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.border.EmptyBorder;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;

import us.bringardner.fsh.ide.core.SyntaxColors;
import us.bringardner.swing.gradient.GradientLook;

/**
 * File &gt; Editor Colors: the script editors' syntax colors. "Follow the theme" (the default), one
 * of the ready-made schemes, or any color changed, which makes a custom set; with a preview. OK
 * puts the choice on the open editors and keeps it in the IDE's preferences, where the JavaFX IDE
 * finds it too; Cancel changes nothing.
 */
public class SyntaxColorsDialog extends JDialog {

	private static final long serialVersionUID = 1L;

	/** A script to show the colors on: a keyword, a command, a comment, strings, variables, numbers, operators. */
	static final String SAMPLE = "# Copy the day's reports\n"
			+ "count=0\n"
			+ "for f in $(ls reports)\n"
			+ "do\n"
			+ "    if [ -f \"$f\" ]; then\n"
			+ "        cp $f /backup/   # keep a copy\n"
			+ "        count=$((count + 1))\n"
			+ "    fi\n"
			+ "done\n"
			+ "echo 'Copied' $count \"files\"\n";

	private final JComboBox<SyntaxColors> scheme = new JComboBox<>();
	private final JButton[] swatches = new JButton[SyntaxColors.ROLES.size()];
	private final JTextField[] hexes = new JTextField[swatches.length];
	private final PreviewArea preview = new PreviewArea();
	private final JLabel about = new JLabel(" ");
	private SyntaxColors colors;
	private boolean updating;
	private boolean accepted;

	/**
	 * @param owner the window it belongs to, or null
	 * @param start the colors chosen now
	 */
	public SyntaxColorsDialog(Window owner, SyntaxColors start) {
		super(owner, "Editor Colors", ModalityType.APPLICATION_MODAL);
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		build();
		select(start);
		pack();
		setLocationRelativeTo(owner);
	}

	/**
	 * Shows the dialog and waits; OK puts the choice on the open editors and saves it.
	 *
	 * @param owner the window it belongs to, or null
	 * @param prefs where the choice is kept, or null not to keep it
	 * @return true if OK
	 */
	public static boolean showDialog(Window owner, Preferences prefs) {
		SyntaxColorsDialog d = new SyntaxColorsDialog(owner, EditorColors.getChosen());
		d.setVisible(true);
		if( !d.isAccepted() ) {
			return false;
		}
		choose(d.getColors(), prefs);
		return true;
	}

	/** Puts colors on the open editors and keeps them in prefs (if not null). */
	static void choose(SyntaxColors colors, Preferences prefs) {
		EditorColors.setChosen(colors);
		EditorColors.applyToOpenWindows();
		if( prefs != null ) {
			colors.save(prefs);
		}
	}

	/** The colors as chosen in the dialog (perhaps "Follow the theme"). */
	public SyntaxColors getColors() {
		return colors;
	}

	public boolean isAccepted() {
		return accepted;
	}

	// ---------------------------------------------------------------- actions

	/** Chooses a scheme (or "Follow the theme", or a custom set). */
	public void select(SyntaxColors c) {
		colors = c;
		updating = true;
		if( c.isCustom() ) {
			boolean there = false;
			for(int i = 0; i < scheme.getItemCount(); i++) {
				if( scheme.getItemAt(i).isCustom() ) {
					there = true;
				}
			}
			if( !there ) {
				scheme.addItem(c);
			}
		}
		for(int i = 0; i < scheme.getItemCount(); i++) {
			SyntaxColors s = scheme.getItemAt(i);
			if( s.getName().equals(c.getName()) ) {
				if( s.isCustom() ) {
					// the custom entry stands for the latest custom colors
					scheme.removeItemAt(i);
					scheme.insertItemAt(c, i);
				}
				scheme.setSelectedIndex(i);
				break;
			}
		}
		updating = false;
		refresh();
	}

	/** Changes one color (by its index in {@link SyntaxColors#ROLES}): that makes a custom set. */
	public void setColor(int role, Color c) {
		select(shown().withColor(role, c.getRGB() & 0xFFFFFF));
	}

	public void ok() {
		accepted = true;
		dispose();
	}

	public void cancel() {
		accepted = false;
		dispose();
	}

	/** The colors shown: the chosen ones, or the theme's for "Follow the theme". */
	SyntaxColors shown() {
		return colors.resolve(EditorColors.darkTheme());
	}

	/** The preview, for tests. */
	RSyntaxTextArea getPreview() {
		return preview;
	}

	// ---------------------------------------------------------------- the dialog's parts

	private void build() {
		JPanel content = new JPanel(new BorderLayout(16, 12));
		content.setBorder(new EmptyBorder(16, 18, 12, 18));
		setContentPane(content);

		JPanel left = new JPanel();
		left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
		scheme.addItem(SyntaxColors.followTheme());
		for(SyntaxColors s : SyntaxColors.SCHEMES) {
			scheme.addItem(s);
		}
		scheme.addActionListener(e->{
			if( !updating && scheme.getSelectedItem() != null ) {
				select((SyntaxColors) scheme.getSelectedItem());
			}
		});
		JPanel schemeRow = new JPanel(new BorderLayout(8, 0));
		schemeRow.add(new JLabel("Colors:"), BorderLayout.WEST);
		schemeRow.add(scheme, BorderLayout.CENTER);
		schemeRow.setAlignmentX(LEFT_ALIGNMENT);
		schemeRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, schemeRow.getPreferredSize().height));
		left.add(schemeRow);
		about.setFont(about.getFont().deriveFont(Font.ITALIC));
		about.setAlignmentX(LEFT_ALIGNMENT);
		left.add(about);
		left.add(Box.createVerticalStrut(8));
		left.add(colorGrid());
		content.add(left, BorderLayout.WEST);

		JPanel right = new JPanel(new BorderLayout(0, 6));
		JLabel heading = new JLabel("Preview");
		heading.setFont(heading.getFont().deriveFont(Font.BOLD, heading.getFont().getSize2D() + 1f));
		right.add(heading, BorderLayout.NORTH);
		preview.setText(SAMPLE);
		preview.setCaretPosition(0);
		RTextScrollPane scroll = new RTextScrollPane(preview);
		scroll.setPreferredSize(new Dimension(440, 300));
		right.add(scroll, BorderLayout.CENTER);
		content.add(right, BorderLayout.CENTER);

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		JButton cancelButton = new JButton("Cancel");
		cancelButton.addActionListener(e->cancel());
		JButton okButton = new JButton("OK");
		okButton.addActionListener(e->ok());
		buttons.add(cancelButton);
		buttons.add(okButton);
		content.add(buttons, BorderLayout.SOUTH);
		getRootPane().setDefaultButton(okButton);
		getRootPane().registerKeyboardAction(e->cancel(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
				JComponent.WHEN_IN_FOCUSED_WINDOW);
		GradientLook.apply(this);
	}

	private JComponent colorGrid() {
		JPanel grid = new JPanel(new GridBagLayout());
		grid.setAlignmentX(LEFT_ALIGNMENT);
		for(int i = 0; i < swatches.length; i++) {
			final int role = i;
			GridBagConstraints c = new GridBagConstraints();
			c.gridy = i;
			c.anchor = GridBagConstraints.WEST;
			c.insets = new Insets(2, 0, 2, 8);
			c.gridx = 0;
			grid.add(new JLabel(SyntaxColors.ROLES.get(i)), c);

			swatches[i] = new JButton(new Swatch(role));
			swatches[i].setToolTipText("Choose the color for " + SyntaxColors.ROLES.get(i).toLowerCase());
			swatches[i].setMargin(new Insets(2, 2, 2, 2));
			swatches[i].addActionListener(e->{
				Color chosen = JColorChooser.showDialog(this, SyntaxColors.ROLES.get(role),
						new Color(shown().rgb(role)));
				if( chosen != null ) {
					setColor(role, chosen);
				}
			});
			c.gridx = 1;
			grid.add(swatches[i], c);

			hexes[i] = new JTextField(7);
			hexes[i].setToolTipText("#RRGGBB");
			hexes[i].addActionListener(e->readHex(role));
			hexes[i].addFocusListener(new FocusAdapter() {
				@Override
				public void focusLost(FocusEvent e) {
					readHex(role);
				}
			});
			c.gridx = 2;
			grid.add(hexes[i], c);
		}
		return grid;
	}

	/** Takes a color typed in a hex field; a bad one is put back as it was. */
	void readHex(int role) {
		try {
			Color c = Color.decode(hexes[role].getText().trim());
			if( (c.getRGB() & 0xFFFFFF) != shown().rgb(role) ) {
				setColor(role, c);
			}
		} catch (NumberFormatException e) {
			hexes[role].setText(shown().hex(role));
		}
	}

	/** The hex field for a color, for tests. */
	JTextField hexField(int role) {
		return hexes[role];
	}

	private void refresh() {
		SyntaxColors s = shown();
		for(int i = 0; i < swatches.length; i++) {
			hexes[i].setText(s.hex(i));
			swatches[i].repaint();
		}
		about.setText(colors.isFollowTheme()
				? (EditorColors.darkTheme() ? "Now the Parley dark colors, as the theme is dark"
						: "Now the light colors, as the theme is light")
				: colors.isCustom() ? "Your own colors" : " ");
		EditorColors.apply(preview, s);
	}

	/** A square of one of the colors shown. */
	private class Swatch implements Icon {
		private final int role;

		Swatch(int role) {
			this.role = role;
		}

		@Override
		public void paintIcon(java.awt.Component c, Graphics g, int x, int y) {
			g.setColor(new Color(shown().rgb(role)));
			g.fillRect(x, y, getIconWidth(), getIconHeight());
			g.setColor(new Color(0, 0, 0, 120));
			g.drawRect(x, y, getIconWidth() - 1, getIconHeight() - 1);
		}

		@Override
		public int getIconWidth() {
			return 28;
		}

		@Override
		public int getIconHeight() {
			return 16;
		}
	}

	/** The preview's editor: a shell script, in the colors shown here rather than the chosen ones. */
	static class PreviewArea extends RSyntaxTextArea implements EditorColors.Preview {
		private static final long serialVersionUID = 1L;

		PreviewArea() {
			super(12, 40);
			setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_UNIX_SHELL);
			setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
			setHighlightCurrentLine(true);
			setCodeFoldingEnabled(false);
		}
	}
}
