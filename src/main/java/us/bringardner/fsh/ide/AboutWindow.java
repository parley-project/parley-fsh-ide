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
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Image;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

import us.bringardner.parley.brand.ParleyBrand;

/**
 * Help &gt; About for the Swing IDE: Margot on the Parley tower beside fsh-ide's name and
 * version, in the Parley palette (ink background, gold text). The JavaFX IDE has its own,
 * {@code fx.AboutStage}, which looks the same.
 */
public class AboutWindow extends JFrame {

	private static final long serialVersionUID = 1L;

	/** The Parley web site, opened by the link at the bottom. */
	public static final String PARLEY_SITE = "https://parley.bringardner.us";

	/** One line on what fsh-ide is. */
	public static final String DESCRIPTION = "An editor and debugger for shell scripts, with fsh, "
			+ "the FileSource Shell, as the interpreter.";

	/** The hero is drawn this tall; it is 960 x 1680, so about 183 px wide. */
	static final int HERO_HEIGHT = 320;

	/** Text that is not gold: a warm off-white that reads on ink. */
	static final Color TEXT = new Color(0xE8E2D0);

	private static AboutWindow showing;

	/** Shows the About window, bringing the open one to the front rather than opening a second. */
	public static void open() {
		if( showing == null || !showing.isDisplayable() ) {
			showing = new AboutWindow();
			showing.setLocationRelativeTo(null);
		}
		showing.setVisible(true);
		showing.toFront();
	}

	/** The version from the jar's manifest, or null when not running from a built jar. */
	public static String version() {
		return AboutWindow.class.getPackage().getImplementationVersion();
	}

	public static void main(String[] args) {
		EventQueue.invokeLater(AboutWindow::open);
	}

	public AboutWindow() {
		setTitle("About fsh-ide");
		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		setIconImages(ParleyBrand.appIcons("terminal"));
		setResizable(false);

		JPanel contentPane = new JPanel(new BorderLayout(24, 0));
		contentPane.setBackground(ParleyBrand.INK);
		contentPane.setBorder(new EmptyBorder(20, 20, 20, 28));
		setContentPane(contentPane);

		Image hero = ParleyBrand.hero();
		int width = hero.getWidth(null) * HERO_HEIGHT / hero.getHeight(null);
		contentPane.add(new JLabel(new ImageIcon(hero.getScaledInstance(width, HERO_HEIGHT, Image.SCALE_SMOOTH))), BorderLayout.WEST);

		JPanel text = new JPanel();
		text.setOpaque(false);
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		contentPane.add(text, BorderLayout.CENTER);

		text.add(Box.createVerticalGlue());
		text.add(label("fsh-ide", Font.BOLD, 22f, ParleyBrand.GOLD_BRIGHT));
		String version = version();
		if( version != null ) {
			text.add(label("Version " + version, Font.PLAIN, 13f, ParleyBrand.GOLD));
		}
		text.add(Box.createVerticalStrut(16));
		text.add(label("<html><div style='width:240px'>" + DESCRIPTION + "</div></html>", Font.PLAIN, 13f, TEXT));
		text.add(Box.createVerticalStrut(12));
		text.add(label("<html>An example of what Parley can help you build.</html>", Font.ITALIC, 13f, TEXT));
		text.add(Box.createVerticalStrut(20));
		text.add(link(PARLEY_SITE));
		text.add(Box.createVerticalStrut(6));
		text.add(label("Apache License 2.0", Font.PLAIN, 11f, ParleyBrand.GOLD_DARK));
		text.add(Box.createVerticalGlue());

		pack();
	}

	private static JLabel label(String text, int style, float size, Color color) {
		JLabel ret = new JLabel(text);
		ret.setFont(ret.getFont().deriveFont(style, size));
		ret.setForeground(color);
		ret.setAlignmentX(JComponent.LEFT_ALIGNMENT);
		return ret;
	}

	/** The site's address, opened in the browser when clicked (where the desktop can). */
	private static JLabel link(String url) {
		JLabel ret = label(url.replaceFirst("^https://", ""), Font.PLAIN, 13f, ParleyBrand.GOLD_BRIGHT);
		if( Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE) ) {
			ret.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			ret.setToolTipText(url);
			ret.addMouseListener(new MouseAdapter() {
				@Override
				public void mouseClicked(MouseEvent e) {
					try {
						Desktop.getDesktop().browse(new URI(url));
					} catch (Exception ex) {
						// nothing to do: the address is on screen to type in
					}
				}
			});
		}
		return ret;
	}
}
