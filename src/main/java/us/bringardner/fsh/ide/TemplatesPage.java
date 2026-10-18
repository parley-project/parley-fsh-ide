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
package us.bringardner.fsh.ide;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;

import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;

import us.bringardner.fsh.ide.core.Configuration;
import us.bringardner.fsh.ide.core.Template;
import us.bringardner.fsh.ide.core.TemplateList;
import us.bringardner.swing.dialog.SettingsPage;

/**
 * The Templates tab of Settings: the templates offered as you type (autocompletion), to change,
 * add to or delete, with Restore Defaults to put the built-in ones back. The changes are kept when
 * the dialog's OK is pressed, and the open editors offer them at once.
 */
public class TemplatesPage implements SettingsPage {

	/** The id it is registered under in swing-widgets' SettingsPages. */
	public static final String ID = "fsh-ide.templates";

	/** What a template's code can have, shown under it. */
	static final String HINT = "<html><div style='width:420px'>${name} is a field to fill in (fields with the same "
			+ "name change together), ${cursor} is where the caret goes, and $$ is a $.</div></html>";

	private final TemplateList templates = new TemplateList(Configuration.getInstance().getTemplates());
	private final DefaultListModel<Template> model = new DefaultListModel<>();
	private final JList<Template> list = new JList<>(model);
	private final JTextField name = new JTextField(16);
	private final JTextField description = new JTextField(30);
	private final RSyntaxTextArea code = new RSyntaxTextArea(10, 50);
	private final JLabel check = new JLabel(" ");
	private final JButton deleteButton = new JButton("Delete");
	private final JPanel panel = new JPanel(new BorderLayout(12, 0));
	/** True while the fields are being filled in from the list, so it isn't taken as an edit. */
	private boolean updating;

	public TemplatesPage() {
		build();
		fill();
		if( !model.isEmpty() ) {
			list.setSelectedIndex(0);
		}
	}

	@Override
	public String getTitle() {
		return "Templates";
	}

	@Override
	public JComponent getComponent() {
		return panel;
	}

	@Override
	public String problem() {
		for(int i = 0; i < templates.size(); i++) {
			String p = TemplateList.problem(templates.get(i));
			if( p != null ) {
				list.setSelectedIndex(i);
				return p;
			}
		}
		return null;
	}

	@Override
	public void ok() throws Exception {
		if( !templates.isChanged() ) {
			return;
		}
		Configuration c = Configuration.getInstance();
		c.setTemplates(templates.toSave());
		c.save();
		// the open editors offer the templates as they are now
		for(Window w : Window.getWindows()) {
			if( w instanceof FshIDE && w.isDisplayable() ) {
				((FshIDE) w).createAutoComplete();
			}
		}
	}

	// ---------------------------------------------------------------- actions

	/** New: a template to fill in, selected, with its name ready to type over. */
	public void actionNew() {
		String n = "new-template";
		for(int i = 2; contains(n); i++) {
			n = "new-template-" + i;
		}
		Template t = new Template(n, "", "");
		int at = templates.add(t);
		fill();
		list.setSelectedIndex(at);
		name.requestFocusInWindow();
		name.selectAll();
	}

	/** Delete: the selected template, once the user says so. */
	public void actionDelete() {
		int i = list.getSelectedIndex();
		if( i < 0 ) {
			return;
		}
		Template t = templates.get(i);
		String message = "Delete the template \"" + t.getName() + "\"?"
				+ (Configuration.isBuiltIn(t) ? "\nRestore Defaults brings back a built-in one." : "");
		if( JOptionPane.showConfirmDialog(panel, message, "Delete Template", JOptionPane.OK_CANCEL_OPTION,
				JOptionPane.WARNING_MESSAGE) == JOptionPane.OK_OPTION ) {
			delete(i);
		}
	}

	/** Deletes the template at i (no questions), and selects the one after it. */
	void delete(int i) {
		templates.remove(i);
		fill();
		if( !model.isEmpty() ) {
			list.setSelectedIndex(Math.min(i, model.size() - 1));
		} else {
			show(null);
		}
	}

