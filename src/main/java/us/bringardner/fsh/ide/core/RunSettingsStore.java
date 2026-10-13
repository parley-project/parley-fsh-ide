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
package us.bringardner.fsh.ide.core;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

import us.bringardner.parley.files.FileSource;
import us.bringardner.parley.files.FileSourceFactory;
import us.bringardner.parley.files.fileproxy.FileProxyFactory;

/**
 * The arguments and redirects of the user's scripts, kept apart from the scripts (in
 * ~/.fsh-ide/run-settings) so a script holds nothing but its code. fsh-ide and fshdb share it.
 * <ul>
 * <li>Only scripts with something set are kept: putting empty settings removes a script's
 *     entry.</li>
 * <li>A script is known by its full path (a local file's canonical path; for a file on another
 *     file system, its URL without any password, then its path).</li>
 * <li>Entries that may be orphans (the script was deleted, renamed outside the IDE, or is on a
 *     server no longer used) clean themselves up: each write drops entries not used for
 *     getMaxAgeDays() (default 180) and, past getMaxEntries() (default 500), the least recently
 *     used; prune() also drops entries for local files that are gone.</li>
 * </ul>
 * Reading needs no lock (the file is replaced whole, by a move). Each change re-reads the
 * file, applies the change and writes it, under a file lock, so several IDE windows and fshdb
 * can share it. With nothing to keep there is no file.
 * <p>
 * The file has one line per script, fields separated by tabs (with \t, \n and \\ escaped):
 * last used (ms), script, arguments, standard input, output, error.
 */
public class RunSettingsStore {

	static final String FILE = "run-settings";
	public static final int DEFAULT_MAX_AGE_DAYS = 180;
	public static final int DEFAULT_MAX_ENTRIES = 500;
	private static final long DAY_MS = 24L*60*60*1000;
	/** Using a script updates its entry's last-used time at most this often. */
	static final long TOUCH_MS = 24L*60*60*1000;
	private static final String HEADER = "# fsh-ide and fshdb: the arguments and redirects scripts are run with,"
			+ " one script per line (last used, script, arguments, stdin, stdout, stderr; tab-separated)";

	private static volatile RunSettingsStore user;

	private final File file;
	private final File lockFile;
	private final LongSupplier clock;
	private volatile int maxAgeDays = DEFAULT_MAX_AGE_DAYS;
	private volatile int maxEntries = DEFAULT_MAX_ENTRIES;

	static final class Entry {
		final RunSettings settings;
		final long lastUsed;

		Entry(RunSettings settings, long lastUsed) {
			this.settings = settings;
			this.lastUsed = lastUsed;
		}
	}

	public RunSettingsStore(File file) {
		this(file, System::currentTimeMillis);
	}

	RunSettingsStore(File file, LongSupplier clock) {
		this.file = file;
		this.lockFile = new File(file.getParentFile(), file.getName()+".lock");
		this.clock = clock;
	}

	/** The user's: ~/.fsh-ide/run-settings. */
	public static RunSettingsStore forUser() {
		if( user == null ) {
			synchronized (RunSettingsStore.class) {
				if( user == null ) {
					File dir = new File(System.getProperty("user.home"), Configuration.DIR);
					user = new RunSettingsStore(new File(dir, FILE));
				}
			}
		}
		return user;
	}

	public File getFile() {
		return file;
	}

	public int getMaxAgeDays() {
		return maxAgeDays;
	}

	public int getMaxEntries() {
		return maxEntries;
	}

	/**
	 * How much is kept: entries not used for maxAgeDays are dropped, and past maxEntries the
	 * least recently used. Values below 1 mean the defaults.
	 */
	public void setLimits(int maxAgeDays, int maxEntries) {
		this.maxAgeDays = maxAgeDays < 1 ? DEFAULT_MAX_AGE_DAYS : maxAgeDays;
		this.maxEntries = maxEntries < 1 ? DEFAULT_MAX_ENTRIES : maxEntries;
	}

	// ---- what scripts are known by

