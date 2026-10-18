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

import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import javax.swing.SwingUtilities;

/**
 * ⌘+ (or ⌘=), ⌘− and ⌘0 (Ctrl on Windows and Linux) in an IDE window: the editors' and console's
 * text bigger, smaller, and back to the standard size.
 * <p>
 * The View menu shows these keys, but on macOS the IDE's menus are the screen's menu bar
 * (Desktop.setDefaultMenuBar), whose shortcuts a focused window never sees. So the keys are caught
 * here, before the editor or the menus, and used up, which also keeps a menu that does see them
 * from acting a second time.
 */
final class TextSizeKeys implements KeyEventDispatcher {

	private static boolean installed;

	private TextSizeKeys() {
	}

	/** Catches the keys in every IDE window; once is enough. */
	static synchronized void install() {
		if( !installed ) {
			KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(new TextSizeKeys());
			installed = true;
		}
	}

	@Override
	public boolean dispatchKeyEvent(KeyEvent e) {
		Runnable action = action(e);
		if( action == null || !inIde(e) ) {
			return false;
		}
		if( e.getID() == KeyEvent.KEY_PRESSED ) {
			action.run();
		}
		// the press acts; its typed and released events go nowhere either
		e.consume();
		return true;
	}

	/**
	 * The platform's shortcut key: ⌘ on macOS, Ctrl elsewhere. Headless (a test building an editor
	 * with no display) the toolkit can't say, so it is worked out from the platform.
	 */
	static int shortcutKey() {
		if( java.awt.GraphicsEnvironment.isHeadless() ) {
			return System.getProperty("os.name", "").toLowerCase().startsWith("mac")
					? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK;
		}
		return Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
	}

	/** What a key does, or null if it isn't one of these. */
	static Runnable action(KeyEvent e) {
		int shortcut = shortcutKey();
		int mods = e.getModifiersEx() & (InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK
				| InputEvent.META_DOWN_MASK | InputEvent.ALT_DOWN_MASK);
		// the shortcut key, with or without shift (⌘+ is shift and = on most keyboards)
		if( (mods & ~InputEvent.SHIFT_DOWN_MASK) != shortcut ) {
			return null;
		}
		int code = e.getKeyCode();
		char c = e.getKeyChar();
		// the window the key was pressed in: only its text changes
		Window w = window(e);
		if( code == KeyEvent.VK_EQUALS || code == KeyEvent.VK_PLUS || code == KeyEvent.VK_ADD || c == '=' || c == '+' ) {
			return ()->FshIDE.actionBiggerText(w);
		}
		if( code == KeyEvent.VK_MINUS || code == KeyEvent.VK_SUBTRACT || c == '-' ) {
			return ()->FshIDE.actionSmallerText(w);
		}
		if( (code == KeyEvent.VK_0 || code == KeyEvent.VK_NUMPAD0 || c == '0') && mods == shortcut ) {
			return ()->FshIDE.actionResetTextSize(w);
		}
		return null;
	}

	/** Only in the IDE's own windows (its main windows and the template editor), not its dialogs. */
	private static boolean inIde(KeyEvent e) {
		Window w = window(e);
		return w instanceof FshIDE || w instanceof TemplateFrame;
	}

	private static Window window(KeyEvent e) {
		if( e.getComponent() instanceof Window ) {
			return (Window) e.getComponent();
		}
		return e.getComponent() == null ? null : SwingUtilities.getWindowAncestor(e.getComponent());
	}
}
