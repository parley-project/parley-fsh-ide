import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * The GraalVM native-image settings for this application's and Parley's own part of the image, made
 * from the class path as the image is built (the pom's native profile runs it), so a class or file
 * that is renamed, added or removed never leaves a stale name behind. A single-file program:
 *
 * <pre>
 * java tools/NativeMetadata.java generate OUT_DIR CLASS_PATH
 * java tools/NativeMetadata.java strip FILE CLASS_PATH
 * </pre>
 *
 * <b>generate</b> writes OUT_DIR/reachability-metadata.json: every class under us.bringardner on the
 * class path, with all its members (commands are made by their constructors and read their options'
 * enums by reflection, which a recording of what ran may miss), a proxy for each of their JNA
 * library interfaces (JNA makes one to call the C library), and every other file of the jars
 * and folders those classes are in (icons, settings, the ServiceLoader lists in META-INF/services),
 * plus the properties files BaseObject looks for beside each class and in META-INF/parley, whether
 * they are there or not.
 * <p>
 * <b>strip</b> takes that same part out of FILE, a reachability-metadata.json recorded with the
 * tracing agent (tools/record-native-config.sh), which is kept for what the JDK (AWT and Swing on
 * each platform) and the other libraries need. A us.bringardner class called from native code (the
 * main class, which the java launcher finds) stays, marked jniAccessible only.
 * <p>
 * CLASS_PATH is jars and class folders, separated as the platform's class path is. The same file is
 * in fsh-studio and parley-files-viewer; fsh's tools/native-reflect.py does this for fsh.
 */
public class NativeMetadata {

	private static final String PACKAGE_PATH = "us/bringardner/";
	private static final String PACKAGE_PREFIX = "us.bringardner.";

	/** The us.bringardner classes on the class path (dotted names) */
	private final TreeSet<String> classes = new TreeSet<>();
	/** The other files of the jars and folders those classes are in (resource names) */
	private final TreeSet<String> files = new TreeSet<>();
	/** Every entry of the class path, to load the classes from */
	private final List<java.net.URL> classPath = new ArrayList<>();

	public static void main(String[] args) throws IOException {
		if( args.length != 3 || !(args[0].equals("generate") || args[0].equals("strip")) ) {
			System.err.println("usage: java NativeMetadata.java generate OUT_DIR CLASS_PATH\n"
					+ "       java NativeMetadata.java strip FILE CLASS_PATH");
			System.exit(2);
		}
		NativeMetadata me = new NativeMetadata();
		for(String entry : args[2].split(java.io.File.pathSeparator)) {
			if( !entry.isEmpty() ) {
				me.scan(Paths.get(entry));
				me.classPath.add(Paths.get(entry).toUri().toURL());
			}
		}
		if( args[0].equals("generate") ) {
			Path dir = Paths.get(args[1]);
			Files.createDirectories(dir);
			Files.write(dir.resolve("reachability-metadata.json"), me.generate().getBytes(StandardCharsets.UTF_8));
			System.out.println("NativeMetadata: " + me.classes.size() + " classes, " + me.files.size() + " files");
		} else {
			Path file = Paths.get(args[1]);
			String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
			Files.write(file, me.strip(text).getBytes(StandardCharsets.UTF_8));
		}
	}

	/** Note a jar's or a class folder's classes and files, if it has us.bringardner classes */
	private void scan(Path entry) throws IOException {
		List<String> names = new ArrayList<>();
		if( Files.isDirectory(entry) ) {
			try(Stream<Path> walk = Files.walk(entry)) {
				walk.filter(Files::isRegularFile).forEach(p -> names.add(entry.relativize(p).toString().replace('\\', '/')));
			}
		} else if( Files.isRegularFile(entry) ) {
			try(InputStream in = Files.newInputStream(entry); ZipInputStream zip = new ZipInputStream(in)) {
				for(ZipEntry e = zip.getNextEntry(); e != null; e = zip.getNextEntry()) {
					if( !e.isDirectory() ) {
						names.add(e.getName());
					}
				}
			}
		}
		boolean ours = false;
		for(String name : names) {
			if( name.startsWith(PACKAGE_PATH) && name.endsWith(".class") ) {
				ours = true;
				break;
			}
		}
		if( !ours ) {
			return;
		}
		for(String name : names) {
			if( name.endsWith(".class") ) {
				if( name.startsWith(PACKAGE_PATH) ) {
					classes.add(name.substring(0, name.length()-6).replace('/', '.'));
				}
			} else if( !name.equals("META-INF/MANIFEST.MF") && !name.startsWith("META-INF/maven/")
					&& !name.startsWith("META-INF/native-image/") && !name.matches("META-INF/[^/]*\\.(SF|DSA|RSA|EC)")
					&& !name.endsWith(".DS_Store") ) {
				files.add(name);
			}
		}
	}

	private String generate() {
		StringBuilder out = new StringBuilder("{\n  \"reflection\": [\n");
		List<String> entries = new ArrayList<>();
		for(String name : classes) {
			entries.add("{\"type\": " + quote(name) + ", \"allDeclaredConstructors\": true, \"allPublicConstructors\": true,"
					+ " \"allDeclaredMethods\": true, \"allPublicMethods\": true, \"allDeclaredFields\": true, \"allPublicFields\": true}");
		}
		for(String name : jnaLibraries()) {
			entries.add("{\"type\": {\"proxy\": [" + quote(name) + "]}}");
		}
		for(int i = 0; i < entries.size(); i++) {
			out.append("    ").append(entries.get(i)).append(i+1 < entries.size() ? ",\n" : "\n");
		}
		out.append("  ],\n  \"resources\": [\n");
		for(String name : files) {
			out.append("    {\"glob\": ").append(quote(glob(name))).append("},\n");
		}
		out.append("    {\"glob\": \"us/bringardner/**/*.properties\"},\n");
		out.append("    {\"glob\": \"META-INF/parley/*.properties\"}\n  ]\n}\n");
		return out.toString();
	}

	/**
	 * The us.bringardner interfaces that are JNA libraries (they extend com.sun.jna.Library): JNA
	 * calls a C library through a proxy of each. None when JNA isn't on the class path.
	 */
	private List<String> jnaLibraries() {
		List<String> ret = new ArrayList<>();
		try(java.net.URLClassLoader loader = new java.net.URLClassLoader(classPath.toArray(new java.net.URL[0]),
				ClassLoader.getPlatformClassLoader())) {
			Class<?> library = Class.forName("com.sun.jna.Library", false, loader);
			for(String name : classes) {
				try {
					Class<?> c = Class.forName(name, false, loader);
					if( c.isInterface() && library.isAssignableFrom(c) ) {
						ret.add(name);
					}
				} catch (Throwable e) {
					//  a class whose own dependencies aren't on the class path is no JNA library in this image
				}
			}
		} catch (ClassNotFoundException | IOException e) {
			//  no JNA
		}
		return ret;
	}

	/** A resource name as a glob that matches only it */
	private static String glob(String name) {
		return name.replace("\\", "\\\\").replace("*", "\\*");
	}

	private static String quote(String text) {
		return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
	}

	/**
	 * The recorded file without this application's and Parley's part. The file is the tracing
	 * agent's: one entry (a JSON object) to a line or to a few lines inside the "reflection" and
	 * "resources" arrays, which is what this reads; nothing else in it is touched.
	 */
	private String strip(String text) {
		StringBuilder out = new StringBuilder();
		int kept = 0, dropped = 0;
		int at = 0;
		while( at < text.length() ) {
			int start = nextEntry(text, at);
			if( start < 0 ) {
				break;
			}
			int end = endOfObject(text, start);
			String entry = text.substring(start, end);
			String replacement = entry;
			String type = stringValue(entry, "type");
			String glob = stringValue(entry, "glob");
			if( firstKey(entry).equals("type") && isOwnProxy(entry) ) {
				replacement = null;
			} else if( type != null && type.startsWith(PACKAGE_PREFIX) && firstKey(entry).equals("type") ) {
				replacement = entry.contains("\"jniAccessible\": true") || entry.contains("\"jniAccessible\":true")
						? "{\n      \"type\": " + quote(type) + ",\n      \"jniAccessible\": true\n    }" : null;
			} else if( glob != null && firstKey(entry).equals("glob") && (glob.startsWith(PACKAGE_PATH) || glob.startsWith("META-INF/parley/")
					|| glob.startsWith("META-INF/services/" + PACKAGE_PREFIX) || files.contains(glob)) ) {
				replacement = null;
			}
			if( replacement == null ) {
				dropped++;
				//  the entry, with the comma that follows it (or, for the last one, the comma before it)
				int after = end;
				while( after < text.length() && Character.isWhitespace(text.charAt(after)) ) {
					after++;
				}
				if( after < text.length() && text.charAt(after) == ',' ) {
					out.append(text, at, lineStart(text, start));
					at = after+1;
					while( at < text.length() && (text.charAt(at) == '\n' || text.charAt(at) == '\r') ) {
						at++;
					}
				} else {
					out.append(text, at, lineStart(text, start));
					//  (the last entry of its array: the comma before it goes, wherever that is by now)
					int last = out.length()-1;
					while( last >= 0 && Character.isWhitespace(out.charAt(last)) ) {
						last--;
					}
					if( last >= 0 && out.charAt(last) == ',' ) {
						out.setLength(last);
						out.append('\n');
					}
					at = end;
					while( at < text.length() && (text.charAt(at) == '\n' || text.charAt(at) == '\r') ) {
						at++;
					}
				}
			} else {
				kept++;
				out.append(text, at, start).append(replacement);
				at = end;
			}
		}
		out.append(text.substring(at));
		System.out.println("NativeMetadata: " + dropped + " entries taken out, " + kept + " kept");
		return out.toString();
	}

	/** True for a proxy entry ({"type": {"proxy": [...]}}) whose interfaces are all us.bringardner's */
	private static boolean isOwnProxy(String entry) {
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("^\\{\\s*\"type\"\\s*:\\s*\\{\\s*\"proxy\"\\s*:\\s*\\[([^\\]]*)\\]").matcher(entry);
		if( !m.find() ) {
			return false;
		}
		for(String name : m.group(1).split(",")) {
			if( !name.trim().startsWith("\"" + PACKAGE_PREFIX) ) {
				return false;
			}
		}
		return true;
	}

	/** The start of the next entry of the top-level arrays: an object two levels in, else -1 */
	private static int nextEntry(String text, int from) {
		int depth = depthAt(text, from);
		boolean inString = false;
		for(int i = from; i < text.length(); i++) {
			char c = text.charAt(i);
			if( inString ) {
				if( c == '\\' ) {
					i++;
				} else if( c == '"' ) {
					inString = false;
				}
			} else if( c == '"' ) {
				inString = true;
			} else if( c == '{' || c == '[' ) {
				if( c == '{' && depth == 2 ) {
					return i;
				}
				depth++;
			} else if( c == '}' || c == ']' ) {
				depth--;
			}
		}
		return -1;
	}

	/** How many objects and arrays are open at a position */
	private static int depthAt(String text, int pos) {
		int depth = 0;
		boolean inString = false;
		for(int i = 0; i < pos; i++) {
			char c = text.charAt(i);
			if( inString ) {
				if( c == '\\' ) {
					i++;
				} else if( c == '"' ) {
					inString = false;
				}
			} else if( c == '"' ) {
				inString = true;
			} else if( c == '{' || c == '[' ) {
				depth++;
			} else if( c == '}' || c == ']' ) {
				depth--;
			}
		}
		return depth;
	}

	/** The position after the object that starts at a position */
	private static int endOfObject(String text, int start) {
		int depth = 0;
		boolean inString = false;
		for(int i = start; i < text.length(); i++) {
			char c = text.charAt(i);
			if( inString ) {
				if( c == '\\' ) {
					i++;
				} else if( c == '"' ) {
					inString = false;
				}
			} else if( c == '"' ) {
				inString = true;
			} else if( c == '{' || c == '[' ) {
				depth++;
			} else if( c == '}' || c == ']' ) {
				if( --depth == 0 ) {
					return i+1;
				}
			}
		}
		return text.length();
	}

	private static int lineStart(String text, int pos) {
		int i = pos;
		while( i > 0 && (text.charAt(i-1) == ' ' || text.charAt(i-1) == '\t') ) {
			i--;
		}
		return i;
	}

	/** The first key of an object's text */
	private static String firstKey(String object) {
		int open = object.indexOf('"');
		int close = open < 0 ? -1 : object.indexOf('"', open+1);
		return close < 0 ? "" : object.substring(open+1, close);
	}

	/** The value of a key whose value is a string, in an object's text; null when it has none */
	private static String stringValue(String object, String key) {
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"" + key + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(object);
		return m.find() ? m.group(1).replace("\\\\", "\\").replace("\\\"", "\"") : null;
	}
}
