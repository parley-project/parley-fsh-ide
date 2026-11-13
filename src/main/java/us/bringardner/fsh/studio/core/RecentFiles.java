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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import us.bringardner.parley.files.FileSourceFactory;

/** The recently opened scripts, most recent first, as paths any file system understands. */
public class RecentFiles {

	private final List<String> paths = new ArrayList<>();

	/** Reads the list as format() wrote it (one path a line); empty for null. */
	public static RecentFiles parse(String saved) {
		RecentFiles ret = new RecentFiles();
		if( saved != null ) {
			for(String name : saved.split("\n")) {
				if( !name.isBlank() && !ret.paths.contains(name)) {
					ret.paths.add(name);
				}
			}
		}
		return ret;
	}

	/** The text to save. */
	public String format() {
		StringBuilder buf = new StringBuilder();
		for(String name : paths) {
			buf.append(name).append('\n');
		}
		return buf.toString();
	}

	public List<String> list() {
		return Collections.unmodifiableList(paths);
	}

	/** The most recent, or null. */
	public String first() {
		return paths.isEmpty() ? null : paths.get(0);
	}

	/** Makes path the most recent, keeping at most max. */
	public void add(String path, int max) {
		paths.remove(path);
		paths.add(0, path);
		while(paths.size() > Math.max(1, max)) {
			paths.remove(paths.size()-1);
		}
	}

	/** @return true if any were in the list */
	public boolean removeAll(Collection<String> remove) {
		return paths.removeAll(remove);
	}

	/**
	 * The paths that no longer exist. One that can't be checked now (its file system is
	 * unavailable) isn't counted as missing. Slow for remote file systems: don't call it on a UI thread.
	 */
	public static List<String> findMissing(Collection<String> paths) {
		List<String> ret = new ArrayList<>();
		for(String name : paths) {
			try {
				if( !FileSourceFactory.getDefaultFactory().createFileSource(name).exists()) {
					ret.add(name);
				}
			} catch (IOException e) {
			}
		}
		return ret;
	}
}
