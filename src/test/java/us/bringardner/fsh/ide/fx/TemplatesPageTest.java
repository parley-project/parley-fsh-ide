package us.bringardner.fsh.ide.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import us.bringardner.fsh.ide.core.Configuration;
import us.bringardner.fsh.ide.core.Template;
import us.bringardner.fx.dialog.FxSettingsPages;
import us.bringardner.fx.dialog.SettingsDialog;

/** The Templates tab of the JavaFX IDE's Settings. */
public class TemplatesPageTest {

	/** The configuration goes to a home of the test's own, not the user's. */
	@BeforeAll
	public static void home() throws Exception {
		System.setProperty("user.home", Files.createTempDirectory("templates-page-fx").toString());
	}

	/** Each test starts from the built-in templates (a test before may have saved others). */
	@BeforeEach
	public void builtIns() {
		Configuration.getInstance().setTemplates(Configuration.getDefaultTemplates());
	}

	@Test
	public void editsAddsDeletesRestoresAndKeeps() throws Exception {
		Fx.run(()->{
			TemplatesPage page = new TemplatesPage();
			assertEquals("Templates", page.getTitle());
			assertEquals(0, page.list().getSelectionModel().getSelectedIndex());
			Template first = page.templates().get(0);
			assertEquals(first.getName(), page.nameField().getText());
			assertEquals(first.getCode(), page.codeEditor().getText());
			assertFalse(page.templates().isChanged(), "showing isn't changing");
			assertTrue(page.checkLabel().getText().contains("parses"), page.checkLabel().getText());

			page.descriptionField().setText("changed");
			assertEquals("changed", page.templates().get(page.list().getSelectionModel().getSelectedIndex()).getDescription());
			page.codeEditor().setText("if true; then\nfi\n");
			assertTrue(page.checkLabel().getText().contains("Line 2"), page.checkLabel().getText());

			page.actionNew();
			assertEquals("new-template", page.nameField().getText());
			page.nameField().setText("hello");
			page.codeEditor().setText("echo ${hello}");
			assertTrue(page.templates().items().stream().anyMatch(t->"hello".equals(t.getName())));

			page.nameField().setText("");
			assertNotNull(page.problem());
			page.nameField().setText("hello");
			assertNull(page.problem());

			page.delete(indexOf(page, "while-loop"));
			assertEquals(-1, indexOf(page, "while-loop"));
			page.ok();
			Configuration c = Configuration.getInstance();
			assertTrue(c.getTemplates().stream().anyMatch(t->"hello".equals(t.getName())));
			assertTrue(c.getTemplates().stream().noneMatch(t->"while-loop".equals(t.getDescription())));

			page.restoreDefaults();
			assertTrue(indexOf(page, "while-loop") >= 0);
			assertTrue(page.templates().items().stream().anyMatch(t->"hello".equals(t.getName())), "the user's own stays");
		});
	}

	@Test
	public void itIsATabInSettings() throws Exception {
		Fx.run(IdeWindow::registerThemePages);
		try {
			Fx.run(()->{
				SettingsDialog d = new SettingsDialog(null, null);
				assertEquals(1, d.getPages().size());
				assertTrue(d.getPages().get(0) instanceof TemplatesPage);
				javafx.scene.control.TabPane tabs = (javafx.scene.control.TabPane) d.getStage().getScene().getRoot()
						.lookup(".tab-pane");
				assertEquals("Templates", tabs.getTabs().get(1).getText());
				d.cancel();
			});
		} finally {
			FxSettingsPages.unregister(TemplatesPage.ID);
		}
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
