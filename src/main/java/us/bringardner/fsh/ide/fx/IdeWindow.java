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

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.prefs.Preferences;

import java.util.Map;

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
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import us.bringardner.fsh.ShellContext;
import us.bringardner.fsh.ide.core.Breakpoint;
import us.bringardner.fsh.ide.core.CompileError;
import us.bringardner.fsh.ide.core.Configuration;
import us.bringardner.fsh.ide.core.DebugVariables;
import us.bringardner.fsh.ide.core.LogBatcher;
import us.bringardner.fsh.ide.core.DebugSession;
import us.bringardner.fsh.ide.core.RecentFiles;
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
	private final CheckMenuItem showDebugPanel = new CheckMenuItem("Show Side Panel");
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
	private String original = "";
	private RecentFiles recent;
	private ScriptRun currentRun;
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
		this.prefs = prefs;
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
		scene.getStylesheets().add(IdeWindow.class.getResource("fsh-ide.css").toExternalForm());
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

	private MenuBar menuBar() {
		MenuItem newFile = item("New", "Shortcut+N", this::actionNew);
		MenuItem open = item("Open...", "Shortcut+O", this::actionOpen);
		MenuItem save = item("Save", "Shortcut+S", this::actionSave);
		MenuItem saveAs = item("Save As...", "Shortcut+Shift+S", this::actionSaveAs);
		MenuItem reload = item("Reload", null, this::actionReload);
		MenuItem newWindow = item("New Window", "Shortcut+Shift+N", ()->new IdeWindow().show());
		MenuItem closeItem = item("Close", "Shortcut+W", ()->{
			if( close()) {
				stage.close();
			}
		});
		Menu file = new Menu("File", null, newFile, open, recentMenu, new SeparatorMenuItem(),
				save, saveAs, reload, new SeparatorMenuItem(), newWindow, closeItem);

		runItem.setAccelerator(KeyCombination.keyCombination("Shortcut+R"));
		runItem.setOnAction(e->run());
		debugItem.setAccelerator(KeyCombination.keyCombination("Shortcut+D"));
		debugItem.setOnAction(e->debug());
		stopItem.setAccelerator(KeyCombination.keyCombination("Shortcut+PERIOD"));
		stopItem.setOnAction(e->stop());
		resumeItem.setAccelerator(KeyCombination.keyCombination("F8"));
		resumeItem.setOnAction(e->debugSession.resume());
		stepOverItem.setAccelerator(KeyCombination.keyCombination("F6"));
		stepOverItem.setOnAction(e->debugSession.stepOver());
		stepIntoItem.setAccelerator(KeyCombination.keyCombination("F5"));
		stepIntoItem.setOnAction(e->debugSession.stepInto());
		suspendItem.setOnAction(e->debugSession.suspend());
		MenuItem toggle = item("Toggle Breakpoint", "Shortcut+B", ()->editor.toggleBreakpoint(editor.getCaretLine()));
		MenuItem clear = item("Clear Console", "Shortcut+K", console::clear);
		Menu run = new Menu("Run", null, runItem, debugItem, stopItem, new SeparatorMenuItem(),
				resumeItem, stepOverItem, stepIntoItem, suspendItem, new SeparatorMenuItem(),
				toggle, useSelection, clear, new SeparatorMenuItem(), showDebugPanel);

		MenuItem undo = item("Undo", "Shortcut+Z", ()->editor.getCodeArea().undo());
		MenuItem redo = item("Redo", "Shortcut+Shift+Z", ()->editor.getCodeArea().redo());
		MenuItem find = item("Find...", "Shortcut+F", ()->findBar.show(false));
		MenuItem findNext = item("Find Next", "Shortcut+G", ()->findBar.findNext(true));
		MenuItem findPrevious = item("Find Previous", "Shortcut+Shift+G", ()->findBar.findNext(false));
		MenuItem replace = item("Replace...", "Shortcut+Alt+F", ()->findBar.show(true));
		MenuItem goTo = item("Go to Line...", "Shortcut+L", this::actionGoToLine);
		MenuItem complete = item("Complete", "Ctrl+Space", completion::show);
		MenuItem foldAll = item("Fold All", "Shortcut+Shift+MINUS", editor::foldAll);
		MenuItem unfoldAll = item("Unfold All", "Shortcut+Shift+EQUALS", editor::unfoldAll);
		CheckMenuItem spelling = new CheckMenuItem("Check Spelling");
		spelling.setSelected(prefs == null || prefs.getBoolean(PREF_SPELLING, true));
		editor.setSpellChecking(spelling.isSelected());
		spelling.selectedProperty().addListener((o, was, is)->{
			editor.setSpellChecking(is);
			if( prefs != null ) {
				prefs.putBoolean(PREF_SPELLING, is);
			}
		});
		Menu edit = new Menu("Edit", null, undo, redo, new SeparatorMenuItem(), find, findNext, findPrevious,
				replace, new SeparatorMenuItem(), goTo, complete, new SeparatorMenuItem(), foldAll, unfoldAll,
				new SeparatorMenuItem(), spelling);

		MenuBar bar = new MenuBar(file, edit, run);
		bar.setUseSystemMenuBar(true);
		return bar;
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
		resumeButton.setOnAction(e->debugSession.resume());
		stepOverButton.setOnAction(e->debugSession.stepOver());
		stepIntoButton.setOnAction(e->debugSession.stepInto());
		suspendButton.setOnAction(e->debugSession.suspend());
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

	/** The script as it would be saved: its settings lines, then the code. */
	String getCode() {
		return new ScriptDocument(editor.getText(), arguments.getText(), stdIn.getText(),
				stdOut.getText(), stdErr.getText()).toText();
	}

	boolean hasChanged() {
		return !original.equals(getCode());
	}

	/** Shows code read from file (null for a new script). */
	void setCode(String code, FileSource file) {
		scriptFile = file;
		ScriptDocument doc = ScriptDocument.parse(code);
		arguments.setText(doc.arguments());
		stdIn.setText(doc.stdIn());
		stdOut.setText(doc.stdOut());
		stdErr.setText(doc.stdErr());
		editor.setText(doc.body());
		original = getCode();
		updateTitle();
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
		return a.showAndWait().filter(b->b == ButtonType.YES).isPresent();
	}

	/** Tells the user about a problem; tests override it. */
	protected void report(String title, Throwable error) {
		Alert a = new Alert(AlertType.ERROR, error == null ? "" : String.valueOf(error.getMessage()));
		a.setHeaderText(title);
		a.initOwner(stage);
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
				Platform.runLater(()->{
					setCode(code, file);
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

	/** Writes the script to file in the background; it's marked saved once that worked. */
	void save(FileSource file) {
		String code = getCode();
		status.setText("Saving "+file.getAbsolutePath()+"...");
		BACKGROUND.execute(()->{
			try {
				ScriptText.write(file, code);
				Platform.runLater(()->{
					scriptFile = file;
					original = code;
					addRecent(file);
					status.setText("Saved "+file.getAbsolutePath());
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

	/** Drops recent files that no longer exist; checked in the background (they may be remote). */
	private void pruneRecent() {
		List<String> names = recent.list();
		BACKGROUND.execute(()->{
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
		if( currentRun != null ) {
			return;
		}
		String all = ScriptText.clean(editor.getText());
		String selected = ScriptText.clean(editor.getSelectedText());
		boolean selection = useSelection.isSelected() && selected != null;
		console.clear();
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
		debugSession.setFirstLine(selection ? editor.getSelectionStartLine() : 0);
		ScriptRun run = new ScriptRun(selection ? selected : all, (r, exitCode, ctx, error)->{
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

	/** Stops the script that's running, if any. */
	void stop() {
		ScriptRun run = currentRun;
		if( run != null && run.isRunning()) {
			status.setText("Stopping...");
			run.cancel();
		}
	}

	/** Enables what can be done now: idle, running, debugging, or paused. */
	private void updateControls() {
		boolean running = currentRun != null;
		boolean paused = running && pausedContext != null;
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
		return pausedContext != null;
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
		return currentRun != null;
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
}