	/**
	 * The key a script is kept under: a local file's canonical path; for a file on another
	 * file system (sftp ...), its file system's URL without a password, then the file's path.
	 */
	public static String keyOf(FileSource script) {
		String path = script.getAbsolutePath();
		FileSourceFactory f = script.getFileSourceFactory();
		if( f == null || FileProxyFactory.FACTORY_ID.equals(f.getTypeId())) {
			File local = new File(path);
			try {
				return local.getCanonicalPath();
			} catch (IOException e) {
				return local.getAbsolutePath();
			}
		}
		String url = withoutPassword(f.getURL());
		while( url.endsWith("/")) {
			url = url.substring(0, url.length()-1);
		}
		return url+(path.startsWith("/") ? path : "/"+path);
	}

	/** scheme://user:password@host... → scheme://user@host... */
	static String withoutPassword(String url) {
		if( url == null ) {
			return "";
		}
		return url.replaceFirst("^([A-Za-z][A-Za-z0-9+.-]*://)([^/@:]*):[^/@]*@", "$1$2@");
	}

	/** A key that names a file on this machine (it's checked for when pruning). */
	private static boolean isLocal(String key) {
		return !key.contains("://");
	}

	// ---- reading and changing

	/** A script's settings (NONE if it has none). Notes the script as used. */
	public RunSettings get(FileSource script) throws IOException {
		return get(keyOf(script));
	}

	public RunSettings get(String key) throws IOException {
		return locked(entries -> {
			Entry e = entries.get(key);
			if( e == null ) {
				return new Result<>(RunSettings.NONE, false);
			}
			long now = clock.getAsLong();
			if( now-e.lastUsed > TOUCH_MS ) {
				entries.put(key, new Entry(e.settings, now));
				return new Result<>(e.settings, true);
			}
			return new Result<>(e.settings, false);
		});
	}

	/** Keeps a script's settings; empty ones remove its entry (nothing is kept for it). */
	public void put(FileSource script, RunSettings settings) throws IOException {
		put(keyOf(script), settings);
	}

	public void put(String key, RunSettings settings) throws IOException {
		locked(entries -> {
			if( settings == null || settings.isEmpty()) {
				return new Result<>(null, entries.remove(key) != null);
			}
			Entry old = entries.get(key);
			if( old != null && old.settings.equals(settings) && clock.getAsLong()-old.lastUsed <= TOUCH_MS ) {
				return new Result<>(null, false);
			}
			entries.put(key, new Entry(settings, clock.getAsLong()));
			return new Result<>(null, true);
		});
	}

	/** Forgets a script's settings (its file is gone, say). */
	public void remove(FileSource script) throws IOException {
		remove(keyOf(script));
	}

	public void remove(String key) throws IOException {
		put(key, RunSettings.NONE);
	}

	/** Forgets every script's settings. */
	public void clear() throws IOException {
		locked(entries -> {
			boolean had = !entries.isEmpty();
			entries.clear();
			return new Result<>(null, had);
		});
	}

	/**
	 * Drops the entries that are probably orphans: those not used for getMaxAgeDays(), those
	 * for local files that no longer exist, and, past getMaxEntries(), the least recently
	 * used. Remote files aren't looked for (that could mean connecting to every server they
	 * were on); they go by age. Run it now and then (fsh-ide does when it starts).
	 * @return how many were dropped
	 */
	public int prune() throws IOException {
		return locked(entries -> {
			int before = entries.size();
			entries.entrySet().removeIf(e -> isLocal(e.getKey()) && !new File(e.getKey()).exists());
			limit(entries);
			int n = before-entries.size();
			return new Result<>(n, n > 0);
		});
	}

	/** Drops entries past their age, then the least recently used past the count. */
	private void limit(Map<String,Entry> entries) {
		long oldest = clock.getAsLong()-maxAgeDays*DAY_MS;
		entries.entrySet().removeIf(e -> e.getValue().lastUsed < oldest);
		int extra = entries.size()-maxEntries;
		if( extra > 0 ) {
			List<Map.Entry<String,Entry>> byUse = new java.util.ArrayList<>(entries.entrySet());
			byUse.sort(java.util.Comparator.comparingLong(e -> e.getValue().lastUsed));
			for(int i=0; i < extra; i++) {
				entries.remove(byUse.get(i).getKey());
			}
		}
	}

