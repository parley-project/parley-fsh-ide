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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The templates as the template manager (Edit &gt; Templates...) changes them, before they are saved:
 * kept in order of name, then description. The Swing and JavaFX managers both work on one.
 */
public final class TemplateList {

	private static final Comparator<Template> ORDER = Comparator
			.comparing((Template t)->t.getName() == null ? "" : t.getName(), String.CASE_INSENSITIVE_ORDER)
			.thenComparing(t->t.getDescription() == null ? "" : t.getDescription());

	private final List<Template> items = new ArrayList<>();
	private List<Template> original;

	/** A working copy of templates (they are copied). */
	public TemplateList(List<Template> templates) {
		reset(templates);
	}

	/** Starts again from these templates: nothing is changed then. */
	public void reset(List<Template> templates) {
		items.clear();
		for(Template t : templates) {
			items.add(t.copy());
		}
		items.sort(ORDER);
		original = copies(items);
	}

	/** The templates, in order (the list itself: don't change it). */
	public List<Template> items() {
		return items;
	}

	/** Copies of the templates, to save. */
	public List<Template> toSave() {
		return copies(items);
	}

	public int size() {
		return items.size();
	}

	public Template get(int i) {
		return items.get(i);
	}

	/** Adds a template; returns where it went. */
	public int add(Template t) {
		items.add(t.copy());
		items.sort(ORDER);
		return indexOf(t);
	}

	/** Replaces the template at i; returns where it is now (the order may change with its name). */
	public int set(int i, Template t) {
		items.set(i, t.copy());
		items.sort(ORDER);
		return indexOf(t);
	}

	public void remove(int i) {
		items.remove(i);
	}

	/**
	 * Puts back the built-in templates as they come: the deleted ones return and changed ones are
	 * replaced. The user's own templates stay.
	 */
	public void restoreDefaults() {
		items.removeIf(Configuration::isBuiltIn);
		items.addAll(Configuration.getDefaultTemplates());
		items.sort(ORDER);
	}

	/** True if anything was added, changed or removed since it was made or {@link #reset}. */
	public boolean isChanged() {
		return !items.equals(original);
	}

	/** Where t is (by its contents), or -1. */
	public int indexOf(Template t) {
		return items.indexOf(t);
	}

	/** What's wrong with a template so it can't be kept, or null: it needs a name. */
	public static String problem(Template t) {
		if( t.getName() == null || t.getName().isBlank() ) {
			return "A template needs a name: it's what you type to use it.";
		}
		if( t.getName().chars().anyMatch(Character::isWhitespace) ) {
			return "A template's name can't have spaces: it's what you type to use it.";
		}
		return null;
	}

	/**
	 * Whether a template's code, filled in with its fields' names, parses: null if it does, else
	 * where and why not ("Line 3: syntax error near unexpected token `fi'"). Some useful templates
	 * are only part of a script, so this is advice, not a rule.
	 */
	public static String check(Template t) {
		String code = t.getCode() == null || t.getCode().isBlank() ? t.getName() : t.getCode();
		if( code == null || code.isBlank() ) {
			return null;
		}
		List<CompileError> errors = new ArrayList<>();
		ScriptParser.parse(TemplateText.expand(code).text, errors);
		if( errors.isEmpty() ) {
			return null;
		}
		CompileError e = errors.get(0);
		return "Line " + e.line + ": " + e.msg;
	}

	private static List<Template> copies(List<Template> list) {
		List<Template> ret = new ArrayList<>();
		for(Template t : list) {
			ret.add(t.copy());
		}
		return ret;
	}
}
