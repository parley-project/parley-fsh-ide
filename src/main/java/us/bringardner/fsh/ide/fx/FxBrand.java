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
package us.bringardner.fsh.ide.fx;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javafx.scene.control.Dialog;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.Window;
import us.bringardner.parley.brand.ParleyBrand;

/**
 * The Parley brand as JavaFX images and colors. ParleyBrand hands out java.awt images; JavaFX
 * reads the same PNGs straight from the parley-brand jar, so no javafx-swing is needed.
 */
public final class FxBrand {

	/** fsh-ide's badge on the Parley app icon. */
	public static final String BADGE = "terminal";

	public static final Color INK = fx(ParleyBrand.INK);
	public static final Color GOLD_BRIGHT = fx(ParleyBrand.GOLD_BRIGHT);
	public static final Color GOLD = fx(ParleyBrand.GOLD);
	public static final Color GOLD_DARK = fx(ParleyBrand.GOLD_DARK);

	private static List<Image> icons;

	private FxBrand() {
	}

	/** The app icon with the terminal badge in every size; the OS picks the one it needs. */
	public static synchronized List<Image> appIcons() {
		if( icons == null ) {
			List<Image> list = new ArrayList<>();
			for (int size : ParleyBrand.ICON_SIZES) {
				Image i = image("parley-icon-" + BADGE + "-" + size + ".png");
				if( i != null ) {
					list.add(i);
				}
			}
			icons = List.copyOf(list);
		}
		return icons;
	}

	/** Margot on the Parley tower with the wordmark, on black (960 x 1680). */
	public static Image hero() {
		return image("parley-hero.png");
	}

	/** Puts the app icons on a window that has none yet. */
	public static void apply(Window window) {
		if( window instanceof Stage ) {
			Stage s = (Stage) window;
			if( s.getIcons().isEmpty() ) {
				s.getIcons().setAll(appIcons());
			}
		}
	}

	/** Puts the app icons on a dialog's window (a dialog does not take them from its owner). */
	public static void apply(Dialog<?> dialog) {
		apply(dialog.getDialogPane().getScene().getWindow());
	}

	/** #RRGGBB, for CSS. */
	public static String css(Color c) {
		return String.format("#%02X%02X%02X", Math.round(c.getRed() * 255), Math.round(c.getGreen() * 255),
				Math.round(c.getBlue() * 255));
	}

	private static Image image(String name) {
		URL url = ParleyBrand.resource(name);
		return url == null ? null : new Image(url.toExternalForm());
	}

	private static Color fx(java.awt.Color c) {
		return Color.rgb(c.getRed(), c.getGreen(), c.getBlue());
	}
}
