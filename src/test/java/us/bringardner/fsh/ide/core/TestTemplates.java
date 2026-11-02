package us.bringardner.fsh.ide.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/** The built-in templates insert code that parses, and old copies of them give way to the new ones. */
public class TestTemplates {

	@Test
	public void everyBuiltInTemplateParses() {
		for(Template t : Configuration.createDefault().getTemplates()) {
			String code = t.getCode() == null || t.getCode().isBlank() ? t.getName() : t.getCode();
			String text = TemplateText.expand(code).text;
			List<CompileError> errors = new ArrayList<>();
			ScriptParser.parse(text, errors);
			assertTrue(errors.isEmpty(), t.getDescription() + ": " + errors + "\n" + text);
		}
	}

	@Test
	public void loopsEchoTheirVariable() {
		for(Template t : Configuration.createDefault().getTemplates()) {
			String text = TemplateText.expand(t.getCode()).text;
			assertFalse(text.contains("echo i\n") || text.contains("echo var\n") || text.contains("picked name\n"),
					t.getDescription() + " echoes the name, not the value:\n" + text);
		}
		// the loop variable and the one echoed are one field, so they change together
		Template forArray = Configuration.createDefault().getTemplates().stream()
				.filter(t->"for-array".equals(t.getDescription())).findFirst().get();
		TemplateText.Expansion e = TemplateText.expand(forArray.getCode());
		assertEquals("for i in 1 2\ndo\n\techo $i\ndone\n", e.text);
	}

	@Test
	public void eachBranchHasItsOwnField() {
		// fields with the same name change together: the then and else commands mustn't
		Template ifElse = Configuration.createDefault().getTemplates().stream()
				.filter(t->"if-else".equals(t.getDescription())).findFirst().get();
		TemplateText.Expansion e = TemplateText.expand(ifElse.getCode());
		List<String> names = new ArrayList<>();
		for(int[] f : e.fields) {
			names.add(e.text.substring(f[0], f[1]));
		}
		assertEquals(List.of("condition", "echo \"true\"", "echo \"false\""), names);
	}

	@Test
	public void theNewTemplatesAreThere() {
		List<String> names = new ArrayList<>();
		Configuration.createDefault().getTemplates().forEach(t->names.add(t.getName()));
		for(String n : new String[] {"function", "read-lines", "read-command", "elif", "heredoc", "subst", "or-exit",
				"if-not", "if-file", "if-dir", "if-empty", "if-set", "match", "strip-prefix", "strip-suffix", "replace",
				"length", "substring", "array", "for-each", "map", "for-keys", "args", "getopts", "connect", "scratch",
				"copy-across", "find-each"}) {
			assertTrue(names.contains(n), n);
		}
	}

	@Test
	public void conditionsAreFieldsNotAlwaysTrueWords() {
		for(Template t : Configuration.createDefault().getTemplates()) {
			String text = TemplateText.expand(t.getCode()).text;
			assertFalse(text.contains("[ true ]") || text.contains("[ false ]"), t.getDescription());
		}
	}

	@Test
	public void unchangedOldCopiesAreReplacedAndEditedOnesKept() {
		Template oldWhile = new Template("while", "while-loop",
				"while [ ${false} ]\n" + "do\n" + "  ${echo}\n" + "done\n");
		Template oldDo = new Template("do", "do-statement", "do\n" + "	# code goes here\n" + "done\n");
		Template edited = new Template("while", "while-loop", "while true\ndo\n  sleep 1\ndone\n");
		Configuration config = Configuration.createDefault();
		config.setTemplates(List.of(oldWhile, oldDo, edited));
		List<Template> now = config.getTemplates();
		assertFalse(now.contains(oldWhile), "the old built-in goes");
		assertFalse(now.contains(oldDo), "the dropped one goes");
		assertTrue(now.contains(edited), "the user's own stays");
		assertEquals(1, now.stream().filter(t->"while-loop".equals(t.getDescription())).count(),
				"the user's version takes the built-in's place");
		assertTrue(now.stream().noneMatch(t->"do-statement".equals(t.getDescription())));
	}

