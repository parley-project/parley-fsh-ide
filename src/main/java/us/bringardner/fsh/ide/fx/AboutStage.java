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

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.stage.Window;
import us.bringardner.fsh.ide.AboutWindow;

/**
 * Help &gt; About for the JavaFX IDE: Margot on the Parley tower beside fsh-ide's name and
 * version, in the Parley palette. It looks like the Swing IDE's {@link AboutWindow}.
 */
public class AboutStage extends Stage {

	/** Text that is not gold: a warm off-white that reads on ink. */
	static final Color TEXT = Color.rgb(0xE8, 0xE2, 0xD0);

	private static AboutStage showing;

	/** Shows the About window, bringing the open one to the front rather than opening a second. */
	public static void open(Window owner) {
		if( showing == null ) {
			showing = new AboutStage();
			showing.setOnHidden(e->showing = null);
			if( owner != null ) {
				showing.setX(owner.getX() + (owner.getWidth() - 540) / 2);
				showing.setY(owner.getY() + (owner.getHeight() - 360) / 2);
			}
		}
		showing.show();
		showing.toFront();
	}

	public AboutStage() {
		setTitle("About fsh-ide");
		setResizable(false);
		FxBrand.apply(this);

		ImageView hero = new ImageView(FxBrand.hero());
		hero.setPreserveRatio(true);
		hero.setSmooth(true);
		hero.setFitHeight(320);

		VBox text = new VBox();
		text.setAlignment(Pos.CENTER_LEFT);
		text.setPrefWidth(270);
		text.getChildren().add(label("fsh-ide", FontWeight.BOLD, FontPosture.REGULAR, 22, FxBrand.GOLD_BRIGHT));
		String version = AboutWindow.version();
		if( version != null ) {
			text.getChildren().add(label("Version " + version, FontWeight.NORMAL, FontPosture.REGULAR, 13, FxBrand.GOLD));
		}
		text.getChildren().add(gap(16));
		text.getChildren().add(label(AboutWindow.DESCRIPTION, FontWeight.NORMAL, FontPosture.REGULAR, 13, TEXT));
		text.getChildren().add(gap(12));
		text.getChildren().add(label("An example of what Parley can help you build.", FontWeight.NORMAL, FontPosture.ITALIC, 13, TEXT));
		text.getChildren().add(gap(20));
		text.getChildren().add(link(AboutWindow.PARLEY_SITE));
		text.getChildren().add(gap(6));
		text.getChildren().add(label("Apache License 2.0", FontWeight.NORMAL, FontPosture.REGULAR, 11, FxBrand.GOLD_DARK));

		HBox root = new HBox(24, hero, text);
		root.setAlignment(Pos.CENTER_LEFT);
		root.setPadding(new Insets(20, 28, 20, 20));
		root.setStyle("-fx-background-color: " + FxBrand.css(FxBrand.INK) + ";");
		setScene(new Scene(root));
	}

	private static Label label(String s, FontWeight weight, FontPosture posture, double size, Color color) {
		Label ret = new Label(s);
		ret.setWrapText(true);
		ret.setMinHeight(Region.USE_PREF_SIZE);
		ret.setFont(Font.font(Font.getDefault().getFamily(), weight, posture, size));
		ret.setTextFill(color);
		return ret;
	}

	private static Region gap(double height) {
		Region ret = new Region();
		ret.setMinHeight(height);
		ret.setPrefHeight(height);
		return ret;
	}

	/** The site's address, opened in the browser when clicked (where the desktop can). */
	private static Hyperlink link(String url) {
		Hyperlink ret = new Hyperlink(url.replaceFirst("^https://", ""));
		ret.setPadding(Insets.EMPTY);
		ret.setFont(Font.font(13));
		ret.setStyle("-fx-text-fill: " + FxBrand.css(FxBrand.GOLD_BRIGHT) + "; -fx-border-color: transparent;");
		ret.setOnAction(e->browse(url));
		return ret;
	}

	/** The browser via java.awt.Desktop; JavaFX's HostServices needs an Application, which FshIdeFx is not. */
	private static void browse(String url) {
		Thread t = new Thread(()->{
			try {
				if( java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE) ) {
					java.awt.Desktop.getDesktop().browse(new java.net.URI(url));
				}
			} catch (Exception ex) {
				// nothing to do: the address is on screen to type in
			}
		}, "open-parley-site");
		t.setDaemon(true);
		t.start();
	}
}
