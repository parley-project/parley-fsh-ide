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

import us.bringardner.fsh.ide.core.Breakpoint;
import us.bringardner.fsh.ide.core.Template;
import us.bringardner.fsh.ide.core.Configuration;
import us.bringardner.fsh.ide.core.CompileError;
import us.bringardner.fsh.ide.core.ScriptText;

import java.awt.BorderLayout;
import java.awt.Component;
import java.util.Collections;
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

import org.fife.ui.autocomplete.AutoCompletion;
import org.fife.ui.autocomplete.DefaultCompletionProvider;
import org.fife.ui.autocomplete.ShorthandCompletion;
import org.fife.ui.autocomplete.TemplateCompletion;
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
	private FshIDETextArea editorPane = new FshIDETextArea(200,200) {
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
	private Icon breakpointIcon = new ImageIcon(Toolkit.getDefaultToolkit().getImage(FshIDE.class.getResource("/img/eclipse_brkp_obj.png")));
	private Icon errorIcon      = new ImageIcon(Toolkit.getDefaultToolkit().getImage(FshIDE.class.getResource("/img/eclipse_err_obj.png")));

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
		

		editorPane.addKeyListener(new KeyAdapter() {
			private FindDialog findDialog;

			@Override

			public void keyPressed(KeyEvent e) {
				int c = e.getKeyCode();
				//System.out.println("Edtor.keyPressed c="+((int)c));
				boolean keep = true;
				if( e.isControlDown() || e.isMetaDown()) {
					// P = 16,  F = 6 ,   S = 19
					if( c == KeyEvent.VK_F) {// 6 == F
						if( findDialog == null ) {
							findDialog = new FindDialog();
						}
						if( !findDialog.isVisible()) {
							findDialog.showDialog(editorPane);
						}
						keep = false;
					} else if( c == KeyEvent.VK_G ) {
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
					} else if( c == KeyEvent.VK_N ) {
						actionNew();
						keep = false;
					} else if( c == KeyEvent.VK_P ) { // P
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
					} else if( c == KeyEvent.VK_O ) { // O
						actionOpen();
						keep = false;
					} else if( c == KeyEvent.VK_S ) { // S
						actionSave();
						keep = false;
					} else if( c == KeyEvent.VK_I ) { // I
						actionFormat();
						keep = false;
					} 
				} else if(e.isAltDown()) {
					if( c == KeyEvent.VK_F) {// 102 == F
						if( findDialog == null ) {
							findDialog = new FindDialog();
							findDialog.showDialog(editorPane);
						} else {
							findDialog.find();
						}
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


	
	
	

	private void actionNew() {
		firePropertyChange(ACTION_NEW, true, false);
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

		DefaultCompletionProvider provider = new DefaultCompletionProvider(){
			@Override
			protected boolean isValidChar(char ch) {
				return Character.isLetterOrDigit(ch) || ch=='_'  || ch=='.' || ch=='#' || ch=='$' ;
			}
		};

		
		for(Template t : Configuration.getInstance().getTemplates()) {
			addCompletion(provider, t);
		}


		for(String var : variables) {
			provider.addCompletion(new ShorthandCompletion(provider, var+"-variable", var));
		}

		
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				if(autoComplete == null ) {
					autoComplete = new AutoCompletion(provider); 
					autoComplete.setAutoCompleteEnabled(true);
					autoComplete.setParameterAssistanceEnabled(true);
					autoComplete.install(editorPane);
				} else {
					autoComplete.setCompletionProvider(provider);
				}
			}
		});

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
			provider.addCompletion(new TemplateCompletion(provider, name,d,code));
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






	private void actionSave() {
		firePropertyChange(ACTION_SAVE, true, false);

	}


	private void actionOpen() {
		firePropertyChange(ACTION_OPEN, true, false);

	}


	public void setText(String string, FileSource scriptFile) {
		editorPane.setText(string);
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

	FshIDETextArea getTextArea() {
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
