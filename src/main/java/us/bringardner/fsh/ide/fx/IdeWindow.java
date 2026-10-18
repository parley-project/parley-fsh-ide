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
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckMenuItem;
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
import us.bringardner.fsh.ide.core.LogBatcher;
import us.bringardner.fsh.ide.core.LoupeSession;
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
	private final Button runButton = new Button("\u25b6 Run");
	private final Button debugButton = new Button("\u2699 Debug");
	private final Button stopButton = new Button("\u25a0 Stop");
	private final Button resumeButton = new Button("Resume");
	private final Button stepOverButton = new Button("Step Over");
	private final Button stepIntoButton = new Button("Step Into");
	private final Button suspendButton = new Button("Suspend");
	private final MenuItem runItem = new MenuItem("Run");
	private final MenuItem debugItem = new MenuItem("Debug");
	private final MenuItem stopItem = new MenuItem("Stop");
	private final MenuItem resumeItem = new MenuItem("Resume");
	private final MenuItem stepOverItem = new MenuItem("Step Over");
	private final MenuItem stepIntoItem = new MenuItem("Step Into");
	private final MenuItem suspendItem = new MenuItem("Suspend");
	private final CheckMenuItem showDebugPanel = new CheckMenuItem("Show Debug View");
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
	private volatile LoupeSession loupe;
	private volatile boolean loupePaused;
	private final CheckMenuItem useAdapter = new CheckMenuItem("Debug Through the Loupe Adapter");
	private final DebugSession debugSession = new DebugSession(line->editor.getBreakpoint(line), new DebugSession.Listener() {
		@Override
		public void paused(int line, ShellContext ctx, Map<String, Object> variables) {
			Platform.runLater(()->{
				log.flush();
				pausedContext = ctx;
				editor.setCurrentLine(line);
				debugPanel.setVariables(variables, true);
				status.setText("Paused at line "+(line+1));
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
		mainSplit.getItems().add(split);
		root.setCenter(mainSplit);
		showDebugPanel.selectedProperty().addListener((o, was, is)->{
			if( is && !mainSplit.getItems().contains(debugPanel)) {
				mainSplit.getItems().add(debugPanel);
				mainSplit.setDividerPositions(0.68);
			} else if( !is ) {
				mainSplit.getItems().remove(debugPanel);
			}
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
		editor.addBreakpointListener(()->debugPanel.setBreakpoints(editor.getBreakpoints().values()));
		editor.setOnBreakpointMenu((bp, e)->editBreakpoint(bp));
		debugPanel.setOnBreakpointChanged(bp->editor.breakpointChanged());
		debugPanel.setOnEditBreakpoint(this::editBreakpoint);
		debugPanel.setOnSetVariable(this::setVariable);
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
				item("Clear Run Settings...", null, this::actionClearRunSettings), new SeparatorMenuItem(),
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
		menus.edit.getItems().addAll(item("Undo", "Shortcut+Z", ()->editor.getCodeArea().undo()),
				item("Redo", "Shortcut+Shift+Z", ()->editor.getCodeArea().redo()), new SeparatorMenuItem(),
				// on whichever text has the focus: the editor, the arguments, the console
				item("Cut", "Shortcut+X", ()->onFocusedText("cut")), item("Copy", "Shortcut+C", ()->onFocusedText("copy")),
				item("Paste", "Shortcut+V", ()->onFocusedText("paste")),
				item("Select All", "Shortcut+A", ()->onFocusedText("selectAll")), new SeparatorMenuItem(),
				item("Find...", "Shortcut+F", ()->findBar.show(false)), item("Find Next", "Shortcut+G", ()->findBar.findNext(true)),
				item("Find Previous", "Shortcut+Shift+G", ()->findBar.findNext(false)),
				item("Replace...", "Shortcut+Alt+F", ()->findBar.show(true)),
				item("Go to Line...", "Shortcut+L", this::actionGoToLine), new SeparatorMenuItem(),
				item("Complete", "Ctrl+Space", completion::show), new SeparatorMenuItem(), spelling);

		// View: the editor's and console's font and text size, and folding
		menus.view.getItems().addAll(item("Bigger Text", "Shortcut+PLUS", this::biggerText),
				item("Smaller Text", "Shortcut+MINUS", this::smallerText),
				item("Reset Text Size", "Shortcut+DIGIT0", this::resetSize),
				item("Editor Font...", null, this::actionEditorFont), new SeparatorMenuItem(),
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
		stopItem.setAccelerator(KeyCombination.keyCombination("Shortcut+PERIOD"));
		stopItem.setOnAction(e->stop());
		resumeItem.setAccelerator(KeyCombination.keyCombination("F8"));
		resumeItem.setOnAction(e->resumeScript());
		stepOverItem.setAccelerator(KeyCombination.keyCombination("F6"));
		stepOverItem.setOnAction(e->stepOverScript());
		stepIntoItem.setAccelerator(KeyCombination.keyCombination("F5"));
		stepIntoItem.setOnAction(e->stepIntoScript());
		suspendItem.setOnAction(e->suspendScript());
		Menu run = new Menu("Run", null, runItem, debugItem, stopItem, new SeparatorMenuItem(),
				resumeItem, stepOverItem, stepIntoItem, suspendItem, new SeparatorMenuItem(),
				item("Toggle Breakpoint", "Shortcut+B", ()->editor.toggleBreakpoint(editor.getCaretLine())),
				new SeparatorMenuItem(), useSelection, useAdapter, new SeparatorMenuItem(),
				item("Clear Console", "Shortcut+K", console::clear), showDebugPanel);

		return menus.build(stage, run);
	}

	/** The application's name: the macOS application menu and Help > About show it. */
	static final String APP_NAME = "fsh-ide";

	/** The IDE windows open now, for Quit. */
	private static final java.util.List<IdeWindow> OPEN = new java.util.ArrayList<>();

	/** Quit (Exit): closes every IDE window, each asking about unsaved changes; false if one stays open. */
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
			case "cut": t.cut(); break;
			case "copy": t.copy(); break;
			case "paste": t.paste(); break;
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
		resumeButton.setTooltip(new Tooltip("Resume (F8)"));
		stepOverButton.setTooltip(new Tooltip("Step Over (F6)"));
		stepIntoButton.setTooltip(new Tooltip("Step Into (F5)"));
		suspendButton.setTooltip(new Tooltip("Stop at the next statement"));
		arguments.setPromptText("Script arguments");
		arguments.setPrefColumnCount(18);
		// the run controls, then the script's settings; they wrap to a second row when the window is narrow
		HBox controls = new HBox(6, runButton, debugButton, stopButton, resumeButton, stepOverButton, stepIntoButton, suspendButton);
		HBox settings = new HBox(6, new Label("Arguments:"), arguments,
				redirect("stdin", stdIn, false), redirect("stdout", stdOut, true), redirect("stderr", stdErr, true));
		for(HBox b : new HBox[] {controls, settings}) {
			b.setStyle("-fx-alignment: center-left;");
			keepWidth(b);
		}
		javafx.scene.layout.FlowPane bar = new javafx.scene.layout.FlowPane(14, 4, controls, settings);
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
		Button browse = new Button("...");
		browse.setTooltip(new Tooltip("Choose a file for "+name));
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
		HBox box = new HBox(2, new Label(name+":"), field, browse);
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
		return new ScriptDocument(editor.getText(), new RunSettings(arguments.getText(), stdIn.getText(),
				stdOut.getText(), stdErr.getText()));
	}

	boolean hasChanged() {
		return !original.equals(getDocument());
	}

	/** Shows a script's text, with only the settings it carries itself (a template, a new script ...). */
	void setCode(String code, FileSource file) {
		setDocument(ScriptDocument.parse(code), file);
	}

	/** Shows a script read from file (null for a new script). */
	void setDocument(ScriptDocument doc, FileSource file) {
		scriptFile = file;
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
								Platform.runLater(()->status.setText("Cleared the run settings."));
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

	private void updateTitle() {
		String name = scriptFile == null ? "Untitled" : scriptFile.getName();
		stage.setTitle((hasChanged() ? "* " : "")+name+" - fsh-ide");
	}

	/** True if there are no unsaved changes or the user agrees to discard them. */
	boolean okToDiscard() {
		return !hasChanged() || confirm("There are unsaved changes. Discard them?");
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
		FileSourceChooser fc = chooser();
		fc.setInitialFileName(scriptFile == null ? "script.sh" : scriptFile.getName());
		FileSource file = fc.showSaveDialog(stage);
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
				Platform.runLater(()->{
					scriptFile = file;
					original = doc;
					addRecent(file);
					status.setText(warning != null ? warning : "Saved "+file.getAbsolutePath());
					updateTitle();
				});
			} catch (IOException e) {
				Platform.runLater(()->{
					status.setText("");
					report("Can't save "+file.getAbsolutePath(), e);
				});
			}
		});
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

	/** Runs the script (or just the selected code, if that's chosen and there is some). */
	void run() {
		start(false);
	}

	/** Runs the script, stopping at breakpoints, with the debug panel shown. */
	void debug() {
		start(true);
	}

	private void start(boolean debug) {
		if( currentRun != null || loupe != null ) {
			return;
		}
		String all = ScriptText.clean(editor.getText());
		String selected = ScriptText.clean(editor.getSelectedText());
		boolean selection = useSelection.isSelected() && selected != null;
		console.clear();
		if( debug && useAdapter.isSelected()) {
			startWithAdapter(selection ? ScriptText.atLine(selected, editor.getSelectionStartLine()) : all);
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
		LoupeSession l = loupe;
		if( l == null ) {
			debugSession.resume();
		} else {
			adapterContinues(l, ()->l.resume());
		}
	}

	private void stepOverScript() {
		LoupeSession l = loupe;
		if( l == null ) {
			debugSession.stepOver();
		} else {
			adapterContinues(l, ()->l.stepOver());
		}
	}

	private void stepIntoScript() {
		LoupeSession l = loupe;
		if( l == null ) {
			debugSession.stepInto();
		} else {
			adapterContinues(l, ()->l.stepInto());
		}
	}

	private void suspendScript() {
		LoupeSession l = loupe;
		if( l == null ) {
			debugSession.suspend();
		} else {
			l.suspend();
		}
	}

	/** Sends the editor's breakpoints, then the request that lets the script go on. */
	private void adapterContinues(LoupeSession l, java.util.function.Supplier<java.util.concurrent.CompletableFuture<Void>> request) {
		java.util.List<Breakpoint> bps = new java.util.ArrayList<>(editor.getBreakpoints().values());
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

	/** Debugs code on the fsh Loupe adapter in this JVM instead of in-process. */
	private void startWithAdapter(String code) {
		if( !stdIn.getText().isBlank() || !stdOut.getText().isBlank() || !stdErr.getText().isBlank()) {
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
		LoupeSession session;
		try {
			session = LoupeSession.inProcess(new LoupeSession.Listener() {
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
						LoupeSession l = loupe;
						loupe = null;
						loupePaused = false;
						editor.setCurrentLine(-1);
						debugPanel.freezeVariables();
						if( error != null ) {
							report("The debug session failed", error);
						}
						updateControls();
						status.setText((killed ? "Stopped" : "Finished")+", exit code "+exitCode);
						if( l != null ) {
							l.close();
						}
					});
				}
			});
		} catch (java.io.IOException e) {
			report("Can't start the adapter", e);
			return;
		}
		loupe = session;
		updateControls();
		status.setText("Debugging...");
		String name = scriptFile != null ? scriptFile.getAbsolutePath() : "fsh";
		java.util.List<String> args = us.bringardner.fsh.ide.core.ScriptText.splitArguments(arguments.getText());
		java.util.List<Breakpoint> bps = new java.util.ArrayList<>(editor.getBreakpoints().values());
		BACKGROUND.execute(()->{
			try {
				session.start(code, name, args, bps);
			} catch (Exception e) {
				Platform.runLater(()->{
					loupe = null;
					session.close();
					updateControls();
					report("The script couldn't start on the adapter", e);
				});
			}
		});
	}

	/** Stops the script that's running, if any. */
	void stop() {
		LoupeSession l = loupe;
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
		boolean running = currentRun != null || loupe != null;
		boolean paused = running && (pausedContext != null || loupePaused);
		runButton.setDisable(running);
		runItem.setDisable(running);
		debugButton.setDisable(running);
		debugItem.setDisable(running);
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
		for(javafx.scene.Node n : new javafx.scene.Node[] {resumeButton, stepOverButton, stepIntoButton, suspendButton}) {
			n.setVisible(running && debugging);
			n.setManaged(running && debugging);
		}
		for(TextField f : new TextField[] {arguments, stdIn, stdOut, stdErr}) {
			f.setDisable(running);
		}
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

	/** Sets a variable of the paused script to what was typed. */
	void setVariable(Variable v, String value) {
		LoupeSession l = loupe;
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
			DebugVariables.set(ctx, v.getName(), value);
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
				}
			});
		});
	}

	boolean isRunning() {
		return currentRun != null || loupe != null;
	}

	DebugSession debugSession() {
		return debugSession;
	}

	DebugPanel debugPanel() {
		return debugPanel;
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

	/** Asks about unsaved changes, stops a running script, and remembers where the window was. */
	boolean close() {
		if( !okToDiscard()) {
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
