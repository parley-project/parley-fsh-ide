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

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BaseMultiResolutionImage;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;

import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.ImageIcon;

/**
 * The toolbar's icons, from the images in /img: Run, Debug, Stop and the debugger's Resume,
 * Suspend and steps at double size (large: the image's @2x file as it is), the redirects' SCALE
 * times the size of their image (icon).
 *
 * An icon has its size for an ordinary screen and twice that for a Retina (HiDPI) one, both drawn
 * from the image's @2x file when it has one.
 */
final class ToolbarIcons {

	/** how much larger than its image an icon is drawn: 10% */
	static final double SCALE = 1.1;

	private ToolbarIcons() {
	}

	/** The size an image side of that many pixels is drawn at. */
	static int scaled(int pixels) {
		return (int) Math.round(pixels*SCALE);
	}

	/**
	 * @param name the image's file in /img, without .png (run, debug, step_over ...)
	 * @return its icon, SCALE times the image's size
	 */
	static Icon icon(String name) {
		URL url = ToolbarIcons.class.getResource("/img/"+name+".png");
		if( url == null ) {
			throw new IllegalArgumentException("No image /img/"+name+".png");
		}
		try {
			BufferedImage base = ImageIO.read(url);
			URL url2 = ToolbarIcons.class.getResource("/img/"+name+"@2x.png");
			// the sharpest there is to draw from
			BufferedImage source = url2 == null ? base : ImageIO.read(url2);
			int width = scaled(base.getWidth());
			int height = scaled(base.getHeight());
			return new ImageIcon(new BaseMultiResolutionImage(draw(source, width, height), draw(source, 2*width, 2*height)));
		} catch (IOException e) {
			// (as an ImageIcon of the image itself does: it's in the application's own jar)
			return new ImageIcon(url);
		}
	}

	/**
	 * @param name the image's file in /img, without .png (run, debug, step_over ...)
	 * @return its icon at double size: the image's @2x file, at that file's own size
	 * @throws IllegalArgumentException if the image has no @2x file
	 */
	static Icon large(String name) {
		URL url = ToolbarIcons.class.getResource("/img/"+name+"@2x.png");
		if( url == null ) {
			throw new IllegalArgumentException("No image /img/"+name+"@2x.png");
		}
		try {
			BufferedImage source = ImageIO.read(url);
			// (a Retina screen's is that image drawn larger: there's none sharper)
			return new ImageIcon(new BaseMultiResolutionImage(source, draw(source, 2*source.getWidth(), 2*source.getHeight())));
		} catch (IOException e) {
			return new ImageIcon(url);
		}
	}

	private static Image draw(BufferedImage source, int width, int height) {
		BufferedImage ret = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = ret.createGraphics();
		try {
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			g.drawImage(source, 0, 0, width, height, null);
		} finally {
			g.dispose();
		}
		return ret;
	}
}
