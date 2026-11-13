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
package us.bringardner.fsh.studio;

import us.bringardner.fsh.debug.Breakpoint;
import us.bringardner.fsh.debug.Template;
import us.bringardner.fsh.studio.core.CompileError;
import us.bringardner.fsh.studio.core.Configuration;
import us.bringardner.fsh.studio.core.TemplateText;
import us.bringardner.fsh.debug.ScriptText;

import java.awt.BorderLayout;
import java.awt.Component;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.fife.ui.rtextarea.Gutter;
import org.fife.ui.rtextarea.GutterIconInfo;
import org.fife.ui.rtextarea.IconRowHeader;
import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;

import org.fife.ui.autocomplete.AutoCompletion;
import org.fife.ui.autocomplete.BasicCompletion;
import org.fife.ui.autocomplete.Completion;
import org.fife.ui.autocomplete.CompletionProvider;
import org.fife.ui.autocomplete.DefaultCompletionProvider;
import org.fife.ui.autocomplete.ParameterChoicesProvider;
import org.fife.ui.autocomplete.ParameterizedCompletion;
import org.fife.ui.autocomplete.ShorthandCompletion;
import org.fife.ui.autocomplete.TemplateCompletion;
import org.fife.ui.autocomplete.Util;
import org.fife.ui.rsyntaxtextarea.SyntaxScheme;
import org.fife.ui.rtextarea.RTextScrollPane;

import us.bringardner.parley.files.FileSource;



public class EditorPanel extends JPanel {

	/* 
	 * Add edit polygon to editor
	 */
	private static final long serialVersionUID = 1L;

	public static final String ACTION_SAVE = "Save";
	public static final String ACTION_OPEN = "Open";
	public static final String ACTION_NEW = "New";
	
	public static interface BreakpointListner {
		void changed();
	}

	private List<BreakpointListner> breakpointListners = new ArrayList<EditorPanel.BreakpointListner>();

	private AutoCompletion autoComplete;
	private FshStudioTextArea editorPane = new FshStudioTextArea(200,200) {
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		@Override
		public void paste() {	
			Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
			Transferable content = cb.getContents(this);
			try {
				Object df = content.getTransferData(DataFlavor.stringFlavor);
				//System.out.println("df="+df.getClass());
				if (df instanceof String) {
					String str = (String) df;
					String cleaned = ScriptText.clean(str);
					if( !cleaned.equals(str)) {
						cb.setContents(new StringSelection(cleaned),null);
					}
				}

			} catch (UnsupportedFlavorException | IOException e) {
			}
			super.paste();
		}
	};

	private List<String> variables = new ArrayList<>();
	private RTextScrollPane scrollPane;
	
	private FileSource scriptDir;
	private Icon breakpointIcon = new ImageIcon(Toolkit.getDefaultToolkit().getImage(FshStudio.class.getResource("/img/breakpoint.png")));
	private Icon errorIcon      = new ImageIcon(Toolkit.getDefaultToolkit().getImage(FshStudio.class.getResource("/img/error.png")));

