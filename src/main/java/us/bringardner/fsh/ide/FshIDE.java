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

import us.bringardner.swing.dialog.SettingsDialog;
import us.bringardner.swing.gradient.GradientLook;
import us.bringardner.swing.gradient.ThemeExtensions;
import us.bringardner.swing.menu.StandardMenus;
import us.bringardner.fsh.ide.core.Breakpoint;
import us.bringardner.fsh.ide.core.ScriptOutline;
import us.bringardner.fsh.ide.core.Configuration;
import us.bringardner.fsh.ide.core.CompileError;
import us.bringardner.fsh.ide.core.DebugSession;
import us.bringardner.fsh.ide.core.ExitChoice;
import us.bringardner.fsh.ide.core.LogBatcher;
import us.bringardner.fsh.ide.core.RecentFiles;
import us.bringardner.fsh.ide.core.RunSettings;
import us.bringardner.fsh.ide.core.RunSettingsStore;
import us.bringardner.fsh.ide.core.ScriptDocument;
import us.bringardner.fsh.ide.core.ScriptParser;
import us.bringardner.fsh.ide.core.ScriptRun;
import us.bringardner.fsh.ide.core.ScriptText;
import us.bringardner.fsh.ide.core.LegacyPreferences;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.KeyboardFocusManager;
import java.awt.Rectangle;
import java.awt.Taskbar;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.border.EmptyBorder;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;
import javax.swing.text.BadLocationException;

import us.bringardner.parley.brand.ParleyBrand;
import us.bringardner.parley.files.FileSource;
import us.bringardner.parley.files.swing.FileSourceChooserDialog;
import us.bringardner.parley.files.FileSourceFactory;
import us.bringardner.fsh.ConsolePanel;
import us.bringardner.fsh.ShellContext;
import us.bringardner.fsh.ide.core.SyntaxNode;


public class FshIDE extends JFrame  {

	private enum IdeRunstate {Idel,Running,Debugging}



	private static  List<FshIDE> ideWindows = new ArrayList<>();

	private synchronized static void register(FshIDE p) {
		ideWindows.add(p);
	}

	/**
	 * Closes a window, asking first only if something would be lost: unsaved changes (Cancel,
	 * Discard and exit, or Save and exit), or a script still running (which is stopped). The
	 * application exits with its last window.
	 */
	private synchronized static void close(FshIDE p) {
		if( !p.okToExit() || !p.okToStopScript()) {
			return;
		}
		p.stopScript();

		ideWindows.remove(p);
		if( p.boundsTimer.isRunning()) {
			p.saveBounds();
		}
		p.changeTimer.stop();
		p.dispose();

		if( ideWindows.size() == 0 ) {
			System.exit(0);
		}

	}

	/** True if no script is running (or paused) here, or the user agrees to stop it. */
	boolean okToStopScript() {
		return runState == IdeRunstate.Idel || confirm("A script is still running. Stop it and close?");
	}

	/** Stops the script running or being debugged here, if there is one. */
	private void stopScript() {
		if( runState != IdeRunstate.Idel ) {
			actionStop();
		}
	}

	/** Asks a yes/no question; tests override it. */
	protected boolean confirm(String question) {
		Object[] options = { "Stop and Close", "Cancel" };
		int ret = JOptionPane.showOptionDialog(this, question, "Warning",
				JOptionPane.OK_CANCEL_OPTION,
				JOptionPane.QUESTION_MESSAGE,
				null,
				options,
				options[1]);
		return ret == 0;
	}

	private int showConfirmDialog() {
		Object[] options = { "Discard and continue", "Don't continue" };

		int ret = JOptionPane.showOptionDialog(this, " There are unsaved changes.\nDo you want to discard them?", "Warning",
				JOptionPane.OK_CANCEL_OPTION, 
				JOptionPane.QUESTION_MESSAGE, 
				null,//getOpenScadIcon(), // do not use a custom Icon
				options, // the titles of buttons
				options[0]); // default button title

		return ret;
	}

	/** True if there are no unsaved changes or the user agrees to discard them. */
	private boolean okToDiscard() {
		return !hasChanged() || showConfirmDialog() == JOptionPane.OK_OPTION;
	}

	/**
	 * Closing the window (or quitting): true if there are no unsaved changes, or the user chooses
	 * Discard and exit, or Save and exit and the script is saved. Cancel, closing the question, or
	 * a save that doesn't happen (Save As canceled, an error) keeps the window open.
	 */
	boolean okToExit() {
		if( !hasChanged()) {
			return true;
		}
		switch (askExit()) {
		case DISCARD:
			return true;
		case SAVE:
			actionSave();
			return !hasChanged();
		default:
			return false;
		}
	}

	/** Asks what to do with unsaved changes when the window closes; tests override it. */
	protected ExitChoice askExit() {
		Object[] options = { "Cancel", "Discard and exit", "Save and exit" };
		int ret = JOptionPane.showOptionDialog(this, "There are unsaved changes.", "Unsaved Changes",
				JOptionPane.DEFAULT_OPTION,
				JOptionPane.WARNING_MESSAGE,
				null,
				options,
				options[2]);
		switch (ret) {
		case 1: return ExitChoice.DISCARD;
		case 2: return ExitChoice.SAVE;
		// Cancel, or the question closed
		default: return ExitChoice.CANCEL;
		}
	}

	/** The editor, for tests. */
	EditorPanel editor() {
		return editorPane;
	}

	boolean hasChanged() {

		boolean ret = !original.equals(getDocument());

		return ret;
	}


	protected static final Preferences prefs = LegacyPreferences.forPackage(FshIDE.class);

	private static final long serialVersionUID = 1L;

	private static final String PREF_RECENT_LIST = "RecentList";
	static final String PREF_SPELLING = "Spelling";

	private static final String KEY_SCREEN_LOCATION = "Location";

	private JPanel contentPane;

	// the script as it was read or last saved (code and run settings)
	private ScriptDocument original = ScriptDocument.parse("");
	private EditorPanel editorPane =new EditorPanel();
	//private JEditorPane editorPane = new JEditorPane();
	private JTextArea logView;

	private FileSource scriptFile;
	private FileSource lastFile;	
	private ScriptRun currentRun;


	private Rectangle lastSize;


	private static final int MAX_LOG_LENGTH = 200_000;

	private final DebugSession.Listener debugListener = new DebugSession.Listener() {
		@Override
		public void paused(int line, ShellContext ctx, Map<String, Object> variables) {
			SwingUtilities.invokeLater(()->{
				logBatcher.flush();
				editorPane.removeAllLineHighlights();
				if( line >= 0 ) {
					try {
						editorPane.addLineHighlight(line, EditorColors.debugLine());
					} catch (BadLocationException e) {
						showError(e, "Add hilight");
					}
				}
				debugVariablePanel.setContext(editorPane, ctx, variables);
			});
		}

		@Override
		public void statement(int line, String text) {
			logBatcher.add((line+1)+": "+text+"\n");
		}

		@Override
		public void conditionFailed(Breakpoint bp, int line, Exception error) {
			showError(error, "Condition evaluation failed (line "+(line+1)+")");
		}

		@Override
		public void resuming() {
			if( SwingUtilities.isEventDispatchThread()) {
				editorPane.removeAllLineHighlights();
			}
		}

		@Override
		public void terminateRequested() {
			actionDebug(true);
		}

		@Override
		public void watchpointHit(us.bringardner.fsh.ide.core.Watchpoint wp, String name, boolean write, Object value) {
			logBatcher.add("Watchpoint: "+DebugSession.describeHit(name, write, value)+"\n");
		}
	};

