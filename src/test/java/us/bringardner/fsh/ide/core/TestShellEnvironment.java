package us.bringardner.fsh.ide.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/** The login shell's environment, for the scripts the IDE runs. */
public class TestShellEnvironment {

	@TempDir
	Path home;

	@Test
	public void parsesWhatEnvPrintsAfterTheMarker() {
		String out = "a startup file's greeting\n"+ShellEnvironment.MARKER+"\0"
				+"PATH=/opt/bin:/usr/bin\0MULTI=one\ntwo\0EQ=a=b\0PWD=/x\0SHLVL=2\0_=/usr/bin/env\0"
				+ShellEnvironment.READER+"=1\0";
		Map<String,String> env = ShellEnvironment.parse(out);
		assertEquals("/opt/bin:/usr/bin", env.get("PATH"));
		assertEquals("one\ntwo", env.get("MULTI"));
		assertEquals("a=b", env.get("EQ"));
		// the shell's own, and the marker variable, aren't taken
		for(String name : new String[] {"PWD", "SHLVL", "_", ShellEnvironment.READER}) {
			assertFalse(env.containsKey(name), name);
		}
		assertThrows(IllegalArgumentException.class, ()->ShellEnvironment.parse("no marker"));
	}

	/** bash as a login shell: what its profile exports is in the environment, what it prints isn't. */
	@DisabledOnOs(OS.WINDOWS)
	@Test
	public void getsWhatTheLoginShellExports() throws Exception {
		Files.writeString(home.resolve(".bash_profile"),
				"echo 'hello from the profile'\n"
				+"export FSHIDE_FROM_PROFILE='set by .bash_profile'\n"
				+"NOT_EXPORTED=1\n"
				+"alias ll='ls -l'\n"
				+"[ -n \"$"+ShellEnvironment.READER+"\" ] && export FSHIDE_SAW_READER=yes\n");
		Map<String,String> env = ShellEnvironment.capture("/bin/bash", 15, Map.of("HOME", home.toString()));
		assertEquals("set by .bash_profile", env.get("FSHIDE_FROM_PROFILE"));
		assertEquals("yes", env.get("FSHIDE_SAW_READER"));
		assertFalse(env.containsKey("NOT_EXPORTED"));
		assertFalse(env.containsKey(ShellEnvironment.READER));
		assertTrue(env.containsKey("PATH"));
	}

	/** A startup file that doesn't finish in time: a problem, not a hang. */
	@DisabledOnOs(OS.WINDOWS)
	@Test
	public void aSlowProfileTimesOut() throws Exception {
		Files.writeString(home.resolve(".bash_profile"), "sleep 30\n");
		IOException e = assertThrows(IOException.class,
				()->ShellEnvironment.capture("/bin/bash", 1, Map.of("HOME", home.toString())));
		assertTrue(e.getMessage().contains("more than 1 seconds"), e.getMessage());
	}
}
