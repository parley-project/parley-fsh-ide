/**
 *	Copyright 2024 Tony Bringardner
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
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;



import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement
@XmlAccessorType (XmlAccessType.FIELD)
public class Configuration {
	private static volatile File configFile;
	private static volatile Configuration global;
	private int maxRecent=10;
	private int minVariableNameLength=2;
	private int tabSize= 4;
	private boolean lineWrap=false;
	private String scheme;
	// how many scripts' arguments and redirects are kept, and for how long (see RunSettingsStore)
	private int runSettingsMaxAgeDays = RunSettingsStore.DEFAULT_MAX_AGE_DAYS;
	private int runSettingsMaxEntries = RunSettingsStore.DEFAULT_MAX_ENTRIES;


	/** The editor's colour scheme as the editor saved it, or null for its default. */
	public String getScheme() {
		return scheme;
	}


	public void setScheme(String scheme) {
		this.scheme = scheme;
	}

	private Map<String,Double[]> defaultColors = new TreeMap<>();

	private List<Template> templates;

	/** The built-in templates the user deleted ({@link #keyOf(Template)}), so they stay deleted. */
	private List<String> deletedTemplates;

	private double[] drawingBackgroundColor = {0.999};



	public static Configuration getInstance()  {
		if( global == null ) {
			synchronized (Configuration.class) {
				if( global == null ) {
					global = load(getConfigFile());
				}
			}
		}

		return global;
	}

	/**
	 * The configuration saved in file, with any default templates it lacks added.
	 * Defaults if there's no file or it can't be read, so the IDE always has one.
	 */
	static Configuration load(File file) {
		if( !file.exists()) {
			return createDefault();
		}
		try {
			Unmarshaller um = jaxb().createUnmarshaller();
			Configuration ret = (Configuration) um.unmarshal(file);
			if( ret.templates == null ) {
				ret.templates = new ArrayList<>();
			}
			ret.populateDefault();
			if( ret.defaultColors == null ) {
				ret.defaultColors = new TreeMap<>();
			}
			return ret;
		} catch (Exception e) {
			loadError = new IOException("Can't read "+file+"; using the default settings", e);
			return createDefault();
		}
	}

	private static volatile IOException loadError;

	/** Why the saved configuration couldn't be read (the defaults are used instead), or null. */
	public static IOException getLoadError() {
		return loadError;
	}

	private static JAXBContext jaxbContext;

	private static synchronized JAXBContext jaxb() throws JAXBException {
		if( jaxbContext == null ) {
			jaxbContext = JAXBContext.newInstance(Configuration.class);
		}
		return jaxbContext;
	}

	private static File getConfigFile() {
		if( configFile == null ) {
			synchronized (Configuration.class) {
				if( configFile == null ) {
					configFile = configFileIn(new File(System.getProperty("user.home")));
				}
			}
		}

		return configFile;
	}

	/** The folder in the user's home that held the configuration before BjlShellIde became fsh-ide. */
	static final String LEGACY_DIR = ".bjlshellIde";
	static final String DIR = ".fsh-ide";
	static final String FILE = "Config.xml";

	/**
	 * home/.fsh-ide/Config.xml. The first time, a configuration saved under the old
	 * folder (home/.bjlshellIde) is copied across, so templates and settings carry over.
	 */
	static File configFileIn(File home) {
		File dir = new File(home,DIR);
		if(!dir.exists()) {
			dir.mkdirs();
		}
		File file = new File(dir,FILE);
		File legacy = new File(new File(home,LEGACY_DIR),FILE);
		if( !file.exists() && legacy.isFile()) {
			try {
				java.nio.file.Files.copy(legacy.toPath(), file.toPath());
			} catch (IOException e) {
				// Start with a fresh configuration rather than fail
			}
		}
		return file;
	}

	public void save () throws IOException {
		save(getConfigFile());
	}

	/** Writes to a temporary file first, so a failed save leaves the old file intact. */
	void save(File file) throws IOException {
		File tmp = new File(file.getParentFile(), file.getName()+".tmp");
		try {
			Marshaller m = jaxb().createMarshaller();
			m.marshal(this, tmp);
			try {
				Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (JAXBException e) {
			throw new IOException(e);
		} finally {
			Files.deleteIfExists(tmp.toPath());
		}
	}

	public int getMaxRecent() {
		return maxRecent;
	}


	public void setMaxRecent(int maxRecent) {
		this.maxRecent = maxRecent;
	}


	/** The user's run settings store, with this configuration's limits. */
	public RunSettingsStore runSettings() {
		RunSettingsStore s = RunSettingsStore.forUser();
		s.setLimits(runSettingsMaxAgeDays, runSettingsMaxEntries);
		return s;
	}

	/** Scripts' arguments and redirects not used for this many days are forgotten. */
	public int getRunSettingsMaxAgeDays() {
		return runSettingsMaxAgeDays;
	}

	public void setRunSettingsMaxAgeDays(int days) {
		this.runSettingsMaxAgeDays = days;
	}

	/** At most this many scripts' arguments and redirects are kept (the least recently used go). */
	public int getRunSettingsMaxEntries() {
		return runSettingsMaxEntries;
	}

	public void setRunSettingsMaxEntries(int entries) {
		this.runSettingsMaxEntries = entries;
	}

	public int getMinVariableNameLength() {
		return minVariableNameLength;
	}


	public void setMinVariableNameLength(int minVariableNameLength) {
		this.minVariableNameLength = minVariableNameLength;
	}

	public List<Template> getTemplates() {
		List<Template> ret= Collections.unmodifiableList(templates);
		
		return ret;
	}


	/**
	 * The templates, as the template manager leaves them. A built-in template that isn't among them
	 * was deleted, and stays deleted until it's restored ({@link #getDefaultTemplates()}).
	 */
	public void setTemplates(List<Template> templates) {
		this.templates = templates == null ? new ArrayList<>() : new ArrayList<>(templates);
		List<String> deleted = new ArrayList<>();
		for(Template t : defaultTemplates) {
			if( this.templates.stream().noneMatch(x->keyOf(x).equals(keyOf(t))) ) {
				deleted.add(keyOf(t));
			}
		}
		deletedTemplates = deleted;
		populateDefault();
	}

	/** The built-in templates, as a new list. */
	public static List<Template> getDefaultTemplates() {
		List<Template> ret = new ArrayList<>();
		for(Template t : defaultTemplates) {
			ret.add(t.copy());
		}
		return ret;
	}

	/** True if t has a built-in template's name and description (its code may have been changed). */
	public static boolean isBuiltIn(Template t) {
		for(Template d : defaultTemplates) {
			if( keyOf(d).equals(keyOf(t)) ) {
				return true;
			}
		}
		return false;
	}

	/** What a template is known by: several share a name (for), so the description too. */
	static String keyOf(Template t) {
		return t.getName() + "\n" + t.getDescription();
	}

	
	/**
	 * The built-in templates. In the code, "${name}" is a field the user fills in (its name is the
	 * text inserted; fields with the same name change together), "${cursor}" is where the caret
	 * goes, and "$$" is a "$" ({@link TemplateText}).
	 */
	private static Template defaultTemplates [] = {
			new Template( "true", "true"),
			new Template( "false", "false"),
			new Template( "until", "until-loop", 
					"until [ ${condition} ]\n"
					+ "do\n"
					+ "  ${echo}\n"
					+ "done\n"),
			new Template( "while", "while-loop", 
					"while [ ${condition} ]\n"
					+ "do\n"
					+ "  ${echo}\n"
					+ "done\n"),
			
			new Template( "for", "for-c-style", 
					"for((${var}=${start}; ${var}<${end}; ${var}++))\n"
					+ "do\n"
					+ "\techo $$${var}\n"
					+ "done\n"),
			new Template( "for", "for-array", 
					"for ${i} in ${1 2}\n"
					+ "do\n"
					+ "\techo $$${i}\n"
					+ "done\n"),
			new Template( "for", "for-list", 
					"for ${word} in ${list of words}\n"
					+ "do\n"
					+ "\techo \"$$${word}\"\n"
					+ "done\n"),
			
			new Template( "for", "for-range", 
					"for ${i} in {${start}..${stop}..${increment}}\n"
					+ "do\n"
					+ "\techo $$${i}\n"
					+ "done\n"),
			
			new Template( "if", "if", 
					"if [ ${condition} ]; then\n"
					+ "  # code to be executed if the condition is true\n"
					+ "  ${echo \"true\"}\n"
					+ "fi\n"),
			new Template( "if-else", "if-else", 
					"if [ ${condition} ]; then\n"
					+ "  # code to be executed if the condition is true\n"
					+ "  ${echo \"true\"}\n"
					+ "else\n"
					+ "  # code to be executed if the condition is false\n"
					+ "  ${echo \"false\"}\n"
					+ "fi\n"),
			new Template( "elif", "if-elif-else", 
					"if [ ${condition} ]; then\n"
					+ "  ${echo \"first\"}\n"
					+ "elif [ ${other condition} ]; then\n"
					+ "  ${echo \"second\"}\n"
					+ "else\n"
					+ "  ${echo \"neither\"}\n"
					+ "fi\n"),
			new Template( "if-not", "if-not: when a command fails", 
					"if ! ${command}; then\n"
					+ "  ${echo \"failed\"}\n"
					+ "fi\n"),
			new Template( "if-file", "if-file: a file exists", 
					"if [ -f \"$$${file}\" ]; then\n"
					+ "  ${echo \"found\"}\n"
					+ "fi\n"),
			new Template( "if-dir", "if-dir: a directory exists", 
					"if [ -d \"$$${dir}\" ]; then\n"
					+ "  ${echo \"found\"}\n"
					+ "fi\n"),
			new Template( "if-empty", "if-empty: a variable is empty or unset", 
					"if [ -z \"$$${var}\" ]; then\n"
					+ "  ${echo \"empty\"}\n"
					+ "fi\n"),
			new Template( "if-set", "if-set: a variable isn't empty", 
					"if [ -n \"$$${var}\" ]; then\n"
					+ "  ${echo \"set\"}\n"
					+ "fi\n"),
			new Template( "match", "match: a regular expression, with the captured groups", 
					"if [[ $$${var} =~ ${regex} ]]; then\n"
					+ "  echo \"$${BASH_REMATCH[1]}\"\n"
					+ "fi\n"),
			new Template( "function", "function", 
					"${name}() {\n"
					+ "  local ${arg}=\"$$1\"\n"
					+ "  ${cursor}\n"
					+ "}\n"),
			new Template( "read-lines", "read-lines: each line of a file", 
					"while IFS= read -r ${line}\n"
					+ "do\n"
					+ "  echo \"$$${line}\"\n"
					+ "done < ${file}\n"),
			new Template( "read-command", "read-command: each line a command prints (variables set in the loop are kept)", 
					"while IFS= read -r ${line}\n"
					+ "do\n"
					+ "  echo \"$$${line}\"\n"
					+ "done < <(${command})\n"),
			new Template( "heredoc", "heredoc: lines of text as a command's input", 
					"cat <<EOF\n"
					+ "${text}\n"
					+ "EOF\n"),
			new Template( "subst", "subst: a command's output $(command)", "$$(${command})"),
			new Template( "or-exit", "or-exit: stop with a message if a command fails", 
					"${command} || { echo \"${message}\" >&2; exit 1; }"),
			new Template( "strip-prefix", "strip-prefix ${var#prefix}", "$${${var}#${prefix}}"),
			new Template( "strip-suffix", "strip-suffix ${var%suffix}", "$${${var}%${suffix}}"),
			new Template( "replace", "replace ${var//find/replace}: every match", "$${${var}//${find}/${replace}}"),
			new Template( "length", "length ${#var}", "$${#${var}}"),
			new Template( "substring", "substring ${var:offset:length}", "$${${var}:${offset}:${length}}"),
			new Template( "array", "array: an indexed array", "${name}=(${a b c})"),
			new Template( "for-each", "for-each: each element of an array", 
					"for ${item} in \"$${${array}[@]}\"\n"
					+ "do\n"
					+ "  echo \"$$${item}\"\n"
					+ "done\n"),
			new Template( "map", "map: an associative array", 
					"declare -A ${map}\n"
					+ "${map}[${key}]=${value}\n"),
			new Template( "for-keys", "for-keys: each key of an associative array", 
					"for ${key} in \"$${!${map}[@]}\"\n"
					+ "do\n"
					+ "  echo \"$$${key}=$${${map}[$$${key}]}\"\n"
					+ "done\n"),
			new Template( "args", "args: a loop over the script's options", 
					"while [ $$# -gt 0 ]\n"
					+ "do\n"
					+ "  case $$1 in\n"
					+ "    -v|--verbose) ${verbose}=1;;\n"
					+ "    -o|--output) ${output}=\"$$2\"; shift;;\n"
					+ "    --) shift; break;;\n"
					+ "    -*) echo \"unknown option: $$1\" >&2; exit 1;;\n"
					+ "    *) break;;\n"
					+ "  esac\n"
					+ "  shift\n"
					+ "done\n"),
			new Template( "getopts", "getopts: the script's single-letter options", 
					"while getopts \"${ab:}\" ${opt}\n"
					+ "do\n"
					+ "  case $$${opt} in\n"
					+ "    a) ${echo \"a\"};;\n"
					+ "    b) echo \"b=$$OPTARG\";;\n"
					+ "    \\?) exit 1;;\n"
					+ "  esac\n"
					+ "done\n"
					+ "shift $$((OPTIND - 1))\n"),
			new Template( "connect", "connect: mount a file system, disconnected when the script ends", 
					"connect ${sftp} host=${host} user=${user} password=@keychain:${service/account} /${mount}\n"
					+ "trap 'disconnect /${mount}' EXIT\n"),
			new Template( "scratch", "scratch: an in-memory file system, gone when the script ends", 
					"connect memory /${scratch}\n"
					+ "trap 'disconnect /${scratch}' EXIT\n"),
			new Template( "copy-across", "copy-across: copy between mounted file systems", 
					"cp -r /${from}/${path} /${to}/"),
			new Template( "find-each", "find-each: each file find finds", 
					"find /${mount}/${dir} -name '${pattern}' | while IFS= read -r ${file}\n"
					+ "do\n"
					+ "  echo \"$$${file}\"\n"
					+ "done\n"),
			new Template( "expression", "expression $((x % 2))", "$$(( ${cursor} ))"),
			new Template( "parameterPlusword", "parameter:+word (If parameter is null or unset, nothing is substituted, otherwise the expansion of word is substituted. The value of parameter is not used.)", 
					"$${${parameter}:+${word}}"),
			new Template( "parameterMinusword", "parameter:-word (If parameter is unset or null, the expansion of word is substituted. Otherwise, the value of parameter is substituted.)", 
					"$${${parameter}:-${word}}"),
			new Template( "parameterEqword", "parameter:=word (If parameter is unset or null, the expansion of word is assigned to parameter, and the result of the expansion is the final value of parameter. Positional parameters and special parameters may not be assigned in this way.)", 
					"$${${parameter}:=${word}}"),
			
			new Template( "parameter?word", 
					"parameter:?word (If parameter is null or unset, the shell writes the expansion of word (or a message to that effect if word is not present) to the standard error and, if it is not interactive, exits with a non-zero status. An interactive shell does not exit, but does not execute the command associated with the expansion. Otherwise, the value of parameter is substituted.)", 
					"$${${parameter}:?${parameter is not set}}"),
			
			
			new Template( "case", "case-statement", 
					"case ${word} in\n"
					+ "  option1 | option2 ) echo \"type1\";;\n"
					+ "  option3 | option4 ) echo \"type2\";;\n"
					+ "  *) echo \"an unknown type\";;\n"
					+ "esac\n"),
			
			new Template( "case", "case-statement-variable", 
					"case $$${var} in\n"
					+ "  option1 | option2 ) echo \"$$${var} is type1\";;\n"
					+ "  option3 | option4 ) echo \"$$${var} is type2\";;\n"
					+ "  *) echo \"$$${var} is an unknown type\";;\n"
					+ "esac\n"),
			
			new Template( "select01", "select", 
					"select ${name} in ${list}\n"
					+ "do\n"
					+ "\techo you picked $$${name}\n"
					+ "\tbreak\n"
					+ "done\n"
					),
			
			new Template( "select02", "select-variable", 
					"select ${name} in $$${var}\n"
					+ "do\n"
					+ "\techo you picked $$${name}\n"
					+ "\tbreak\n"
					+ "done\n"
					),
	};

	/**
	 * Built-in templates as earlier versions had them. A saved configuration has a copy of each, so
	 * an unchanged copy is taken out when it's read; one the user changed is theirs and stays.
	 */
	private static Template retiredTemplates [] = {

			new Template( "true", "true"),
			new Template( "false", "false"),
			new Template( "until", "until-loop", 
					"until [ ${true} ]\n"
					+ "do\n"
					+ "  ${echo}\n"
					+ "done"),
			new Template( "while", "while-loop", 
					"while [ ${false} ]\n"
					+ "do\n"
					+ "  ${echo}\n"
					+ "done\n"),
			
			new Template( "for", "for-c-style", 
					"for((${var}=${start}; ${var}<${end}; ${var}++))\n"
					+ "do\n"
					+ "\n\techo ${var};\n"
					+ "done\n"),
			new Template( "for", "for-array", 
					"for ${i} in ${1 2};\n"
					+ "do\n"
					+ "\n\techo ${i};\n"
					+ "done\n"),
			new Template( "for", "for-list", 
					"for word in ${list of words}\n"
					+ "do \n"
					+ " echo ${\"$word\"}\n"
					+ "done"),
			
			new Template( "for", "for-range", 
					"for ${i} in {${start}..${stop}..${increment}}\n"
					+ "do\n"
					+ "\techo  ${$i}\n"
					+ "done"),
			
			new Template( "if", "if", 
					"if [ ${condition} ]; then\n"
					+ "  # code to be executed if the condition is true\n"
					+ "fi"),
			new Template( "if-else", "if-else", 
					"if [ ${condition} ]; then\n"
					+ "  # code to be executed if the condition is true\n"
					+ "else\n"
					+ "  # code to be executed if the condition is false\n"
					+ "fi"),
			new Template( "expression", "expression $((x % 2))", "$$(( ${cursor} ))"),
			new Template( "do", "do-statement", 
					"do\n"
					+ "	# code goes here\n"
					+ "done\n"),
			new Template( "parameterPlusword", "parameter:+word (If parameter is null or unset, nothing is substituted, otherwise the expansion of word is substituted. The value of parameter is not used.)", 
					"$${${parameter}:+${word}}"),
			new Template( "parameterMinusword", "parameter:−word (If parameter is unset or null, the expansion of word is substituted. Otherwise, the value of parameter is substituted.)", 
					"$${${parameter}:-${word}}"),
			new Template( "parameterEqword", "parameter:=word (If parameter is unset or null, the expansion of word is assigned to parameter, and the result of the expansion is the final value of parameter. Positional parameters and special parameters may not be assigned in this way.)", 
					"$${${parameter}:=${word}}"),
			
			new Template( "parameter?word", 
					"parameter:?word (If parameter is null or unset, the shell writes the expansion \nof word (or a message to that effect if word is not present) to the standard error and, if it is not interactive, exits with a non-zero status. An interactive shell does not exit, but does not execute the command associated with the expansion. Otherwise, the value of parameter is substituted.)", 
					"$${${parameter}:?${parameter is not set}}"),
			
			
			new Template( "case", "case-statement", 
					"case word  in\n"
					+ "  option1 | option2 ) echo \"type1\";;\n"
					+ "  option3 | option4 ) echo \"type2\";;\n"
					+ "  *) echo \"an unknown type\";;\n"
					+ "esac"),
			
			new Template( "case", "case-statement-variable", 
					"case $$var  in\n"
					+ "  option1 | option2 ) echo \"$$var is type1\";;\n"
					+ "  option3 | option4 ) echo \"$$var is type2\";;\n"
					+ "  *) echo \"$$var is an unknown type\";;\n"
					+ "esac"),
			
			new Template( "select01", "select", 
					"select ${name} in ${list};\n"
					+ "do\n"
					+ "	echo you picked ${name}\n"
					+ "	break;\n"
					+ "done"
					),
			
			new Template( "select02", "select-variable", 
					"select ${name} in $$var;\n"
					+ "do\n"
					+ "	echo you picked ${$name}\n"
					+ "	break;\n"
					+ "done"
					),
			
	};

	/**
	 * Takes out unchanged copies of retired built-ins, and adds each built-in that's missing, unless
	 * the user deleted it. A built-in the user changed (the same name and description, other code)
	 * is theirs, and the built-in isn't added beside it.
	 */
	private void populateDefault() {
		if( templates == null ) {
			templates = new ArrayList<>();
		}
		for(Template t : retiredTemplates) {
			if( !java.util.Arrays.asList(defaultTemplates).contains(t) ) {
				templates.remove(t);
			}
		}
		List<String> deleted = deletedTemplates == null ? List.of() : deletedTemplates;
		for(Template t : defaultTemplates) {
			String key = keyOf(t);
			if( !deleted.contains(key) && templates.stream().noneMatch(x->keyOf(x).equals(key)) ) {
				templates.add(t);
			}
		}
	}

	public static Configuration createDefault() {
		Configuration ret = new Configuration();
		ret.maxRecent = 10;
		ret.minVariableNameLength = 2;
		ret.templates = new ArrayList<>();
		ret.defaultColors = new TreeMap<>();
		ret.setLineWrap(false);
		ret.setTabSize(4);

		ret.populateDefault();

		return ret;
	}
	// 287 323 842 997     807140



	public static void main(String[] args) throws IOException {
		boolean create = true;
		if( create ) {
			Configuration config = createDefault();
			config.save();
		} else {
			Configuration config = getInstance();
			System.out.println("Number or templates="+config.templates.size());
			System.out.println("maxRecent="+config.maxRecent);
			System.out.println("minVariableNameLength="+config.minVariableNameLength);
			List<Template> temp = config.getTemplates();
			List<Template> temp2 = config.getTemplates();
			System.out.println("eq="+temp.equals(temp2));
		}
	}


	public Map<String,Double[]> getDefaultColors() {
		return defaultColors;
	}


	public void setDefaultColors(Map<String,Double[]> defaultColors) {
		this.defaultColors = defaultColors;
	}


	public double[] getDrawingBackgroundColor() {
		return drawingBackgroundColor;
	}


	public void setDrawingBackgroundColor(double[] drawingBackgroundColor) {
		this.drawingBackgroundColor = drawingBackgroundColor;

	}


	public int getTabSize() {
		return tabSize;
	}


	public void setTabSize(int tabSize) {
		this.tabSize = tabSize;
	}


	public boolean isLineWrap() {
		return lineWrap;
	}


	public void setLineWrap(boolean lineWrap) {
		this.lineWrap = lineWrap;
	}




}
