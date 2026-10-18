package us.bringardner.fsh.ide.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
}