	private final DebugSession debugSession = new DebugSession(line->editorPane.getBreakpoint(line), debugListener);

	// Debug log lines from the script's thread, added to logView in batches on the EDT
	private final LogBatcher logBatcher = new LogBatcher(SwingUtilities::invokeLater, this::appendLog);

	private void appendLog(String text) {
		logView.append(text);
		// keep the end of a long run's log
		int extra = logView.getDocument().getLength() - MAX_LOG_LENGTH;
		if( extra > 0 ) {
			try {
				logView.getDocument().remove(0, extra);
			} catch (BadLocationException e) {
			}
		}
	}

	public static void fixWindowsIcons(final List<? extends java.awt.Image> iconImages) {
		PropertyChangeListener l = new PropertyChangeListener() {

			private Window prevActiveWindow;

			@Override
			public void propertyChange(PropertyChangeEvent evt) {
				final Window o = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
				if (o != null && prevActiveWindow != o) {
					prevActiveWindow = o;
					List<java.awt.Image> windowIcons = o.getIconImages();
					if (windowIcons == null || windowIcons.size() == 0) {
						o.setIconImages(iconImages);
					}
				}
			}
		};
		KeyboardFocusManager.getCurrentKeyboardFocusManager().addPropertyChangeListener("activeWindow", l); //$NON-NLS-1$
	}

	/**
	 * Launch the application.
	 */
	public static void main(String [] args) {
		// the environment the scripts run with: a terminal's (see ShellEnvironment), got now, in the background
		us.bringardner.fsh.ide.core.ShellEnvironment.start(problem->SwingUtilities.invokeLater(()->{
			JOptionPane pane = new JOptionPane(problem, JOptionPane.WARNING_MESSAGE);
			// (over an IDE window, so it isn't behind another application's)
			java.awt.Frame owner = null;
			for(java.awt.Frame f : java.awt.Frame.getFrames()) {
				if( f.isShowing() && f instanceof FshIDE ) {
					owner = f;
				}
			}
			javax.swing.JDialog d = pane.createDialog(owner, APP_NAME);
			d.setModal(false);
			d.setVisible(true);
		}));
		// on macOS each window's menus at the top of the screen, under the application's name
		StandardMenus.useScreenMenuBar(APP_NAME);
		// A GraalVM native image has no java.home, and AWT needs one (font configuration): use the
		// folder of the executable, where the image keeps the AWT libraries.
		if( System.getProperty("java.home") == null ) {
			ProcessHandle.current().info().command().ifPresent(c->{
				java.io.File dir = new java.io.File(c).getParentFile();
				if( dir != null ) {
					System.setProperty("java.home", dir.getAbsolutePath());
				}
			});
		}
		// The gradient look, as in parley-files-viewer, with the font and colors the user chose in
		// the appearance settings: before any window is made
		// the script editor's syntax colors are part of the theme, with a page in the theme designer
		ThemeExtensions.register(new EditorThemeExtension());
		// Settings has a Templates tab, after Appearance
		us.bringardner.swing.dialog.SettingsPages.register(TemplatesPage.ID, TemplatesPage::new);
		GradientLook.install(prefs);
		// the editors' and console's font, apart from the appearance font
		EditorFonts.load(prefs);
		EventQueue.invokeLater(()->openWindow());
	}

