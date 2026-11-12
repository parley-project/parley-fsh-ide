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
package us.bringardner.fsh.ide.fx;

import us.bringardner.fsh.ide.core.EditorFont;
import us.bringardner.fsh.ide.core.SyntaxColors;
import us.bringardner.fx.gradient.GradientLook;
import us.bringardner.fx.menu.StandardMenus;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.prefs.Preferences;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import us.bringardner.fsh.ShellContext;
import us.bringardner.fsh.ide.core.Breakpoint;
import us.bringardner.fsh.ide.core.CompileError;
import us.bringardner.fsh.ide.core.Configuration;
import us.bringardner.fsh.ide.core.DebugSession;
import us.bringardner.fsh.ide.core.DebugVariables;
import us.bringardner.fsh.ide.core.ExitChoice;
import us.bringardner.fsh.ide.core.LogBatcher;
import us.bringardner.fsh.ide.core.AdapterSession;
import us.bringardner.fsh.ide.core.LoupeSession;
import us.bringardner.fsh.ide.core.RemoteDebug;
import us.bringardner.fsh.ide.core.RecentFiles;
import us.bringardner.fsh.ide.core.RunSettings;
import us.bringardner.fsh.ide.core.RunSettingsStore;
import us.bringardner.fsh.ide.core.ScriptDocument;
import us.bringardner.fsh.ide.core.ScriptParser;
import us.bringardner.fsh.ide.core.ScriptRun;
import us.bringardner.fsh.ide.core.ScriptText;
import us.bringardner.fsh.ide.core.Variable;
import us.bringardner.parley.files.FileSource;
import us.bringardner.parley.files.FileSourceFactory;
import us.bringardner.parley.files.FileSourceFilter;
import us.bringardner.parley.files.fx.FileSourceChooser;

/**
 * A JavaFX fsh-ide window: edit a script, run it, and see its output in the console. Files
 * can be on any file system (the chooser can connect to remote ones). The workings are in
 * fsh-ide's core package, shared with the Swing IDE; the recent files list is shared too.
 */
public class IdeWindow {

	/** Where the recent files are kept: the Swing IDE's node, so both see the same list. */
	static final String PREFERENCES_NODE = "/us/bringardner/fsh/ide";
	static final String PREF_RECENT_LIST = "RecentList";
	static final String PREF_BOUNDS = "FxBounds";
	static final String PREF_SPELLING = "FxSpelling";
	static final String PREF_SHOW_OUTLINE = "FxShowOutline";

	static final ExecutorService BACKGROUND = Executors.newCachedThreadPool(r->{
		Thread t = new Thread(r, "fsh-ide-fx");
		t.setDaemon(true);
		return t;
	});

	private final Stage stage;
	private final Preferences prefs;
	private final ScriptEditor editor = new ScriptEditor();
	private final ConsoleView console = new ConsoleView();
	private final TextField arguments = new TextField();
	private final TextField stdIn = new TextField();
	private final TextField stdOut = new TextField();
	private final TextField stdErr = new TextField();
	private final Label status = new Label();
	private final Menu recentMenu = new Menu("Open Recent");
	private final CheckMenuItem useSelection = new CheckMenuItem("Run Selected Code Only");
	// the Swing IDE's icons, with what each does as its tooltip
	private final Button runButton = FxIcons.iconOnly(new Button(), "run.png", "Run");
	private final Button debugButton = FxIcons.iconOnly(new Button(), "debug.png", "Debug");
	private final Button stopButton = FxIcons.iconOnly(new Button(), "terminate.png", "Stop");
	private final Button resumeButton = FxIcons.iconOnly(new Button(), "resume.png", "Resume (F8)");
	private final Button stepOverButton = FxIcons.iconOnly(new Button(), "step_over.png", "Step Over (F6)");
	private final Button stepIntoButton = FxIcons.iconOnly(new Button(), "step_into.png", "Step Into (F5)");
	private final Button suspendButton = FxIcons.iconOnly(new Button(), "suspend.png", "Suspend: stop at the next statement");
	/** the debug controls, shown only while debugging */
	private final HBox debugControls = new HBox(6);
	private final MenuItem runItem = new MenuItem("Run");
	private final MenuItem debugItem = new MenuItem("Debug");
	private final MenuItem debugRemoteItem = new MenuItem("Debug Remote...");
	private final MenuItem stopItem = new MenuItem("Stop");
	private final MenuItem resumeItem = new MenuItem("Resume");
	private final MenuItem stepOverItem = new MenuItem("Step Over");
	private final MenuItem stepIntoItem = new MenuItem("Step Into");
	private final MenuItem suspendItem = new MenuItem("Suspend");
	private final CheckMenuItem showDebugPanel = new CheckMenuItem("Show Debug View");
	// the script's functions and variables, beside the editor (View > Show Outline)
	private final OutlineView outline = new OutlineView();
	private final CheckMenuItem showOutline = new CheckMenuItem("Show Outline");
	private final SplitPane editArea = new SplitPane();
	private final FindBar findBar = new FindBar(editor);
	private final CompletionPopup completion = new CompletionPopup(editor, ()->Configuration.getInstance().getTemplates());
	private final DebugPanel debugPanel = new DebugPanel();
	private final SplitPane mainSplit = new SplitPane();
	private final LogBatcher log = new LogBatcher(Platform::runLater, debugPanel::appendLog);
	// the syntax check runs once typing pauses
	private final PauseTransition syntaxCheckDelay = new PauseTransition(javafx.util.Duration.millis(300));
	private int syntaxCheckCount;
	// while debugging: the script's context, while it's paused
	private ShellContext pausedContext;
	private boolean debugging;

	private FileSource scriptFile;
	private FileSource lastFile;
	// the script as it was loaded or last saved, to tell whether it has changed
	// the script as it was read or last saved (code and run settings)
	private ScriptDocument original = ScriptDocument.parse("");
	private RecentFiles recent;
	private ScriptRun currentRun;
	// a debug run on a Loupe adapter (instead of currentRun's debug session), and whether it's stopped
	private volatile AdapterSession loupe;
	private volatile boolean loupePaused;
	private final CheckMenuItem useAdapter = new CheckMenuItem("Debug Through the Loupe Adapter");
	// why the script is stopping, when a watchpoint stopped it (script's thread)
	private volatile String watchpointStop;
	private final DebugSession debugSession = new DebugSession(line->editor.getBreakpoint(line), new DebugSession.Listener() {
		@Override
		public void watchpointHit(us.bringardner.fsh.ide.core.Watchpoint wp, String name, boolean write, Object value) {
			String hit = DebugSession.describeHit(name, write, value);
			watchpointStop = hit;
			log.add("Watchpoint: "+hit+"\n");
		}

		@Override
		public void paused(int line, ShellContext ctx, Map<String, Object> variables) {
			// (set on this thread just before, by watchpointHit, when a watchpoint stopped it)
			String hit = watchpointStop;
			watchpointStop = null;
			// (after Step Into: just the statement it stopped at, when the line has others)
			DebugSession.Statement statement = IdeWindow.this.debugSession.pausedStatement();
			Platform.runLater(()->{
				log.flush();
				pausedContext = ctx;
				if( statement == null || !editor.setCurrentStatement(line, statement.column, statement.length, statement.text)) {
					editor.setCurrentLine(line);
				}
				debugPanel.setVariables(variables, true);
				status.setText("Paused at line "+(line+1)+(hit == null ? "" : ": watchpoint, "+hit));
				updateControls();
			});
		}

		@Override
		public void statement(int line, String text) {
			log.add((line+1)+": "+text+"\n");
		}

		@Override
		public void conditionFailed(Breakpoint bp, int line, Exception error) {
			Platform.runLater(()->report("The condition of the breakpoint on line "+(line+1)+" failed", error));
		}

		@Override
		public void resuming() {
			// on the JavaFX thread: Resume or Step was pressed
			pausedContext = null;
			editor.setCurrentLine(-1);
			debugPanel.freezeVariables();
			status.setText("Running...");
			updateControls();
		}

		@Override
		public void terminateRequested() {
			Platform.runLater(IdeWindow.this::stop);
		}
	});

	public IdeWindow() {
		this(new Stage(), Preferences.userRoot().node(PREFERENCES_NODE));
	}

