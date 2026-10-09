package us.bringardner.fsh.ide.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** The settings lines kept at the top of a saved script. */
public class TestScriptDocument {

	@Test
	public void readsTheSettings() {
		ScriptDocument doc = ScriptDocument.parse("#BjlIdeScriptArgs=a b\n#BjlIdeScriptIn=/tmp/in\n"
				+ "#BjlIdeScriptOut=/tmp/out\n#BjlIdeScriptErr=/tmp/err\necho hi\n");
		assertEquals("a b", doc.arguments());
		assertEquals("/tmp/in", doc.stdIn());
		assertEquals("/tmp/out", doc.stdOut());
		assertEquals("/tmp/err", doc.stdErr());
		assertEquals("echo hi\n", doc.body());
	}

	@Test
	public void settingsLinesCanBeAnywhere() {
		ScriptDocument doc = ScriptDocument.parse("echo a\n#BjlIdeScriptArgs=x\necho b\n");
		assertEquals("x", doc.arguments());
		assertEquals("echo a\necho b\n", doc.body());
	}

	@Test
	public void writesOnlyTheSettingsThatAreSet() {
		assertEquals("#BjlIdeScriptOut=/tmp/out\necho hi\n",
				new ScriptDocument("echo hi\n", "  ", "", " /tmp/out ", null).toText());
	}

	@Test
	public void roundTrip() {
		String text = "#BjlIdeScriptArgs=1 2\n#BjlIdeScriptErr=/tmp/e\necho \"$1\"\nexit 0\n";
		assertEquals(text, ScriptDocument.parse(text).toText());
	}

	@Test
	public void plainScript() {
		ScriptDocument doc = ScriptDocument.parse("echo hi");
		assertEquals("", doc.arguments());
		assertEquals("echo hi\n", doc.body());
	}

	@Test
	public void anEmptyScriptStaysEmpty() {
		ScriptDocument doc = ScriptDocument.parse("");
		assertEquals("", doc.body());
		assertEquals("", doc.toText());
	}
}
