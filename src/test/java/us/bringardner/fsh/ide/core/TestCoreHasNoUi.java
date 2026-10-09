package us.bringardner.fsh.ide.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/** The core is shared by the Swing IDE and a JavaFX one, so it mustn't use either toolkit. */
public class TestCoreHasNoUi {

	// fsh's DebugContext.isBreakpoint takes a java.awt.Point (a plain value, no UI)
	private static final List<String> ALLOWED = List.of("import java.awt.Point;",
			// Jazzy, the spelling engine inside RSyntaxTextArea's spellchecker, has no UI
			"import org.fife.com.swabunga.spell.engine.SpellDictionary;",
			"import org.fife.com.swabunga.spell.engine.SpellDictionaryHashMap;",
			"import org.fife.com.swabunga.spell.engine.Word;");

	@Test
	public void noUiImports() throws IOException {
		Path core = Path.of("src/main/java/us/bringardner/fsh/ide/core");
		assertTrue(Files.isDirectory(core), "run from the project folder");
		List<String> found = new ArrayList<>();
		try(Stream<Path> files = Files.list(core)) {
			for(Path p : files.filter(f->f.toString().endsWith(".java")).toList()) {
				for(String line : Files.readAllLines(p)) {
					String t = line.trim();
					if( (t.startsWith("import javax.swing") || t.startsWith("import java.awt")
							|| t.startsWith("import javafx") || t.startsWith("import org.fife"))
							&& !ALLOWED.contains(t)) {
						found.add(p.getFileName()+": "+t);
					}
				}
			}
		}
		assertEquals(List.of(), found);
	}
}
