package us.bringardner.fsh.ide;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.TreePath;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import us.bringardner.fsh.ide.core.CompileError;
import us.bringardner.fsh.ide.core.ScriptOutline;
import us.bringardner.fsh.ide.core.ScriptParser;

/** The Swing IDE's outline: functions and variables, open unless closed, going to their lines. */
public class TestOutlinePanel {

	@BeforeAll
	public static void headless() {
		System.setProperty("java.awt.headless", "true");
	}

	private static ScriptOutline outline(String code) {
		List<CompileError> errors = new ArrayList<>();
		return ScriptOutline.of(ScriptParser.parse(code, errors));
	}

	private static List<String> rows(JTree tree) {
		List<String> ret = new ArrayList<>();
		for(int row=0; row < tree.getRowCount(); row++ ) {
			Object o = ((javax.swing.tree.DefaultMutableTreeNode) tree.getPathForRow(row).getLastPathComponent()).getUserObject();
			ret.add(String.valueOf(o));
		}
		return ret;
	}

	@Test
	public void showsFunctionsAndVariablesAndKeepsWhatWasClosed() throws Exception {
		SwingUtilities.invokeAndWait(()->{
			OutlinePanel p = new OutlinePanel();
			p.setOutline(outline("x=1\nf() {\n  local y\n}\n"));
			JTree tree = p.tree();
			assertEquals(List.of("Functions", "f() 2", "y 3", "Variables", "x 1"), rows(tree));

			// a closed function stays closed as the outline is rebuilt (as the script is edited)
			tree.collapsePath(tree.getPathForRow(1));
			p.setOutline(outline("x=1\nf() {\n  local y\n}\nz=2\n"));
			assertEquals(List.of("Functions", "f() 2", "Variables", "x 1", "z 5"), rows(tree));

			p.setOutline(null);
			assertEquals(List.of(), rows(tree));
		});
	}

	@Test
	public void enterGoesToTheLine() throws Exception {
		List<Integer> lines = new ArrayList<>();
		SwingUtilities.invokeAndWait(()->{
			OutlinePanel p = new OutlinePanel();
			p.setOnGoToLine(lines::add);
			p.setOutline(outline("a=1\nb=2\n"));
			JTree tree = p.tree();
			TreePath b = tree.getPathForRow(2);
			tree.setSelectionPath(b);
			java.awt.event.KeyEvent enter = new java.awt.event.KeyEvent(tree, java.awt.event.KeyEvent.KEY_PRESSED,
					System.currentTimeMillis(), 0, java.awt.event.KeyEvent.VK_ENTER, '\n');
			for(java.awt.event.KeyListener l : tree.getKeyListeners()) {
				l.keyPressed(enter);
			}
		});
		assertEquals(List.of(2), lines);
	}
}
