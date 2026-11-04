package us.bringardner.fsh.ide.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;

/** FshIdeFx's start: JavaFX's class path warning is left out, and its other messages are not. */
public class FshIdeFxTest {

	@Test
	public void onlyTheClassPathWarningIsLeftOut() {
		FshIdeFx.quietClassPathWarning();
		List<String> logged = new ArrayList<>();
		Handler h = new Handler() {
			@Override public void publish(LogRecord r) { logged.add(r.getMessage()); }
			@Override public void flush() { }
			@Override public void close() { }
		};
		Logger log = Logger.getLogger("javafx");
		log.addHandler(h);
		try {
			// as JavaFX logs it (PlatformLogger is on System.Logger, which is java.util.logging here)
			System.Logger fx = System.getLogger("javafx");
			fx.log(System.Logger.Level.WARNING, "Unsupported JavaFX configuration: classes were loaded from 'unnamed module @1'");
			fx.log(System.Logger.Level.WARNING, "something else");
		} finally {
			log.removeHandler(h);
		}
		assertEquals(List.of("something else"), logged);
	}
}
