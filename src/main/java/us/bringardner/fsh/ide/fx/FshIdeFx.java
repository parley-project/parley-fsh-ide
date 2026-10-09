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

import javafx.application.Platform;

/**
 * Starts the JavaFX fsh-ide. (A plain main rather than a JavaFX Application, so it also starts
 * when JavaFX is on the classpath instead of the module path.) The Swing IDE is FshIDE.
 */
public class FshIdeFx {

	public static void main(String[] args) {
		Platform.setImplicitExit(true);
		Platform.startup(()->new IdeWindow().show());
	}
}
