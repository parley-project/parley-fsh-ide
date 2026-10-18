package us.bringardner.fsh.ide.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The template manager's working copy, and deleted built-ins staying deleted. */
public class TestTemplateList {

	@TempDir
	Path dir;

	@Test
	public void keepsTheTemplatesInOrderAndKnowsWhenTheyChange() {
		TemplateList list = new TemplateList(Configuration.createDefault().getTemplates());
		assertFalse(list.isChanged());
		for(int i = 1; i < list.size(); i++) {
			assertTrue(list.get(i - 1).getName().compareToIgnoreCase(list.get(i).getName()) <= 0);
		}
		int at = list.add(new Template("aaa", "first", "echo a"));
		assertEquals(0, at);
		assertTrue(list.isChanged());
		at = list.set(at, new Template("zzz", "last", "echo z"));
		assertEquals(list.size() - 1, at, "renamed: it moves");
		list.remove(at);
		assertFalse(list.isChanged(), "back as it was");
	}

	@Test
	public void restoreDefaultsPutsTheBuiltInsBackAndKeepsTheUsersOwn() {
		TemplateList list = new TemplateList(Configuration.createDefault().getTemplates());
		Template mine = new Template("mine", "my own", "echo mine");
		list.add(mine);
		int i = indexOf(list, "while-loop");
		list.set(i, new Template("while", "while-loop", "while true; do :; done"));
		list.remove(indexOf(list, "if-else"));
		list.restoreDefaults();
		assertTrue(list.items().contains(mine));
		assertTrue(list.get(indexOf(list, "while-loop")).getCode().contains("${condition}"));
		assertTrue(indexOf(list, "if-else") >= 0);
		assertEquals(Configuration.createDefault().getTemplates().size() + 1, list.size());
	}

	@Test
	public void problemsAndChecks() {
		assertNotNull(TemplateList.problem(new Template("", "d", "echo")));
		assertNotNull(TemplateList.problem(new Template("two words", "d", "echo")));
		assertNull(TemplateList.problem(new Template("ok", "d", "echo")));
		assertNull(TemplateList.check(new Template("ok", "d", "if [ ${c} ]; then\n  echo\nfi\n")));
		String c = TemplateList.check(new Template("bad", "d", "if [ ${c} ]; then\nfi\n"));
		assertTrue(c.startsWith("Line 2: "), c);
	}

	@Test
	public void aDeletedBuiltInStaysDeletedUntilRestored() throws IOException {
		Configuration config = Configuration.createDefault();
		TemplateList list = new TemplateList(config.getTemplates());
		list.remove(indexOf(list, "while-loop"));
		config.setTemplates(list.toSave());
		assertEquals(-1, indexOf(new TemplateList(config.getTemplates()), "while-loop"));

		// saved and read back: still deleted
		java.io.File file = dir.resolve("Config.xml").toFile();
		config.save(file);
		Configuration read = Configuration.load(file);
		assertEquals(-1, indexOf(new TemplateList(read.getTemplates()), "while-loop"));
		assertEquals(config.getTemplates().size(), read.getTemplates().size());

		// restored
		TemplateList again = new TemplateList(read.getTemplates());
		again.restoreDefaults();
		read.setTemplates(again.toSave());
		assertTrue(indexOf(new TemplateList(read.getTemplates()), "while-loop") >= 0);
	}

	@Test
	public void aChangedBuiltInTakesItsPlace() {
		Configuration config = Configuration.createDefault();
		TemplateList list = new TemplateList(config.getTemplates());
		list.set(indexOf(list, "while-loop"), new Template("while", "while-loop", "while true; do :; done\n"));
		config.setTemplates(list.toSave());
		List<Template> now = config.getTemplates();
		assertEquals(1, now.stream().filter(t->"while-loop".equals(t.getDescription())).count());
		assertTrue(now.contains(new Template("while", "while-loop", "while true; do :; done\n")));
	}

	private static int indexOf(TemplateList list, String description) {
		for(int i = 0; i < list.size(); i++) {
			if( description.equals(list.get(i).getDescription()) ) {
				return i;
			}
		}
		return -1;
	}
}
