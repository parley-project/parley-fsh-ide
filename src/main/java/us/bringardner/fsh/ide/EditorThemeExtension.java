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
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;

import us.bringardner.fsh.ide.core.SyntaxColors;
import us.bringardner.swing.gradient.Theme;
import us.bringardner.swing.gradient.ThemeExtension;

/**
 * The script editors' syntax colors as part of the appearance theme: the "Script editor" page of
 * the theme designer (Preferences &gt; New theme... or Edit theme...). A new theme starts with the
 * colors of the theme it is made from; a ready-made scheme can be loaded, and any color changed,
 * with a preview. A theme without colors of its own gets colors that match it
 * ({@link #matching(Theme)}): "Match the theme" on the page, which keeps none of its own, so the
 * editor follows the theme's colors as they change.
 */
public class EditorThemeExtension implements ThemeExtension {

	@Override
	public String getId() {
		return SyntaxColors.EXTENSION_ID;
	}

	@Override
	public String getTitle() {
		return "Script editor";
	}

	@Override
	public Map<String, String> defaults(Theme theme) {
		return matching(theme).toValues();
	}

	/** The editor colors that match a theme ({@link SyntaxColors#matching}). */
	public static SyntaxColors matching(Theme theme) {
		return SyntaxColors.matching(rgb(theme.getStart()), rgb(theme.getAccent()), rgb(theme.getField()),
				rgb(theme.getFieldText()), theme.isDark());
	}

	/** A theme's editor colors: its own, or those that match it. */
	public static SyntaxColors colorsOf(Theme theme) {
		return SyntaxColors.ofTheme(theme.getExtras(), matching(theme));
	}

	private static int rgb(Color c) {
		return c.getRGB() & 0xFFFFFF;
	}

	@Override
	public Page createPage() {
		return new ColorsPage();
	}

	/** A script to show the colors on: a keyword, a command, a comment, strings, variables, numbers, operators. */
	public static final String SAMPLE = "# Copy the day's reports\n"
			+ "count=0\n"
			+ "for f in $(ls reports)\n"
			+ "do\n"
			+ "    if [ -f \"$f\" ]; then\n"
			+ "        cp $f /backup/   # keep a copy\n"
			+ "        count=$((count + 1))\n"
			+ "    fi\n"
			+ "done\n"
			+ "echo 'Copied' $count \"files\"\n";

	/** The page: a ready-made scheme to load, each color, and a preview. */
	static class ColorsPage extends JPanel implements Page {

		private static final long serialVersionUID = 1L;

		private final JComboBox<SyntaxColors> scheme = new JComboBox<>();
		private final JButton[] swatches = new JButton[SyntaxColors.ROLES.size()];
		private final JTextField[] hexes = new JTextField[swatches.length];
		private final PreviewArea preview = new PreviewArea();
		private SyntaxColors colors = SyntaxColors.LIGHT;
		/** The theme being designed, for the colors that match it. */
		private Theme theme = Theme.GOLD;
		private Runnable listener;
		private boolean updating;

		ColorsPage() {
			super(new BorderLayout(0, 10));
			setOpaque(false);
			setBorder(new EmptyBorder(8, 8, 8, 8));

			JPanel top = new JPanel(new BorderLayout(8, 0));
			top.setOpaque(false);
			top.add(new JLabel("Load a scheme:"), BorderLayout.WEST);
			// first: colors that follow the theme's
			scheme.addItem(matching(theme));
			for(SyntaxColors s : SyntaxColors.SCHEMES) {
				scheme.addItem(s);
			}
			scheme.setToolTipText("Set every color from a ready-made scheme, or match the theme's colors");
			scheme.addActionListener(e->{
				if( !updating && scheme.getSelectedItem() instanceof SyntaxColors ) {
					SyntaxColors s = (SyntaxColors) scheme.getSelectedItem();
					if( s.isMatch() ) {
						change(matching(theme));
					} else if( !s.isCustom() ) {
						change(s);
					}
				}
			});
			top.add(scheme, BorderLayout.CENTER);
			add(top, BorderLayout.NORTH);

			add(colorGrid(), BorderLayout.CENTER);

			preview.setText(SAMPLE);
			preview.setCaretPosition(0);
			RTextScrollPane scroll = new RTextScrollPane(preview);
			scroll.setPreferredSize(new Dimension(380, 190));
			add(scroll, BorderLayout.SOUTH);
			show(colors);
		}

		@Override
		public JComponent getComponent() {
			return this;
		}

		@Override
		public void setValues(Map<String, String> values, Theme theme) {
			this.theme = theme;
			// a theme with no colors of its own matches its colors, and so will one made from it
			show(SyntaxColors.hasColors(theme.getExtras()) ? SyntaxColors.fromValues(values, matching(theme))
					: matching(theme));
		}

