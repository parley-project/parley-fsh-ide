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
package us.bringardner.fsh.ide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import javax.swing.JTextArea;

import org.fife.ui.autocomplete.Completion;
import org.fife.ui.autocomplete.DefaultCompletionProvider;
import org.fife.ui.autocomplete.SortByRelevanceComparator;
import org.fife.ui.autocomplete.TemplateCompletion;
import org.junit.jupiter.api.Test;

import us.bringardner.fsh.ide.core.Configuration;
import us.bringardner.fsh.ide.core.Template;

public class TestEditorCompletions {

	private static List<Completion> offered(DefaultCompletionProvider provider, String typed) {
		JTextArea text = new JTextArea(typed);
		text.setCaretPosition(typed.length());
		return provider.getCompletions(text);
	}

	/** what's typed offers every template whose name starts with it (they were cut off at elif's "if-elif-else") */
	@Test
	public void everyTemplateStartingWithWhatsTypedIsOffered() {
		List<Template> templates = Configuration.createDefault().getTemplates();
		DefaultCompletionProvider provider = EditorPanel.completionProvider(templates, List.of("ifCount"));
		for(String typed : new String[] {"if", "for", "case", "e", "s", "p", "r"}) {
			long names = templates.stream().filter(t->t.getName().toLowerCase().startsWith(typed)).count()
					+ ("ifCount-variable".startsWith(typed) ? 1 : 0);
			assertEquals(names, offered(provider, typed).size(), typed);
		}
		assertEquals(1, offered(provider, "ifC").size(), "the variable");
	}

	/** a template's name with hyphens is one word to complete, a hyphen elsewhere isn't */
	@Test
	public void hyphenatedNamesComplete() {
		DefaultCompletionProvider provider = EditorPanel.completionProvider(Configuration.createDefault().getTemplates(), List.of("count"));
		List<String> names = new java.util.ArrayList<>();
		offered(provider, "if-fi").forEach(c->names.add(c.getInputText()));
		assertEquals(List.of("if-file", "if-files"), names);
		names.clear();
		offered(provider, "echo $((x-co").forEach(c->names.add(c.getInputText()));
		assertTrue(names.contains("count-variable"), "the word after the minus: " + names);
	}

	/** a field with choices gets them as the library's parameter choices, in the template's order */
	@Test
	public void aFieldWithChoicesListsThem() {
		List<Template> templates = Configuration.createDefault().getTemplates();
		DefaultCompletionProvider provider = EditorPanel.completionProvider(templates, List.of());
		for(String[] t : new String[][] {{"if-file", "0", "-e", "19"}, {"if-number", "1", "-eq", "6"}, {"if-logic", "1", "&&", "2"}}) {
			TemplateCompletion tc = (TemplateCompletion) offered(provider, t[0]).stream()
					.filter(c->c.getInputText().equals(t[0])).findFirst().get();
			int field = Integer.parseInt(t[1]);
			List<Completion> choices = provider.getParameterChoicesProvider().getParameterChoices(null, tc.getParam(field));
			assertNotNull(choices, t[0]);
			assertEquals(Integer.parseInt(t[3]), choices.size(), t[0]);
			List<Completion> sorted = new java.util.ArrayList<>(choices);
			sorted.sort(new SortByRelevanceComparator());
			assertEquals(t[2], sorted.get(0).getReplacementText(), t[0] + ": the first, as the template has them");
			for(int i = 0; i < tc.getParamCount(); i++) {
				if( i != field ) {
					assertNull(provider.getParameterChoicesProvider().getParameterChoices(null, tc.getParam(i)), t[0] + " " + i);
				}
			}
		}
		TemplateCompletion logic = (TemplateCompletion) offered(provider, "if-logic").stream()
				.filter(c->c.getInputText().equals("if-logic")).findFirst().get();
		List<Completion> or = provider.getParameterChoicesProvider().getParameterChoices(null, logic.getParam(1));
		assertTrue(or.stream().anyMatch(c->c.getReplacementText().equals("||")));
	}
}
