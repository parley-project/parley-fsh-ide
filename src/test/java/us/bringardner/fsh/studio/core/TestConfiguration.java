package us.bringardner.fsh.studio.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import us.bringardner.fsh.debug.Template;

/** Saved settings and templates are read back (they used to be replaced by the defaults). */
public class TestConfiguration {

	@TempDir
	Path dir;

	@Test
	public void readsWhatWasSaved() throws IOException {
		Configuration config = Configuration.createDefault();
		config.setMaxRecent(7);
		config.setTabSize(2);
		List<Template> templates = new ArrayList<>(config.getTemplates());
		Template mine = new Template("mine", "my template", "echo mine");
		templates.add(mine);
		config.setTemplates(templates);
		File file = dir.resolve("Config.xml").toFile();
		config.save(file);

		Configuration read = Configuration.load(file);
		assertEquals(7, read.getMaxRecent());
		assertEquals(2, read.getTabSize());
		assertTrue(read.getTemplates().contains(mine));
		assertEquals(config.getTemplates().size(), read.getTemplates().size());
		assertTrue(Files.notExists(dir.resolve("Config.xml.tmp")));
	}

	@Test
	public void addsMissingDefaultTemplates() throws IOException {
		Path file = dir.resolve("Config.xml");
		Files.writeString(file, "<configuration><maxRecent>3</maxRecent></configuration>");

		Configuration read = Configuration.load(file.toFile());
		assertEquals(3, read.getMaxRecent());
		assertEquals(Configuration.createDefault().getTemplates().size(), read.getTemplates().size());
	}

	@Test
	public void usesTheDefaultsWhenTheFileIsUnreadable() throws IOException {
		Path file = dir.resolve("Config.xml");
		Files.writeString(file, "not xml");

		Configuration read = Configuration.load(file.toFile());
		assertEquals(Configuration.createDefault().getMaxRecent(), read.getMaxRecent());
		assertEquals(Configuration.createDefault().getTemplates().size(), read.getTemplates().size());
	}

	@Test
	public void setTemplatesKeepsTheGivenTemplates() {
		Configuration config = Configuration.createDefault();
		Template mine = new Template("mine", "echo mine");
		config.setTemplates(List.of(mine));
		assertTrue(config.getTemplates().contains(mine));
	}
}
