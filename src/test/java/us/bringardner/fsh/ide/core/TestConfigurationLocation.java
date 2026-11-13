package us.bringardner.fsh.ide.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The configuration is ~/.fsh-studio/Config.xml. */
public class TestConfigurationLocation {

	@TempDir
	Path home;

	@Test
	public void usesTheSettingsFolder() {
		File file = Configuration.configFileIn(home.toFile());
		assertEquals(home.resolve(".fsh-studio").resolve("Config.xml").toFile(), file);
		assertTrue(file.getParentFile().isDirectory());
		assertFalse(file.exists());
	}

	@Test
	public void doesNotCopyOldConfigurations() throws IOException {
		for(String old : new String[] {".bjlshellIde", ".fsh-ide"}) {
			Files.createDirectories(home.resolve(old));
			Files.writeString(home.resolve(old).resolve("Config.xml"), "<old/>");
		}
		File file = Configuration.configFileIn(home.toFile());
		assertFalse(file.exists());
	}

	@Test
	public void keepsTheConfigurationOnceItExists() throws IOException {
		Path now = home.resolve(".fsh-studio");
		Files.createDirectories(now);
		Files.writeString(now.resolve("Config.xml"), "<new/>");

		File file = Configuration.configFileIn(home.toFile());
		assertEquals("<new/>", Files.readString(file.toPath()));
	}
}
