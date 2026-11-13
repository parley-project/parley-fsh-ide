package us.bringardner.fsh.studio.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import us.bringardner.fsh.studio.core.RecentFiles;

public class TestRecentFiles {

	@TempDir
	Path dir;

	@Test
	public void mostRecentFirstUpToTheMaximum() {
		RecentFiles recent = new RecentFiles();
		recent.add("/a", 3);
		recent.add("/b", 3);
		recent.add("/c", 3);
		recent.add("/a", 3);
		assertEquals(List.of("/a", "/c", "/b"), recent.list());
		recent.add("/d", 3);
		assertEquals(List.of("/d", "/a", "/c"), recent.list());
		assertEquals("/d", recent.first());
	}

	@Test
	public void savedAndRead() {
		RecentFiles recent = new RecentFiles();
		recent.add("/b", 10);
		recent.add("/a", 10);
		assertEquals(List.of("/a", "/b"), RecentFiles.parse(recent.format()).list());
	}

	@Test
	public void nothingSaved() {
		assertEquals(List.of(), RecentFiles.parse(null).list());
		assertEquals(List.of(), RecentFiles.parse("").list());
		assertNull(RecentFiles.parse("\n\n").first());
	}

	@Test
	public void findsTheMissingOnes() throws IOException {
		Path there = Files.writeString(dir.resolve("there.sh"), "echo hi");
		String gone = dir.resolve("gone.sh").toString();
		assertEquals(List.of(gone), RecentFiles.findMissing(List.of(there.toString(), gone)));
	}
}
