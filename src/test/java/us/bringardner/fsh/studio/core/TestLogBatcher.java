package us.bringardner.fsh.studio.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

public class TestLogBatcher {

	@Test
	public void manyLinesOneUpdate() {
		List<Runnable> scheduled = new ArrayList<>();
		List<String> shown = new ArrayList<>();
		LogBatcher log = new LogBatcher(scheduled::add, shown::add);
		log.add("a\n");
		log.add("b\n");
		log.add("c\n");
		assertEquals(1, scheduled.size());
		scheduled.get(0).run();
		assertEquals(List.of("a\nb\nc\n"), shown);

		log.add("d\n");
		assertEquals(2, scheduled.size());
		log.clear();
		scheduled.get(1).run();
		assertEquals(1, shown.size());
	}
}
