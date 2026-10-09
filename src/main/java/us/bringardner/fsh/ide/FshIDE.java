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
import us.bringardner.fsh.ide.core.Configuration;
import us.bringardner.fsh.ide.core.CompileError;
import us.bringardner.fsh.ide.core.DebugSession;
import us.bringardner.fsh.ide.core.LogBatcher;
import us.bringardner.fsh.ide.core.RecentFiles;
import us.bringardner.fsh.ide.core.ScriptDocument;
import us.bringardner.fsh.ide.core.ScriptParser;
import us.bringardner.fsh.ide.core.ScriptRun;
import us.bringardner.fsh.ide.core.ScriptText;
import us.bringardner.fsh.ide.core.LegacyPreferences;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.KeyboardFocusManager;
import java.awt.Rectangle;
import java.awt.Taskbar;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ActionEvent;
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
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
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

	private synchronized static void close(FshIDE p) {
		boolean approved = false;
		if( p.hasChanged()) {
			if( p.showConfirmDialog() != JOptionPane.OK_OPTION) {
				return;
			} else {
				approved = true;
			}
		}

		if(!approved && ideWindows.size() == 1) {
			Object[] options = { "Exit Application", "No, Don't exit" };

			int ret = JOptionPane.showOptionDialog(p, "Closing this window will cause the application to exit.\nClose anyway?", "Warning",
					JOptionPane.OK_CANCEL_OPTION, 
					JOptionPane.QUESTION_MESSAGE, 
					null, // do not use a custom Icon
					options, // the titles of buttons
					options[0]); // default button title

			if( ret != JOptionPane.OK_OPTION) {
				return;
			}
		}


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

	private boolean hasChanged() {

		String tmp = getCode();
		boolean ret = !original.equals(tmp);

		return ret;
	}


	protected static final Preferences prefs = LegacyPreferences.forPackage(FshIDE.class);

	private static final long serialVersionUID = 1L;

	private static final String PREF_RECENT_LIST = "RecentList";

	private static final String KEY_SCREEN_LOCATION = "Location";

	private JPanel contentPane;

	private String original = "";
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
						editorPane.addLineHighlight(line, Color.green);
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

		ArrayList<java.awt.Image> imageList = new ArrayList<java.awt.Image>();

		String sizes[] = {"100x100","64x64","25x25","16x16"};
		for(String s : sizes) {			
			java.awt.Image image = Toolkit.getDefaultToolkit().getImage(FshIDE.class.getResource("/img/Globe"+s+".png"));
			imageList.add(image);
		}


		fixWindowsIcons(imageList);
		if(Taskbar.isTaskbarSupported()){
			Taskbar bar = Taskbar.getTaskbar();
			bar.setIconImage(imageList.get(0));
		}
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

	private JCheckBoxMenuItem profileCheckItem;

	private Component horizontalStrut;

	private DebugControlPanel debugControlPanel;

	private JButton debugButton;

	private DebugVariablePanel debugVariablePanel;

	private JCheckBox showDebugViewCheckBox;

	private JSplitPane debugSplitPane;

	private JPanel debugPanel;

	private ConsolePanel outputTextArea;

	private JSplitPane centerSplitPane;

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
	 * remote file systems. One that can't be checked now (unavailable) is kept.
	 */
	private void pruneRecentFiles() {
		List<String> names = new ArrayList<>(recent.list());
		BACKGROUND.execute(()->{
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
				SwingUtilities.invokeLater(()->{
					if( !isDisplayable() || (editCount != edits && !okToDiscard())) {
						return;
					}
					setCode(code, execute, file);
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

		JMenuBar menuBar = new JMenuBar();
		controlPanel.add(menuBar, BorderLayout.NORTH);

		JMenu fileMenu = new JMenu("File");
		menuBar.add(fileMenu);

		JMenuItem mntmNewMenuItem = new JMenuItem("Open");
		mntmNewMenuItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				actionOpen();
			}
		});

		JMenuItem mntmNewMenuItem_7 = new JMenuItem("Preferences");
		mntmNewMenuItem_7.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				actionPreferences();
			}
		});
		fileMenu.add(mntmNewMenuItem_7);
		fileMenu.add(mntmNewMenuItem);
		fileMenu.add(openRecentMenu);

		JMenuItem newFileMenu = new JMenuItem("New File");
		newFileMenu.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				actionNew();
			}
		});
		fileMenu.add(newFileMenu);

		JMenuItem newWindowMenu = new JMenuItem("New Window");
		newWindowMenu.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				actionNewWindow();
			}
		});
		fileMenu.add(newWindowMenu);

		JMenuItem mntmNewMenuItem_1 = new JMenuItem("Save");
		mntmNewMenuItem_1.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				actionSave();
			}
		});
		fileMenu.add(mntmNewMenuItem_1);

		buildRecentMenu();

		JMenuItem mntmNewMenuItem_2 = new JMenuItem("Save As");
		mntmNewMenuItem_2.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				actionSaveAs();
			}
		});
		fileMenu.add(mntmNewMenuItem_2);


		JMenuItem mntmNewMenuItem_3 = new JMenuItem("Reload");
		mntmNewMenuItem_3.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				actionReload();
			}
		});
		fileMenu.add(mntmNewMenuItem_3);


		JMenuItem item = new JMenuItem("Reload");
		item.addActionListener((e)-> actionReload());
		editorPane.appendMenu(item);


		item = new JMenuItem("Breakpoint");
		//item.addActionListener((e)-> actionBreakpoint());
		editorPane.appendMenu(item);


		JMenu mnNewMenu_1 = new JMenu("Import");
		fileMenu.add(mnNewMenu_1);



		JMenu mnNewMenu_2 = new JMenu("Export");
		fileMenu.add(mnNewMenu_2);



		fileMenu.add(new JSeparator());



		JMenuItem mntmNewMenuItem_6 = new JMenuItem("Exit");
		mntmNewMenuItem_6.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				actionExit();
			}
		});
		fileMenu.add(mntmNewMenuItem_6);

		JMenu mnNewMenu = new JMenu("Debug");
		menuBar.add(mnNewMenu);


		useSelectedCodeCheckItem = new JCheckBoxMenuItem("Use selected code");
		useSelectedCodeCheckItem.setSelected(true);
		mnNewMenu.add(useSelectedCodeCheckItem);


		autoExecuteCheckItem = new JCheckBoxMenuItem("Execute on load");
		autoExecuteCheckItem.setSelected(false);
		mnNewMenu.add(autoExecuteCheckItem);

		profileCheckItem = new JCheckBoxMenuItem("Profile");
		mnNewMenu.add(profileCheckItem);






		JButton btnHelp = new JButton("");
		menuBar.add(btnHelp);
		btnHelp.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				actionHelp();
			}
		});
		btnHelp.setPreferredSize(new Dimension(40, 40));
		//btnHelp.setIcon(new ImageIcon(getClass().getResource("/HelpBlack.png")));
		btnHelp.setToolTipText("Help");

		showDebugViewCheckBox = new JCheckBox("Debug View");
		showDebugViewCheckBox.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				actionShowDebugView();
			}
		});
		menuPanel.add(showDebugViewCheckBox);

		JPanel executePanel = new JPanel();
		menuPanel.add(executePanel);
		executePanel.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 5));

		horizontalStrut = Box.createHorizontalStrut(40);
		executePanel.add(horizontalStrut);


		debugButton = new JButton("");
		debugButton.setIcon(new ImageIcon(FshIDE.class.getResource("/img/eclipe_debug_view.png")));
		executePanel.add(debugButton);
		debugButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				actionDebug(true);
			}
		});


		executeButton = new JButton("");
		executeButton.setSelectedIcon(new ImageIcon(FshIDE.class.getResource("/img/eclipe_delete_config@2x.png")));
		executeButton.setIcon(new ImageIcon(FshIDE.class.getResource("/img/eclipe_run_exc.png")));
		executePanel.add(executeButton);

		scriptArgPanel = new JPanel();
		menuPanel.add(scriptArgPanel);

		argumentsTextField = new JTextField();
		scriptArgPanel.add(argumentsTextField);
		argumentsTextField.setBorder(new TitledBorder(new EtchedBorder(EtchedBorder.LOWERED, null, null), "Script Arguments", TitledBorder.LEADING, TitledBorder.TOP, null, new Color(0, 0, 0)));
		argumentsTextField.setColumns(30);

		JPanel panel = new JPanel();
		scriptArgPanel.add(panel);
		panel.setBorder(new TitledBorder(null, "stdin", TitledBorder.LEADING, TitledBorder.TOP, null, new Color(0, 0, 0)));

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
		panel_1.setBorder(new TitledBorder(new EtchedBorder(EtchedBorder.LOWERED, null, null), "stdout", TitledBorder.LEADING, TitledBorder.TOP, null, new Color(0, 0, 0)));

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
		panel_1_1.setBorder(new TitledBorder(new EtchedBorder(EtchedBorder.LOWERED, null, null), "stderr", TitledBorder.LEADING, TitledBorder.TOP, null, new Color(0, 0, 0)));

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



		// macOS: the screen-top menu bar. Elsewhere that action isn't supported
		// (setDefaultMenuBar throws), so the menu goes on the window instead.
		if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.APP_MENU_BAR)) {
			Desktop.getDesktop().setDefaultMenuBar(menuBar);
		} else {
			setJMenuBar(menuBar);
		}


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
		JScrollPane stdViewScrollPane = new JScrollPane();
		outputTextArea = new ConsolePanel();		
		stdViewScrollPane.setViewportView(outputTextArea);



		editSplitPane.setLeftComponent(editorPane);
		editSplitPane.setRightComponent(stdViewScrollPane);

		centerSplitPane.setLeftComponent(editSplitPane);
		centerSplitPane.setRightComponent(debugSplitPane);

		original = "";				
		editorPane.setText(original,null);


		debugVariablePanel  = new DebugVariablePanel();
		debugVariablePanel.setContext(editorPane, null);
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
	}



	protected void actionBrowse(JTextField fld) {
		try {

			String current = fld.getText().trim();
			FileSourceChooserDialog fc = new FileSourceChooserDialog();
			if( !current.isEmpty()) {
				FileSource file = FileSourceFactory.getDefaultFactory().createFileSource(current);
				fc.setSelectedFile(file);
			}
			if(fc.showOpenDialog(this) == FileSourceChooserDialog.APPROVE_OPTION) {
				FileSource file = fc.getSelectedFile();
				fld.setText(file.getAbsolutePath());
			}
		} catch (Exception e) {
			showError(e, "");
		}

	}

	protected void actionShowDebugView() {
		if( runState == IdeRunstate.Idel) {
			boolean b = showDebugViewCheckBox.isSelected();
			if( b ) {
				debugVariablePanel.setContext(editorPane, null);
			}
			debugSplitPane.setVisible(b);		
			centerSplitPane.resetToPreferredSizes();
			showSyntaxTree();
		}
	}

	protected void actionDebug(boolean b) {

		switch (runState) {
		case Idel:
			debugButton.setVisible(false);
			executeButton.setVisible(false);
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


	protected void actionPreferences() {


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

		setCode("",false, null);

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

	/** The script as it would be saved. */
	protected String getCode() {		
		return new ScriptDocument(editorPane.getText(), argumentsTextField.getText(),
				stdInTextField.getText(), stdOutTextField.getText(), stdErrTextField.getText()).toText();
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
			executeButton.setIcon(new ImageIcon(FshIDE.class.getResource("/img/eclipe_run_exc.png")));		
			horizontalStrut.setVisible(true);
			debugButton.setVisible(true);
			executeButton.setVisible(true);
			debugControlPanel.setVisible(false);
			runState = IdeRunstate.Idel;
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

			executeButton.setIcon(new ImageIcon(FshIDE.class.getResource("/img/Stop.png")));

			scriptArgPanel.setVisible(false);
			logBatcher.clear();
			logView.setText("");
			outputTextArea.clear();
			if( state == IdeRunstate.Debugging) {
				debugControlPanel.setVisible(true);
			}
			boolean selection = useSelectedCodeCheckItem.isSelected() && code.selectedCode != null;
			debugSession.setFirstLine(selection ? code.selectedLine : 0);
			debugSession.setActive(state == IdeRunstate.Debugging);
			currentRun = new ScriptRun(selection ? code.selectedCode : code.allCode, this::runFinished)
					.scriptName(scriptFile != null ? scriptFile.getAbsolutePath() : "fsh")
					.arguments(argumentsTextField.getText())
					.redirects(stdInTextField.getText(), stdOutTextField.getText(), stdErrTextField.getText())
					.console(outputTextArea.getStdIn(), outputTextArea.getStdOut(), outputTextArea.getStdErr())
					.debug(debugSession)
					.stackSize(stackSize);
			runState = state;
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
			// the variables as the script left them
			Map<String,Object> vars = ctx.getVariables();
			SwingUtilities.invokeLater(()->debugVariablePanel.setContext(editorPane, ctx, vars));
		}
		SwingUtilities.invokeLater(()->{
			if( currentRun == run ) {
				stopTask();
			}
		});
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

	/** Shows code from file (null for a new script). */
	protected void setCode(String code,boolean execute, FileSource file) {
		scriptFile = file;
		ScriptDocument doc = ScriptDocument.parse(code);
		argumentsTextField.setText(doc.arguments());
		stdInTextField.setText(doc.stdIn());
		stdOutTextField.setText(doc.stdOut());
		stdErrTextField.setText(doc.stdErr());

		editorPane.setText(doc.body(),scriptFile);
		// what saving it now would write, so it doesn't count as changed
		original = getCode();
		updateTitle();
	}

	protected void actionSave() {

		final String code = getCode();
		if( code.isEmpty()) {
			showWarning("Nothing to save");
			return;
		}

		try {
			if( scriptFile == null) {
				actionSaveAs();
			} else {
				ScriptText.write(scriptFile, code);
				original = code;
				addRecent(scriptFile);
				updateTitle();
			}
		} catch (IOException e) {
			showError(e, "Can't save");
		}
	}

	protected void actionSaveAs() {
		final String code = getCode();
		if( code.isEmpty()) {
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

		if(fc.showSaveDialog(null) == FileSourceChooserDialog.APPROVE_OPTION) {
			FileSource previous = scriptFile;
			scriptFile = fc.getSelectedFile();
			actionSave();
			if( hasChanged()) {
				// not saved: keep the file the editor came from
				scriptFile = previous;
			}
		}




	}

	private void addRecent(FileSource file2) {
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

		if(fc.showOpenDialog(null) == FileSourceChooserDialog.APPROVE_OPTION) {
			loadFile(fc.getSelectedFile(), false);
		}


	}





	public void createAutoComplete() {
		editorPane.createAutoComplete();

	}



}
