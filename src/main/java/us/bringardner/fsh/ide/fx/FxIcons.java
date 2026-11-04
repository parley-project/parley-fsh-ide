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

import javafx.scene.control.ButtonBase;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

/**
 * The Swing IDE's images (resources/img), for the JavaFX IDE: the same run, debug, step,
 * redirect, breakpoint and error icons. JavaFX takes an image's @2x file on a Retina screen.
 */
final class FxIcons {

	private FxIcons() {
	}

	/** /img/name, as the Swing IDE has it */
	static Image image(String name) {
		return new Image(FxIcons.class.getResource("/img/"+name).toExternalForm());
	}

	static ImageView icon(String name) {
		return new ImageView(image(name));
	}

	/** b shows only the image, as the Swing IDE's buttons do; what it does is its tooltip */
	static <B extends ButtonBase> B iconOnly(B b, String name, String tooltip) {
		b.setText("");
		b.setGraphic(icon(name));
		b.setTooltip(new Tooltip(tooltip));
		return b;
	}
}
