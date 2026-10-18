package us.bringardner.fsh.ide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.GraphicsEnvironment;
import java.io.File;
import java.nio.file.Files;

import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import us.bringardner.fsh.ide.core.Configuration;
import us.bringardner.fsh.ide.core.Template;
import us.bringardner.swing.dialog.SettingsDialog;
import us.bringardner.swing.dialog.SettingsPages;
import us.bringardner.swing.gradient.GradientLook;

/** The Templates tab of the Swing IDE's Settings. */
public class TestTemplatesPage {

	/** The configuration goes to a home of the test's own, not the user's. */
	@BeforeAll
	public static void home() throws Exception {
		System.setProperty("user.home", Files.createTempDirectory("templates-page").toString());
	}

	/** Each test starts from the built-in templates (a test before may have saved others). */
	@BeforeEach
	public void builtIns() {
		Configuration.getInstance().setTemplates(Configuration.getDefaultTemplates());
	}

	@Test
	public void editsAddsDeletesAndRestores() throws Exception {
		Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());
		SwingUtilities.invokeAndWait(()->{
			TemplatesPage page = new TemplatesPage();
			assertEquals("Templates", page.getTitle());
			assertEquals(0, page.list().getSelectedIndex());
			Template first = page.templates().get(0);
			assertEquals(first.getName(), page.nameField().getText());
			assertEquals(first.getCode(), page.codeArea().getText());
			assertTrue(page.checkLabel().getText().contains("parses"), page.checkLabel().getText());
			assertFalse(page.templates().isChanged(), "showing isn't changing");

			// typing changes the template
			page.descriptionField().setText("changed");
			assertEquals("changed", page.templates().get(page.list().getSelectedIndex()).getDescription());
			assertTrue(page.templates().isChanged());

			// code that doesn't parse says so
			page.codeArea().setText("if true; then\nfi\n");
			assertTrue(page.checkLabel().getText().contains("Line 2"), page.checkLabel().getText());

			// New: selected, named so it can be typed over
			int before = page.templates().size();
			page.actionNew();
			assertEquals(before + 1, page.templates().size());
			assertEquals("new-template", page.nameField().getText());
			page.nameField().setText("zz-mine");
			page.codeArea().setText("echo ${hello}");
			Template mine = page.templates().get(page.list().getSelectedIndex());
			assertEquals("zz-mine", mine.getName());
			assertEquals(page.templates().size() - 1, page.list().getSelectedIndex(), "kept in order of name");

			// a template needs a name
			page.nameField().setText("");
			assertNotNull(page.problem());
			page.nameField().setText("zz-mine");
			assertNull(page.problem());

			// Delete, then Restore Defaults: the built-in is back, the user's own stays
			int whileAt = indexOf(page, "while-loop");
			page.delete(whileAt);
			assertEquals(-1, indexOf(page, "while-loop"));
			page.restoreDefaults();
			assertTrue(indexOf(page, "while-loop") >= 0);
			assertTrue(page.templates().items().stream().anyMatch(t->"zz-mine".equals(t.getName())));
		});
	}

	@Test
	public void okKeepsTheTemplatesInTheConfiguration() throws Exception {
		Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());
		SwingUtilities.invokeAndWait(()->{
			try {
				TemplatesPage page = new TemplatesPage();
				page.delete(indexOf(page, "while-loop"));
				page.actionNew();
				page.nameField().setText("hello");
				page.codeArea().setText("echo hello");
				page.ok();
				Configuration c = Configuration.getInstance();
				assertTrue(c.getTemplates().stream().anyMatch(t->"hello".equals(t.getName())));
				assertTrue(c.getTemplates().stream().noneMatch(t->"while-loop".equals(t.getDescription())),
						"a deleted built-in stays deleted");
				File saved = new File(System.getProperty("user.home"), ".fsh-ide/Config.xml");
				assertTrue(saved.isFile());
				assertTrue(Files.readString(saved.toPath()).contains("hello"));
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		});
	}

	@Test
	public void itIsATabInSettings() throws Exception {
		Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());
		SettingsPages.register(TemplatesPage.ID, TemplatesPage::new);
		try {
			SwingUtilities.invokeAndWait(()->{
				GradientLook.install();
				SettingsDialog d = new SettingsDialog(null, null);
				assertEquals("Settings", d.getTitle());
				assertEquals(1, d.getPages().size());
				assertTrue(d.getPages().get(0) instanceof TemplatesPage);
				JTabbedPane tabs = find(d.getContentPane());
				assertEquals("Templates", tabs.getTitleAt(1));
				d.cancel();
			});
		} finally {
			SettingsPages.unregister(TemplatesPage.ID);
		}
	}

	private static JTabbedPane find(java.awt.Container c) {
		for(java.awt.Component x : c.getComponents()) {
			if( x instanceof JTabbedPane ) {
				return (JTabbedPane) x;
			}
			if( x instanceof java.awt.Container ) {
				JTabbedPane r = find((java.awt.Container) x);
				if( r != null ) {
					return r;
				}
			}
		}
		return null;
	}

	private static int indexOf(TemplatesPage page, String description) {
		for(int i = 0; i < page.templates().size(); i++) {
			if( description.equals(page.templates().get(i).getDescription()) ) {
				return i;
			}
		}
		return -1;
	}
}
