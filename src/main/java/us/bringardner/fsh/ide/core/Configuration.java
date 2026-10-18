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
			populateDefault(ret.templates);
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


	public void setTemplates(List<Template> templates) {
		this.templates = templates == null ? new ArrayList<>() : new ArrayList<>(templates);
		populateDefault(this.templates);
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
					+ "  ${echo}\n"
					+ "fi\n"),
			new Template( "if-else", "if-else", 
					"if [ ${condition} ]; then\n"
					+ "  # code to be executed if the condition is true\n"
					+ "  ${echo}\n"
					+ "else\n"
					+ "  # code to be executed if the condition is false\n"
					+ "  ${echo}\n"
					+ "fi\n"),
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

	private static void populateDefault(List<Template> templates) {
		for(Template t : retiredTemplates) {
			if( !java.util.Arrays.asList(defaultTemplates).contains(t) ) {
				templates.remove(t);
			}
		}
		for(Template t : defaultTemplates) {
			if(!templates.contains(t)) {
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

		populateDefault(ret.templates);

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
