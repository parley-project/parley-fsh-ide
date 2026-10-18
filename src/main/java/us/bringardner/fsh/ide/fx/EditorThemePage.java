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

import java.util.Map;
import java.util.prefs.Preferences;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import us.bringardner.fsh.ide.core.EditorFont;
import us.bringardner.fsh.ide.core.SyntaxColors;
import us.bringardner.fx.dialog.FxThemePage;
import us.bringardner.swing.gradient.Theme;

/**
 * The script editors' syntax colors in the JavaFX theme designer (Settings &gt; New theme... or Edit
 * theme...): the Script editor page, as in the Swing IDE. A ready-made scheme can be loaded and any
 * color changed, with a preview. A new theme starts with the colors of the theme it is made from.
 */
class EditorThemePage extends VBox implements FxThemePage {

	private final ComboBox<SyntaxColors> scheme = new ComboBox<>();
	private final ColorPicker[] pickers = new ColorPicker[SyntaxColors.ROLES.size()];
	private final TextField[] hexes = new TextField[pickers.length];
	private final ScriptPreview preview = new ScriptPreview();
	private final EditorFont font;
	private SyntaxColors colors = SyntaxColors.LIGHT;
	private Runnable listener;
	private boolean updating;

	EditorThemePage() {
		this(EditorFont.load(Preferences.userRoot().node(IdeWindow.PREFERENCES_NODE)));
	}

	/** @param font the font of the preview */
	EditorThemePage(EditorFont font) {
		super(10);
		this.font = font;
		scheme.getItems().addAll(SyntaxColors.SCHEMES);
		scheme.setTooltip(new Tooltip("Set every color from a ready-made scheme"));
		scheme.valueProperty().addListener((o, was, is)->{
			if( !updating && is != null && !is.isCustom() ) {
				change(is);
			}
		});
		HBox top = new HBox(8, new Label("Load a scheme:"), scheme);
		top.setAlignment(Pos.CENTER_LEFT);
		getChildren().addAll(top, colorGrid(), preview);
		preview.setPrefSize(400, 190);
		show(colors);
	}

	@Override
	public Node getNode() {
		return this;
	}

	@Override
	public void setValues(Map<String, String> values, Theme theme) {
		show(SyntaxColors.fromValues(values, theme.isDark()));
	}

	@Override
	public Map<String, String> getValues() {
		return colors.toValues();
	}

	@Override
	public void setChangeListener(Runnable listener) {
		this.listener = listener;
	}

	/** The colors shown. */
	SyntaxColors getColors() {
		return colors;
	}

	/** Loads a scheme, or changes colors, as the user would. */
	void change(SyntaxColors c) {
		show(c);
		if( listener != null ) {
			listener.run();
		}
	}

	void setColor(int role, int rgb) {
		change(colors.withColor(role, rgb & 0xFFFFFF));
	}

	private void show(SyntaxColors c) {
		colors = c;
		updating = true;
		// the scheme box shows the scheme, or Custom for colors of one's own
		scheme.getItems().removeIf(SyntaxColors::isCustom);
		if( c.isCustom() ) {
			scheme.getItems().add(c);
		}
		scheme.setValue(c);
		for(int i = 0; i < pickers.length; i++) {
			hexes[i].setText(c.hex(i));
			pickers[i].setValue(Color.web(c.hex(i)));
		}
		updating = false;
		preview.show(c, font);
	}

	private Node colorGrid() {
		// two columns of seven, so the page is no taller than the look's colors
		GridPane grid = new GridPane();
		grid.setHgap(6);
		grid.setVgap(4);
		int half = (pickers.length + 1) / 2;
		for(int i = 0; i < pickers.length; i++) {
			final int role = i;
			int col = i < half ? 0 : 3;
			int row = i % half;
			Label l = new Label(SyntaxColors.ROLES.get(i));
			if( col > 0 ) {
				GridPane.setMargin(l, new Insets(0, 0, 0, 14));
			}
			grid.add(l, col, row);
			pickers[i] = new ColorPicker();
			pickers[i].setStyle("-fx-color-label-visible: false;");
			pickers[i].setTooltip(new Tooltip("Choose the color for " + SyntaxColors.ROLES.get(i).toLowerCase()));
			pickers[i].setOnAction(e->{
				if( !updating ) {
					Color c = pickers[role].getValue();
					setColor(role, ((int) Math.round(c.getRed() * 255) << 16) | ((int) Math.round(c.getGreen() * 255) << 8)
							| (int) Math.round(c.getBlue() * 255));
				}
			});
			grid.add(pickers[i], col + 1, row);
			hexes[i] = new TextField();
			hexes[i].setPrefColumnCount(7);
			hexes[i].setTooltip(new Tooltip("#RRGGBB"));
			hexes[i].setOnAction(e->readHex(role));
			hexes[i].focusedProperty().addListener((o, was, is)->{
				if( !is ) {
					readHex(role);
				}
			});
			grid.add(hexes[i], col + 2, row);
		}
		return grid;
	}

	/** Takes a color typed in a hex field; a bad one is put back as it was. */
	void readHex(int role) {
		try {
			int rgb = java.awt.Color.decode(hexes[role].getText().trim()).getRGB() & 0xFFFFFF;
			if( rgb != colors.rgb(role) ) {
				setColor(role, rgb);
			}
		} catch (NumberFormatException e) {
			hexes[role].setText(colors.hex(role));
		}
	}

	/** The hex field for a color, for tests. */
	TextField hexField(int role) {
		return hexes[role];
	}

	/** The scheme box, for tests. */
	ComboBox<SyntaxColors> schemeBox() {
		return scheme;
	}

	/** The preview, for tests. */
	ScriptPreview preview() {
		return preview;
	}
}