	IdeWindow(Stage stage, Preferences prefs) {
		this.stage = stage;
		registerThemePages();
		// the windows Quit closes
		OPEN.add(this);
		stage.addEventHandler(javafx.stage.WindowEvent.WINDOW_HIDDEN, e->OPEN.remove(this));
		this.prefs = prefs;
		// The Parley app icon with the terminal badge
		FxBrand.apply(stage);
		recent = RecentFiles.parse(prefs == null ? null : prefs.get(PREF_RECENT_LIST, null));

		BorderPane root = new BorderPane();
		javafx.scene.layout.FlowPane toolbar = toolbar();
		// wrap at the window's width, not FlowPane's default
		toolbar.prefWrapLengthProperty().bind(root.widthProperty().subtract(12));
		root.setTop(new VBox(menuBar(), toolbar));
		BorderPane editorPane = new BorderPane(editor);
		editorPane.setBottom(findBar);
		SplitPane split = new SplitPane(editorPane, console);
		split.setOrientation(Orientation.VERTICAL);
		split.setDividerPositions(0.65);
		// the outline at the left of the editor and console
		editArea.getItems().add(split);
		SplitPane.setResizableWithParent(outline, false);
		// a variable's right-click menu in the outline: watch it (before or while debugging)
		outline.setOnWatchVariable(name->{
			// the debug view shows, with its Watchpoints tab
			showDebugPanel.setSelected(true);
			debugPanel.addWatchpoint(name);
		});
		outline.setOnGoToLine(line->{
			editor.goToLine(line);
			editor.getCodeArea().requestFocus();
		});
		showOutline.selectedProperty().addListener((o, was, is)->{
			if( is && !editArea.getItems().contains(outline)) {
				editArea.getItems().add(0, outline);
				editArea.setDividerPositions(0.2);
			} else if( !is ) {
				editArea.getItems().remove(outline);
			}
			if( prefs != null ) {
				prefs.putBoolean(PREF_SHOW_OUTLINE, is);
			}
		});
		showOutline.setSelected(prefs == null || prefs.getBoolean(PREF_SHOW_OUTLINE, true));
		mainSplit.getItems().add(editArea);
		root.setCenter(mainSplit);
		showDebugPanel.selectedProperty().addListener((o, was, is)->{
			if( is && !mainSplit.getItems().contains(debugPanel)) {
				mainSplit.getItems().add(debugPanel);
				mainSplit.setDividerPositions(0.68);
			} else if( !is ) {
				mainSplit.getItems().remove(debugPanel);
			}
			showStartVariables();
		});
		status.setPadding(new Insets(2, 6, 2, 6));
		root.setBottom(status);

		Scene scene = new Scene(root, 1100, 760);
		// the gradient look shared with parley-files-viewer, then the IDE's own editor and console styles
		GradientLook.apply(scene);
		scene.getStylesheets().add(IdeWindow.class.getResource("fsh-ide.css").toExternalForm());
		scene.getStylesheets().add(FxFonts.stylesheet());
		applySyntaxColors(scene, us.bringardner.swing.gradient.Theme.current());
		applyEditorFont(scene, EditorFont.load(prefs));
		scene.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, this::textSizeKeys);
		stage.setScene(scene);
		restoreBounds();
		stage.setOnCloseRequest(e->{
			if( !close()) {
				e.consume();
			}
		});

