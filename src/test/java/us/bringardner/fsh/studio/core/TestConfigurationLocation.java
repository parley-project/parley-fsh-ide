package us.bringardner.fsh.studio.core;

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
		// .fsh-studio is the one folder made in the home
		assertEquals(java.util.List.of(".fsh-studio"), java.util.Arrays.asList(home.toFile().list()));
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
