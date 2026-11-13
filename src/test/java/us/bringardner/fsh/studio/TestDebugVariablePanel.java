package us.bringardner.fsh.studio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import us.bringardner.fsh.debug.Variable;

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

	/**
	 * While no script runs, the fsh Variables tab shows what the next run starts with (and says
	 * so); the User Variables tab keeps the finished script's. A new run's take their place.
	 */
	@Test
	public void theNextRunsVariablesShowWhileNothingRuns() throws Exception {
		SwingUtilities.invokeAndWait(()->{
			ShellContext ctx = new ShellContext(new Console());
			ctx.setVariable("count", "3");
			ctx.setVariable("IFS", ",");
			DebugVariablePanel panel = new DebugVariablePanel();
			// a finished debug run's variables
			panel.setContext(null, null, ctx.getVariables());
			assertTrue(!panel.showsStartVariables());
			assertEquals(",", value(panel.fshTable(), "IFS"));

			panel.setStartVariables(us.bringardner.fsh.debug.ScriptRun.startVariables("/scripts/hello.fsh", "a b"));
			assertTrue(panel.showsStartVariables());
			List<String> fsh = names(panel.fshTable());
			assertEquals(" \t\n", value(panel.fshTable(), "IFS"));
			assertEquals("/scripts/hello.fsh", value(panel.fshTable(), "$0"));
			// (the arguments are the script's own: not on this tab)
			assertTrue(!fsh.contains("$1"), fsh.toString());
			// the script's own, as it left them
			assertEquals(List.of("count"), names(panel.userTable()));

			// the next debug run: cleared, then its own
			panel.setContext(null, null);
			assertTrue(!panel.showsStartVariables());
			assertEquals(0, panel.fshTable().getModel().getRowCount());
			panel.setContext(null, ctx, ctx.getVariables());
			assertTrue(!panel.showsStartVariables());
			assertEquals(",", value(panel.fshTable(), "IFS"));
		});
	}

	private static String value(JTable table, String name) {
		for(int row=0; row < table.getModel().getRowCount(); row++ ) {
			if( name.equals(table.getModel().getValueAt(row, 0))) {
				return String.valueOf(table.getModel().getValueAt(row, 1));
			}
		}
		return null;
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
		assertThrows(IllegalArgumentException.class, ()->DebugVariablePanel.parseValue(Boolean.TRUE, "maybe"));
		assertEquals("line 1\nline 2", DebugVariablePanel.parseValue("x", "line 1\nline 2"));
	}

	/** The tables show a variable's name and value (its type decides what a changed value becomes, but isn't a column). */
	@Test
	public void theVariableTablesShowNameAndValue() throws Exception {
		SwingUtilities.invokeAndWait(()->{
			ShellContext ctx = new ShellContext(new Console());
			ctx.setVariable("count", Long.valueOf(3));
			DebugVariablePanel panel = new DebugVariablePanel();
			panel.setContext(null, ctx, ctx.getVariables());
			for(JTable table : new JTable[] {panel.userTable(), panel.fshTable()}) {
				assertEquals(2, table.getColumnCount());
				assertEquals("Name", table.getColumnName(0));
				assertEquals("Value", table.getColumnName(1));
			}
			assertEquals("3", value(panel.userTable(), "count"));
		});
	}

	/**
	 * Every table's headings are drawn in the theme's header colors, both of them: the theme's
	 * header text on the theme's header background (not on whatever the look and feel draws there).
	 */
	@Test
	public void theHeadingsAreTheThemesHeaderTextOnItsHeaderBackground() throws Exception {
		SwingUtilities.invokeAndWait(()->{
			Object oldBackground = javax.swing.UIManager.get("TableHeader.background");
			Object oldForeground = javax.swing.UIManager.get("TableHeader.foreground");
			java.awt.Color dark = new java.awt.Color(0x1B2A41);
			try {
				javax.swing.UIManager.put("TableHeader.background", new javax.swing.plaf.ColorUIResource(dark));
				javax.swing.UIManager.put("TableHeader.foreground", new javax.swing.plaf.ColorUIResource(java.awt.Color.WHITE));
				ShellContext ctx = new ShellContext(new Console());
				ctx.setVariable("count", "3");
				DebugVariablePanel panel = new DebugVariablePanel();
				panel.setContext(null, ctx, ctx.getVariables());
				for(JTable table : new JTable[] {panel.userTable(), panel.fshTable(), panel.breakpointsTable(), panel.watchpointsTable()}) {
					for(int col=0; col < table.getColumnCount(); col++ ) {
						java.awt.Component c = table.getTableHeader().getDefaultRenderer().getTableCellRendererComponent(
								table, table.getColumnModel().getColumn(col).getHeaderValue(), false, false, -1, col);
						assertTrue(c.isOpaque(), "the heading paints its own background");
						assertEquals(dark.getRGB(), c.getBackground().getRGB());
						assertEquals(java.awt.Color.WHITE.getRGB(), c.getForeground().getRGB());
						// and what is painted is that background: a pixel in the corner, clear of the text
						c.setSize(120, 24);
						java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(120, 24, java.awt.image.BufferedImage.TYPE_INT_RGB);
						java.awt.Graphics2D g = image.createGraphics();
						try {
							c.paint(g);
						} finally {
							g.dispose();
						}
						assertEquals(dark.getRGB(), image.getRGB(1, 1));
					}
				}
				// the column the table is sorted by keeps its arrow
				JTable user = panel.userTable();
				user.getRowSorter().toggleSortOrder(0);
				javax.swing.JLabel name = (javax.swing.JLabel) user.getTableHeader().getDefaultRenderer().getTableCellRendererComponent(user, "Name", false, false, -1, 0);
				assertEquals(javax.swing.UIManager.getIcon("Table.ascendingSortIcon"), name.getIcon());
				javax.swing.JLabel val = (javax.swing.JLabel) user.getTableHeader().getDefaultRenderer().getTableCellRendererComponent(user, "Value", false, false, -1, 1);
				assertEquals(null, val.getIcon());
			} finally {
				javax.swing.UIManager.put("TableHeader.background", oldBackground);
				javax.swing.UIManager.put("TableHeader.foreground", oldForeground);
			}
		});
	}

	@Test
	public void watchpointsTable() throws Exception {
		SwingUtilities.invokeAndWait(()->{
			List<us.bringardner.fsh.debug.Watchpoint> list = new java.util.concurrent.CopyOnWriteArrayList<>();
			list.add(new us.bringardner.fsh.debug.Watchpoint("$count", true, false));
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