	private Gutter gutter;
	// Edited on the EDT only. Each breakpoint's gutter icon tracks its line as the text changes.
	private final Map<Breakpoint,GutterIconInfo> breakpointTags = new LinkedHashMap<>();
	// Line -> breakpoint, rebuilt on the EDT after each edit; the script thread reads it
	private volatile Map<Integer,Breakpoint> breakpoints = Collections.emptyMap();
	private final List<GutterIconInfo> errorTags = new ArrayList<>();


	
	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {




		EditorPanel frame = new EditorPanel();

		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					frame.setVisible(true);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	public Object addLineHighlight(int line,Color color) throws BadLocationException {
		
		return editorPane.addLineHighlight(line, color);
	}
	public void removeLineHighlight(Object id) {
		editorPane.removeLineHighlight(id);
	}
	public void removeAllLineHighlights() {
		editorPane.removeAllLineHighlights();
		removeStatementHighlight();
	}

	/**
	 * Scrolls so line (0-based) shows, with lines around it, if it doesn't already (a folded
	 * block it's in is opened); a line that shows is left where it is. The caret and the
	 * selection stay as they are. The debugger shows the line a script stopped on with it.
	 */
	public void showLine(int line) {
		try {
			if( line < 0 || line >= editorPane.getLineCount()) {
				return;
			}
			int offset = editorPane.getLineStartOffset(line);
			editorPane.getFoldManager().ensureOffsetNotInClosedFold(offset);
			java.awt.geom.Rectangle2D at = editorPane.modelToView2D(offset);
			if( at == null ) {
				// (not laid out yet)
				return;
			}
			java.awt.Rectangle visible = editorPane.getVisibleRect();
			int top = (int) at.getY();
			int height = (int) Math.ceil(at.getHeight());
			if( top >= visible.y && top+height <= visible.y+visible.height ) {
				return;
			}
			// in the middle of the editor (sideways, where it is)
			int y = Math.max(0, top-(visible.height-height)/2);
			editorPane.scrollRectToVisible(new java.awt.Rectangle(visible.x, y, 1, visible.height));
		} catch (BadLocationException e) {
			// no such line: nothing to show
		}
	}

	/** for the tests: true if all of line (0-based) shows in the editor */
	boolean isLineShowing(int line) {
		try {
			java.awt.geom.Rectangle2D at = editorPane.modelToView2D(editorPane.getLineStartOffset(line));
			java.awt.Rectangle visible = editorPane.getVisibleRect();
			return at != null && at.getY() >= visible.y && at.getY()+at.getHeight() <= visible.y+visible.height;
		} catch (BadLocationException e) {
			return false;
		}
	}

	/** for the tests: how far down the editor is scrolled */
	int scrolledTo() {
		return editorPane.getVisibleRect().y;
	}

	// the statement highlighted (where Step Into stopped), or null
	private Object statementHighlight;

	/**
	 * Highlights just a statement on line (0-based): length characters from column, if the line
	 * still has text there (otherwise nothing is highlighted).
	 * @return false if it couldn't be (the line has changed): highlight the whole line instead
	 */
	public boolean addStatementHighlight(int line, int column, int length, String text, Color color) {
		removeStatementHighlight();
		try {
			if( line < 0 || line >= editorPane.getLineCount()) {
				return false;
			}
			int start = editorPane.getLineStartOffset(line)+column;
			int end = start+length;
			if( column < 0 || end > editorPane.getLineEndOffset(line) || !editorPane.getText(start, length).equals(text)) {
				return false;
			}
			statementHighlight = editorPane.getHighlighter().addHighlight(start, end,
					new javax.swing.text.DefaultHighlighter.DefaultHighlightPainter(color));
			return true;
		} catch (BadLocationException e) {
			return false;
		}
	}

	/** for the tests: the statement highlighted (start and end in the text), or null */
	int[] getStatementHighlight() {
		if( statementHighlight == null ) {
			return null;
		}
		javax.swing.text.Highlighter.Highlight h = (javax.swing.text.Highlighter.Highlight) statementHighlight;
		return new int[] {h.getStartOffset(), h.getEndOffset()};
	}

	/** Removes the statement highlight (see addStatementHighlight). */
	public void removeStatementHighlight() {
		if( statementHighlight != null ) {
			editorPane.getHighlighter().removeHighlight(statementHighlight);
			statementHighlight = null;
		}
	}
	
	public void addBreapointListner(BreakpointListner l) {
		breakpointListners.add(l);
	}

	public boolean removeBreapointListner(BreakpointListner l) {
		return breakpointListners.remove(l);
	}

	public void clearAllBreeakpointLisners() {
		breakpointListners.clear();
	}


	@Override
	public void setBackground(Color bg) {
		super.setBackground(bg);
		if( editorPane != null) {
			editorPane.setBackground(bg);
		}		
	}


	/** Breakpoints by (0-based) line. A snapshot, safe to read from any thread. */
	public Map<Integer, Breakpoint> getBreakpoints() {
		return breakpoints;
	}

	/** The breakpoint on line (0-based), or null. */
	public Breakpoint getBreakpoint(int line) {
		return breakpoints.get(line);
	}

	/** Adds a breakpoint on line (0-based) unless the line is blank or already has one. */
	public Breakpoint addBreakpoint(int line) {
		Breakpoint bp = breakpoints.get(line);
		if( bp != null ) {
			return bp;
		}
		String code = "";
		try {
			int start = editorPane.getLineStartOffset(line);
			int end = editorPane.getLineEndOffset(line);
			code = editorPane.getText(start, end-start).trim();
			if( code.isEmpty()) {
				return null;
			}
			GutterIconInfo tag = gutter.addLineTrackingIcon(line, breakpointIcon);
			bp = new Breakpoint();
			breakpointTags.put(bp, tag);
		} catch (BadLocationException e) {
			return null;
		}
		bp.setCode(code);
		bp.setLine(line);
		refreshBreakpoints();
		fireBreakpointsChanged();
		return bp;
	}

	public void removeBreakpoint(Breakpoint bp) {
		GutterIconInfo tag = breakpointTags.remove(bp);
		if( tag != null ) {
			gutter.removeTrackingIcon(tag);
			refreshBreakpoints();
			fireBreakpointsChanged();
		}
	}

	private void fireBreakpointsChanged() {
		for(BreakpointListner l : breakpointListners) {
			l.changed();
		}
	}

	/**
	 * Moves each breakpoint to the line its icon is now on. Two that end up on the same
	 * line (the text between them was deleted) become one.
	 * @return true if any breakpoint changed line
	 */
	private boolean refreshBreakpoints() {
		boolean changed = false;
		Map<Integer,Breakpoint> map = new TreeMap<>();
		for(Iterator<Map.Entry<Breakpoint,GutterIconInfo>> it = breakpointTags.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<Breakpoint,GutterIconInfo> e = it.next();
			Breakpoint bp = e.getKey();
			int line;
			try {
				line = editorPane.getLineOfOffset(e.getValue().getMarkedOffset());
			} catch (BadLocationException ex) {
				line = -1;
			}
			if( line < 0 || map.containsKey(line)) {
				gutter.removeTrackingIcon(e.getValue());
				it.remove();
				changed = true;
				continue;
			}
			if( line != bp.getLine()) {
				bp.setLine(line);
				changed = true;
			}
			map.put(line, bp);
		}
		breakpoints = Collections.unmodifiableMap(map);
		return changed;
	}

	private int lineAt(Point p) {
		int offs = editorPane.viewToModel2D(new Point(0, p.y));
		try {
			return offs < 0 ? -1 : editorPane.getLineOfOffset(offs);
		} catch (BadLocationException e) {
			return -1;
		}
	}

	/** The compile error message on line (0-based), or null. */
	String errorAt(int line) {
		for(GutterIconInfo tag : errorTags) {
			try {
				if( editorPane.getLineOfOffset(tag.getMarkedOffset()) == line) {
					return tag.getToolTip();
				}
			} catch (BadLocationException e) {
			}
		}
		return null;
	}

	private void gutterClicked(MouseEvent e) {
		int line = lineAt(e.getPoint());
		if( line < 0 ) {
			return;
		}
		Breakpoint bp = breakpoints.get(line);
		if( SwingUtilities.isRightMouseButton(e) || e.isPopupTrigger()) {
			if( bp != null) {
				BreakpointPropertiesDialog d = new BreakpointPropertiesDialog();
				Point p = e.getLocationOnScreen();
				d.showDialog(bp, gutter, p.x, p.y);
				if( d.isDelete()) {
					removeBreakpoint(bp);
				} else {
					fireBreakpointsChanged();
				}
			}
			return;
		}
		String err = errorAt(line);
		if( err != null ) {
			JOptionPane.showMessageDialog(gutter, err, "Compile Error: ", JOptionPane.ERROR_MESSAGE);
		} else if( bp != null ) {
			removeBreakpoint(bp);
		} else {
			addBreakpoint(line);
		}
	}

	/**
	 * Create the frame.
	 */
	public EditorPanel() {
		editorPane.setAutoscrolls(true);
		editorPane.setLineWrap(true);
		editorPane.setCodeFoldingEnabled(true);

		setLayout(new BorderLayout(0, 0));

		scrollPane = new RTextScrollPane(editorPane);
		// the editor's colors follow the theme (a dark theme gives a dark editor)
		EditorColors.apply(editorPane);
		scrollPane.setIconRowHeaderEnabled(true);
		scrollPane.setLineNumbersEnabled(true);
		scrollPane.setFoldIndicatorEnabled(true);
		scrollPane.setWheelScrollingEnabled(true);
		scrollPane.setAutoscrolls(true);

		// Breakpoints and compile errors are gutter icons, which follow their line as text
		// is inserted or removed above them (and are drawn in the right place when lines wrap).
		gutter = scrollPane.getGutter();
		MouseAdapter gutterMouse = new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				gutterClicked(e);
			}
		};
		boolean found = false;
		for(Component c : gutter.getComponents()) {
			if( c instanceof IconRowHeader) {
				c.addMouseListener(gutterMouse);
				found = true;
			}
		}
		if( !found ) {
			gutter.addMouseListener(gutterMouse);
		}
		editorPane.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e) {
				textChanged();
			}
			@Override
			public void removeUpdate(DocumentEvent e) {
				textChanged();
			}
			@Override
			public void changedUpdate(DocumentEvent e) {
			}
		});

		add(scrollPane, BorderLayout.CENTER);
		createAutoComplete();
		

		// ⌘F, ⌘G, ⌘L, ⌘N, ⌘O and ⌘S are the window's menus (Edit and File); the editor keeps ⌘P
		// (the matching brace) and ⌘I (format), and Alt-F finds the next match
		editorPane.addKeyListener(new KeyAdapter() {

			@Override

			public void keyPressed(KeyEvent e) {
				int c = e.getKeyCode();
				boolean keep = true;
				if( e.isControlDown() || e.isMetaDown()) {
					if( c == KeyEvent.VK_P ) { // P
						keep = false;
						Document doc = editorPane.getDocument();
						int dot = editorPane.getCaretPosition();
						String left = null;
						String right = null;
						int ch = -1;
						try {right = doc.getText(dot, 1);	ch = right.charAt(0);} catch (BadLocationException e1) {			}
						if( ch != '{' && ch != '}') {
							try {left = doc.getText(--dot, 1);	ch = left.charAt(0);} catch (BadLocationException e1) {			}
						}
						switch (ch) {
						case '{':
							highlightMatching((char)ch, '}', dot+1, 1, doc,e.isShiftDown());
							break;
						case '}':
							highlightMatching((char)ch, '{', dot-1, -1, doc,e.isShiftDown());
							break;

						}
					} else if( c == KeyEvent.VK_I ) { // I
						actionFormat();
						keep = false;
					} 
				} else if(e.isAltDown()) {
					if( c == KeyEvent.VK_F) {
						findNext();
						keep = false;
					}
				}
				if( keep ) {
					super.keyPressed(e);
				} else {
					e.consume();
				}
			}

		
		});

	}

	private void textChanged() {
		if( !breakpointTags.isEmpty() && refreshBreakpoints()) {
			fireBreakpointsChanged();
		}
	}


	
	
	

	private FindDialog findDialog;

	/** Edit > Find...: the find dialog, on this editor. */
	public void showFind() {
		showFind(false);
	}

	/** Edit > Replace...: the find dialog, with the cursor in its Replace box. */
	public void showReplace() {
		showFind(true);
	}

	private void showFind(boolean replace) {
		if( findDialog == null ) {
			findDialog = new FindDialog();
		}
		findDialog.showDialog(editorPane, replace);
	}

	/** Edit > Find Next: the next match of what was last looked for (or the find dialog, the first time). */
	public void findNext() {
		find(true);
	}

	/** Edit > Find Previous: the match before the selection (or the find dialog, the first time). */
	public void findPrevious() {
		find(false);
	}

	private void find(boolean forward) {
		if( findDialog == null ) {
			showFind();
		} else {
			findDialog.find(forward);
		}
	}

	/** Edit > Complete: the completions (templates and the script's variables) for the word at the caret. */
	public void complete() {
		if( autoComplete != null ) {
			autoComplete.doCompletion();
		}
	}

	/** Edit > Go to Line...: asks for a line number and moves the caret there. */
	public void goToLine() {
		GoToDialog dialog = new GoToDialog();
		Point loc3 = editorPane.getLocationOnScreen();
		Point loc = editorPane.getCaret().getMagicCaretPosition();
		if( loc != null ) {
			loc3.x+=loc.x;
			loc3.y+=loc.y;
		}
		dialog.setLocation(loc3);
		int line = dialog.getLine();
		if( line >0) {
			try {
				editorPane.setCaretPosition(editorPane.getLineStartOffset(line-1));
			} catch (BadLocationException e1) {
			}
		}
	}






	private void actionFormat() {
		String text = editorPane.getSelectedText();
		if( text != null && !text.isEmpty()) {
			int start = editorPane.getSelectionStart();
			int end   = editorPane.getSelectionEnd();

			Document doc = editorPane.getDocument();
			//System.out.println("dco class="+doc.getClass().getName());
			try {
				doc.remove(start, end-start);
				doc.insertString(start, CodeFormatter.format(text), null);
			} catch (BadLocationException e) {
				logError(e, "insert formatted code");
			}			
		}
	}




	public void createAutoComplete()  {

		DefaultCompletionProvider provider = completionProvider(Configuration.getInstance().getTemplates(), variables);

		
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				if(autoComplete == null ) {
					autoComplete = new AutoCompletion(provider); 
					autoComplete.setAutoCompleteEnabled(true);
					autoComplete.setParameterAssistanceEnabled(true);
					autoComplete.setParamChoicesRenderer(new ChoiceRenderer());
					autoComplete.install(editorPane);
				} else {
					autoComplete.setCompletionProvider(provider);
				}
			}
		});

	}

	/**
	 * The completions for the templates and the script's variables. The library keeps its list
	 * sorted by what each completion shows (a template's description) but finds what matches the
	 * typed text by name, so once they're all in, they're sorted by name: otherwise the matches stop
	 * at the first one whose description sorts among them but whose name doesn't ("if" offered only
	 * a few of the if- templates, up to elif's "if-elif-else").
	 */
	static DefaultCompletionProvider completionProvider(List<Template> templates, List<String> variables) {
		FshCompletionProvider provider = new FshCompletionProvider();
		for(Template t : templates) {
			addCompletion(provider, t);
		}
		for(String var : variables) {
			provider.addCompletion(new ShorthandCompletion(provider, var+"-variable", var));
		}
		provider.sortByName();
		return provider;
	}

	/**
	 * fsh's words (with $, # and .), its completions sorted by name ({@link #sortByName()}), and
	 * the lists for template fields with choices ("${|-e : exists|-f : a regular file}"): when
	 * such a field is the one being filled in, the library shows its choices under it.
	 */
	static class FshCompletionProvider extends DefaultCompletionProvider implements ParameterChoicesProvider {
		private final Map<ParameterizedCompletion.Parameter, List<Completion>> choices = new IdentityHashMap<>();

		FshCompletionProvider() {
			setParameterChoicesProvider(this);
		}

		@Override
		protected boolean isValidChar(char ch) {
			return Character.isLetterOrDigit(ch) || ch=='_'  || ch=='.' || ch=='#' || ch=='$' ;
		}

		/**
		 * The word before the caret: with its hyphens ("if-fi") when a completion's name starts with
		 * that, else as the library takes it (after the last hyphen: "x-co" is "co").
		 */
		@Override
		public String getAlreadyEnteredText(JTextComponent comp) {
			String word = super.getAlreadyEnteredText(comp);
			try {
				int caret = comp.getCaretPosition();
				int start = caret-word.length();
				javax.swing.text.Document doc = comp.getDocument();
				while( start > 0 ) {
					char c = doc.getText(start-1, 1).charAt(0);
					if( isValidChar(c) || c == '-' ) {
						start--;
					} else {
						break;
					}
				}
				String hyphenated = doc.getText(start, caret-start);
				if( hyphenated.length() > word.length() && !hyphenated.startsWith("-")) {
					for(Completion c : completions) {
						if( Util.startsWithIgnoreCase(c.getInputText(), hyphenated)) {
							return hyphenated;
						}
					}
				}
			} catch (javax.swing.text.BadLocationException e) {
				// as the library takes it
			}
			return word;
		}

		/** sorted as the library looks them up: by name, ignoring case */
		@SuppressWarnings("unchecked")
		void sortByName() {
			completions.sort(comparator);
		}

		@Override
		public List<Completion> getParameterChoices(JTextComponent tc, ParameterizedCompletion.Parameter param) {
			return choices.get(param);
		}

		/**
		 * Gives tc's fields their choices from the template's code. The library makes one
		 * parameter of each name (later fields with the name copy it), so the fields are counted
		 * that way.
		 */
		void addChoices(TemplateCompletion tc, String code) {
			TemplateText.Expansion e = TemplateText.expand(code);
			java.util.Set<String> names = new java.util.HashSet<>();
			int param = 0;
			for(int i = 0; i < e.fields.size(); i++) {
				int[] f = e.fields.get(i);
				if( !names.add(e.text.substring(f[0], f[1]))) {
					continue;
				}
				List<TemplateText.Choice> list = e.choices.get(i);
				if( list != null && param < tc.getParamCount()) {
					List<Completion> items = new ArrayList<>();
					for(int n = 0; n < list.size(); n++) {
						TemplateText.Choice c = list.get(n);
						ChoiceCompletion item = new ChoiceCompletion(this, c.value, c.label);
						item.setRelevance(list.size()-n);	// (the list is sorted by relevance: in the template's order)
						items.add(item);
					}
					choices.put(tc.getParam(param), items);
				}
				param++;
			}
		}
	}

	/**
	 * One of a template field's choices. The library puts in what toString() gives, so that's
	 * the value alone; the list shows what it means beside it ({@link ChoiceRenderer}).
	 */
	static class ChoiceCompletion extends BasicCompletion {
		ChoiceCompletion(CompletionProvider provider, String value, String label) {
			super(provider, value, label == null || label.isEmpty() ? null : label);
		}

		@Override
		public String toString() {
			return getReplacementText();
		}
	}

	/** A field's choices as the list shows them: the value, then what it means. */
	static class ChoiceRenderer extends javax.swing.DefaultListCellRenderer {
		private static final long serialVersionUID = 1L;

		@Override
		public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index,
				boolean selected, boolean focus) {
			Object shown = value;
			if( value instanceof ChoiceCompletion ) {
				ChoiceCompletion c = (ChoiceCompletion) value;
				shown = c.getShortDescription() == null ? c.getReplacementText()
						: c.getReplacementText() + " \u2014 " + c.getShortDescription();
			}
			return super.getListCellRendererComponent(list, shown, index, selected, focus);
		}
	}

	/** Adds t to provider: a template if its code has \${...} fields, otherwise a shorthand. */
	static void addCompletion(DefaultCompletionProvider provider, Template t) {
		String name = t.getName();
		String code = t.getCode();
		String description = t.getDescription();
		String d = description == null || description.isBlank() ? name : description;
		if( code == null || code.isBlank()) {
			provider.addCompletion(new ShorthandCompletion(provider,name,name,d));
		} else if( code.indexOf("$") >= 0) {
			TemplateCompletion tc = new TemplateCompletion(provider, name, d, TemplateText.withoutChoices(code));
			provider.addCompletion(tc);
			if( provider instanceof FshCompletionProvider ) {
				((FshCompletionProvider) provider).addChoices(tc, code);
			}
		} else {
			provider.addCompletion(new ShorthandCompletion(provider,name,code,d));
		}
	}

	private boolean highlightMatching(char start,char end, int pos, int inc,Document doc,boolean select)  {
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
						editorPane.setCaretPosition(pos);
						if( select ) {
							editorPane.select(startPos, pos);
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



	public void logError(Throwable error, String title) {
		//	error.printStackTrace();
		if(SwingUtilities.isEventDispatchThread()) {
			JOptionPane.showMessageDialog(this, error.toString(), title, JOptionPane.ERROR_MESSAGE);
		} else {
			SwingUtilities.invokeLater(new Runnable() {

				@Override
				public void run() {
					JOptionPane.showMessageDialog(EditorPanel.this, error.toString(), title, JOptionPane.ERROR_MESSAGE);
				}
			});
		}
	}










	// a script is running: the text can't be changed (see setEditable)
	private boolean locked;

	/**
	 * Whether the text can be changed: false while a script runs, since a change wouldn't reach
	 * the running script and would move its lines away from the ones the debugger shows. Then
	 * nothing changes it (typing, paste, undo, replace, a template, a completion ...); it can
	 * still be scrolled, selected, copied and searched, and setText still loads a script.
	 */
	public void setEditable(boolean editable) {
		locked = !editable;
		editorPane.setEditable(editable);
		javax.swing.text.Document doc = editorPane.getDocument();
		if( doc instanceof javax.swing.text.AbstractDocument && ((javax.swing.text.AbstractDocument) doc).getDocumentFilter() != lock ) {
			((javax.swing.text.AbstractDocument) doc).setDocumentFilter(lock);
		}
	}

	public boolean isEditable() {
		return !locked;
	}

	// refuses every change to the text while it's locked
	private final javax.swing.text.DocumentFilter lock = new javax.swing.text.DocumentFilter() {
		@Override
		public void insertString(FilterBypass fb, int offset, String string, javax.swing.text.AttributeSet attr) throws BadLocationException {
			if( !locked ) {
				super.insertString(fb, offset, string, attr);
			}
		}

		@Override
		public void remove(FilterBypass fb, int offset, int length) throws BadLocationException {
			if( !locked ) {
				super.remove(fb, offset, length);
			}
		}

		@Override
		public void replace(FilterBypass fb, int offset, int length, String text, javax.swing.text.AttributeSet attrs) throws BadLocationException {
			if( !locked ) {
				super.replace(fb, offset, length, text, attrs);
			}
		}
	};

	/** See FshStudioTextArea.setPopupItems: the right-click menu's items in place of the edit menu's. */
	public void setPopupItems(java.util.function.IntFunction<java.util.List<java.awt.Component>> provider) {
		editorPane.setPopupItems(provider);
	}

	/** The line (0-based) offset is on, or -1. */
	public int getLineOfOffset(int offset) {
		try {
			return editorPane.getLineOfOffset(offset);
		} catch (BadLocationException e) {
			return -1;
		}
	}

	/** Copies the selected text to the clipboard. */
	public void copy() {
		editorPane.copy();
	}

	/** Adds a breakpoint on line (0-based), or removes the one there. */
	public void toggleBreakpoint(int line) {
		Breakpoint bp = breakpoints.get(line);
		if( bp != null ) {
			removeBreakpoint(bp);
		} else {
			addBreakpoint(line);
		}
	}

	/** Shows a breakpoint's dialog (its condition and hit count), by the mouse pointer; it may delete the breakpoint. */
	public void editBreakpoint(Breakpoint bp) {
		BreakpointPropertiesDialog d = new BreakpointPropertiesDialog();
		java.awt.PointerInfo pointer = java.awt.MouseInfo.getPointerInfo();
		Point p = pointer == null ? editorPane.getLocationOnScreen() : pointer.getLocation();
		d.showDialog(bp, editorPane, p.x, p.y);
		if( d.isDelete()) {
			removeBreakpoint(bp);
		} else {
			fireBreakpointsChanged();
		}
	}

	public void setText(String string, FileSource scriptFile) {
		// (a script is loaded even while the text can't be edited)
		boolean wasLocked = locked;
		locked = false;
		try {
			editorPane.setText(string);
		} finally {
			locked = wasLocked;
		}
		editorPane.setCaretPosition(0);
		editorPane.removeAllLineHighlights();
		gutter.removeAllTrackingIcons();
		breakpointTags.clear();
		errorTags.clear();
		breakpoints = Collections.emptyMap();
		scriptDir = scriptFile;
		try {
			if( scriptDir != null && scriptDir.isFile()) {
				scriptDir = scriptDir.getParentFile();
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
		fireBreakpointsChanged();
	}

	public void addDocumentListener(DocumentListener l) {
		editorPane.getDocument().addDocumentListener(l);
	}

	FshStudioTextArea getTextArea() {
		return editorPane;
	}

	public String getText() {
		return editorPane.getText();
	}

	/** The (0-based) line the selection starts on. */
	public int getSelectionStartLine() {
		try {
			return editorPane.getLineOfOffset(editorPane.getSelectionStart());
		} catch (BadLocationException e) {
			return 0;
		}
	}

	public String getSelectedText() {
		String ret = editorPane.getSelectedText();
		return ret;
	}

	public void appendMenu(JMenuItem item) {
		JPopupMenu m =editorPane.getPopupMenu();
		m.add(item);				
	}

	
	public int getLineStartOffset(int line) {
		try {
			return editorPane.getLineStartOffset(line);
		} catch (BadLocationException e) {
		}
		return -1;
	}


	public void setCeretPosotionFromLine(int line) {
		try {			
			int offset = editorPane.getLineStartOffset(line);
			editorPane.setCaretPosition(offset);
		} catch (BadLocationException e) {
		}		
	}

	public void setHighlightCurrentLine(boolean b) {
		editorPane.setHighlightCurrentLine(b);
	}

	

	public Color getCurrentLineHighlightColor() {
		return editorPane.getCurrentLineHighlightColor();
	}

	public void setCeretPosotionFromOffset(int offset) {
		editorPane.setCaretPosition(offset);
	}

	public void setTabSize(int size) {		
		editorPane.setTabSize(size);		
	}
	
	public int getTabSize() {
		return editorPane.getTabSize();
	}
	
	@Override
	public void setFont(Font font) {		
		super.setFont(font);
		if( editorPane != null ) {
			editorPane.setFont(font);
		}
	}
	
	public void setLineWrap(boolean b) {
		editorPane.setLineWrap(b);
	}
	
	public boolean isLineWrap() {
		return editorPane.getLineWrap();
	}
	
	public SyntaxScheme getSyntaxScheme() {
		return editorPane.getSyntaxScheme();
	}
	
	public void setSyntaxScheme(SyntaxScheme scheme) {
		editorPane.setSyntaxScheme(scheme);
	}

	/** Call on the EDT. error.line is 1-based. */
	public void addErrorMarker(CompileError error) {
		int line = error.line-1;
		if( line >= 0 && line < editorPane.getLineCount()) {
			try {
				errorTags.add(gutter.addLineTrackingIcon(line, errorIcon, error.msg));
			} catch (BadLocationException e) {
			}
		}
	}

	/** Call on the EDT. */
	public void clearErrorMarkers() {
		for(GutterIconInfo tag : errorTags) {
			gutter.removeTrackingIcon(tag);
		}
		errorTags.clear();
	}
}
