package us.bringardner.fsh.studio;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.InputStream;

import org.fife.ui.rsyntaxtextarea.Theme;
import org.junit.jupiter.api.Test;

/** The dark editor theme is found beside EditorColors (it was lost once when the package moved). */
public class TestEditorColors {

	@Test
	public void theDarkThemeIsInTheJar() throws IOException {
		try (InputStream in = EditorColors.class.getResourceAsStream(EditorColors.DARK)) {
			assertNotNull(in, EditorColors.DARK + " beside EditorColors");
			assertNotNull(Theme.load(in));
		}
	}
}
