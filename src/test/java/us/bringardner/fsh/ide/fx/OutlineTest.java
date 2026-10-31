package us.bringardner.fsh.ide.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javafx.scene.control.TreeItem;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import us.bringardner.fsh.ide.core.ScriptOutline;

/** The JavaFX IDE's outline: the script's functions and variables beside the editor. */
public class OutlineTest {

	@TempDir
	static Path home;

	@BeforeAll
	public static void quietHome() {
		System.setProperty("user.home", home.toString());
	}

	private static IdeWindow window(IdeWindowTest.MemoryPreferences prefs, String code) throws Exception {
		return Fx.call(()->{
			IdeWindow w = new IdeWindow(new Stage(), prefs);
			w.setCode(code, null);
			return w;
		});
	}

	// the outline's rows as shown (open items' children included)
	private static List<String> rows(OutlineView o) {
		List<String> ret = new ArrayList<>();
		for(int i=0; i < o.treeView().getExpandedItemCount(); i++ ) {
			Object v = o.treeView().getTreeItem(i).getValue();
			ret.add(v instanceof ScriptOutline.Item ? v.toString() : String.valueOf(v));
		}
		return ret;
	}

	@Test
	public void showsTheScriptsFunctionsAndVariablesAndGoesToTheirLines() throws Exception {
		IdeWindow w = window(new IdeWindowTest.MemoryPreferences(), "x=1\nf() {\n  local y\n}\nf\n");
		Fx.waitFor(()->!w.outline().outline().isEmpty());
		assertEquals(List.of("Functions", "f() 2", "y 3", "Variables", "x 1"), Fx.call(()->rows(w.outline())));

		// a closed function stays closed as the script changes
		Fx.run(()->w.outline().treeView().getTreeItem(1).setExpanded(false));
		Fx.run(()->w.editor().setText("x=1\nf() {\n  local y\n}\nf\nz=2\n"));
		Fx.waitFor(()->w.outline().outline().getVariables().size() == 2);
		assertEquals(List.of("Functions", "f() 2", "Variables", "x 1", "z 6"), Fx.call(()->rows(w.outline())));

		// a script that doesn't parse keeps the outline it had
		Fx.run(()->w.editor().setText("x=1\nif then\n"));
		Thread.sleep(800);
		assertEquals(2, Fx.call(()->w.outline().outline().getVariables().size()));

		// Enter on z goes to its line
		Fx.run(()->w.editor().setText("x=1\nf() {\n  local y\n}\nf\nz=2\n"));
		Fx.run(()->{
			TreeItem<Object> z = w.outline().treeView().getTreeItem(4);
			w.outline().treeView().getSelectionModel().select(z);
			w.outline().treeView().fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER, false, false, false, false));
		});
		assertEquals(5, Fx.call(()->w.editor().getCaretLine()));
	}

	@Test
	public void theViewMenuHidesAndShowsIt() throws Exception {
		IdeWindowTest.MemoryPreferences prefs = new IdeWindowTest.MemoryPreferences();
		IdeWindow w = window(prefs, "x=1\n");
		assertTrue(Fx.call(()->w.showOutlineItem().isSelected()));
		assertTrue(Fx.call(()->w.outlineShown()));
		Fx.run(()->w.showOutlineItem().setSelected(false));
		assertFalse(Fx.call(()->w.outlineShown()));
		assertFalse(prefs.getBoolean(IdeWindow.PREF_SHOW_OUTLINE, true));
		// remembered for the next window
		IdeWindow next = window(prefs, "");
		assertFalse(Fx.call(()->next.showOutlineItem().isSelected()));
		Fx.run(()->next.showOutlineItem().setSelected(true));
		assertTrue(Fx.call(()->next.outlineShown()));
	}
}
