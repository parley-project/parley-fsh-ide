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

import java.awt.Component;
import java.awt.Point;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextAreaEditorKit;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;

import us.bringardner.fsh.ide.core.SpellChecking;



public class FshIDETextArea extends RSyntaxTextArea {
	private static final long serialVersionUID = 1L;
	
	public FshIDETextArea() {
		super();
		initMe();
	}

	public FshIDETextArea(int i, int j) {
		super(i, j);
		initMe();
	}

	
	/** The spell checker, once the dictionary has been read (in the background); null before. */
	private volatile FshSpellingParser spelling;
	/** where the right-click that opens the popup menu was, to find the word it was on */
	Point popupPoint;
	/** the spelling items at the top of the popup menu now, taken out the next time */
	private final List<Component> spellingItems = new ArrayList<>();

	/** whether to check spelling: kept here for the parser, which is made when the dictionary has been read */
	private volatile boolean spellChecking = true;

	/** Turns spell checking of comments and quoted text on or off. */
	public void setSpellChecking(boolean on) {
		spellChecking = on;
		FshSpellingParser sp = spelling;
		if( sp != null ) {
			sp.setChecking(on);
		}
	}

	public boolean isSpellChecking() {
		return spellChecking;
	}

	/** The spell checker for this text area, or null until the dictionary has been read. */
	FshSpellingParser getSpelling() {
		return spelling;
	}

	@Override
	protected void processMouseEvent(MouseEvent e) {
		// (before the listeners, one of which shows the popup menu: configurePopupMenu needs the point)
		if( e.isPopupTrigger() ) {
			popupPoint = e.getPoint();
		}
		super.processMouseEvent(e);
	}

	/** The popup menu with, when the click was on a misspelled word, the suggestions, Ignore and Add to Dictionary first. */
	@Override
	protected void configurePopupMenu(JPopupMenu popupMenu) {
		super.configurePopupMenu(popupMenu);
		for(Component c : spellingItems) {
			popupMenu.remove(c);
		}
		spellingItems.clear();
		FshSpellingParser sp = spelling;
		if( sp == null || popupPoint == null ) {
			return;
		}
		int pos = viewToModel2D(popupPoint);
		int[] word = pos < 0 ? null : sp.misspelledAt(pos);
		if( word == null ) {
			return;
		}
		String w;
		try {
			w = getText(word[0], word[1]-word[0]);
		} catch (BadLocationException e) {
			return;
		}
		List<Component> items = new ArrayList<>();
		List<String> suggestions = sp.suggestions(w, 6);
		for(String s : suggestions) {
			JMenuItem item = new JMenuItem(s);
			item.addActionListener(e->replaceRange(s, word[0], word[1]));
			items.add(item);
		}
		if( suggestions.isEmpty() ) {
			JMenuItem none = new JMenuItem("No suggestions");
			none.setEnabled(false);
			items.add(none);
		}
		items.add(new JPopupMenu.Separator());
		JMenuItem ignore = new JMenuItem("Ignore \""+w+"\"");
		ignore.addActionListener(e->sp.ignore(w));
		items.add(ignore);
		JMenuItem add = new JMenuItem("Add \""+w+"\" to Dictionary");
		add.addActionListener(e->{
			try {
				sp.addToDictionary(w);
			} catch (IOException ex) {
				sp.ignore(w);
			}
		});
		items.add(add);
		items.add(new JPopupMenu.Separator());
		for(int i=0; i < items.size(); i++) {
			popupMenu.insert(items.get(i), i);
		}
		spellingItems.addAll(items);
	}

	private void initMe() {
		 setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_UNIX_SHELL);
	     setCodeFoldingEnabled(true);
		foldKeys(this);
	       
		setLineWrap(true);
		setTabSize(4);
	
		// each text area needs its own parser (its ignored words), but they share the dictionary
		SpellChecking.english().thenAccept(dict->{
			if( dict != null ) {
				SwingUtilities.invokeLater(()->{
					FshSpellingParser parser = new FshSpellingParser(this, dict);
					parser.setChecking(spellChecking);
					spelling = parser;
					addParser(parser);
				});
			}
		});


		ActionMap aMap = getActionMap();
		InputMap inMap = getInputMap();

		String name = RSyntaxTextAreaEditorKit.rstaCollapseAllFoldsAction;
		Action a = new RSyntaxTextAreaEditorKit.CollapseAllFoldsAction();
		inMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK ),name);
		aMap.put(name,  a);

		name = RSyntaxTextAreaEditorKit.rstaExpandAllFoldsAction;
		a = new RSyntaxTextAreaEditorKit.ExpandAllFoldsAction();
		inMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ADD, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK ),name);
		aMap.put(name,  a);


		name = RSyntaxTextAreaEditorKit.rstaCollapseAllCommentFoldsAction;
		a = new RSyntaxTextAreaEditorKit.CollapseAllCommentFoldsAction();
		inMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK ),name);
		aMap.put(name,  a);

		
	}

	protected boolean highlightMatching(char start,char end, int pos, int inc,Document doc,boolean select)  {
		boolean ret = false;
		int cnt = 1;
		int len= doc.getLength();
		int startPos = pos;
		try {
			while( pos >=0 && pos < len) {
				char c = doc.getText(pos, 1).charAt(0);
				if( c == start) {
					cnt++;
				} else if( c == end) {
					if( --cnt == 0 ) {
						setCaretPosition(pos);
						if( select ) {
							select(startPos, pos);
						}
						return true;
					}
				}
				pos+=inc;
			}
		} catch(BadLocationException e) {
		}
		return ret;
	}

	/**
	 * Folding on [ and ] with the shortcut key and Alt (Option), as in the JavaFX IDE: fold and unfold
	 * at the caret, and with Shift all of them. The keypad's + and - are the text size (View menu),
	 * so RSyntaxTextArea's own keypad keys for the caret's fold no longer reach it; its keypad / and *
	 * (all) still do.
	 */
	static void foldKeys(org.fife.ui.rsyntaxtextarea.RSyntaxTextArea editor) {
		int keys = TextSizeKeys.shortcutKey() | java.awt.event.InputEvent.ALT_DOWN_MASK;
		int shift = java.awt.event.InputEvent.SHIFT_DOWN_MASK;
		javax.swing.InputMap in = editor.getInputMap();
		in.put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_OPEN_BRACKET, keys),
				org.fife.ui.rsyntaxtextarea.RSyntaxTextAreaEditorKit.rstaCollapseFoldAction);
		in.put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_CLOSE_BRACKET, keys),
				org.fife.ui.rsyntaxtextarea.RSyntaxTextAreaEditorKit.rstaExpandFoldAction);
		in.put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_OPEN_BRACKET, keys | shift),
				org.fife.ui.rsyntaxtextarea.RSyntaxTextAreaEditorKit.rstaCollapseAllFoldsAction);
		in.put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_CLOSE_BRACKET, keys | shift),
				org.fife.ui.rsyntaxtextarea.RSyntaxTextAreaEditorKit.rstaExpandAllFoldsAction);
	}
}
