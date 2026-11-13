/**
 *	Copyright 2026 Tony Bringardner
 *
 *	Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. 
 *	You may obtain a copy of the License at
 *
 *	http://www.apache.org/licenses/LICENSE-2.0
 *
 *	Unless required by applicable law or agreed to in writing, software distributed under the License is distributed 
 *	on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for 
 *	the specific language governing permissions and limitations under the License.
 */
package us.bringardner.fsh.studio.core;

import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Collects text from any thread and hands it to a UI in batches, on the UI's thread, so a
 * script that logs a line per statement doesn't queue a UI update per line.
 */
public class LogBatcher {

	private final Executor uiThread;
	private final Consumer<String> sink;
	private final StringBuilder pending = new StringBuilder();
	private boolean flushScheduled;

	/**
	 * @param uiThread runs a task on the UI's thread (SwingUtilities::invokeLater, Platform::runLater)
	 * @param sink adds text to the UI; called on the UI's thread
	 */
	public LogBatcher(Executor uiThread, Consumer<String> sink) {
		this.uiThread = uiThread;
		this.sink = sink;
	}

	public void add(String text) {
		synchronized (pending) {
			pending.append(text);
			if( flushScheduled ) {
				return;
			}
			flushScheduled = true;
		}
		uiThread.execute(this::flush);
	}

	/** Hands over what's pending now. Call on the UI's thread. */
	public void flush() {
		String text;
		synchronized (pending) {
			text = pending.toString();
			pending.setLength(0);
			flushScheduled = false;
		}
		if( !text.isEmpty()) {
			sink.accept(text);
		}
	}

	/** Drops what's pending. */
	public void clear() {
		synchronized (pending) {
			pending.setLength(0);
		}
	}
}