	/** The number of scripts with settings. */
	public int size() throws IOException {
		return locked(entries -> new Result<>(entries.size(), false));
	}

	// ---- the file

	private static final class Result<T> {
		final T value;
		final boolean changed;

		Result(T value, boolean changed) {
			this.value = value;
			this.changed = changed;
		}
	}

	private interface Change<T> {
		Result<T> apply(Map<String,Entry> entries);
	}

	/**
	 * Reads the entries and applies the change. If it changes them, it's done again under the
	 * file lock (on a fresh read) and the result is written, after dropping what's past the
	 * limits. A change must depend only on the entries it's given.
	 */
	private <T> T locked(Change<T> change) throws IOException {
		synchronized (RunSettingsStore.class) {
			Result<T> r = change.apply(read());
			if( !r.changed ) {
				return r.value;
			}
			File dir = file.getParentFile();
			if( dir != null && !dir.exists()) {
				dir.mkdirs();
			}
			try (RandomAccessFile raf = new RandomAccessFile(lockFile, "rw");
					FileChannel ch = raf.getChannel();
					FileLock lock = ch.lock()) {
				Map<String,Entry> entries = read();
				r = change.apply(entries);
				if( r.changed ) {
					limit(entries);
					write(entries);
				}
				return r.value;
			}
		}
	}

	private Map<String,Entry> read() throws IOException {
		Map<String,Entry> ret = new LinkedHashMap<>();
		if( !file.isFile()) {
			return ret;
		}
		List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
		for(String line : lines) {
			if( line.isBlank() || line.startsWith("#")) {
				continue;
			}
			String[] f = line.split("\t", -1);
			if( f.length < 6 ) {
				continue;
			}
			long used;
			try {
				used = Long.parseLong(f[0]);
			} catch (NumberFormatException e) {
				continue;
			}
			RunSettings s = new RunSettings(unescape(f[2]), unescape(f[3]), unescape(f[4]), unescape(f[5]));
			if( !s.isEmpty()) {
				ret.put(unescape(f[1]), new Entry(s, used));
			}
		}
		return ret;
	}

	/** Writes a temporary file, then moves it into place, so a failed write leaves the old one. */
	private void write(Map<String,Entry> entries) throws IOException {
		if( entries.isEmpty()) {
			// nothing to keep: no file
			Files.deleteIfExists(file.toPath());
			return;
		}
		StringBuilder b = new StringBuilder(HEADER).append('\n');
		for(Map.Entry<String,Entry> e : entries.entrySet()) {
			RunSettings s = e.getValue().settings;
			b.append(e.getValue().lastUsed).append('\t').append(escape(e.getKey()))
				.append('\t').append(escape(s.arguments())).append('\t').append(escape(s.stdIn()))
				.append('\t').append(escape(s.stdOut())).append('\t').append(escape(s.stdErr())).append('\n');
		}
		File tmp = new File(file.getParentFile(), file.getName()+".tmp");
		try {
			Files.write(tmp.toPath(), b.toString().getBytes(StandardCharsets.UTF_8));
			try {
				Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
			}
		} finally {
			Files.deleteIfExists(tmp.toPath());
		}
	}

	static String escape(String s) {
		StringBuilder b = new StringBuilder(s.length());
		for(char c : s.toCharArray()) {
			switch (c) {
			case '\\': b.append("\\\\"); break;
			case '\t': b.append("\\t"); break;
			case '\n': b.append("\\n"); break;
			case '\r': b.append("\\r"); break;
			default: b.append(c);
			}
		}
		return b.toString();
	}

	static String unescape(String s) {
		StringBuilder b = new StringBuilder(s.length());
		for(int i=0; i < s.length(); i++) {
			char c = s.charAt(i);
			if( c == '\\' && i+1 < s.length()) {
				char n = s.charAt(++i);
				switch (n) {
				case 't': b.append('\t'); break;
				case 'n': b.append('\n'); break;
				case 'r': b.append('\r'); break;
				default: b.append(n);
				}
			} else {
				b.append(c);
			}
		}
		return b.toString();
	}
}