	/** The saved window bounds, or null if missing, unreadable or not on any screen now. */
	static Rectangle parseBounds(String text, Rectangle[] screens) {
		if( text == null ) {
			return null;
		}
		String parts[] = text.split("[,]");
		if( parts.length != 4 ) {
			return null;
		}
		Rectangle ret;
		try {
			ret = new Rectangle(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()),
					Integer.parseInt(parts[2].trim()), Integer.parseInt(parts[3].trim()));
		} catch (NumberFormatException e) {
			return null;
		}
		if( ret.width < 100 || ret.height < 100 ) {
			return null;
		}
		// the title bar must be on a screen, or the window can't be moved
		Rectangle title = new Rectangle(ret.x, ret.y, ret.width, 30);
		for(Rectangle s : screens) {
			if( s.intersects(title)) {
				return ret;
			}
		}
		return null;
	}

	private static Rectangle[] screenBounds() {
		GraphicsDevice[] devices = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
		Rectangle[] ret = new Rectangle[devices.length];
		for(int idx=0; idx < devices.length; idx++) {
			ret[idx] = devices[idx].getDefaultConfiguration().getBounds();
		}
		return ret;
	}

	private static void openWindow() {

		// Dialogs (new terminal, open remote, new folder...) get the app icon too, not the Java cup
		ParleyBrand.installDefaultWindowIcons("terminal");

		// The Parley app icon with the terminal badge, in every size; the OS picks the one it needs
		List<java.awt.Image> imageList = ParleyBrand.appIcons("terminal");


		fixWindowsIcons(imageList);
		// Windows has a Taskbar but no ICON_IMAGE feature (it throws); the frame icons below cover it
		if(Taskbar.isTaskbarSupported() && Taskbar.getTaskbar().isSupported(Taskbar.Feature.ICON_IMAGE)){
			Taskbar bar = Taskbar.getTaskbar();
			// The Dock / taskbar shows the icon large
			bar.setIconImage(ParleyBrand.icon(512, "terminal"));
		}
		// About, Settings and Quit: in the application menu on macOS (StandardMenus, see buildMenus)
		FshIDE frame = new FshIDE();
		frame.setIconImages(imageList);

		if( ideWindows.size() > 0 ) {
			FshIDE last = ideWindows.get(ideWindows.size()-1);
			Rectangle b = last.getBounds();
			b.x+=50;
			b.y+=50;
			frame.lastSize = b;
			frame.setBounds(b);			
		} else {
			Rectangle b = parseBounds(prefs.get(KEY_SCREEN_LOCATION, null), screenBounds());
			if( b != null ) {
				frame.setBounds(b);
			}
		}

		register(frame);
		frame.setVisible(true);
		IOException configError = Configuration.getLoadError();
		if( configError != null && ideWindows.size() == 1 ) {
			frame.showError(configError.getCause() != null ? configError.getCause() : configError, configError.getMessage());
		}
	}

	public class ExecutableCode {
		String allCode;
		String selectedCode;
		// editor line (0-based) the selection starts on
		int selectedLine;
	}


	private RecentFiles recent = new RecentFiles();

	private void saveRecentList() {
		prefs.put(PREF_RECENT_LIST, recent.format());
		try {
			prefs.flush();
		} catch (Exception e) {
			showError(e,"Can't save preferences");
		}
	}



	JMenu openRecentMenu = new JMenu("Open Recent");
	// read by the script thread too
	private volatile IdeRunstate runState = IdeRunstate.Idel;

	private JButton executeButton;

	private JCheckBoxMenuItem useSelectedCodeCheckItem;

	private JCheckBoxMenuItem autoExecuteCheckItem;


	private Component horizontalStrut;

	private DebugControlPanel debugControlPanel;

	private JButton debugButton;
	/** Stop, as the JavaFX IDE's: stops what is running or being debugged; disabled otherwise */
	private final JButton stopButton = new JButton("");

	private DebugVariablePanel debugVariablePanel;

	/** View > Show Debug View */
	private final JCheckBoxMenuItem showDebugViewItem = new JCheckBoxMenuItem("Show Debug View");

	private JSplitPane debugSplitPane;

	private JPanel debugPanel;

	private ConsolePanel outputTextArea;

	private JSplitPane centerSplitPane;
	// the script's functions and variables, beside the editor (View > Show Outline)
	private final OutlinePanel outlinePanel = new OutlinePanel();
	private JSplitPane outlineSplitPane;
	// the outline's width when it was hidden, for when it shows again
	private int outlineWidth = 200;
	static final String PREF_SHOW_OUTLINE = "ShowOutline";

	private void buildRecentMenu() {
		openRecentMenu.removeAll();
		for(String name : recent.list()) {
			JMenuItem item = new JMenuItem(name);
			item.addActionListener((e)->actionOpenRecent(name));
			openRecentMenu.add(item);
		}
	}

	private void actionOpenRecent(String name) {
		if( !okToDiscard()) {
			return;
		}
		try {
			loadFile(FileSourceFactory.getDefaultFactory().createFileSource(name), autoExecuteCheckItem.isSelected());
		} catch (IOException e) {
			showError(e, name);
		}
	}

	/**
	 * Drops recent files that no longer exist. Checked in the background: they may be on
	 * remote file systems. One that can't be checked now (unavailable) is kept. The run
	 * settings of scripts that are gone, or long unused, are dropped too.
	 */
	private void pruneRecentFiles() {
		List<String> names = new ArrayList<>(recent.list());
		BACKGROUND.execute(()->{
			try {
				Configuration.getInstance().runSettings().prune();
			} catch (IOException e) {
				// tried again next time
			}
			List<String> missing = RecentFiles.findMissing(names);
			if( !missing.isEmpty()) {
				SwingUtilities.invokeLater(()->{
					if( recent.removeAll(missing)) {
						saveRecentList();
						buildRecentMenu();
					}
				});
			}
		});
	}

	/** Reading files, which may be on remote file systems, and checking for recent ones. */
	private static final ExecutorService BACKGROUND = Executors.newCachedThreadPool(r->{
		Thread t = new Thread(r, "FshIDE background");
		t.setDaemon(true);
		return t;
	});

	// counts edits; a file read in the background isn't shown over edits made meanwhile
	private int editCount;

	/** Reads file in the background, then shows it. */
	private void loadFile(FileSource file, boolean execute) {
		int edits = editCount;
		BACKGROUND.execute(()->{
			try {
				String code = ScriptText.read(file);
				ScriptDocument doc = openDocument(file, code);
				SwingUtilities.invokeLater(()->{
					if( !isDisplayable() || (editCount != edits && !okToDiscard())) {
						return;
					}
					setDocument(doc, file);
					addRecent(file);
				});
			} catch (IOException e) {
				showError(e, "Can't read "+file.getAbsolutePath());
			}
		});
	}

	/** The title: the script's name, with a * if it has unsaved changes. */
	private void updateTitle() {
		String title = (hasChanged() ? "* " : "") + (scriptFile == null ? "" : scriptFile.getName());
		if( !title.equals(getTitle())) {
			setTitle(title);
		}
	}

	/** After edits stop for a moment: the title, and the syntax check if the code changed. */
	private final Timer changeTimer = new Timer(300, (e)->{
		updateTitle();
		checkSyntax();
	});

	private final DocumentListener changeListener = new DocumentListener() {
		@Override
		public void insertUpdate(DocumentEvent e) {
			changed();
		}
		@Override
		public void removeUpdate(DocumentEvent e) {
			changed();
		}
		@Override
		public void changedUpdate(DocumentEvent e) {
		}
		private void changed() {
			editCount++;
			changeTimer.restart();
		}
	};

	/** Syntax checks, one at a time, for all windows. */
	private static final ExecutorService SYNTAX_CHECKER = Executors.newSingleThreadExecutor(r->{
		Thread t = new Thread(r, "FshIDE syntax check");
		t.setDaemon(true);
		return t;
	});

	private String lastCheckedCode;
	// a check's result is shown only if no newer check has been asked for
	private int syntaxCheckCount;
	private SyntaxNode pendingTree;
	private String pendingTreeErrors;

	private void checkSyntax() {
		// not trimmed: error line numbers must match the editor's
		String code = ScriptText.clean(editorPane.getText());
		if( code.equals(lastCheckedCode)) {
			return;
		}
		lastCheckedCode = code;
		int check = ++syntaxCheckCount;
		if( code.isBlank()) {
			editorPane.clearErrorMarkers();
			outlinePanel.setOutline(null);
			return;
		}
		SYNTAX_CHECKER.execute(()->{
			List<CompileError> errors = new ArrayList<>();
			SyntaxNode tree;
			try {
				tree = ScriptParser.parse(code, errors);
			} catch (RuntimeException e) {
				return;
			}
			SwingUtilities.invokeLater(()->{
				if( check != syntaxCheckCount || !isDisplayable()) {
					return;
				}
				StringBuilder buf = new StringBuilder();
				editorPane.clearErrorMarkers();
				for(CompileError e : errors) {
					buf.append(e).append('\n');
					editorPane.addErrorMarker(e);
				}
				pendingTree = tree;
				pendingTreeErrors = buf.toString();
				showSyntaxTree();
				// a script that doesn't parse keeps the outline it had
				if( errors.isEmpty()) {
					outlinePanel.setOutline(ScriptOutline.of(tree));
				}
			});
		});
	}

	/** Lays out the syntax tree, which only matters when the debug view shows it. */
	private void showSyntaxTree() {
		if( pendingTree != null && debugSplitPane.isVisible()) {
			debugVariablePanel.updateTree(pendingTree, pendingTreeErrors);
			pendingTree = null;
			pendingTreeErrors = null;
		}
	}

	/** Saves the window's bounds once it has stopped moving, not for every step of a drag. */
	private final Timer boundsTimer = new Timer(500, (e)->saveBounds());

	private void saveBounds() {
		boundsTimer.stop();
		Rectangle b = getBounds();
		if(lastSize == null || !lastSize.equals(b)) {
			lastSize = b;
			prefs.put(KEY_SCREEN_LOCATION, String.format("%d,%d,%d,%d", b.x,b.y,b.width,b.height));
			try {
				prefs.flush();
			} catch (BackingStoreException e) {
				showError(e,"Can't save preferences");
			}
		}
	}


	/**
	 * Create the frame.
	 */
	public FshIDE() {
		editorPane.setHighlightCurrentLine(false);

		recent = RecentFiles.parse(prefs.get(PREF_RECENT_LIST, null));

		setBounds(100, 100, 1580, 1000);		
		contentPane = new JPanel();
		contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));

		setContentPane(contentPane);
		contentPane.setLayout(new BorderLayout(0, 0));
		JPanel controlPanel = new JPanel();
		controlPanel.setLayout(new BorderLayout(0, 0));

		contentPane.add(controlPanel, BorderLayout.NORTH);
		JPanel menuPanel = new JPanel();
		FlowLayout flowLayout = (FlowLayout) menuPanel.getLayout();
		flowLayout.setAlignment(FlowLayout.LEFT);
		controlPanel.add(menuPanel, BorderLayout.SOUTH);

		// the editor's own menu: Reload, and Breakpoint
		JMenuItem item = new JMenuItem("Reload");
		item.addActionListener((e)-> actionReload());
		editorPane.appendMenu(item);
		item = new JMenuItem("Breakpoint");
		editorPane.appendMenu(item);

		// the Run menu's options (the menus themselves: buildMenus)
		useSelectedCodeCheckItem = new JCheckBoxMenuItem("Run Selected Code Only");
		useSelectedCodeCheckItem.setSelected(true);
		autoExecuteCheckItem = new JCheckBoxMenuItem("Run When Opened");
		autoExecuteCheckItem.setSelected(false);

		showDebugViewItem.addActionListener(e->actionShowDebugView());

		JPanel executePanel = new JPanel();
		menuPanel.add(executePanel);
		executePanel.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 5));

		horizontalStrut = Box.createHorizontalStrut(40);
		executePanel.add(horizontalStrut);


		debugButton = new JButton("");
		debugButton.setIcon(new ImageIcon(FshIDE.class.getResource("/img/debug.png")));
		executePanel.add(debugButton);
		debugButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				actionDebug(true);
			}
		});


		executeButton = new JButton("");
		executeButton.setIcon(new ImageIcon(FshIDE.class.getResource("/img/run.png")));
		executePanel.add(executeButton);

		stopButton.setIcon(new ImageIcon(FshIDE.class.getResource("/img/terminate.png")));
		stopButton.setToolTipText("Stop");
		stopButton.addActionListener(e->actionStop());
		executePanel.add(stopButton);
		updateRunButtons();

		scriptArgPanel = new JPanel();
		menuPanel.add(scriptArgPanel);

		argumentsTextField = new JTextField();
		scriptArgPanel.add(argumentsTextField);
		argumentsTextField.setBorder(new TitledBorder(new EtchedBorder(EtchedBorder.LOWERED, null, null), "Script Arguments", TitledBorder.LEADING, TitledBorder.TOP, null, null));
		argumentsTextField.setColumns(30);

		JPanel panel = new JPanel();
		scriptArgPanel.add(panel);
		panel.setBorder(new TitledBorder(null, "stdin", TitledBorder.LEADING, TitledBorder.TOP, null, null));

		JButton btnNewButton = new JButton("");
		btnNewButton.setIcon(new ImageIcon(FshIDE.class.getResource("/img/redirect_in.png")));
		btnNewButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				actionBrowse(stdInTextField);
			}
		});
		panel.add(btnNewButton);

		stdInTextField = new JTextField();
		panel.add(stdInTextField);
		stdInTextField.setColumns(10);

		JPanel panel_1 = new JPanel();
		scriptArgPanel.add(panel_1);
		panel_1.setBorder(new TitledBorder(new EtchedBorder(EtchedBorder.LOWERED, null, null), "stdout", TitledBorder.LEADING, TitledBorder.TOP, null, null));

		JButton btnNewButton_1 = new JButton("");
		btnNewButton_1.setIcon(new ImageIcon(FshIDE.class.getResource("/img/redirect_out.png")));
		btnNewButton_1.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				actionBrowse(stdOutTextField);
			}
		});
		panel_1.add(btnNewButton_1);

		stdOutTextField = new JTextField();
		panel_1.add(stdOutTextField);
		stdOutTextField.setColumns(10);

		JPanel panel_1_1 = new JPanel();
		scriptArgPanel.add(panel_1_1);
		panel_1_1.setBorder(new TitledBorder(new EtchedBorder(EtchedBorder.LOWERED, null, null), "stderr", TitledBorder.LEADING, TitledBorder.TOP, null, null));

		JButton btnNewButton_1_1 = new JButton("");
		btnNewButton_1_1.setIcon(new ImageIcon(FshIDE.class.getResource("/img/redirect_out.png")));
		btnNewButton_1_1.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				actionBrowse(stdErrTextField);
			}
		});
		panel_1_1.add(btnNewButton_1_1);

		stdErrTextField = new JTextField();
		stdErrTextField.setColumns(10);
		panel_1_1.add(stdErrTextField);

		debugControlPanel = new DebugControlPanel(debugSession);
		menuPanel.add(debugControlPanel);
		debugControlPanel.setVisible(false);
		FlowLayout flowLayout_1 = (FlowLayout) debugControlPanel.getLayout();
		flowLayout_1.setAlignment(FlowLayout.CENTER);
		executeButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				actionExecute(true);
			}
		});



		// the menus every Parley application has (swing-widgets' StandardMenus): in the window's menu
		// bar, which on macOS is at the top of the screen
		buildMenus();


		debugPanel = new JPanel();
		debugPanel.setLayout(new BorderLayout(0, 0));

		JSplitPane editSplitPane = new JSplitPane();
		editSplitPane.setResizeWeight(0.6);
		editSplitPane.setOrientation(JSplitPane.VERTICAL_SPLIT);

		debugSplitPane = new JSplitPane();
		debugSplitPane.setVisible(false);
		debugSplitPane.setOrientation(JSplitPane.VERTICAL_SPLIT);
		debugSplitPane.setOneTouchExpandable(true);
		debugSplitPane.setResizeWeight(0.6);

		centerSplitPane = new JSplitPane();
		centerSplitPane.setResizeWeight(0.7);

		contentPane.add(centerSplitPane, BorderLayout.CENTER);
		// ConsolePanel scrolls its own text: another scroll pane around it
		// would take the mouse wheel with nothing to scroll
		outputTextArea = new ConsolePanel();
		EditorFonts.apply(outputTextArea);
		// the editor's colors: not a white console under a tinted editor
		EditorColors.apply(outputTextArea);



		editSplitPane.setLeftComponent(editorPane);
		editSplitPane.setRightComponent(outputTextArea);

		// the outline at the left of the editor and console
		outlineSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, outlinePanel, editSplitPane);
		outlineSplitPane.setResizeWeight(0);
		outlineSplitPane.setContinuousLayout(true);
		outlineSplitPane.setDividerLocation(200);
		outlinePanel.setVisible(prefs.getBoolean(PREF_SHOW_OUTLINE, true));
		// a variable's right-click menu in the outline: watch it (before or while debugging)
		outlinePanel.setOnWatchVariable(name->{
			// the debug view shows, with its Watchpoints tab
			if( !showDebugViewItem.isSelected()) {
				showDebugViewItem.setSelected(true);
				actionShowDebugView();
			}
			debugVariablePanel.addWatchpoint(name);
		});
		outlinePanel.setOnGoToLine(line->{
			editorPane.setCeretPosotionFromLine(line-1);
			editorPane.getTextArea().requestFocusInWindow();
		});
		centerSplitPane.setLeftComponent(outlineSplitPane);
		centerSplitPane.setRightComponent(debugSplitPane);

		original = ScriptDocument.parse("");
		editorPane.setText("",null);


		debugVariablePanel  = new DebugVariablePanel();
		debugVariablePanel.setContext(editorPane, null);
		debugVariablePanel.setWatchpoints(debugSession.getWatchpoints());
		// a clicked variable shows (name = value) in the text area below
		debugVariablePanel.setOnShowVariable(text->{
			logBatcher.flush();
			appendLog(text);
		});
		editorPane.addBreapointListner(()->{			
			debugVariablePanel.refreshBreakpoints();
		});

		debugPanel.add(debugVariablePanel, BorderLayout.CENTER);
		JScrollPane logViewScrollPane = new JScrollPane();
		logView = new JTextArea();
		logView.setText("");
		logViewScrollPane.setViewportView(logView);

		debugSplitPane.setRightComponent(logViewScrollPane);
		debugSplitPane.setLeftComponent(debugPanel);		
		debugSplitPane.setResizeWeight(.6);
		editorPane.addPropertyChangeListener(new PropertyChangeListener() {

			@Override
			public void propertyChange(PropertyChangeEvent evt) {
				String name = evt.getPropertyName();
				if( EditorPanel.ACTION_NEW.equals(name)) {
					actionNew();
				} else if( EditorPanel.ACTION_OPEN.equals(name)) {
					actionOpen();
				} else if( EditorPanel.ACTION_SAVE.equals(name)) {
					actionSave();
				} else if( EditorPanel.ACTION_NEW.equals(name)) {
					actionNew();
				}

			}
		});

		editorPane.addPropertyChangeListener(new PropertyChangeListener() {

			@Override
			public void propertyChange(PropertyChangeEvent evt) {
				String name = evt.getPropertyName();
				if( EditorPanel.ACTION_NEW.equals(name)) {
					actionNew();
				} else if( EditorPanel.ACTION_SAVE.equals(name)) {
					actionSave();
				} else if( EditorPanel.ACTION_OPEN.equals(name)) {
					actionOpen();
				}

			}
		});

		addComponentListener(new ComponentAdapter( ) {
			@Override
			public void componentResized(ComponentEvent ev) {
				boundsTimer.restart();
			}

			@Override
			public void componentMoved(ComponentEvent e) {
				boundsTimer.restart();
			};
		});
		boundsTimer.setRepeats(false);

		// close() decides: it closes the window, or (if the user says no) leaves it open. (A
		// JFrame hides itself by default, so No left the program running with no window.)
		setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				close(FshIDE.this);
			}
		});

		changeTimer.setRepeats(false);
		editorPane.addDocumentListener(changeListener);
		for(JTextField f : new JTextField[] {argumentsTextField, stdInTextField, stdOutTextField, stdErrTextField}) {
			f.getDocument().addDocumentListener(changeListener);
		}

		pruneRecentFiles();
		String name = recent.first();
		if( name != null ) {
			try {
				loadFile(FileSourceFactory.getDefaultFactory().createFileSource(name), autoExecuteCheckItem.isSelected());
			} catch (IOException ex) {
				showError(ex, "Can't read last edited file = "+name);
			}
		}

		// the gradient look (swing-widgets' GradientLook), as in parley-files-viewer
		GradientLook.apply(this);
	}



	protected void actionBrowse(JTextField fld) {
		try {

			String current = fld.getText().trim();
			FileSourceChooserDialog fc = new FileSourceChooserDialog();
			if( !current.isEmpty()) {
				FileSource file = FileSourceFactory.getDefaultFactory().createFileSource(current);
				fc.setSelectedFile(file);
			}
			// no file chosen (the window closed): nothing changes
			if(fc.showOpenDialog(this) == FileSourceChooserDialog.APPROVE_OPTION && fc.getSelectedFile() != null) {
				FileSource file = fc.getSelectedFile();
				fld.setText(file.getAbsolutePath());
			}
		} catch (Exception e) {
			showError(e, "");
		}

	}

	protected void actionShowDebugView() {
		if( runState == IdeRunstate.Idel) {
			boolean b = showDebugViewItem.isSelected();
			if( b ) {
				// (the variables a debug run left stay, as in the JavaFX IDE, until the next one)
				debugVariablePanel.refreshBreakpoints();
			}
			debugSplitPane.setVisible(b);		
			centerSplitPane.resetToPreferredSizes();
			showSyntaxTree();
		}
	}

	protected void actionDebug(boolean b) {

		switch (runState) {
		case Idel:
			editorPane.setHighlightCurrentLine(false);
			debugControlPanel.setVisible(true);
			debugSplitPane.setVisible(true);
			centerSplitPane.resetToPreferredSizes();
			showSyntaxTree();
			startTask(IdeRunstate.Debugging);

			break;
		case Debugging:

			stopTask();
			break;

		default:
			throw new IllegalArgumentException("Unexpected runState in actionDebug: " + runState);
		}


	}




	protected void actionNewWindow() {
		openWindow();

	}


	/** The application's name: the macOS application menu and Help > About show it. */
	static final String APP_NAME = "fsh-ide";

	/**
	 * The window's menus, laid out as in every Parley application (StandardMenus): File, Edit, View,
	 * Run, Window and Help, with About, Settings... and Quit in the application menu on macOS and in
	 * File and Help elsewhere. The same as the JavaFX IDE's.
	 */
	private void buildMenus() {
		StandardMenus menus = new StandardMenus(APP_NAME).about(AboutWindow::open)
				.settings(()->showAppearance(this)).quit(FshIDE::okToQuit);
		int shift = StandardMenus.SHIFT;

		JMenu file = menus.file;
		file.add(StandardMenus.item("New", KeyEvent.VK_N, this::actionNew));
		file.add(StandardMenus.item("Open...", KeyEvent.VK_O, this::actionOpen));
		file.add(openRecentMenu);
		buildRecentMenu();
		file.addSeparator();
		file.add(StandardMenus.item("Save", KeyEvent.VK_S, this::actionSave));
		file.add(StandardMenus.item("Save As...", StandardMenus.keys(KeyEvent.VK_S, shift), this::actionSaveAs));
		file.add(StandardMenus.item("Reload", null, this::actionReload));
		file.addSeparator();
		JMenuItem clearRunSettings = StandardMenus.item("Clear Run Settings...", null, this::actionClearRunSettings);
		clearRunSettings.setToolTipText("Forget the arguments and redirects kept for every script");
		file.add(clearRunSettings);
		file.addSeparator();
		file.add(StandardMenus.item("New Window", StandardMenus.keys(KeyEvent.VK_N, shift), this::actionNewWindow));
		file.add(StandardMenus.item("Close", KeyEvent.VK_W, this::actionExit));

		JMenu edit = menus.edit;
		edit.add(StandardMenus.item("Undo", KeyEvent.VK_Z, ()->editorPane.getTextArea().undoLastAction()));
		edit.add(StandardMenus.item("Redo", StandardMenus.keys(KeyEvent.VK_Z, shift), ()->editorPane.getTextArea().redoLastAction()));
		edit.addSeparator();
		// on whichever text field has the focus: the editor, the arguments, the console
		edit.add(textItem("Cut", KeyEvent.VK_X, new javax.swing.text.DefaultEditorKit.CutAction()));
		edit.add(textItem("Copy", KeyEvent.VK_C, new javax.swing.text.DefaultEditorKit.CopyAction()));
		edit.add(textItem("Paste", KeyEvent.VK_V, new javax.swing.text.DefaultEditorKit.PasteAction()));
		edit.add(textItem("Select All", KeyEvent.VK_A, new javax.swing.text.TextAction("select-all") {
			private static final long serialVersionUID = 1L;

			@Override
			public void actionPerformed(ActionEvent e) {
				javax.swing.text.JTextComponent t = getFocusedComponent();
				if( t != null ) {
					t.selectAll();
				}
			}
		}));
		edit.addSeparator();
		edit.add(StandardMenus.item("Find...", KeyEvent.VK_F, editorPane::showFind));
		edit.add(StandardMenus.item("Find Next", KeyEvent.VK_G, editorPane::findNext));
		edit.add(StandardMenus.item("Find Previous", StandardMenus.keys(KeyEvent.VK_G, shift), editorPane::findPrevious));
		edit.add(StandardMenus.item("Replace...", StandardMenus.keys(KeyEvent.VK_F, java.awt.event.InputEvent.ALT_DOWN_MASK), editorPane::showReplace));
		edit.add(StandardMenus.item("Go to Line...", KeyEvent.VK_L, editorPane::goToLine));
		edit.addSeparator();
		// (the key is the completion library's own, on the editor: the menu shows it)
		edit.add(StandardMenus.item("Complete", org.fife.ui.autocomplete.AutoCompletion.getDefaultTriggerKey(), editorPane::complete));
		edit.addSeparator();
		edit.add(spellingItem(editorPane.getTextArea(), prefs));

		fillViewMenu(menus.view);

		JMenu run = new JMenu("Run");
		run.add(StandardMenus.item("Run", KeyEvent.VK_R, ()->actionExecute(true)));
		// shift: the editor's own ⌘D deletes the line
		run.add(StandardMenus.item("Debug", StandardMenus.keys(KeyEvent.VK_D, shift), ()->actionDebug(true)));
		run.add(StandardMenus.item("Stop", KeyEvent.VK_PERIOD, this::actionStop));
		run.addSeparator();
		run.add(useSelectedCodeCheckItem);
		run.add(autoExecuteCheckItem);

		// what environment scripts get (see ShellEnvironment)
		menus.help.add(StandardMenus.item("Script Environment...", ()->showScriptEnvironment(this)));

		menus.install(this, run);
	}

	/** An Edit item that acts on the text field with the focus. */
	private static JMenuItem textItem(String text, int key, javax.swing.Action action) {
		JMenuItem item = new JMenuItem(action);
		item.setText(text);
		item.setAccelerator(StandardMenus.keys(key, 0));
		return item;
	}

	/** Run > Stop: stops the script running or being debugged. */
	protected void actionStop() {
		if( runState == IdeRunstate.Running ) {
			actionExecute(true);
		} else if( runState == IdeRunstate.Debugging ) {
			actionDebug(true);
		}
	}

	/**
	 * Quit (Exit): closes every IDE window, each asking about unsaved changes and a running
	 * script; false if one stays open.
	 */
	static boolean okToQuit() {
		for(FshIDE w : new ArrayList<>(ideWindows)) {
			if( !w.okToExit() || !w.okToStopScript()) {
				return false;
			}
		}
		for(FshIDE w : new ArrayList<>(ideWindows)) {
			w.stopScript();
			ideWindows.remove(w);
			if( w.boundsTimer.isRunning() ) {
				w.saveBounds();
			}
			w.changeTimer.stop();
			w.dispose();
		}
		return true;
	}

	/**
	 * View: the editors' and console's text bigger or smaller (also with ⌘+ / ⌘−, or Ctrl), back to
	 * the standard size (⌘0), or another fixed-width font. The menu items show the keys; TextSizeKeys
	 * handles them.
	 */
	/** Help > Script Environment...: what environment scripts run with, and why. */
	static void showScriptEnvironment(java.awt.Component parent) {
		// (it may wait for the login shell)
		new Thread(()->{
			String report = us.bringardner.fsh.ide.core.ShellEnvironment.report();
			SwingUtilities.invokeLater(()->{
				JTextArea text = new JTextArea(report, 28, 80);
				text.setEditable(false);
				text.setCaretPosition(0);
				JOptionPane.showMessageDialog(parent, new JScrollPane(text), "Script Environment", JOptionPane.INFORMATION_MESSAGE);
			});
		}, "script-environment").start();
	}

	/**
	 * Edit > Check Spelling, as the JavaFX IDE's: on unless it was switched off (prefs remembers
	 * it), and switching it turns the text area's spell checking on or off.
	 */
	static JCheckBoxMenuItem spellingItem(FshIDETextArea area, Preferences prefs) {
		JCheckBoxMenuItem ret = new JCheckBoxMenuItem("Check Spelling", prefs.getBoolean(PREF_SPELLING, true));
		area.setSpellChecking(ret.isSelected());
		ret.addActionListener(e->{
			boolean on = ret.isSelected();
			area.setSpellChecking(on);
			prefs.putBoolean(PREF_SPELLING, on);
		});
		return ret;
	}

	private void fillViewMenu(JMenu view) {
		int shortcut = java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
		JMenuItem bigger = new JMenuItem("Bigger Text");
		// shown as ⌘+, as Mac applications show it; ⌘=, ⇧⌘= and the keypad's + all work (TextSizeKeys)
		bigger.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_PLUS, shortcut));
		bigger.addActionListener(e->actionBiggerText(this));
		JMenuItem smaller = new JMenuItem("Smaller Text");
		smaller.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_MINUS, shortcut));
		smaller.addActionListener(e->actionSmallerText(this));
		JMenuItem reset = new JMenuItem("Reset Text Size");
		reset.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_0, shortcut));
		reset.addActionListener(e->actionResetTextSize(this));
		JMenuItem font = new JMenuItem("Editor Font...");
		font.addActionListener(e->EditorFontDialog.showDialog(this, prefs));
		view.add(bigger);
		view.add(smaller);
		view.add(reset);
		view.addSeparator();
		view.add(font);
		view.addSeparator();
		JCheckBoxMenuItem showOutline = new JCheckBoxMenuItem("Show Outline", prefs.getBoolean(PREF_SHOW_OUTLINE, true));
		showOutline.addActionListener(e->{
			boolean b = showOutline.isSelected();
			prefs.putBoolean(PREF_SHOW_OUTLINE, b);
			if( !b ) {
				outlineWidth = outlineSplitPane.getDividerLocation();
			}
			outlinePanel.setVisible(b);
			if( b ) {
				outlineSplitPane.setDividerLocation(Math.max(80, outlineWidth));
			}
		});
		view.add(showOutline);
		view.add(showDebugViewItem);
		view.addSeparator();
		int fold = shortcut | java.awt.event.InputEvent.ALT_DOWN_MASK;
		int all = fold | java.awt.event.InputEvent.SHIFT_DOWN_MASK;
		view.add(foldItem("Fold", org.fife.ui.rsyntaxtextarea.RSyntaxTextAreaEditorKit.rstaCollapseFoldAction,
				java.awt.event.KeyEvent.VK_OPEN_BRACKET, fold));
		view.add(foldItem("Unfold", org.fife.ui.rsyntaxtextarea.RSyntaxTextAreaEditorKit.rstaExpandFoldAction,
				java.awt.event.KeyEvent.VK_CLOSE_BRACKET, fold));
		view.add(foldItem("Fold All", org.fife.ui.rsyntaxtextarea.RSyntaxTextAreaEditorKit.rstaCollapseAllFoldsAction,
				java.awt.event.KeyEvent.VK_OPEN_BRACKET, all));
		view.add(foldItem("Unfold All", org.fife.ui.rsyntaxtextarea.RSyntaxTextAreaEditorKit.rstaExpandAllFoldsAction,
				java.awt.event.KeyEvent.VK_CLOSE_BRACKET, all));
		// the keys themselves work wherever the menu bar is (see TextSizeKeys)
		TextSizeKeys.install();
	}

	/**
	 * A View menu item for one of the editor's fold actions. The menu shows the key; the editor's own
	 * key binding (FshIDETextArea.foldKeys) acts on it, so it works with the screen menu bar too.
	 */
	private JMenuItem foldItem(String text, String action, int key, int modifiers) {
		JMenuItem item = new JMenuItem(text);
		item.setAccelerator(javax.swing.KeyStroke.getKeyStroke(key, modifiers));
		item.addActionListener(e->{
			FshIDETextArea editor = editorPane.getTextArea();
			javax.swing.Action a = editor.getActionMap().get(action);
			if( a != null ) {
				a.actionPerformed(new ActionEvent(editor, ActionEvent.ACTION_PERFORMED, action));
			}
		});
		return item;
	}

	/**
	 * View > Bigger Text (and its keys): this window's editors and console, for this session only;
	 * View > Editor Font... is what changes the font for good.
	 */
	static void actionBiggerText(java.awt.Window w) {
		EditorFonts.setWindowFont(w, EditorFonts.fontOf(w).bigger());
	}

	/** View > Smaller Text: as Bigger Text. */
	static void actionSmallerText(java.awt.Window w) {
		EditorFonts.setWindowFont(w, EditorFonts.fontOf(w).smaller());
	}

	/** View > Reset Text Size: this window back to the editor font, as last set with Editor Font.... */
	static void actionResetTextSize(java.awt.Window w) {
		EditorFonts.setWindowFont(w, null);
	}

	protected void actionPreferences() {
		showAppearance(this);
	}

	/**
	 * The appearance settings (the theme and font, shared with the other Parley applications
	 * through swing-widgets), saved in the IDE's preferences; the JavaFX IDE uses the theme too.
	 * The editors keep their own font; their colors are the theme's (EditorColors): the theme designer
	 * has a Script editor page for them.
	 */
	static void showAppearance(java.awt.Window owner) {
		if( SettingsDialog.showDialog(owner, prefs) ) {
			// the editors' colors follow the new theme
			EditorColors.applyToOpenWindows();
		}
	}

	protected void actionHelp() {
		try {
		} catch (Exception e) {
			showError(e,"Can't open help file");
		}

	}

	protected void actionNew() {
		if( !okToDiscard()) {
			return;
		}
		if( scriptFile != null) {
			lastFile = scriptFile;
		}
		scriptFile = null;

		setDocument(ScriptDocument.parse(""), null);

	}

	public void showError(Throwable e1,String msg) {
		if( SwingUtilities.isEventDispatchThread()) {
			String err = "";
			if( e1 != null ) {
				err = e1.toString();
			}
			JOptionPane.showMessageDialog(contentPane, err,msg, JOptionPane.ERROR_MESSAGE);
		} else {
			SwingUtilities.invokeLater(()->showError(e1, msg));
		}		

	}

	private void showWarning(String msg) {
		if( SwingUtilities.isEventDispatchThread()) {
			JOptionPane.showMessageDialog(contentPane, "", msg, JOptionPane.WARNING_MESSAGE);
		} else {
			SwingUtilities.invokeLater(()->JOptionPane.showMessageDialog(contentPane, "", msg, JOptionPane.WARNING_MESSAGE));
		}
	}

	private ExecutableCode getExecutableCode() {
		ExecutableCode ret = new ExecutableCode();
		ret.allCode = ScriptText.clean(editorPane.getText());

		ret.selectedCode = ScriptText.clean(editorPane.getSelectedText());
		if( ret.selectedCode != null ) {
			ret.selectedLine = editorPane.getSelectionStartLine();
		}
		return ret;
	}

	/** The script as it would be saved: the code, and the run settings kept for it. */
	protected ScriptDocument getDocument() {
		return new ScriptDocument(editorPane.getText(), new RunSettings(argumentsTextField.getText(),
				stdInTextField.getText(), stdOutTextField.getText(), stdErrTextField.getText()));
	}

	/** A script read from file, with its run settings (call it off the event thread: it reads the store). */
	private ScriptDocument openDocument(FileSource file, String code) {
		try {
			return ScriptDocument.open(file, code, Configuration.getInstance().runSettings());
		} catch (IOException e) {
			// the script without its settings, rather than not at all
			String msg = "Can't read the run settings: "+e.getMessage();
			SwingUtilities.invokeLater(()->appendLog(msg));
			return ScriptDocument.parse(code);
		}
	}

	// -Xss4m
	private long stackSize=((1024*1024));
	private JTextField argumentsTextField;
	private JTextField stdInTextField;
	private JTextField stdOutTextField;
	private JTextField stdErrTextField;
	private JPanel scriptArgPanel;
	protected void actionExecute(boolean logError) {

		switch (runState) {
		case Idel:				
			startTask(IdeRunstate.Running);
			break;
		case Running:
			stopTask();
			break;

		default:
			throw new IllegalArgumentException("Unexpected runState in actionExecute: " + runState);
		}

	}

	/**
	 * Debug, Run and Stop, as in the JavaFX IDE: Debug and Run while nothing runs, Stop while a
	 * script runs or is being debugged.
	 */
	private void updateRunButtons() {
		boolean running = runState != IdeRunstate.Idel;
		debugButton.setEnabled(!running);
		executeButton.setEnabled(!running);
		stopButton.setEnabled(running);
	}

	/** for the tests: [Debug, Run, Stop] enabled */
	boolean[] runButtonsEnabled() {
		return new boolean[] {debugButton.isEnabled(), executeButton.isEnabled(), stopButton.isEnabled()};
	}

	private void stopTask () {
		if( SwingUtilities.isEventDispatchThread()) {
			ScriptRun run = currentRun;
			if(run !=null && run.isRunning() ) {
				run.cancel();
				editorPane.removeAllLineHighlights();

				new Thread(()->{
					try {
						while(!run.join(1000)) {
							SwingUtilities.invokeLater(()->{
								outputTextArea.getStdOut().println("Waiting for task to complete\n");
							});
							run.interrupt();
						}
					} catch (InterruptedException e) {
					}

					// only if a new run hasn't started meanwhile
					SwingUtilities.invokeLater(()->{
						if( currentRun == run ) {
							currentRun = null;
						}
					});

				}).start();
			}

			scriptArgPanel.setVisible(true);
			horizontalStrut.setVisible(true);
			debugControlPanel.setVisible(false);
			runState = IdeRunstate.Idel;
			updateRunButtons();
			debugSession.setActive(false);
			editorPane.setHighlightCurrentLine(false);
			actionShowDebugView();
		} else {
			SwingUtilities.invokeLater(()->stopTask());
		}
	}

	private void startTask(IdeRunstate state) {

		if( SwingUtilities.isEventDispatchThread()) {
			for(Breakpoint bp: editorPane.getBreakpoints().values()) {
				bp.reset();
			}

			ExecutableCode code = getExecutableCode();
			if( code.allCode == null || code.allCode.isEmpty()) {
				return;
			}

			scriptArgPanel.setVisible(false);
			logBatcher.clear();
			logView.setText("");
			outputTextArea.clear();
			// what environment scripts get, once, in the Log tab
			us.bringardner.fsh.ide.core.ShellEnvironment.logOnce(logBatcher::add);
			if( state == IdeRunstate.Debugging) {
				debugControlPanel.setVisible(true);
				// a new debug run: the last one's variables go
				debugVariablePanel.setContext(editorPane, null);
			}
			boolean selection = useSelectedCodeCheckItem.isSelected() && code.selectedCode != null;
			// a selection runs on its own lines, so its line numbers are the script's
			debugSession.setFirstLine(0);
			debugSession.setActive(state == IdeRunstate.Debugging);
			currentRun = new ScriptRun(selection ? ScriptText.atLine(code.selectedCode, code.selectedLine) : code.allCode, this::runFinished)
					.scriptName(scriptFile != null ? scriptFile.getAbsolutePath() : "fsh")
					.arguments(argumentsTextField.getText())
					.redirects(stdInTextField.getText(), stdOutTextField.getText(), stdErrTextField.getText())
					.console(outputTextArea.getStdIn(), outputTextArea.getStdOut(), outputTextArea.getStdErr())
					.debug(debugSession)
					.stackSize(stackSize);
			runState = state;
			updateRunButtons();
			currentRun.start();
		} else {
			SwingUtilities.invokeLater(()->startTask(state));
		}
	}

	/** On the run's thread. */
	private void runFinished(ScriptRun run, int exitCode, ShellContext ctx, Exception error) {
		if( error != null ) {
			showError(error, "");
		}
		logBatcher.add("exitCode = "+exitCode+"\n");
		if( ctx != null && debugSession.isActive()) {
			// the variables as the script left them, kept in the tables after the run (as in the
			// JavaFX IDE); without its context, since the script is over, so they can't be edited
			Map<String,Object> vars = ctx.getVariables();
			SwingUtilities.invokeLater(()->debugVariablePanel.setContext(editorPane, null, vars));
		}
		SwingUtilities.invokeLater(()->{
			if( currentRun == run ) {
				stopTask();
			}
		});
	}



	/** Forgets the arguments and redirects kept for every script (this window's stay until saved). */
	protected void actionClearRunSettings() {
		RunSettingsStore store = Configuration.getInstance().runSettings();
		int n;
		try {
			n = store.size();
		} catch (IOException e) {
			showError(e, "Can't read the run settings");
			return;
		}
		if( n == 0 ) {
			JOptionPane.showMessageDialog(contentPane, "No scripts have arguments or redirects kept.");
			return;
		}
		if( JOptionPane.showConfirmDialog(contentPane, "Forget the arguments and redirects kept for "+n
				+(n == 1 ? " script?" : " scripts?"), "Clear Run Settings", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION ) {
			return;
		}
		try {
			store.clear();
		} catch (IOException e) {
			showError(e, "Can't clear the run settings");
		}
	}

	protected void actionExit() {
		close(this);
	}

	protected void actionReload() {
		FileSource file = scriptFile != null ? scriptFile : lastFile;
		if( file == null || !okToDiscard()) {
			return;
		}
		loadFile(file, autoExecuteCheckItem.isSelected());

	}

	/** Shows a script read from file (null for a new script). */
	protected void setDocument(ScriptDocument doc, FileSource file) {
		scriptFile = file;
		argumentsTextField.setText(doc.arguments());
		stdInTextField.setText(doc.stdIn());
		stdOutTextField.setText(doc.stdOut());
		stdErrTextField.setText(doc.stdErr());

		editorPane.setText(doc.body(),scriptFile);
		// so it doesn't count as changed (a script with settings lines from an earlier version
		// loses them when it's next saved)
		original = getDocument();
		updateTitle();
	}

	protected void actionSave() {

		final ScriptDocument doc = getDocument();
		if( doc.body().isEmpty()) {
			showWarning("Nothing to save");
			return;
		}

		try {
			if( scriptFile == null) {
				actionSaveAs();
			} else {
				// the code to the file; the arguments and redirects to the user's store (or
				// removed from it, if they were cleared)
				String warning = doc.save(scriptFile, Configuration.getInstance().runSettings());
				original = doc;
				addRecent(scriptFile);
				updateTitle();
				if( warning != null ) {
					showWarning(warning);
				}
			}
		} catch (IOException e) {
			showError(e, "Can't save");
		}
	}

	protected void actionSaveAs() {
		if( getDocument().body().isEmpty()) {
			showWarning("Nothing to save");
			return;
		}
		FileSourceChooserDialog fc = new FileSourceChooserDialog();
		if( lastFile==null) {
			lastFile = scriptFile;
		}
		if( lastFile!=null ) {
			try {
				fc.setCurrentDirectory(lastFile.getParentFile());
			} catch (IOException e) {				
			}
		}

		// no file chosen (the window closed): nothing is saved
		if(fc.showSaveDialog(null) == FileSourceChooserDialog.APPROVE_OPTION && fc.getSelectedFile() != null) {
			FileSource previous = scriptFile;
			scriptFile = fc.getSelectedFile();
			actionSave();
			if( hasChanged()) {
				// not saved: keep the file the editor came from
				scriptFile = previous;
			}
		}




	}

	/** Adds file to the recent list (kept in the preferences); tests override it. */
	protected void addRecent(FileSource file2) {
		recent.add(file2.getAbsolutePath(), Configuration.getInstance().getMaxRecent());
		saveRecentList();
		buildRecentMenu();


	}

	protected void actionOpen() {
		if( !okToDiscard()) {
			return;
		}
		FileSourceChooserDialog fc = new FileSourceChooserDialog();
		if( lastFile==null) {
			lastFile = scriptFile;
		}
		if( lastFile!=null ) {
			try {
				fc.setCurrentDirectory(lastFile.getParentFile());
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		// no file chosen (the window closed): nothing opens
		if(fc.showOpenDialog(null) == FileSourceChooserDialog.APPROVE_OPTION && fc.getSelectedFile() != null) {
			loadFile(fc.getSelectedFile(), false);
		}


	}





	public void createAutoComplete() {
		editorPane.createAutoComplete();

	}



}