		@Override
		public Map<String, String> getValues() {
			// none of its own when matching the theme, so the editor follows the theme's colors
			return colors.isMatch() ? new java.util.TreeMap<>() : colors.toValues();
		}

		@Override
		public void themeChanged(Theme theme) {
			this.theme = theme;
			if( colors.isMatch() ) {
				SyntaxColors c = matching(theme);
				if( !c.equals(colors) ) {
					show(c);
				}
			}
		}

		@Override
		public void setChangeListener(Runnable listener) {
			this.listener = listener;
		}

		/** The colors shown. */
		SyntaxColors getColors() {
			return colors;
		}

		/** Loads a scheme, or changes one color, as the user would. */
		void change(SyntaxColors c) {
			show(c);
			if( listener != null ) {
				listener.run();
			}
		}

		void setColor(int role, Color c) {
			change(colors.withColor(role, c.getRGB() & 0xFFFFFF));
		}

		private void show(SyntaxColors c) {
			colors = c;
			updating = true;
			// the scheme box shows the scheme, or Custom for colors of one's own; the first item
			// is the colors that match the theme as it is now
			for(int i = scheme.getItemCount() - 1; i >= 0; i--) {
				if( scheme.getItemAt(i).isCustom() || scheme.getItemAt(i).isMatch() ) {
					scheme.removeItemAt(i);
				}
			}
			scheme.insertItemAt(c.isMatch() ? c : matching(theme), 0);
			if( c.isCustom() ) {
				scheme.addItem(c);
			}
			scheme.setSelectedItem(c);
			updating = false;
			for(int i = 0; i < swatches.length; i++) {
				hexes[i].setText(c.hex(i));
				swatches[i].repaint();
			}
			EditorColors.apply(preview, c);
		}

		private JComponent colorGrid() {
			// two columns of seven, so the page is no taller than the look's colors
			JPanel grid = new JPanel(new GridBagLayout());
			grid.setOpaque(false);
			int half = (swatches.length + 1) / 2;
			for(int i = 0; i < swatches.length; i++) {
				final int role = i;
				GridBagConstraints c = new GridBagConstraints();
				c.gridy = i % half;
				c.anchor = GridBagConstraints.WEST;
				c.insets = new Insets(2, i < half ? 0 : 14, 2, 6);
				c.gridx = i < half ? 0 : 3;
				grid.add(new JLabel(SyntaxColors.ROLES.get(i)), c);
				c.insets = new Insets(2, 0, 2, 6);

				swatches[i] = new JButton(new Swatch(role));
				swatches[i].setToolTipText("Choose the color for " + SyntaxColors.ROLES.get(i).toLowerCase());
				swatches[i].setMargin(new Insets(2, 2, 2, 2));
				swatches[i].addActionListener(e->{
					Color chosen = JColorChooser.showDialog(SwingUtilities.getWindowAncestor(this),
							SyntaxColors.ROLES.get(role), new Color(colors.rgb(role)));
					if( chosen != null ) {
						setColor(role, chosen);
					}
				});
				c.gridx++;
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
				c.gridx++;
				grid.add(hexes[i], c);
			}
			return grid;
		}

		/** Takes a color typed in a hex field; a bad one is put back as it was. */
		void readHex(int role) {
			try {
				Color c = Color.decode(hexes[role].getText().trim());
				if( (c.getRGB() & 0xFFFFFF) != colors.rgb(role) ) {
					setColor(role, c);
				}
			} catch (NumberFormatException e) {
				hexes[role].setText(colors.hex(role));
			}
		}

		/** The hex field for a color, for tests. */
		JTextField hexField(int role) {
			return hexes[role];
		}

		/** The scheme box, for tests. */
		JComboBox<SyntaxColors> schemeBox() {
			return scheme;
		}

		/** The preview, for tests. */
		RSyntaxTextArea getPreview() {
			return preview;
		}

		/** A square of one of the colors shown. */
		private class Swatch implements javax.swing.Icon {
			private final int role;

			Swatch(int role) {
				this.role = role;
			}

			@Override
			public void paintIcon(java.awt.Component c, Graphics g, int x, int y) {
				g.setColor(new Color(colors.rgb(role)));
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
	}

	/** The preview's editor: a shell script, in the page's colors rather than the theme's. */
	static class PreviewArea extends RSyntaxTextArea implements EditorColors.Preview {
		private static final long serialVersionUID = 1L;

		PreviewArea() {
			super(9, 40);
			setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_UNIX_SHELL);
			setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
			setHighlightCurrentLine(true);
			setCodeFoldingEnabled(false);
			setEditable(false);
		}
	}
}
