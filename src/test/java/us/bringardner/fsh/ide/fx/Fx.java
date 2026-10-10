package us.bringardner.fsh.ide.fx;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assumptions;

import javafx.application.Platform;

/** Runs test code on the JavaFX thread; skips the test if JavaFX can't start (no display). */
final class Fx {

	private static volatile Throwable startFailure;
	private static volatile boolean started;

	private Fx() {
	}

	static synchronized void start() {
		if( !started && startFailure == null ) {
			CountDownLatch up = new CountDownLatch(1);
			try {
				Platform.startup(up::countDown);
				if( !up.await(10, TimeUnit.SECONDS)) {
					startFailure = new IllegalStateException("JavaFX didn't start");
				}
				Platform.setImplicitExit(false);
				started = startFailure == null;
			} catch (IllegalStateException alreadyRunning) {
				started = true;
			} catch (Throwable e) {
				startFailure = e;
			}
		}
		Assumptions.assumeTrue(started, ()->"JavaFX can't start here: "+startFailure);
	}

	/** Runs code on the JavaFX thread and returns its result. */
	static <T> T call(Callable<T> code) throws Exception {
		start();
		CompletableFuture<T> ret = new CompletableFuture<>();
		Platform.runLater(()->{
			try {
				ret.complete(code.call());
			} catch (Throwable e) {
				ret.completeExceptionally(e);
			}
		});
		try {
			return ret.get(30, TimeUnit.SECONDS);
		} catch (java.util.concurrent.ExecutionException e) {
			if( e.getCause() instanceof Exception ) { Exception ex = (Exception) e.getCause();
				throw ex;
			}
			throw e;
		}
	}

	interface Code {
		void run() throws Exception;
	}

	static void run(Code code) throws Exception {
		call(()->{
			code.run();
			return null;
		});
	}

	/** Waits (up to 20 s) until condition, checked on the JavaFX thread, is true. */
	static void waitFor(Callable<Boolean> condition) throws Exception {
		long end = System.currentTimeMillis()+20_000;
		while( !call(condition)) {
			if( System.currentTimeMillis() > end ) {
				throw new AssertionError("Timed out waiting");
			}
			Thread.sleep(20);
		}
	}
}
