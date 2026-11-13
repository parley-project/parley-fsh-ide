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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.image.MultiResolutionImage;
import java.nio.file.Path;
import java.util.List;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The toolbar: its icons are 10% larger than their images, and Run is its first button. */
public class TestToolbarIcons {

	@TempDir
	static Path home;

	@Test
	public void anIconIsTenPercentLargerThanItsImage() {
		// 16 x 16 images
		for(String name : new String[] {"run", "debug", "terminate", "resume", "suspend", "step_into", "step_over", "step_return"}) {
			Icon icon = ToolbarIcons.icon(name);
			assertEquals(18, icon.getIconWidth(), name);
			assertEquals(18, icon.getIconHeight(), name);
		}
		// 12 x 14 images
		for(String name : new String[] {"redirect_in", "redirect_out"}) {
			Icon icon = ToolbarIcons.icon(name);
			assertEquals(13, icon.getIconWidth(), name);
			assertEquals(15, icon.getIconHeight(), name);
		}
		assertEquals(18, ToolbarIcons.scaled(16));
		assertThrows(IllegalArgumentException.class, ()->ToolbarIcons.icon("no_such_image"));
	}

	/** A Retina screen gets an image of twice the size, not the ordinary one stretched. */
	@Test
	public void anIconHasAnImageForARetinaScreen() {
		Image image = ((ImageIcon) ToolbarIcons.icon("run")).getImage();
		assertTrue(image instanceof MultiResolutionImage, String.valueOf(image.getClass()));
		List<Image> sizes = ((MultiResolutionImage) image).getResolutionVariants();
		assertEquals(2, sizes.size());
		assertEquals(18, sizes.get(0).getWidth(null));
		assertEquals(36, sizes.get(1).getWidth(null));
		assertEquals(36, sizes.get(1).getHeight(null));
		assertSame(sizes.get(1), ((MultiResolutionImage) image).getResolutionVariant(36, 36));
		// the image isn't blank
		java.awt.image.BufferedImage big = (java.awt.image.BufferedImage) sizes.get(1);
		boolean drawn = false;
		for(int x=0; x < big.getWidth() && !drawn; x++ ) {
			for(int y=0; y < big.getHeight() && !drawn; y++ ) {
				drawn = (big.getRGB(x, y) >>> 24) != 0;
			}
		}
		assertTrue(drawn);
	}

	@Test
	public void runIsTheToolbarsFirstButton() throws Exception {
		Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "needs a screen");
		System.setProperty("user.home", home.toString());
		// no recent script of the user's
		FshIDE.usePreferences(new MemoryPreferences());
		FshIDE[] w = new FshIDE[1];
		SwingUtilities.invokeAndWait(()->{
			w[0] = new FshIDE();
			w[0].setSize(1200, 700);
			w[0].setVisible(true);
			w[0].validate();
		});
		try {
			SwingUtilities.invokeAndWait(()->{
				JButton[] b = w[0].runButtons();
				JButton run = b[0], debug = b[1], stop = b[2];
				assertSame(run.getParent(), debug.getParent());
				assertSame(run.getParent(), stop.getParent());
				// the first button there, and left of the others
				JButton first = null;
				for(java.awt.Component c : run.getParent().getComponents()) {
					if( c instanceof JButton ) {
						first = (JButton) c;
						break;
					}
				}
				assertSame(run, first);
				assertTrue(run.getX() < debug.getX() && debug.getX() < stop.getX(), run.getX()+" "+debug.getX()+" "+stop.getX());
				for(JButton button : b) {
					assertEquals(18, button.getIcon().getIconWidth());
				}
			});
		} finally {
			SwingUtilities.invokeAndWait(()->w[0].dispose());
		}
	}
}