	/** the tests a script makes: one template for each kind, the test a field with choices */
	@Test
	public void theTestGroupsAreThereAndParse() {
		List<Template> all = Configuration.createDefault().getTemplates();
		String[][] groups = {
				{"if-file", "-e", "-f", "-d", "-L", "-s", "-r", "-w", "-x", "-p", "-S", "-c", "-b", "-u", "-g", "-k", "-O", "-G", "-N", "! -e"},
				{"if-files", "-nt", "-ot", "-ef"},
				{"if-string", "==", "!=", "=~", "<", ">"},
				{"if-number", "-eq", "-ne", "-lt", "-le", "-gt", "-ge"},
				{"if-variable", "-v", "! -v"},
				{"if-logic", "&&", "||"},
				{"if-command", "command", "! command", "command -v name >/dev/null 2>&1", "[ $# -lt 1 ]", "[ \"$EUID\" -eq 0 ]"},
		};
		for(String[] g : groups) {
			Template t = all.stream().filter(x->g[0].equals(x.getName())).findFirst().orElse(null);
			assertNotNull(t, g[0]);
			assertTrue(t.getDescription().startsWith(g[0] + ": "), t.getDescription());
			assertNull(TemplateList.check(t), g[0] + " parses");
			TemplateText.Expansion e = TemplateText.expand(t.getCode());
			List<String> values = new ArrayList<>();
			for(List<TemplateText.Choice> c : e.choices) {
				if( c != null ) {
					c.forEach(x->values.add(x.value));
					assertTrue(c.stream().allMatch(x->!x.label.isEmpty()), g[0] + ": each choice says what it means");
				}
			}
			for(int i = 1; i < g.length; i++) {
				assertTrue(values.contains(g[i]), g[0] + " offers " + g[i] + ": " + values);
			}
			// each choice put in parses too
			for(int f = 0; f < e.fields.size(); f++) {
				if( e.choices.get(f) == null ) {
					continue;
				}
				for(TemplateText.Choice c : e.choices.get(f)) {
					String text = e.text.substring(0, e.fields.get(f)[0]) + c.value + e.text.substring(e.fields.get(f)[1]);
					List<CompileError> errors = new ArrayList<>();
					ScriptParser.parse(text, errors);
					assertTrue(errors.isEmpty(), g[0] + " with " + c.value + ": " + errors);
				}
			}
		}
		// the single if- tests are choices now
		for(String gone : new String[] {"if-exists", "if-readable", "if-newer", "if-eq", "if-and", "if-ok"}) {
			assertTrue(all.stream().noneMatch(x->gone.equals(x.getName())), gone);
		}
	}

	@Test
	public void aFieldWithChoicesPutsInTheFirst() {
		TemplateText.Expansion e = TemplateText.expand("if [ ${|-e : exists|-f : a file|-d} \"$$${file}\" ]");
		assertEquals("if [ -e \"$file\" ]", e.text);
		assertEquals(2, e.fields.size());
		assertEquals("-e", e.text.substring(e.fields.get(0)[0], e.fields.get(0)[1]));
		assertEquals("file", e.text.substring(e.fields.get(1)[0], e.fields.get(1)[1]));
		assertEquals(3, e.choices.get(0).size());
		assertEquals("a file", e.choices.get(0).get(1).label);
		assertEquals("-d", e.choices.get(0).get(2).value);
		assertEquals("", e.choices.get(0).get(2).label);
		assertNull(e.choices.get(1));
		// \ for a | or } in a value, $$ for a $
		e = TemplateText.expand("a ${|\\|\\| : or|$$x\\} : brace} b");
		assertEquals("a || b", e.text);
		assertEquals("$x}", e.choices.get(0).get(1).value);
		// for the Swing IDE: a plain field of the first value
		assertEquals("if [ ${-e} \"$$${file}\" ]", TemplateText.withoutChoices("if [ ${|-e : exists|-f : a file} \"$$${file}\" ]"));
	}

	@Test
	public void theOldIfFileAndTheSingleTestsGiveWayToTheGroups() {
		Template oldFile = new Template("if-file", "if-file: a file exists",
				"if [ -f \"$$${file}\" ]; then\n  ${echo \"found\"}\nfi\n");
		Template oldExists = new Template("if-exists", "if-exists: a file or directory exists (-e)",
				"if [ -e \"$$${file}\" ]; then\n  ${echo \"found\"}\nfi\n");
		Configuration config = Configuration.createDefault();
		List<Template> saved = new ArrayList<>(config.getTemplates());
		saved.removeIf(t->t.getName().equals("if-command"));
		saved.add(oldFile);
		saved.add(oldExists);
		config.setTemplates(saved);
		List<Template> now = config.getTemplates();
		assertFalse(now.contains(oldFile));
		assertFalse(now.contains(oldExists));
		assertEquals(1, now.stream().filter(t->t.getName().equals("if-file")).count());
		assertTrue(now.stream().anyMatch(t->t.getName().equals("if-file") && t.getCode().contains("${|")));
		assertFalse(now.stream().anyMatch(t->t.getName().equals("if-command")), "deleted, it stays deleted");
	}

	/** a template's name with hyphens is one word to complete, a hyphen elsewhere isn't */
	@Test
	public void theWordToCompleteKeepsHyphensForTemplateNames() {
		List<Template> all = Configuration.createDefault().getTemplates();
		assertEquals("if-fi", Completions.prefix("x=1\nif-fi", 9, all));
		assertEquals("if-", Completions.prefix("if-", 3, all));
		assertEquals("co", Completions.prefix("echo $((x-co", 12, all), "a variable after a minus");
		assertEquals("la", Completions.prefix("ls -la", 6, all), "an option");
		List<String> names = new ArrayList<>();
		Completions.matching("if-fi", all, "").forEach(c->names.add(c.label));
		assertEquals(List.of("if-file", "if-files"), names);
	}
}
