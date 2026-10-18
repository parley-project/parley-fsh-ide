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
		assertTrue(now.stream().anyMatch(t->"while-loop".equals(t.getDescription()) && t.getCode().contains("${condition}")));
		assertTrue(now.stream().noneMatch(t->"do-statement".equals(t.getDescription())));
	}
}
