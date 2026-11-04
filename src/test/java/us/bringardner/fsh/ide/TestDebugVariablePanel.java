package us.bringardner.fsh.ide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.JTable;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import us.bringardner.fsh.Console;
import us.bringardner.fsh.ShellContext;
import us.bringardner.fsh.ide.core.Variable;

/** The Swing IDE's variables: the script's own and fsh's in their own tabs, long values. */
public class TestDebugVariablePanel {

	@TempDir
	static Path home;

	@BeforeAll
	public static void headless() {
		System.setProperty("java.awt.headless", "true");
		System.setProperty("user.home", home.toString());
	}

	private static List<String> names(JTable table) {
		List<String> ret = new ArrayList<>();
		for(int row=0; row < table.getModel().getRowCount(); row++ ) {
			ret.add(String.valueOf(table.getModel().getValueAt(row, 0)));
		}
		return ret;
	}

	@Test
	public void theScriptsOwnAndFshsAreInTheirOwnTabs() throws Exception {
		SwingUtilities.invokeAndWait(()->{
			ShellContext ctx = new ShellContext(new Console());
			ctx.setVariable("count", "3");
			ctx.setVariable("IFS", ",");
			Map<String,Object> vars = ctx.getVariables();
			DebugVariablePanel panel = new DebugVariablePanel();
			panel.setContext(null, ctx, vars);
			List<String> user = names(panel.userTable());
			List<String> fsh = names(panel.fshTable());
			assertTrue(user.contains("count"), user.toString());
			assertTrue(!user.contains("IFS"), user.toString());
			assertTrue(fsh.contains("IFS"), fsh.toString());
			assertTrue(fsh.contains("$0"), fsh.toString());
			assertTrue(!fsh.contains("count"), fsh.toString());
			assertEquals(vars.size(), user.size()+fsh.size());
		});
	}

	/** After a debug run: the variables as the script left them, with no context (it's over). */
	@Test
	public void aFinishedScriptsVariablesStay() throws Exception {
		SwingUtilities.invokeAndWait(()->{
			ShellContext ctx = new ShellContext(new Console());
			ctx.setVariable("count", "3");
			DebugVariablePanel panel = new DebugVariablePanel();
			panel.setContext(null, null, ctx.getVariables());
			assertTrue(names(panel.userTable()).contains("count"), names(panel.userTable()).toString());
			assertTrue(names(panel.fshTable()).contains("$0"), names(panel.fshTable()).toString());
			// until they're cleared, for the next run
			panel.setContext(null, null);
			assertEquals(0, panel.userTable().getModel().getRowCount());
			assertEquals(0, panel.fshTable().getModel().getRowCount());
		});
	}

	@Test
	public void aLongValueMakesTheTableWiderThanTheTab() throws Exception {
		SwingUtilities.invokeAndWait(()->{
			ShellContext ctx = new ShellContext(new Console());
			ctx.setVariable("long", "x".repeat(2000));
			DebugVariablePanel panel = new DebugVariablePanel();
			panel.setContext(null, ctx, ctx.getVariables());
			JTable table = panel.userTable();
			// the Value column is as wide as the value, so the table scrolls sideways in a narrow tab
			assertTrue(table.getPreferredSize().width > 2000, ""+table.getPreferredSize().width);
		});
	}

	@Test
	public void aClickedVariableShowsAsNameEqualsValue() {
		assertEquals("count = 3\n", DebugVariablePanel.describe(new Variable("count", 3)));
		assertEquals("lines = a\nb\n", DebugVariablePanel.describe(new Variable("lines", "a\nb")));
		assertEquals("empty = \n", DebugVariablePanel.describe(new Variable("empty", null)));
	}

	@Test
	public void theEditorsTextKeepsABooleanABoolean() {
		assertEquals(Boolean.FALSE, DebugVariablePanel.parseValue(Boolean.TRUE, " false\n"));
		assertEquals("maybe", DebugVariablePanel.parseValue(Boolean.TRUE, "maybe"));
		assertEquals("line 1\nline 2", DebugVariablePanel.parseValue("x", "line 1\nline 2"));
	}

	@Test
	public void watchpointsTable() throws Exception {
		SwingUtilities.invokeAndWait(()->{
			List<us.bringardner.fsh.ide.core.Watchpoint> list = new java.util.concurrent.CopyOnWriteArrayList<>();
			list.add(new us.bringardner.fsh.ide.core.Watchpoint("$count", true, false));
			DebugVariablePanel panel = new DebugVariablePanel();
			panel.setWatchpoints(list);
			JTable table = panel.watchpointsTable();
			assertEquals(1, table.getModel().getRowCount());
			assertEquals("count", table.getModel().getValueAt(0, 1));
			assertEquals(true, table.getModel().getValueAt(0, 2));
			assertEquals(false, table.getModel().getValueAt(0, 3));
			// the check boxes change the watchpoint the script sees
			table.getModel().setValueAt(true, 0, 3);
			table.getModel().setValueAt(false, 0, 0);
			assertTrue(list.get(0).isWrite());
			assertTrue(!list.get(0).isEnabled());
		});
	}

	@Test
	public void watchpointSettingsNeedANameAndReadOrWrite() {
		assertEquals(null, DebugVariablePanel.watchpointError("x", false, true));
		assertTrue(DebugVariablePanel.watchpointError("", true, true) != null);
		assertTrue(DebugVariablePanel.watchpointError("a-b", true, true) != null);
		assertTrue(DebugVariablePanel.watchpointError("x", false, false) != null);
	}
}
