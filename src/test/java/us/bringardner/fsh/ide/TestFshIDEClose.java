package us.bringardner.fsh.ide;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.GraphicsEnvironment;
import java.nio.file.Path;

import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Closing the window is left to FshIDE.close(), which may keep it open (if the user says no). */
public class TestFshIDEClose {

	@TempDir
	static Path home;

	@BeforeAll
	public static void quietHome() {
		System.setProperty("user.home", home.toString());
	}

	@Test
	public void theWindowDoesntHideItself() throws Exception {
		Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "needs a screen");
		SwingUtilities.invokeAndWait(()->{
			FshIDE w = new FshIDE();
			try {
				assertEquals(WindowConstants.DO_NOTHING_ON_CLOSE, w.getDefaultCloseOperation());
			} finally {
				w.dispose();
			}
		});
	}
}