		editor.getCodeArea().textProperty().addListener((o, was, is)->{
			updateTitle();
			syntaxCheckDelay.playFromStart();
		});
		syntaxCheckDelay.setOnFinished(e->checkSyntax());
		editor.setContextMenuProvider(this::runMenu);
		// typing while a script runs: the status line says why nothing happens
		editor.getCodeArea().addEventFilter(javafx.scene.input.KeyEvent.KEY_TYPED, e->{
			if( !e.isShortcutDown() && !e.isControlDown() && !e.isAltDown() && !e.getCharacter().isEmpty()
					&& e.getCharacter().charAt(0) >= ' ' ) {
				canEdit();
			}
		});
		editor.addBreakpointListener(()->debugPanel.setBreakpoints(editor.getBreakpoints().values()));
		editor.setOnBreakpointMenu((bp, e)->editBreakpoint(bp));
		debugPanel.setOnBreakpointChanged(bp->editor.breakpointChanged());
		debugPanel.setOnEditBreakpoint(this::editBreakpoint);
		debugPanel.setOnEditVariable(this::editVariable);
		debugPanel.setWatchpoints(debugSession.getWatchpoints());
		debugPanel.setOnGoToLine(line->{
			editor.goToLine(line);
			editor.getCodeArea().requestFocus();
		});
		for(TextField f : new TextField[] {arguments, stdIn, stdOut, stdErr}) {
			f.textProperty().addListener((o, was, is)->updateTitle());
		}
		updateControls();
		buildRecentMenu();
		updateTitle();
	}

	/**
	 * The window's menus, laid out as in every Parley application (fx-widgets' StandardMenus): File,
	 * Edit, View, Run, Window and Help, with About and Quit in the application menu on macOS and in
	 * File and Help elsewhere. The same as the Swing IDE's.
	 */
	private MenuBar menuBar() {
		StandardMenus menus = new StandardMenus(APP_NAME).about(()->AboutStage.open(stage)).settings(this::actionSettings)
				.quit(IdeWindow::okToQuit);

		menus.file.getItems().addAll(item("New", "Shortcut+N", this::actionNew),
				item("Open...", "Shortcut+O", this::actionOpen), recentMenu, new SeparatorMenuItem(),
				item("Save", "Shortcut+S", this::actionSave), item("Save As...", "Shortcut+Shift+S", this::actionSaveAs),
				item("Reload", null, this::actionReload), new SeparatorMenuItem(),
				item("New Window", "Shortcut+Shift+N", ()->new IdeWindow().show()),
				item("Close", "Shortcut+W", ()->{
					if( close()) {
						stage.close();
					}
				}));

		CheckMenuItem spelling = new CheckMenuItem("Check Spelling");
		spelling.setSelected(prefs == null || prefs.getBoolean(PREF_SPELLING, true));
		editor.setSpellChecking(spelling.isSelected());
		spelling.selectedProperty().addListener((o, was, is)->{
			editor.setSpellChecking(is);
			if( prefs != null ) {
				prefs.putBoolean(PREF_SPELLING, is);
			}
		});
		menus.edit.getItems().addAll(item("Undo", "Shortcut+Z", ()->{
					if( canEdit()) {
						editor.getCodeArea().undo();
					}
				}),
				item("Redo", "Shortcut+Shift+Z", ()->{
					if( canEdit()) {
						editor.getCodeArea().redo();
					}
				}), new SeparatorMenuItem(),
				// on whichever text has the focus: the editor, the arguments, the console
				item("Cut", "Shortcut+X", ()->onFocusedText("cut")), item("Copy", "Shortcut+C", ()->onFocusedText("copy")),
				item("Paste", "Shortcut+V", ()->onFocusedText("paste")),
				item("Select All", "Shortcut+A", ()->onFocusedText("selectAll")), new SeparatorMenuItem(),
				item("Find...", "Shortcut+F", ()->findBar.show(false)), item("Find Next", "Shortcut+G", ()->findBar.findNext(true)),
				item("Find Previous", "Shortcut+Shift+G", ()->findBar.findNext(false)),
				item("Replace...", "Shortcut+Alt+F", ()->findBar.show(true)),
				item("Go to Line...", "Shortcut+L", this::actionGoToLine), new SeparatorMenuItem(),
				item("Complete", "Ctrl+Space", ()->{
					if( canEdit()) {
						completion.show();
					}
				}), new SeparatorMenuItem(), spelling);

		// View: the editor's and console's font and text size, and folding
		menus.view.getItems().addAll(item("Bigger Text", "Shortcut+PLUS", this::biggerText),
				item("Smaller Text", "Shortcut+MINUS", this::smallerText),
				item("Reset Text Size", "Shortcut+DIGIT0", this::resetSize),
				item("Editor Font...", null, this::actionEditorFont), new SeparatorMenuItem(),
				showOutline, showDebugPanel, new SeparatorMenuItem(),
				// folding on [ and ], with Alt (Option): + and - are the text size
				item("Fold", "Shortcut+Alt+OPEN_BRACKET", editor::foldAtCaret),
				item("Unfold", "Shortcut+Alt+CLOSE_BRACKET", editor::unfoldAtCaret),
				item("Fold All", "Shortcut+Alt+Shift+OPEN_BRACKET", editor::foldAll),
				item("Unfold All", "Shortcut+Alt+Shift+CLOSE_BRACKET", editor::unfoldAll));

		runItem.setAccelerator(KeyCombination.keyCombination("Shortcut+R"));
		runItem.setOnAction(e->run());
		// shift: as in the Swing IDE, whose editor keeps ⌘D for deleting a line
		debugItem.setAccelerator(KeyCombination.keyCombination("Shortcut+Shift+D"));
		debugItem.setOnAction(e->debug());
		debugRemoteItem.setOnAction(e->debugRemote());
		stopItem.setAccelerator(KeyCombination.keyCombination("Shortcut+PERIOD"));
		stopItem.setOnAction(e->stop());
		resumeItem.setAccelerator(KeyCombination.keyCombination("F8"));
		resumeItem.setOnAction(e->resumeScript());
		stepOverItem.setAccelerator(KeyCombination.keyCombination("F6"));
		stepOverItem.setOnAction(e->stepOverScript());
		stepIntoItem.setAccelerator(KeyCombination.keyCombination("F5"));
		stepIntoItem.setOnAction(e->stepIntoScript());
		suspendItem.setOnAction(e->suspendScript());
		Menu run = new Menu("Run", null, runItem, debugItem, debugRemoteItem, stopItem, new SeparatorMenuItem(),
				resumeItem, stepOverItem, stepIntoItem, suspendItem, new SeparatorMenuItem(),
				item("Toggle Breakpoint", "Shortcut+B", ()->editor.toggleBreakpoint(editor.getCaretLine())),
				new SeparatorMenuItem(), useSelection, useAdapter, new SeparatorMenuItem(),
				item("Clear Console", "Shortcut+K", console::clear), new SeparatorMenuItem(),
				item("Clear Run Settings...", null, this::actionClearRunSettings));
		// right-click on the console: Clear Console, as in the Run menu
		console.getArea().setContextMenu(new ContextMenu(item("Clear Console", null, console::clear)));

		// what environment scripts get (see ShellEnvironment)
		menus.help.getItems().add(item("Script Environment...", null, this::showScriptEnvironment));

		return menus.build(stage, run);
	}

	/** Help > Script Environment...: what environment scripts run with, and why. */
	void showScriptEnvironment() {
		// (it may wait for the login shell)
		new Thread(()->{
			String report = us.bringardner.fsh.ide.core.ShellEnvironment.report();
			Platform.runLater(()->{
				javafx.scene.control.Alert a = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
				a.initOwner(stage);
				a.setTitle("Script Environment");
				a.setHeaderText("Script Environment");
				javafx.scene.control.TextArea text = new javafx.scene.control.TextArea(report);
				text.setEditable(false);
				text.setPrefColumnCount(80);
				text.setPrefRowCount(28);
				a.getDialogPane().setContent(text);
				a.setResizable(true);
				a.show();
			});
		}, "script-environment").start();
	}

	/** The application's name: the macOS application menu and Help > About show it. */
	static final String APP_NAME = "fsh-ide";

	/** The IDE windows open now, for Quit. */
	private static final java.util.List<IdeWindow> OPEN = new java.util.ArrayList<>();

	/** Quit (Exit): closes every IDE window, each asking about unsaved changes and a running script; false if one stays open. */
	static boolean okToQuit() {
		for(IdeWindow w : new java.util.ArrayList<>(OPEN)) {
			if( !w.close() ) {
				return false;
			}
			w.stage.close();
		}
		return true;
	}

	/** Cut, Copy, Paste or Select All on the text with the focus: a field, or else the editor. */
	private void onFocusedText(String action) {
		javafx.scene.Node n = stage.getScene() == null ? null : stage.getScene().getFocusOwner();
		if( n instanceof javafx.scene.control.TextInputControl ) {
			javafx.scene.control.TextInputControl t = (javafx.scene.control.TextInputControl) n;
			switch (action) {
			case "cut": t.cut(); break;
			case "copy": t.copy(); break;
			case "paste": t.paste(); break;
			default: t.selectAll(); break;
			}
		} else {
			org.fxmisc.richtext.CodeArea t = editor.getCodeArea();
			switch (action) {
			case "cut":
				if( canEdit()) {
					t.cut();
				}
				break;
			case "copy": t.copy(); break;
			case "paste":
				if( canEdit()) {
					t.paste();
				}
				break;
			default: t.selectAll(); break;
			}
		}
	}


	private static MenuItem item(String text, String accelerator, Runnable action) {
		MenuItem item = new MenuItem(text);
		if( accelerator != null ) {
			item.setAccelerator(KeyCombination.keyCombination(accelerator));
		}
		item.setOnAction(e->action.run());
		return item;
	}

	private javafx.scene.layout.FlowPane toolbar() {
		runButton.setOnAction(e->run());
		debugButton.setOnAction(e->debug());
		stopButton.setOnAction(e->stop());
		resumeButton.setOnAction(e->resumeScript());
		stepOverButton.setOnAction(e->stepOverScript());
		stepIntoButton.setOnAction(e->stepIntoScript());
		suspendButton.setOnAction(e->suspendScript());
		arguments.setPromptText("Script arguments");
		arguments.setPrefColumnCount(18);
		// in the Swing IDE's order: Debug and Run (and Stop), the script's settings, then the debug
		// controls (Resume, Suspend, Step Into, Step Over); they wrap to a second row when the window is narrow
		HBox controls = new HBox(6, debugButton, runButton, stopButton);
		HBox settings = new HBox(6, new Label("Arguments:"), arguments,
				redirect("stdin", stdIn, false), redirect("stdout", stdOut, true), redirect("stderr", stdErr, true));
		debugControls.getChildren().setAll(resumeButton, suspendButton, stepIntoButton, stepOverButton);
		for(HBox b : new HBox[] {controls, settings, debugControls}) {
			b.setStyle("-fx-alignment: center-left;");
			keepWidth(b);
		}
		javafx.scene.layout.FlowPane bar = new javafx.scene.layout.FlowPane(14, 4, controls, settings, debugControls);
		bar.setPadding(new Insets(4, 6, 4, 6));
		return bar;
	}

	/** Buttons and labels in box keep their full width; only the text fields shrink. */
	private static void keepWidth(javafx.scene.Parent box) {
		for(javafx.scene.Node n : box.getChildrenUnmodifiable()) {
			if( n instanceof Button || n instanceof Label ) {
				((javafx.scene.control.Control) n).setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
			} else if( n instanceof HBox ) {
				keepWidth((HBox) n);
			}
		}
	}

	/** A redirect file field with a button to choose the file. */
	private HBox redirect(String name, TextField field, boolean output) {
		field.setPromptText("console");
		field.setPrefColumnCount(8);
		field.setTooltip(new Tooltip("A file for "+name+"; empty for the console"));
		Button browse = FxIcons.iconOnly(new Button(), output ? "redirect_out.png" : "redirect_in.png", "Choose a file for "+name);
		browse.setOnAction(e->{
			FileSourceChooser fc = new FileSourceChooser();
			fc.setTitle(name);
			FileSource current = fileOrNull(field.getText());
			if( current != null ) {
				try {
					fc.setInitialDirectory(current.getParentFile());
				} catch (IOException ex) {
				}
				fc.setInitialFileName(current.getName());
			}
			FileSource chosen = output ? fc.showSaveDialog(stage) : fc.showOpenDialog(stage);
			if( chosen != null ) {
				field.setText(chosen.getAbsolutePath());
			}
		});
		// (the button before the field, as in the Swing IDE)
		HBox box = new HBox(2, new Label(name+":"), browse, field);
		box.setStyle("-fx-alignment: center-left;");
		return box;
	}

	private static FileSource fileOrNull(String path) {
		if( path == null || path.isBlank()) {
			return null;
		}
		try {
			return FileSourceFactory.getDefaultFactory().createFileSource(path.trim());
		} catch (IOException e) {
			return null;
		}
	}

	// ---- the script

	/** The user's store of scripts' arguments and redirects; tests use their own. */
	protected RunSettingsStore runSettings() {
		return Configuration.getInstance().runSettings();
	}

	/** The script as it would be saved: the code, and the run settings kept for it. */
	ScriptDocument getDocument() {
		// (while another machine's script is shown for a debug session, the document is still the user's own)
		RemoteView view = remoteView;
		return new ScriptDocument(view != null ? view.body : editor.getText(), new RunSettings(arguments.getText(), stdIn.getText(),
				stdOut.getText(), stdErr.getText()));
	}

	boolean hasChanged() {
		return !original.equals(getDocument());
	}

	/** Shows a script's text, with only the settings it carries itself (a template, a new script ...). */
	void setCode(String code, FileSource file) {
		setDocument(ScriptDocument.parse(code), file);
	}

	/**
	 * While the debug view shows and no script runs, its fsh Variables tab shows what the next
	 * run starts with (when a run ends, the view opens, or the script's file, its $0, changes).
	 */
	private void showStartVariables() {
		if( !showDebugPanel.isSelected() || isRunning()) {
			return;
		}
		// (once the login shell's environment is known, so this doesn't wait for it here)
		us.bringardner.fsh.ide.core.ShellEnvironment.whenKnown(()->Platform.runLater(()->{
			if( showDebugPanel.isSelected() && !isRunning()) {
				debugPanel.setStartVariables(ScriptRun.startVariables(
						scriptFile != null ? scriptFile.getAbsolutePath() : "fsh", arguments.getText()));
			}
		}));
	}

	/** Shows a script read from file (null for a new script). */
	void setDocument(ScriptDocument doc, FileSource file) {
		// (a script opened over another machine's: there is nothing to put back)
		remoteView = null;
		scriptFile = file;
		showStartVariables();
		arguments.setText(doc.arguments());
		stdIn.setText(doc.stdIn());
		stdOut.setText(doc.stdOut());
		stdErr.setText(doc.stdErr());
		editor.setText(doc.body());
		// so it doesn't count as changed (settings lines from an earlier version go when it's saved)
		original = getDocument();
		updateTitle();
	}

	/** Forgets the arguments and redirects kept for every script (this window's stay until saved). */
	void actionClearRunSettings() {
		RunSettingsStore store = runSettings();
		BACKGROUND.execute(()->{
			try {
				int n = store.size();
				Platform.runLater(()->{
					if( n == 0 ) {
						status.setText("No scripts have arguments or redirects kept.");
					} else if( confirm("Forget the arguments and redirects kept for "+n+(n == 1 ? " script?" : " scripts?"))) {
						BACKGROUND.execute(()->{
							try {
								store.clear();
								Platform.runLater(()->{
									// the open windows' too: what they show is what's kept, and saving them would keep it again
									for(IdeWindow w : new java.util.ArrayList<>(OPEN)) {
										w.clearRunSettingsFields();
									}
									status.setText("Cleared the run settings of "+n+(n == 1 ? " script." : " scripts."));
								});
							} catch (IOException e) {
								Platform.runLater(()->report("Can't clear the run settings", e));
							}
						});
					}
				});
			} catch (IOException e) {
				Platform.runLater(()->report("Can't read the run settings", e));
			}
		});
	}

	/**
	 * Clear Run Settings, in this window: no arguments or redirects; as the script was kept, so it
	 * doesn't count as changed (its code still does, if it was).
	 */
	void clearRunSettingsFields() {
		arguments.setText("");
		stdIn.setText("");
		stdOut.setText("");
		stdErr.setText("");
		original = new ScriptDocument(original.body(), RunSettings.NONE);
		updateTitle();
	}

	private void updateTitle() {
		RemoteView view = remoteView;
		if( view != null ) {
			stage.setTitle(view.title+" - fsh-ide");
			return;
		}
		String name = scriptFile == null ? "Untitled" : scriptFile.getName();
		stage.setTitle((hasChanged() ? "* " : "")+name+" - fsh-ide");
	}

	/** True if there are no unsaved changes or the user agrees to discard them. */
	boolean okToDiscard() {
		return !hasChanged() || confirm("There are unsaved changes. Discard them?");
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
			return saveNow();
		default:
			return false;
		}
	}

	/** Asks what to do with unsaved changes when the window closes; tests override it. */
	protected ExitChoice askExit() {
		Alert a = exitQuestion();
		// closing the question is Cancel
		return a.showAndWait().map(IdeWindow::exitChoice).orElse(ExitChoice.CANCEL);
	}

	/** The exit question: Cancel, Discard and exit, Save and exit, in that order on every platform. */
	Alert exitQuestion() {
		ButtonType cancel = new ButtonType("Cancel", ButtonData.CANCEL_CLOSE);
		ButtonType discard = new ButtonType("Discard and exit", ButtonData.OTHER);
		ButtonType save = new ButtonType("Save and exit", ButtonData.OK_DONE);
		Alert a = new Alert(AlertType.WARNING, "There are unsaved changes.", cancel, discard, save);
		a.setTitle("Unsaved Changes");
		a.setHeaderText(null);
		a.initOwner(stage);
		FxBrand.apply(a);
		// not each platform's own order
		if( a.getDialogPane().lookup(".button-bar") instanceof ButtonBar ) {
			((ButtonBar) a.getDialogPane().lookup(".button-bar")).setButtonOrder(ButtonBar.BUTTON_ORDER_NONE);
		}
		return a;
	}

	/** The choice an exit question's button stands for. */
	static ExitChoice exitChoice(ButtonType b) {
		switch (b.getButtonData()) {
		case OK_DONE: return ExitChoice.SAVE;
		case OTHER: return ExitChoice.DISCARD;
		default: return ExitChoice.CANCEL;
		}
	}

	/**
	 * Saves the script now, not in the background, for Save and exit: asks for a file if it has
	 * none; false if it isn't saved (no file chosen, or an error, which is reported).
	 */
	boolean saveNow() {
		FileSource file = scriptFile;
		if( file == null ) {
			file = chooseSaveFile();
			if( file == null ) {
				return false;
			}
		}
		ScriptDocument doc = getDocument();
		try {
			saved(file, doc, doc.save(file, runSettings()));
			return true;
		} catch (IOException e) {
			status.setText("");
			report("Can't save "+file.getAbsolutePath(), e);
			return false;
		}
	}

	/** Asks for the file to save to (Save As); null if none is chosen. Tests override it. */
	protected FileSource chooseSaveFile() {
		FileSourceChooser fc = chooser();
		fc.setInitialFileName(scriptFile == null ? "script.sh" : scriptFile.getName());
		return fc.showSaveDialog(stage);
	}

	/** Asks the user; tests override it. */
	protected boolean confirm(String question) {
		Alert a = new Alert(AlertType.CONFIRMATION, question, ButtonType.YES, ButtonType.NO);
		a.initOwner(stage);
		FxBrand.apply(a);
		return a.showAndWait().filter(b->b == ButtonType.YES).isPresent();
	}

	/** Tells the user about a problem; tests override it. */
	protected void report(String title, Throwable error) {
		Alert a = new Alert(AlertType.ERROR, error == null ? "" : String.valueOf(error.getMessage()));
		a.setHeaderText(title);
		a.initOwner(stage);
		FxBrand.apply(a);
		a.showAndWait();
	}

	void actionNew() {
		if( okToDiscard()) {
			if( scriptFile != null ) {
				lastFile = scriptFile;
			}
			setCode("", null);
		}
	}

	private FileSourceChooser chooser() {
		FileSourceChooser fc = new FileSourceChooser();
		FileSource near = scriptFile != null ? scriptFile : lastFile;
		if( near != null ) {
			try {
				fc.setInitialDirectory(near.getParentFile());
			} catch (IOException e) {
			}
		}
		fc.getFilters().add(new FileSourceFilter() {
			@Override
			public boolean accept(FileSource f) {
				String n = f.getName();
				return n.endsWith(".sh") || n.endsWith(".fsh") || n.endsWith(".bash") || !n.contains(".");
			}

			@Override
			public String getDescription() {
				return "Shell scripts (*.sh, *.fsh, *.bash)";
			}
		});
		return fc;
	}

	void actionOpen() {
		if( okToDiscard()) {
			FileSource file = chooser().showOpenDialog(stage);
			if( file != null ) {
				load(file);
			}
		}
	}

	void actionReload() {
		FileSource file = scriptFile != null ? scriptFile : lastFile;
		if( file != null && okToDiscard()) {
			load(file);
		}
	}

	/** Reads file in the background (it may be remote), then shows it. */
	void load(FileSource file) {
		status.setText("Reading "+file.getAbsolutePath()+"...");
		BACKGROUND.execute(()->{
			try {
				String code = ScriptText.read(file);
				ScriptDocument doc;
				try {
					doc = ScriptDocument.open(file, code, runSettings());
				} catch (IOException e) {
					// the script without its settings, rather than not at all
					doc = ScriptDocument.parse(code);
				}
				ScriptDocument shown = doc;
				Platform.runLater(()->{
					setDocument(shown, file);
					addRecent(file);
					status.setText(file.getAbsolutePath());
				});
			} catch (IOException e) {
				Platform.runLater(()->{
					status.setText("");
					report("Can't read "+file.getAbsolutePath(), e);
				});
			}
		});
	}

	void actionSave() {
		if( scriptFile == null ) {
			actionSaveAs();
		} else {
			save(scriptFile);
		}
	}

	void actionSaveAs() {
		FileSource file = chooseSaveFile();
		if( file != null ) {
			save(file);
		}
	}

	/**
	 * Writes the script to file in the background, and keeps its arguments and redirects in the
	 * user's store (or removes them from it, if they were cleared); it's marked saved once the
	 * file is written.
	 */
	void save(FileSource file) {
		ScriptDocument doc = getDocument();
		RunSettingsStore store = runSettings();
		status.setText("Saving "+file.getAbsolutePath()+"...");
		BACKGROUND.execute(()->{
			try {
				String warning = doc.save(file, store);
				Platform.runLater(()->saved(file, doc, warning));
			} catch (IOException e) {
				Platform.runLater(()->{
					status.setText("");
					report("Can't save "+file.getAbsolutePath(), e);
				});
			}
		});
	}

	/** After doc is written to file: it's the window's script, and no longer changed. */
	private void saved(FileSource file, ScriptDocument doc, String warning) {
		scriptFile = file;
		showStartVariables();
		original = doc;
		addRecent(file);
		status.setText(warning != null ? warning : "Saved "+file.getAbsolutePath());
		updateTitle();
	}

	// ---- recent files

	private void addRecent(FileSource file) {
		recent.add(file.getAbsolutePath(), Configuration.getInstance().getMaxRecent());
		saveRecent();
		buildRecentMenu();
	}

	private void saveRecent() {
		if( prefs != null ) {
			prefs.put(PREF_RECENT_LIST, recent.format());
		}
	}

	private void buildRecentMenu() {
		recentMenu.getItems().clear();
		for(String path : recent.list()) {
			MenuItem item = new MenuItem(path);
			item.setMnemonicParsing(false);
			item.setOnAction(e->{
				FileSource f = fileOrNull(path);
				if( f != null && okToDiscard()) {
					load(f);
				}
			});
			recentMenu.getItems().add(item);
		}
		recentMenu.setDisable(recent.list().isEmpty());
	}

	/**
	 * Drops recent files that no longer exist; checked in the background (they may be remote).
	 * The run settings of scripts that are gone, or long unused, are dropped too.
	 */
	private void pruneRecent() {
		List<String> names = recent.list();
		RunSettingsStore store = runSettings();
		BACKGROUND.execute(()->{
			try {
				store.prune();
			} catch (IOException e) {
				// tried again next time
			}
			List<String> missing = RecentFiles.findMissing(names);
			if( !missing.isEmpty()) {
				Platform.runLater(()->{
					if( recent.removeAll(missing)) {
						saveRecent();
						buildRecentMenu();
					}
				});
			}
		});
	}

	// ---- running

	/**
	 * Runs the script (or just the selected code, if that's chosen and there is some). While a
	 * debugged script is paused: lets it run to its end without stopping at breakpoints.
	 */
	void run() {
		if( isPaused()) {
			runToEnd();
		} else {
			start(false);
		}
	}

	/** Lets the paused script finish without stopping at any more breakpoints. */
	void runToEnd() {
		AdapterSession l = loupe;
		if( l == null ) {
			if( pausedContext != null ) {
				debugSession.runToEnd();
			}
		} else if( loupePaused ) {
			// the adapter is sent no breakpoints, so it doesn't stop again
			adapterContinues(l, java.util.List.of(), ()->l.resume());
		}
	}

	/** Runs the script, stopping at breakpoints, with the debug panel shown. */
	void debug() {
		start(true);
	}

	private void start(boolean debug) {
		if( isRunning()) {
			return;
		}
		String all = ScriptText.clean(editor.getText());
		String selected = ScriptText.clean(editor.getSelectedText());
		boolean selection = useSelection.isSelected() && selected != null;
		console.clear();
		// what environment scripts get, in the Log tab (after a debug run's clearing of it)
		Platform.runLater(()->us.bringardner.fsh.ide.core.ShellEnvironment.logStatus(log::add));
		if( debug && useAdapter.isSelected()) {
			startWithAdapter(selection ? ScriptText.atLine(selected, editor.getSelectionStartLine()) : all, null);
			return;
		}
		debugging = debug;
		debugSession.setActive(debug);
		if( debug ) {
			for(Breakpoint bp : editor.getBreakpoints().values()) {
				bp.reset();
			}
			log.clear();
			debugPanel.clearLog();
			debugPanel.clearVariables();
			showDebugPanel.setSelected(true);
		}
		// a selection runs on its own lines, so its line numbers are the script's
		debugSession.setFirstLine(0);
		ScriptRun run = new ScriptRun(selection ? ScriptText.atLine(selected, editor.getSelectionStartLine()) : all, (r, exitCode, ctx, error)->{
			// the variables as the script left them, read on its thread
			Map<String,Object> finalVariables = debug && ctx != null ? ctx.getVariables() : null;
			Platform.runLater(()->{
				if( error != null ) {
					report("The script couldn't run", error);
				}
				if( currentRun == r ) {
					currentRun = null;
					pausedContext = null;
					editor.setCurrentLine(-1);
					log.flush();
					if( finalVariables != null ) {
						debugPanel.setVariables(finalVariables, false);
					}
					// (the fsh Variables tab: what the next run starts with)
					showStartVariables();
					updateControls();
					status.setText((r.isCanceled() ? "Stopped" : "Finished")+", exit code "+exitCode);
				}
			});
		});
		run.scriptName(scriptFile != null ? scriptFile.getAbsolutePath() : "fsh")
			.arguments(arguments.getText())
			.redirects(stdIn.getText(), stdOut.getText(), stdErr.getText())
			.console(console.getStdIn(), console.getStdOut(), console.getStdErr())
			.debug(debugSession);
		currentRun = run;
		updateControls();
		status.setText(debug ? "Debugging..." : "Running...");
		run.start();
	}

	/** The debug controls: for the script on the adapter if there is one, else the in-process session. */
	private void resumeScript() {
		AdapterSession l = loupe;
		if( l == null ) {
			debugSession.resume();
		} else {
			adapterContinues(l, ()->l.resume());
		}
	}

	private void stepOverScript() {
		AdapterSession l = loupe;
		if( l == null ) {
			debugSession.stepOver();
		} else {
			adapterContinues(l, ()->l.stepOver());
		}
	}

	private void stepIntoScript() {
		AdapterSession l = loupe;
		if( l == null ) {
			debugSession.stepInto();
		} else {
			adapterContinues(l, ()->l.stepInto());
		}
	}

	private void suspendScript() {
		AdapterSession l = loupe;
		if( l == null ) {
			debugSession.suspend();
		} else {
			l.suspend();
		}
	}

	/** Sends the editor's breakpoints, then the request that lets the script go on. */
	private void adapterContinues(AdapterSession l, java.util.function.Supplier<java.util.concurrent.CompletableFuture<Void>> request) {
		adapterContinues(l, new java.util.ArrayList<>(editor.getBreakpoints().values()), request);
	}

	/** Sends these breakpoints, then the request that lets the script go on. */
	private void adapterContinues(AdapterSession l, java.util.List<Breakpoint> bps, java.util.function.Supplier<java.util.concurrent.CompletableFuture<Void>> request) {
		loupePaused = false;
		editor.setCurrentLine(-1);
		debugPanel.freezeVariables();
		status.setText("Running...");
		updateControls();
		BACKGROUND.execute(()->{
			try {
				l.syncBreakpoints(bps);
				request.get().get();
			} catch (Exception e) {
				Platform.runLater(()->report("The adapter couldn't continue", e));
			}
		});
	}

	/**
	 * Run &gt; Debug Remote...: asks where and what (see {@link RemoteDebug}), and debugs there.
	 */
	void debugRemote() {
		if( isRunning()) {
			return;
		}
		askRemoteDebug(us.bringardner.fsh.ide.core.Configuration.getInstance().getRemoteDebug()).ifPresent(this::debugRemote);
	}

	/** Asks for Debug Remote's settings (empty: canceled); tests override it. */
	protected java.util.Optional<RemoteDebug> askRemoteDebug(RemoteDebug last) {
		return RemoteDebugDialog.ask(stage, last);
	}

	/** Debugs on another machine as how says, and keeps how for the next time. */
	void debugRemote(RemoteDebug how) {
		if( isRunning()) {
			return;
		}
		us.bringardner.fsh.ide.core.Configuration config = us.bringardner.fsh.ide.core.Configuration.getInstance();
		config.setRemoteDebug(how);
		BACKGROUND.execute(()->{
			try {
				config.save();
			} catch (IOException e) {
				Platform.runLater(()->report("Can't keep the Debug Remote settings", e));
			}
		});
		console.clear();
		startWithAdapter(ScriptText.clean(editor.getText()), how.copy());
	}

	/**
	 * What the editor held while it shows another machine's script for a debug session (a script
	 * file there, or one a debugger attached to): the user's own script and its breakpoints, put
	 * back when the session ends.
	 */
	private static final class RemoteView {
		final String body;
		final String title;
		final java.util.List<Breakpoint> breakpoints;
		final int caret;

		RemoteView(String body, String title, java.util.List<Breakpoint> breakpoints, int caret) {
			this.body = body;
			this.title = title;
			this.breakpoints = breakpoints;
			this.caret = caret;
		}
	}

	private RemoteView remoteView;

	/** Shows the script being debugged on another machine in place of the user's (which is kept). */
	private void showRemoteScript(RemoteDebug how, AdapterSession.Started started) {
		String body = editor.getText();
		java.util.List<Breakpoint> bps = new java.util.ArrayList<>(editor.getBreakpoints().values());
		int caret = editor.getCodeArea().getCaretPosition();
		String name = started.getPath() == null ? "script" : started.getPath();
		// (set before the text: the document is the user's own from here on, see getDocument)
		remoteView = new RemoteView(body, name+" on "+how.where(), bps, caret);
		editor.setText(started.getText());
		updateTitle();
	}

	/** Puts the user's own script (and its breakpoints) back after a session that showed another machine's. */
	private void restoreOwnScript() {
		RemoteView view = remoteView;
		if( view == null ) {
			return;
		}
		editor.setText(view.body);
		for(Breakpoint old : view.breakpoints) {
			Breakpoint bp = editor.addBreakpoint(old.getLine());
			if( bp != null ) {
				bp.setEnabled(old.isEnabled(false));
				bp.setConditional(old.isConditional());
				bp.setCondition(old.getCondition());
				bp.setHitCount(old.isHitCount());
				bp.setHitCount(old.getHitCount());
			}
		}
		if( !view.breakpoints.isEmpty()) {
			editor.breakpointChanged();
		}
		editor.getCodeArea().moveTo(Math.min(view.caret, editor.getText().length()));
		remoteView = null;
		updateTitle();
	}

	/** True while the editor shows a script that is being debugged on another machine, not the user's own. */
	boolean showsRemoteScript() {
		return remoteView != null;
	}

	/**
	 * Debugs on a Loupe adapter instead of in-process: the fsh adapter in this JVM (remote null),
	 * or as remote says, on another machine.
	 */
	private void startWithAdapter(String code, RemoteDebug remote) {
		boolean runsAScript = remote == null || remote.getMode() != RemoteDebug.Mode.ATTACH;
		if( runsAScript && (!stdIn.getText().isBlank() || !stdOut.getText().isBlank() || !stdErr.getText().isBlank())) {
			status.setText("The adapter doesn't redirect the script's streams: clear the redirect fields");
			return;
		}
		debugging = true;
		debugSession.setActive(false);
		for(Breakpoint bp : editor.getBreakpoints().values()) {
			bp.reset();
		}
		log.clear();
		debugPanel.clearLog();
		debugPanel.clearVariables();
		showDebugPanel.setSelected(true);
		AdapterSession.Listener listener = new AdapterSession.Listener() {
				@Override
				public void paused(int line, String reason, Map<String,Object> variables) {
					Platform.runLater(()->{
						loupePaused = true;
						editor.setCurrentLine(line);
						debugPanel.setVariables(variables, true);
						status.setText("Paused at line "+(line+1));
						updateControls();
					});
				}

				@Override
				public void output(String category, String text) {
					Platform.runLater(()->("stderr".equals(category) ? console.getStdErr() : console.getStdOut()).print(text));
				}

				@Override
				public void breakpoint(Breakpoint bp, String status, int line, String message) {
					if( "rejected".equals(status)) {
						Platform.runLater(()->IdeWindow.this.status.setText("Breakpoint on line "+(bp.getLine()+1)+": "+message));
					}
				}

				@Override
				public void finished(int exitCode, boolean killed, Exception error) {
					Platform.runLater(()->{
						AdapterSession l = loupe;
						loupe = null;
						loupePaused = false;
						editor.setCurrentLine(-1);
						debugPanel.freezeVariables();
						restoreOwnScript();
						showStartVariables();
						if( error != null ) {
							report("The debug session failed", error);
						}
						updateControls();
						status.setText((killed ? "Stopped" : "Finished")+(exitCode < 0 && killed ? "" : ", exit code "+exitCode));
						if( l != null ) {
							l.close();
						}
					});
				}
		};
		String name = scriptFile != null ? scriptFile.getAbsolutePath() : "fsh";
		java.util.List<String> args = us.bringardner.fsh.ide.core.ScriptText.splitArguments(arguments.getText());
		java.util.List<Breakpoint> bps = new java.util.ArrayList<>(editor.getBreakpoints().values());
		if( remote == null ) {
			LoupeSession session;
			try {
				session = LoupeSession.inProcess(listener);
			} catch (java.io.IOException e) {
				report("Can't start the adapter", e);
				return;
			}
			loupe = session;
			updateControls();
			status.setText("Debugging...");
			BACKGROUND.execute(()->{
				try {
					session.start(code, name, args, bps);
				} catch (Exception e) {
					Platform.runLater(()->adapterStartFailed(session, "The script couldn't start on the adapter", e));
				}
			});
			return;
		}
		// another machine: connecting can take a while, so the window is "running" (Stop gives up) from now
		connecting = true;
		updateControls();
		status.setText("Connecting to "+remote.where()+"...");
		BACKGROUND.execute(()->{
			AdapterSession session;
			try {
				session = remote.connect(listener);
			} catch (Exception e) {
				Platform.runLater(()->{
					connecting = false;
					showStartVariables();
					updateControls();
					status.setText("");
					report("Can't connect to "+remote.where(), e);
				});
				return;
			}
			Platform.runLater(()->{
				boolean gaveUp = !connecting;
				connecting = false;
				if( gaveUp ) {
					// Stop was chosen while it connected
					session.close();
					updateControls();
					status.setText("Stopped");
					return;
				}
				loupe = session;
				updateControls();
				status.setText("Debugging on "+remote.where()+"...");
				BACKGROUND.execute(()->{
					AdapterSession.Started started;
					try {
						started = session.start(remote, code, name, args, bps);
					} catch (Exception e) {
						Platform.runLater(()->adapterStartFailed(session, "Debugging couldn't start on "+remote.where(), e));
						return;
					}
					Platform.runLater(()->remoteStarted(session, remote, started));
				});
			});
		});
	}

	// Debug Remote is connecting (no session yet)
	private volatile boolean connecting;

	private void adapterStartFailed(AdapterSession session, String title, Exception e) {
		if( loupe == session ) {
			loupe = null;
		}
		session.close();
		restoreOwnScript();
		showStartVariables();
		updateControls();
		status.setText("");
		report(title, e);
	}

	/** A remote session has started: a script that isn't the editor's is shown, stopped before its first command. */
	private void remoteStarted(AdapterSession session, RemoteDebug remote, AdapterSession.Started started) {
		if( loupe != session ) {
			return;	// it ended already
		}
		if( started.getText() != null ) {
			showRemoteScript(remote, started);
		}
		if( started.isStopped()) {
			loupePaused = true;
			editor.setCurrentLine(started.getLine());
			debugPanel.setVariables(started.getVariables(), true);
			status.setText(started.getNote() != null ? started.getNote()
					: "Stopped before the first command, line "+(started.getLine()+1)+": set breakpoints, then Resume");
		}
		updateControls();
	}

	/** Stops the script that's running, if any. */
	void stop() {
		if( connecting ) {
			connecting = false;
			status.setText("Stopping...");
			return;
		}
		AdapterSession l = loupe;
		if( l != null ) {
			status.setText("Stopping...");
			l.terminate();
			return;
		}
		ScriptRun run = currentRun;
		if( run != null && run.isRunning()) {
			status.setText("Stopping...");
			run.cancel();
		}
	}

	/** Enables what can be done now: idle, running, debugging, or paused. */
	private void updateControls() {
		boolean running = currentRun != null || loupe != null || connecting;
		boolean paused = running && (pausedContext != null || loupePaused);
		// while paused, Run lets the script run to its end without stopping at breakpoints
		runButton.setDisable(running && !paused);
		runItem.setDisable(running && !paused);
		runButton.setTooltip(paused ? new Tooltip("Run to the end, without stopping at breakpoints") : null);
		debugButton.setDisable(running);
		debugItem.setDisable(running);
		debugRemoteItem.setDisable(running);
		stopButton.setDisable(!running);
		stopItem.setDisable(!running);
		for(javafx.scene.control.ButtonBase b : new javafx.scene.control.ButtonBase[] {resumeButton, stepOverButton, stepIntoButton}) {
			b.setDisable(!paused);
		}
		for(MenuItem m : new MenuItem[] {resumeItem, stepOverItem, stepIntoItem}) {
			m.setDisable(!paused);
		}
		suspendButton.setDisable(!running || !debugging || paused);
		suspendItem.setDisable(suspendButton.isDisable());
		// the debug controls only show while debugging
		for(javafx.scene.Node n : new javafx.scene.Node[] {debugControls, resumeButton, stepOverButton, stepIntoButton, suspendButton}) {
			n.setVisible(running && debugging);
			n.setManaged(running && debugging);
		}
		for(TextField f : new TextField[] {arguments, stdIn, stdOut, stdErr}) {
			f.setDisable(running);
		}
		// while a script runs it can't be edited (see ScriptEditor.setEditable)
		editor.setEditable(!running);
	}

	// ---- while a script runs: no edits, and the editor's right-click menu is the run's or the debugger's

	/** what the status line adds when an edit is tried while a script runs */
	static final String NOT_EDITABLE = " (the script can't be edited until it ends)";

	/** True if the script can be edited; if not (a script is running), the status line says why. */
	private boolean canEdit() {
		if( editor.isEditable()) {
			return true;
		}
		if( !status.getText().endsWith(NOT_EDITABLE)) {
			status.setText(status.getText()+NOT_EDITABLE);
		}
		return false;
	}

	private static MenuItem menuItem(String text, boolean enabled, Runnable action) {
		MenuItem ret = new MenuItem(text);
		ret.setDisable(!enabled);
		ret.setOnAction(e->action.run());
		return ret;
	}

	/** (shortened, on one line, for a menu item) */
	private static String forMenu(String text) {
		String t = text.trim().replaceAll("\\s+", " ");
		return t.length() > 40 ? t.substring(0, 37)+"..." : t;
	}

	/**
	 * The editor's right-click menu for the place clicked (a position in the text), while a
	 * script runs: for a plain run Stop and Copy; for a debug run the debug menu (Run to Line,
	 * the line's breakpoint, the variable or selection clicked, Resume and the steps, Stop,
	 * Copy), with what needs a stopped script greyed until it stops. Null while nothing runs:
	 * the edit menu.
	 */
	ContextMenu runMenu(int pos) {
		if( !isRunning()) {
			return null;
		}
		ContextMenu menu = new ContextMenu();
		String selection = editor.getCodeArea().getSelectedText();
		MenuItem stop = menuItem("Stop", true, this::stop);
		MenuItem copy = menuItem("Copy", selection != null && !selection.isEmpty(), ()->editor.getCodeArea().copy());
		if( !debugging ) {
			menu.getItems().addAll(stop, copy);
			return menu;
		}
		boolean paused = isPaused();
		String text = editor.getText();
		int at = Math.max(0, Math.min(pos, text.length()));
		int line = editor.getCodeArea().offsetToPosition(at, org.fxmisc.richtext.model.TwoDimensional.Bias.Forward).getMajor();
		menu.getItems().addAll(menuItem("Run to Line "+(line+1), paused, ()->runToLine(line)), new SeparatorMenuItem(),
				menuItem("Toggle Breakpoint", true, ()->editor.toggleBreakpoint(line)));
		Breakpoint bp = editor.getBreakpoint(line);
		if( bp != null ) {
			menu.getItems().add(menuItem("Breakpoint Properties...", true, ()->editBreakpoint(bp)));
		}
		menu.getItems().add(new SeparatorMenuItem());
		// the variable clicked (to watch, and to show), or the selection (to show)
		String variable = ScriptText.variableAt(text, at, debugPanel::hasVariable);
		if( variable != null && us.bringardner.fsh.ide.core.Watchpoint.isValidName(variable)) {
			menu.getItems().add(menuItem("Watch "+variable+"...", true, ()->debugPanel.addWatchpoint(variable)));
		}
		String show = selection != null && !selection.isBlank() ? selection : variable;
		if( show != null ) {
			menu.getItems().add(menuItem("Show Value of "+forMenu(show), paused, ()->showValue(show)));
		}
		if( variable != null || show != null ) {
			menu.getItems().add(new SeparatorMenuItem());
		}
		menu.getItems().addAll(menuItem("Resume", paused, this::resumeScript), menuItem("Step Over", paused, this::stepOverScript),
				menuItem("Step Into", paused, this::stepIntoScript), stop, new SeparatorMenuItem(), copy);
		return menu;
	}

	/**
	 * Run to Line: lets the paused script run and stops it when it reaches line (0-based), this
	 * once, or sooner at a breakpoint or a watchpoint.
	 */
	void runToLine(int line) {
		if( !isPaused()) {
			return;
		}
		AdapterSession l = loupe;
		if( l == null ) {
			debugSession.runToLine(line);
			return;
		}
		// on the adapter: a breakpoint there for this once (the editor's are sent again the next time it goes on)
		java.util.List<Breakpoint> bps = new java.util.ArrayList<>();
		for(Breakpoint bp : editor.getBreakpoints().values()) {
			if( bp.getLine() != line ) {
				bps.add(bp);
			}
		}
		Breakpoint to = new Breakpoint();
		to.setLine(line);
		to.reset();
		bps.add(to);
		adapterContinues(l, bps, ()->l.resume());
	}

	/**
	 * Shows what text (a variable's name, or text with a $ in it) is in the paused script, on
	 * the status line and under the variables: "text = value", or why it has none.
	 */
	void showValue(String text) {
		if( !isPaused()) {
			return;
		}
		String name = text.trim();
		AdapterSession l = loupe;
		if( l != null ) {
			BACKGROUND.execute(()->{
				String line;
				try {
					line = name+" = "+l.evaluate(DebugVariables.expression(name));
				} catch (IllegalArgumentException e) {
					line = e.getMessage();
				} catch (Exception e) {
					line = "Can't show "+name+": "+e.getMessage();
				}
				String shown = line;
				Platform.runLater(()->shownValue(shown));
			});
			return;
		}
		ShellContext ctx = pausedContext;
		if( ctx == null ) {
			return;
		}
		try {
			shownValue(name+" = "+DebugVariables.valueOf(ctx, name));
		} catch (IllegalArgumentException e) {
			shownValue(e.getMessage());
		}
	}

	private void shownValue(String line) {
		debugPanel.showValue(line);
		String first = line.replace("\r\n", "⏎").replace('\n', '⏎').replace('\r', '⏎');
		status.setText(first.length() > 200 ? first.substring(0, 197)+"..." : first);
	}

	// for tests
	boolean runButtonDisabled() {
		return runButton.isDisabled();
	}

	/** Stopped at a breakpoint or step. */
	boolean isPaused() {
		return pausedContext != null || loupePaused;
	}

	// ---- breakpoints, variables and the syntax check

	/** Shows bp's properties, then applies them or deletes it. */
	void editBreakpoint(Breakpoint bp) {
		switch (editBreakpointDialog(bp)) {
		case DELETE:
			editor.removeBreakpoint(bp);
			break;
		case CHANGED:
			editor.breakpointChanged();
			break;
		default:
			break;
		}
	}

	/** Asks for a line number and goes there. */
	void actionGoToLine() {
		Integer line = askLine(editor.getCaretLine()+1, editor.getCodeArea().getParagraphs().size());
		if( line != null && !editor.goToLine(line)) {
			status.setText("There's no line "+line);
		}
		editor.getCodeArea().requestFocus();
	}

	/** Asks for a line (1 to max); null if canceled or not a number. Tests override it. */
	protected Integer askLine(int current, int max) {
		javafx.scene.control.TextInputDialog d = new javafx.scene.control.TextInputDialog(""+current);
		d.initOwner(stage);
		FxBrand.apply(d);
		d.setTitle("Go to Line");
		d.setHeaderText(null);
		d.setContentText("Line (1-"+max+"):");
		return d.showAndWait().map(t->{
			try {
				return Integer.valueOf(t.trim());
			} catch (NumberFormatException e) {
				return null;
			}
		}).orElse(null);
	}

	/** Shows a breakpoint's properties; tests override it. */
	protected BreakpointDialog.Result editBreakpointDialog(Breakpoint bp) {
		return BreakpointDialog.edit(stage, bp);
	}

	/** Opens a variable's value in an editor and, while the script is paused, sets what is typed. */
	void editVariable(Variable v) {
		boolean editable = debugPanel.variablesEditable();
		editVariableDialog(v, editable).ifPresent(text->{
			if( editable ) {
				setVariable(v, text);
			}
		});
	}

	/** Shows a variable's value in a multi-line editor; tests override it. */
	protected java.util.Optional<String> editVariableDialog(Variable v, boolean editable) {
		return VariableDialog.edit(stage, v, editable);
	}

	/** Sets a variable of the paused script to what was typed. */
	void setVariable(Variable v, String value) {
		AdapterSession l = loupe;
		if( l != null ) {
			if( !loupePaused ) {
				return;
			}
			BACKGROUND.execute(()->{
				try {
					Map<String,Object> vars = l.setVariable(v.getName(), value);
					Platform.runLater(()->debugPanel.setVariables(vars, true));
				} catch (Exception e) {
					Platform.runLater(()->report("Can't set "+v.getName(), e));
				}
			});
			return;
		}
		ShellContext ctx = pausedContext;
		if( ctx == null ) {
			return;
		}
		try {
			DebugVariables.set(ctx, v.getName(), DebugVariables.parseValue(v.getValue(), value));
		} catch (RuntimeException e) {
			report("Can't set "+v.getName(), e);
		}
		debugPanel.setVariables(ctx.getVariables(), true);
	}

	/** Parses the script in the background and marks its syntax errors; a check overtaken by a newer one is dropped. */
	void checkSyntax() {
		String code = ScriptText.clean(editor.getText());
		int check = ++syntaxCheckCount;
		BACKGROUND.execute(()->{
			List<CompileError> errors = new java.util.ArrayList<>();
			us.bringardner.fsh.ide.core.SyntaxNode tree;
			try {
				tree = code.isBlank() ? null : ScriptParser.parse(code, errors);
			} catch (RuntimeException e) {
				return;
			}
			Platform.runLater(()->{
				if( check == syntaxCheckCount ) {
					editor.setErrors(errors);
					debugPanel.setSyntax(tree, errors);
					// a script that doesn't parse keeps the outline it had
					if( errors.isEmpty()) {
						outline.setOutline(us.bringardner.fsh.ide.core.ScriptOutline.of(tree));
					}
				}
			});
		});
	}

	boolean isRunning() {
		return currentRun != null || loupe != null || connecting;
	}

	DebugSession debugSession() {
		return debugSession;
	}

	DebugPanel debugPanel() {
		return debugPanel;
	}

	/** for the tests: Run > Debug Through the Loupe Adapter */
	void useAdapter(boolean use) {
		useAdapter.setSelected(use);
	}

	/** for the tests: View > Show Debug View */
	void showDebugView(boolean show) {
		showDebugPanel.setSelected(show);
	}

	OutlineView outline() {
		return outline;
	}

	CheckMenuItem showOutlineItem() {
		return showOutline;
	}

	boolean outlineShown() {
		return editArea.getItems().contains(outline);
	}

	FindBar findBar() {
		return findBar;
	}

	CompletionPopup completion() {
		return completion;
	}

	// ---- the window

	/** Shows the window, with the most recent file if there is one. */
	public void show() {
		stage.show();
		pruneRecent();
		String first = recent.first();
		if( scriptFile == null && first != null ) {
			FileSource f = fileOrNull(first);
			if( f != null ) {
				load(f);
			}
		}
	}

	/**
	 * Asks about unsaved changes (Cancel, Discard and exit, or Save and exit) and a running (or
	 * paused) script, stops the script, and remembers where the window was; false if the window
	 * stays open.
	 */
	boolean close() {
		if( !okToExit()) {
			return false;
		}
		if( isRunning() && !confirm("A script is still running. Stop it and close?")) {
			return false;
		}
		stop();
		if( prefs != null ) {
			prefs.put(PREF_BOUNDS, (int) stage.getX()+","+(int) stage.getY()+","+(int) stage.getWidth()+","+(int) stage.getHeight());
		}
		return true;
	}

	private void restoreBounds() {
		String b = prefs == null ? null : prefs.get(PREF_BOUNDS, null);
		if( b == null ) {
			return;
		}
		String[] p = b.split(",");
		try {
			double x = Double.parseDouble(p[0]), y = Double.parseDouble(p[1]);
			double w = Double.parseDouble(p[2]), h = Double.parseDouble(p[3]);
			// only if it's still on a screen
			if( !javafx.stage.Screen.getScreensForRectangle(x, y, Math.max(w, 1), 30).isEmpty() && w >= 300 && h >= 200 ) {
				stage.setX(x);
				stage.setY(y);
				stage.setWidth(w);
				stage.setHeight(h);
			}
		} catch (RuntimeException e) {
			// unreadable: use the default size and place
		}
	}

	// for tests
	ScriptEditor editor() { return editor; }
	ConsoleView console() { return console; }
	TextField argumentsField() { return arguments; }
	TextField stdOutField() { return stdOut; }
	FileSource scriptFile() { return scriptFile; }
	RecentFiles recent() { return recent; }
	String statusText() { return status.getText(); }
	CheckMenuItem useSelectionItem() { return useSelection; }
	Stage stage() { return stage; }

	/**
	 * The theme's syntax colors on the script editor: its own (set on the theme designer's Script
	 * editor page), or those that match it. The colors of the theme before are taken off first.
	 */
	static void applySyntaxColors(Scene scene, us.bringardner.swing.gradient.Theme theme) {
		scene.getStylesheets().removeIf(s->s.startsWith("data:text/css;base64,") && isCss(s, SyntaxColors.CSS_CLASS));
		scene.getRoot().getStyleClass().remove(SyntaxColors.CSS_CLASS);
		SyntaxColors colors = us.bringardner.fsh.ide.EditorThemeExtension.colorsOf(theme);
		scene.getRoot().getStyleClass().add(SyntaxColors.CSS_CLASS);
		scene.getStylesheets().add("data:text/css;base64," + java.util.Base64.getEncoder().encodeToString(
				("/* " + SyntaxColors.CSS_CLASS + " */\n" + colors.css()).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
	}

	/** True if a data: stylesheet is the one marked with this class (the comment it starts with). */
	private static boolean isCss(String url, String cssClass) {
		String css = new String(java.util.Base64.getDecoder().decode(url.substring(url.indexOf(',') + 1)),
				java.nio.charset.StandardCharsets.UTF_8);
		return css.startsWith("/* " + cssClass + " */");
	}

	/**
	 * The theme designer's Script editor page (the syntax colors, part of each theme), in this IDE
	 * as in the Swing one.
	 */
	static void registerThemePages() {
		if( us.bringardner.swing.gradient.ThemeExtensions.all().stream().noneMatch(x->x.getId().equals(SyntaxColors.EXTENSION_ID)) ) {
			us.bringardner.swing.gradient.ThemeExtensions.register(new us.bringardner.fsh.ide.EditorThemeExtension());
		}
		us.bringardner.fx.dialog.FxThemePages.register(SyntaxColors.EXTENSION_ID, EditorThemePage::new);
		// Settings has a Templates tab, after Appearance
		us.bringardner.fx.dialog.FxSettingsPages.register(TemplatesPage.ID, TemplatesPage::new);
	}

	/**
	 * Settings...: the theme and font, shared with the other Parley applications (fx-widgets'
	 * SettingsDialog). OK puts them on every open window, the syntax colors of the theme included.
	 */
	void actionSettings() {
		if( us.bringardner.fx.dialog.SettingsDialog.showDialog(stage, prefs) ) {
			themeChanged();
		}
	}

	/** The current theme's syntax colors on every open IDE window (the look itself is already on them). */
	static void themeChanged() {
		for(IdeWindow w : new java.util.ArrayList<>(OPEN)) {
			if( w.stage.getScene() != null ) {
				applySyntaxColors(w.stage.getScene(), us.bringardner.swing.gradient.Theme.current());
			}
		}
	}

	/**
	 * View > Editor Font...: the editors' and console's font for good: kept, and put on every open IDE
	 * window in place of any window's own size.
	 */
	void actionEditorFont() {
		EditorFont f = EditorFontDialog.choose(stage, EditorFont.load(prefs));
		if( f != null ) {
			chooseEditorFont(f);
		}
	}

	/** Makes a font the editor font: kept, and on every open IDE window. */
	void chooseEditorFont(EditorFont font) {
		if( prefs != null ) {
			font.save(prefs);
		}
		for(IdeWindow w : new java.util.ArrayList<>(OPEN)) {
			w.windowFont = null;
			if( w.stage.getScene() != null ) {
				applyEditorFont(w.stage.getScene(), font);
			}
		}
	}

	/** This window's own font for this session (the text size keys), or null for the editor font. */
	private EditorFont windowFont;

	/**
	 * The editor font (kept in the IDE's preferences, set here or in the Swing IDE) on the script
	 * editor and console. The standard font adds nothing: fsh-ide.css has it.
	 */
	static void applyEditorFont(Scene scene, EditorFont font) {
		scene.getStylesheets().removeIf(s->s.startsWith("data:text/css;base64,") && isEditorFontCss(s));
		scene.getRoot().getStyleClass().remove(EditorFont.CSS_CLASS);
		if( font.isDefault() ) {
			return;
		}
		scene.getRoot().getStyleClass().add(EditorFont.CSS_CLASS);
		scene.getStylesheets().add("data:text/css;base64," + java.util.Base64.getEncoder().encodeToString(
				("/* " + EditorFont.CSS_CLASS + " */\n" + font.css(FxFonts.monospaced())).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
	}

	private static boolean isEditorFontCss(String url) {
		return isCss(url, EditorFont.CSS_CLASS);
	}

	/**
	 * The text size keys (and View > Bigger Text, Smaller Text): this window's editor and console
	 * only, for this session; nothing is kept. View > Editor Font... changes the font for good.
	 */
	private void changeWindowFont(java.util.function.UnaryOperator<EditorFont> change) {
		EditorFont font = change.apply(editorFont());
		windowFont = font.equals(EditorFont.load(prefs)) ? null : font;
		applyEditorFont(stage.getScene(), font);
	}

	/** The editor font now on this window: its own for this session, or the one kept. */
	EditorFont editorFont() {
		return windowFont != null ? windowFont : EditorFont.load(prefs);
	}

	/** View > Bigger Text: this window, for this session. */
	void biggerText() {
		changeWindowFont(EditorFont::bigger);
	}

	/** View > Smaller Text: this window, for this session. */
	void smallerText() {
		changeWindowFont(EditorFont::smaller);
	}

	/** View > Reset Text Size: this window back to the editor font, as last set with Editor Font.... */
	void resetSize() {
		windowFont = null;
		applyEditorFont(stage.getScene(), EditorFont.load(prefs));
	}

	/**
	 * Shortcut and + (= with or without shift, or the keypad's +), - (or the keypad's -) and 0: the
	 * editor's and console's text bigger, smaller and the standard size, whichever key is used. The
	 * menu items show them, but a menu accelerator takes one key only.
	 */
	void textSizeKeys(javafx.scene.input.KeyEvent e) {
		if( !e.isShortcutDown() || e.isAltDown() ) {
			return;
		}
		javafx.scene.input.KeyCode k = e.getCode();
		String t = e.getText();
		Runnable action = null;
		if( k == javafx.scene.input.KeyCode.EQUALS || k == javafx.scene.input.KeyCode.PLUS
				|| k == javafx.scene.input.KeyCode.ADD || "=".equals(t) || "+".equals(t) ) {
			action = this::biggerText;
		} else if( k == javafx.scene.input.KeyCode.MINUS || k == javafx.scene.input.KeyCode.SUBTRACT || "-".equals(t) ) {
			action = this::smallerText;
		} else if( !e.isShiftDown() && (k == javafx.scene.input.KeyCode.DIGIT0 || k == javafx.scene.input.KeyCode.NUMPAD0) ) {
			action = this::resetSize;
		}
		if( action != null ) {
			action.run();
			e.consume();
		}
	}
}
