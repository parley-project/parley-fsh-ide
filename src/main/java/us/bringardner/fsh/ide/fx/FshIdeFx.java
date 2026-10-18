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

import java.util.prefs.Preferences;
import javafx.application.Platform;
import us.bringardner.parley.brand.ParleyBrand;

/**
 * Starts the JavaFX fsh-ide. (A plain main rather than a JavaFX Application, so it also starts
 * when JavaFX is on the classpath instead of the module path.) The Swing IDE is FshIDE.
 */
public class FshIdeFx {

	public static void main(String[] args) {
		// the theme and font chosen in Settings... (both IDEs keep their preferences in the same place)
		us.bringardner.fx.dialog.SettingsDialog.load(Preferences.userRoot().node(IdeWindow.PREFERENCES_NODE));
		Platform.setImplicitExit(true);
		Platform.startup(()->{
			setDockIcon();
			new IdeWindow().show();
		});
	}

	/**
	 * The Dock / taskbar icon. JavaFX sets it from the stage icons on Windows and Linux but not on
	 * macOS, where it stays the generic Java icon unless set through java.awt.Taskbar, as the Swing
	 * IDE does. Best effort: where that isn't supported it does nothing.
	 */
	static void setDockIcon() {
		try {
			if( java.awt.Taskbar.isTaskbarSupported()
					&& java.awt.Taskbar.getTaskbar().isSupported(java.awt.Taskbar.Feature.ICON_IMAGE) ) {
				java.awt.Taskbar.getTaskbar().setIconImage(ParleyBrand.icon(512, FxBrand.BADGE));
			}
		} catch (Throwable e) {
			// headless, or no taskbar: the window icons are enough
		}
	}
}