	/** Restore Defaults: the built-in templates as they come, once the user says so. */
	public void actionRestoreDefaults() {
		if( JOptionPane.showConfirmDialog(panel,
				"Put the built-in templates back as they come?\nYour own templates stay.", "Restore Defaults",
				JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.OK_OPTION ) {
			restoreDefaults();
		}
	}

	void restoreDefaults() {
		templates.restoreDefaults();
		fill();
		if( !model.isEmpty() ) {
			list.setSelectedIndex(0);
		}
	}

	/** The working copy (for tests). */
	TemplateList templates() {
		return templates;
	}

	JList<Template> list() {
		return list;
	}

	JTextField nameField() {
		return name;
	}

	JTextField descriptionField() {
		return description;
	}

	RSyntaxTextArea codeArea() {
		return code;
	}

	JLabel checkLabel() {
		return check;
	}

	// ---------------------------------------------------------------- the parts

	private boolean contains(String n) {
		return templates.items().stream().anyMatch(t->n.equals(t.getName()));
	}

	/** The list from the working copy, keeping the selection where it can. */
	private void fill() {
		updating = true;
		model.clear();
		for(Template t : templates.items()) {
			model.addElement(t);
		}
		updating = false;
	}

	/** Shows a template in the fields (null: none, and they're disabled). */
	private void show(Template t) {
		updating = true;
		name.setText(t == null ? "" : t.getName());
		description.setText(t == null ? "" : t.getDescription());
		code.setText(t == null ? "" : t.getCode());
		code.setCaretPosition(0);
		code.discardAllEdits();
		name.setEnabled(t != null);
		description.setEnabled(t != null);
		code.setEnabled(t != null);
		deleteButton.setEnabled(t != null);
		updating = false;
		showCheck(t);
	}

	/** Whether the code parses, under it. */
	private void showCheck(Template t) {
		String c = t == null ? null : TemplateList.check(t);
		String p = t == null ? null : TemplateList.problem(t);
		check.setText(p != null ? "⚠ " + p : c == null ? (t == null ? " " : "✓ The code parses")
				: "⚠ " + c + " (fine if it's only part of a script)");
	}

	/** The fields changed: the selected template becomes what they say. */
	private void edited() {
		int i = list.getSelectedIndex();
		if( updating || i < 0 ) {
			return;
		}
		Template t = new Template(name.getText().trim(), description.getText().trim(), code.getText());
		int at = templates.set(i, t);
		updating = true;
		model.clear();
		for(Template x : templates.items()) {
			model.addElement(x);
		}
		list.setSelectedIndex(at);
		list.ensureIndexIsVisible(at);
		updating = false;
		showCheck(t);
	}

	private void build() {
		panel.setOpaque(false);
		panel.setBorder(new EmptyBorder(10, 10, 10, 10));

		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.setCellRenderer(new DefaultListCellRenderer() {
			private static final long serialVersionUID = 1L;

			@Override
			public Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean selected,
					boolean focus) {
				Template t = (Template) value;
				String d = t.getDescription() == null || t.getDescription().isBlank() || t.getDescription().equals(t.getName())
						? "" : "&nbsp;&nbsp;" + escape(t.getDescription());
				return super.getListCellRendererComponent(l, "<html><b>" + escape(t.getName()) + "</b>" + d + "</html>",
						index, selected, focus);
			}
		});
		list.addListSelectionListener(e->{
			if( !updating && !e.getValueIsAdjusting() ) {
				int i = list.getSelectedIndex();
				show(i < 0 ? null : templates.get(i));
			}
		});
		JScrollPane listScroll = new JScrollPane(list);
		listScroll.setPreferredSize(new Dimension(300, 380));

		JButton newButton = new JButton("New");
		newButton.setToolTipText("Add a template");
		newButton.addActionListener(e->actionNew());
		deleteButton.setToolTipText("Delete the selected template");
		deleteButton.addActionListener(e->actionDelete());
		JButton restore = new JButton("Restore Defaults");
		restore.setToolTipText("Put the built-in templates back as they come; your own stay");
		restore.addActionListener(e->actionRestoreDefaults());
		JPanel listButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		listButtons.setOpaque(false);
		listButtons.add(newButton);
		listButtons.add(deleteButton);
		listButtons.add(restore);

		JPanel left = new JPanel(new BorderLayout(0, 6));
		left.setOpaque(false);
		left.add(listScroll, BorderLayout.CENTER);
		left.add(listButtons, BorderLayout.SOUTH);
		panel.add(left, BorderLayout.WEST);

		name.setToolTipText("What you type to use the template");
		description.setToolTipText("Shown in the list of completions");
		code.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_UNIX_SHELL);
		code.setCodeFoldingEnabled(false);
		code.setTabSize(4);
		RTextScrollPane codeScroll = new RTextScrollPane(code);
		codeScroll.setPreferredSize(new Dimension(460, 300));
		EditorColors.apply(code);

		JPanel form = new JPanel(new GridBagLayout());
		form.setOpaque(false);
		GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(0, 0, 6, 8);
		c.anchor = GridBagConstraints.WEST;
		c.gridx = 0;
		c.gridy = 0;
		form.add(new JLabel("Name"), c);
		c.gridx = 1;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.weightx = 1;
		form.add(name, c);
		c.gridx = 0;
		c.gridy = 1;
		c.fill = GridBagConstraints.NONE;
		c.weightx = 0;
		form.add(new JLabel("Description"), c);
		c.gridx = 1;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.weightx = 1;
		form.add(description, c);
		c.gridx = 0;
		c.gridy = 2;
		c.gridwidth = 2;
		c.fill = GridBagConstraints.NONE;
		form.add(new JLabel("Code"), c);
		c.gridy = 3;
		c.fill = GridBagConstraints.BOTH;
		c.weighty = 1;
		form.add(codeScroll, c);
		c.gridy = 4;
		c.weighty = 0;
		c.fill = GridBagConstraints.HORIZONTAL;
		form.add(check, c);
		JLabel hint = new JLabel(HINT);
		hint.setFont(hint.getFont().deriveFont(Font.ITALIC, hint.getFont().getSize2D() - 1f));
		c.gridy = 5;
		c.insets = new Insets(0, 0, 0, 8);
		form.add(hint, c);
		panel.add(form, BorderLayout.CENTER);

		DocumentListener changes = new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e) {
				edited();
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				edited();
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				edited();
			}
		};
		name.getDocument().addDocumentListener(changes);
		description.getDocument().addDocumentListener(changes);
		code.getDocument().addDocumentListener(changes);
	}

	private static String escape(String s) {
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
