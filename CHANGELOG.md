# Changelog

## 1.1.0 (unreleased)

### Removed (2026-11-12: the JavaFX IDE)

- The JavaFX IDE (`us.bringardner.fsh.ide.fx`, started by `FshIdeFx`) is no longer part of fsh-ide,
  with its tests, its stylesheet and its libraries (JavaFX, RichTextFX, fx-widgets, parley-files-fx).
  fsh-ide is the Swing IDE. The tag `javafx-final` is the last commit that has the JavaFX IDE, as it
  was. Entries below that speak of "both IDEs" or of the JavaFX IDE describe it as it was then.

### Changed (2026-11-13: the name)

- The IDE is named **Fsh Studio** in the application menu, Help > About. The native binary is `studio` (`studio.exe`); the project, jar, the native archives'
  names and the `~/.fsh-ide` folder keep the name fsh-ide.

### Changed (2026-11-12: the native image's settings redone)

- **Native image**: the recorded `reachability-metadata.json` no longer names fsh-ide's or Parley's
  classes and files, so a rename can't leave a stale name behind (it still named
  `us.bringardner.parley.core.ParleyLogger`). `mvn -Pnative package` lists them from the class path
  as the image is built (`tools/NativeMetadata.java`: every `us.bringardner` class with its members,
  a proxy for each JNA library interface, and the other files of their jars); the recorded file keeps
  what the JDK (AWT, Swing) and the other libraries need, and `tools/record-native-config.sh` takes
  Parley's part out after recording.
- **Native image on Linux**: the X11 entries are recorded (the file had Windows' only), and the image
  was built and run on Linux x86-64: edit, save, run, debug to a breakpoint and step, Settings, About.
  macOS (Aqua, x86-64) is recorded too, 2026-11-12; its image is still to build and run.

### Added

- **Run > Debug Remote...**, in both IDEs: debugs a script on another machine, through fsh-loupe's
  adapter listening there (`FshAdapterMain --listen address:port`) or by attaching to a script
  started there with `fsh --wait-for-debugger address:port script`. The dialog asks for the machine,
  the port and what to debug: the script in the editor, run there; a script file on that machine;
  or a script waiting there for a debugger. A script that isn't the editor's is shown in the editor
  while it is debugged, stopped before its first command (set breakpoints, then Resume), and the
  window's own script and breakpoints are put back when the session ends. The connection has no
  login and no encryption (the dialog says so). The last settings are kept. In `core`:
  `RemoteDebug` (the settings), `AdapterSession` (what the IDEs drive; `LoupeSession` is one).
- The IDE is a Debug Adapter Protocol client too: Debug Remote's dialog has a **Protocol** choice
  (Loupe, or DAP), and `DapSession` (in `core`) debugs through an adapter that speaks DAP on a
  port, in the same three ways. Over DAP a script that isn't the editor's is debugged without its
  text being shown (parley-loupe's bridge doesn't answer DAP's `source` request); the IDE says so.
- The Swing IDE can debug through a Loupe adapter (it had no adapter path): Debug Remote's
  sessions use the debug view, the debug controls and the editor's debug menu (Run to Line and
  Show Value included).
- While a script is being debugged, a right-click in the editor shows a debug menu in place of
  the edit menu, in both IDEs, for the line and the text clicked: **Run to Line** (the script
  runs and stops when it reaches that line, this once, or sooner at a breakpoint or a watchpoint),
  **Toggle Breakpoint**, **Breakpoint Properties...** (on a line with one), **Watch** *name*...
  and **Show Value of** *name* (on a variable; Show Value also takes a selection such as
  `${capital[Japan]}`, and shows the value under the variables, without running anything: text
  that would run a command is refused), **Resume**, **Step Over**, **Step Into**, **Stop** and
  **Copy**. What needs a stopped script is greyed while it runs. Run to Line and Show Value work
  through the Loupe adapter too. During a plain run the menu is **Stop** and **Copy**.
- While the debug view shows and no script is running, its fsh Variables tab shows what the next
  run starts with, in both IDEs: the shell's own variables, the environment's (the login shell's
  included) and `$0`, under a line that says so. It's refreshed when a run ends, the view opens or
  the script's file changes. What the file `FSH_ENV` names sets isn't shown (that file runs when
  the script does), and the values can't be changed there. After a debug run the User Variables
  tab still shows the script's own variables as it left them.
- Scripts run with the environment a terminal would give them, in both IDEs and through Loupe.
  An IDE opened from the Finder or the Dock (or some Linux desktops) gets a small environment, not
  the one a terminal's login shell makes from your startup files, so a script that found its
  programs in a terminal didn't in the IDE. As JetBrains' IDEs and VS Code do, the IDE runs your
  `$SHELL` once as it starts (`-l -i -c`, in the background, for up to 15 seconds) and runs scripts
  with the environment variables it has: only exported variables, not aliases or functions, as a
  script from a terminal has. `FSH_IDE_ENVIRONMENT_READER` is set while it runs, for a startup file
  to skip what needs a terminal. If it fails the IDE says so, and scripts run with its own. Not on
  Windows, nor when the IDE was started from a shell. Each run's Log tab starts with which
  environment scripts got, and Help > Script Environment... shows it: what was done (or why not),
  the shell, FSH_ENV, PATH in the order it's searched, and every variable.
- As `fsh script` does, a script runs the file `FSH_ENV` names first (not in the debugger).
- The breakpoint dialog, in both IDEs, has an info button (a new info icon) that tells how the
  condition and the hit count stop a breakpoint: what the Debug Adapter Protocol specification
  says (SourceBreakpoint), what it means here, and the specification's reference, with a button
  or link that opens it. The Swing dialog's condition box is always shown, as the JavaFX one's
  (greyed until its check box is checked).
- The Breakpoints tab, in both IDEs, shows that a breakpoint can be changed: an Edit... button
  (for the selected breakpoint) opens the dialog a double-click does, to give it a condition or a
  hit count or delete it, and the tab and its breakpoints say so in their tooltips.
- The Swing IDE's Edit menu has what the JavaFX IDE's has: Find Previous (Cmd+Shift+G), Replace...
  (Cmd+Alt+F: the Find dialog with the cursor in Replace) and Complete (Ctrl+Space, the
  completion the editor already gave). The Find dialog finds as the JavaFX find bar does
  (`core.TextSearch`): forward or back, case, whole words, regular expressions (Replace fills in
  `$1`...) and wrapping alike; a match is selected in the editor, and Replace All is undone as one.
  The dialog has a Find Previous button beside Find.
- Autocompletion templates for the tests a script makes, one for each kind, with the test as a
  field with choices: `if-file` (exists, a regular file, a directory, a link, readable, writable,
  runnable, a pipe, a socket, a device, the set-user-ID, set-group-ID and sticky bits, yours, your
  group's, changed since read, doesn't exist), `if-files` (newer, older, the same file),
  `if-string` (==, !=, =~, <, >), `if-number` (-eq, -ne, -lt, -le, -gt, -ge), `if-variable`
  (empty or not, set or not), `if-logic` (&& or ||) and `if-command` (succeeds, fails, is
  available, too few arguments, root, a terminal, interactive, a shell option, an arithmetic
  expression). `if-file` takes the place of the old one (a regular file exists), which is `-f`
  among its choices, and `if-dir`, `if-empty` and `if-set` are gone: they're `if-file`'s `-d` and
  `if-variable`'s `-z` and `-n`. (Unchanged copies of them in your settings go too.)
- Template fields with choices: `${|-e : exists|-f : a regular file}` puts in the first value,
  and when the field is the one being filled in, the editor lists the choices under it, each with
  what it means (Up and Down, Enter or a click). In the JavaFX IDE, Tab now goes from field to
  field of a template just inserted, Shift+Tab back, as in the Swing IDE. The Templates settings
  page's note says how to write one.
- Watchpoints on variables, in both IDEs: the debugger stops when the script reads a variable,
  writes it (sets or unsets it), or either, and marks the line doing it, as a breakpoint does.
  The Watchpoints tab, beside Breakpoints, lists them, each with check boxes for On, Read and
  Write; Add... asks for the variable and Read and/or Write (double-click one to change it), and
  a variable's right-click menu in the outline (before or while debugging) and in the variables
  tabs has Watch name.... A write stops once the
  value is set, so the variables view shows the new one, and the log (and in the JavaFX IDE the
  status line) says which watchpoint stopped it: `count written (value: 5)`, `count read`.
  An array's watchpoint stops for its elements too (`a[2]=x` writes `a`). Running to the end
  (Run while paused) passes them by, as it does breakpoints, and the debugger's own looks at the variables
  (and changes made in the variables view) don't set them off. Needs fsh's variable hooks
  (`DebugContext.variableRead`/`variableWritten`); debugging through the Loupe adapter doesn't
  stop at them yet.

Needs swing-widgets, fx-widgets, parley-files-swing, parley-files-fx and fsh 1.1.0.

### Changed

- A script can't be edited while it runs or is being debugged, in both IDEs: a change wouldn't
  reach the running script, and would move its lines away from the ones the debugger shows. It can
  still be scrolled, selected, copied and searched. The Swing IDE's title and the JavaFX IDE's
  status line say so, and it can be edited again when the run ends.
- The Swing IDE's Run menu has no Profile item: it did nothing.
- The Swing IDE's Stop is the JavaFX IDE's: a Stop button after Run, enabled while a script runs
  or is being debugged, stops it. Run no longer turns into a stop button while a script runs, and
  Debug and Run stay in the toolbar (disabled) while debugging instead of disappearing.
- Show Debug View is in the View menu, under Show Outline, in both IDEs (it was in the Run
  menu), and the Swing IDE's toolbar no longer has a Debug View check box.
- The JavaFX IDE uses the Swing IDE's icons: its Run, Debug, Stop, Resume, Step Over, Step Into
  and Suspend buttons show only the icon, with what each does as its tooltip, in the Swing IDE's
  order (Debug, Run, Stop, the script's settings, then Resume, Suspend, Step Into, Step Over);
  the redirect buttons have the redirect icons, before their fields; breakpoints and syntax errors in the margin are the
  Swing IDE's breakpoint and error icons (a breakpoint that's off is faded).
- The breakpoint dialog's buttons say Delete, Cancel and OK, in both IDEs (the Swing IDE's had
  only a trash can, a cancel and a smile icon).
- The breakpoint dialog's condition is "Stop when the expression is true", in both IDEs (it was
  "Conditional" and "Stop only when this command succeeds"), and the Swing dialog's empty
  condition box shows an example, `[ $i -gt 3 ]`, as the JavaFX one does.
- The console matches the script editor above it: the same background, text, caret and selection
  colors, following the theme (it was white in light themes). Errors stay red; in the JavaFX IDE,
  prompts are in the comment color. In both IDEs.
- Settings has a Templates tab, after Appearance, in both IDEs: the autocompletion templates in a
  list, to change (name, description and code, with a note saying whether the code parses), add
  (New), delete, or put back as they come (Restore Defaults, which keeps your own). OK keeps them
  and the open editors offer them at once. A built-in template you delete stays deleted; one you
  change takes the built-in's place. The old template window, which nothing opened, is gone.
- More templates for autocompletion: function, read-lines, read-command, elif, heredoc, subst,
  or-exit, if-not, if-file, if-dir, if-empty, if-set, match (`=~` with `BASH_REMATCH`), the string
  operations strip-prefix, strip-suffix, replace, length and substring, array, for-each, map,
  for-keys, args and getopts (option loops), and fsh's own connect and scratch (each disconnected
  when the script ends), copy-across and find-each. if and if-else give each branch its own field.
- The script editor matches the theme: a theme without editor colors of its own (the built-in ones,
  say) gives the editor a light tint of the theme's light color as its background, the line
  numbers, selection and caret in its accent, and the syntax colors darkened where they would be
  hard to read (a dark theme: a shade darker than its fields). It was white whatever the theme.
  "Match the theme" is first on the theme designer's Script editor page; it keeps no colors of its
  own, so the editor follows the theme's colors as they change. A ready-made scheme or a color of
  one's own still sets them for good. In both IDEs.
- Bigger Text, Smaller Text and their keys change the window you're in, for this session only;
  View > Editor Font... is what changes the editor font for good, in every window. Reset Text Size
  goes back to the font chosen with Editor Font... (it was 13 points), in both IDEs.
- The JavaFX IDE has Settings… too (fx-widgets' SettingsDialog), the same as the Swing IDE's: the
  theme and font with a preview, and New theme…, Edit theme… and Delete theme with the Script editor
  page for the syntax colors. OK changes the open windows at once. View > Editor Font… is in the
  JavaFX IDE as well. Before, the JavaFX IDE used what was chosen in the Swing IDE.
- The menus follow the standard Parley layout, the same in the Swing and JavaFX IDEs and in
  parley-files-viewer: File, Edit (Undo, Redo, Cut, Copy, Paste, Select All, Find…, Find Next,
  Go to Line…), View, Run, Window and Help. Settings… (⌘,) opens the appearance dialog. Debug is
  now ⇧⌘D (⌘D is the editor's Delete Line). The run options (Run Selected Code Only, Run When
  Opened, Profile) and Show Debug View moved to the Run menu; the empty Import and Export menus
  were dropped.
- The look of parley-files-viewer (swing-widgets' and fx-widgets' GradientLook), with themes: Gold
  (default), Dark, Light, Copper, Sage, Ocean, Slate, Plum, or one made from any color, chosen in
  File > Preferences with the font. The editors follow the theme: a dark theme gives a dark editor
  with matching syntax colors, in both IDEs. The JavaFX IDE uses the theme chosen in the Swing IDE.
- The script editors' syntax colors are part of the theme. The theme designer (Preferences > New
  theme... or Edit theme...) has a Script editor page (`EditorThemeExtension`, a swing-widgets
  `ThemeExtension`): load a ready-made scheme (Light, Parley dark, Dark, Eclipse, IDEA, Monokai,
  Visual Studio, Druid) and change any of the 14 colors (background, text, keywords, commands,
  comments, strings, variables, numbers, operators, selection, current line, line numbers and their
  background, caret), with a preview. A new theme starts with the colors of the theme it is made
  from; a theme without its own, such as a built-in one, gets the light colors, or the Parley dark
  ones for a dark theme. Kept in the theme file as `fsh-ide.editor.*` settings (`SyntaxColors`, in
  the UI-free core), and the JavaFX IDE uses them too.
- The editors' and console's font, apart from the appearance font: View > Bigger Text (⌘+),
  Smaller Text (⌘−) and Reset Text Size (⌘0), and in the Swing IDE View > Editor Font... (a
  fixed-width family and a size, with a preview). The line numbers follow the size. Kept in the
  IDE's preferences (`EditorFont`, in the UI-free core) for both IDEs; until it is changed, the
  editors keep the 13-point monospaced font they have always had.
- Folding has its own keys, the same in both IDEs, now that + and - are the text size: Fold
  (Cmd-Alt-[) and Unfold (Cmd-Alt-]) at the caret, Fold All and Unfold All with Shift (Ctrl for
  Cmd on Windows and Linux). In the Swing IDE they are in the View menu; in the JavaFX IDE, in
  Edit, where Fold All and Unfold All were Cmd-Shift-- and Cmd-Shift-=.

### Fixed

- The Swing editor's right-click edit menu no longer has a Breakpoint item that did nothing
  (breakpoints are toggled in the margin, or from the debug menu while debugging).
- While debugging in the Swing IDE, the editor scrolls to the line the script stopped on when it
  isn't in view (to the middle of the editor, opening a folded block it's in); a line that shows
  is left where it is, and the caret isn't moved. The JavaFX IDE already did.
- A variable a script gets from the login shell's environment (one the IDE itself doesn't have
  when it's opened from the Finder, such as `CLASSPATH` or `SDKMAN_PLATFORM` set in a startup file)
  shows on the fsh Variables tab, not with the script's own on User Variables.
- Clear ALL Run Settings... (now in the Run menu, in both IDEs; it was in File) also empties the
  arguments and redirects of the scripts open in the IDE's windows, so saving one doesn't keep
  them again, and says how many scripts' settings it cleared (the Swing IDE said nothing).
- Run > Clear Current Run Settings (below it, in both IDEs) empties only the arguments and
  redirects of the script in the window; nothing is asked, and what's kept for other scripts stays.
- The Swing IDE's stdout and stderr browse buttons open a Save dialog, where a new file can be
  named, as the JavaFX IDE's do (they only let you pick a file that was already there).
- The IDEs' own debugger takes a breakpoint's condition and hit count as Loupe does (and the Debug
  Adapter Protocol): the condition first, and only a hit where it's true counts; the hit count
  then stops on hit number N and every hit after it (it stopped on the Nth hit only, and checked
  the condition only then, so with both it stopped at most once). The dialogs say "Stop from hit
  number" (it was "Hit Count" and "Stop only on hit number"), with a tooltip saying what's
  counted; the Breakpoints tables show "when [ $i -gt 3 ], from hit 5". The Swing dialog's hit
  number is a spinner, as the JavaFX one's: OK with no number, or not a number, failed.
- The Swing IDE keeps the variables a debug run left in the Variables tables after it ends, as the
  JavaFX IDE does, until the next debug run (with Show Debug View on, the end of the run cleared
  them). They can't be edited then: the script is over.
- The Swing IDE's Find dialog: Selected Lines searches the lines that were selected (it searched
  the selection, and marked matches at the wrong place), a match at the start of the script can
  be replaced, Wrap Search wraps, the label says "Case Sensitive", and closing the dialog with its
  window's close button clears what it marked.
- JavaFX IDE: Down past the last completion in view selected one out of sight; the list scrolls
  with the selection now (Up too), and the list of a template field's choices does the same.
- The Swing IDE's autocompletion offered only some of the templates that start with what's
  typed: `if` listed the if- templates only up to `if-dir`. The completion library looks them up
  by name but kept them sorted by description; they're sorted by name now.
- The built-in templates insert code that runs. if and if-else had branches with only a comment (a
  syntax error); while and until tested `[ false ]` and `[ true ]`, which are always true (so while
  never ended), and now have a condition field; the for, select and case templates echoed the
  variable's name rather than its value (`echo i`), and now echo `$i`, linked to the loop variable's
  field. The do-statement template, not valid on its own, is gone. Unchanged copies of the old
  templates in a saved configuration are replaced; edited ones are kept.
- The JavaFX IDE's editor and console are in a fixed-width font everywhere. fsh-ide.css listed
  several families ("Menlo", "Consolas", ...), but JavaFX's CSS takes only one, so where Menlo
  isn't installed (Windows, Linux) the console was in the proportional System font; the family is
  now chosen in code from those the computer has. A font chosen with Editor Font... now reaches the
  editor's text too (RichTextFX's text didn't take it from the editor), with the line numbers two
  points smaller.

## 1.0.0 (2026-10-21)

First release as fsh-ide. Formerly BjlShellIde (`us.bringardner:bjl_shell_ide`).

### Changed
- A script's arguments and redirects are no longer written into the script. They're kept per
  user in `~/.fsh-ide/run-settings` (`RunSettingsStore`), by the script's full path (a remote
  script's URL, without any password). Saving a script writes only its code; if it has no
  arguments or redirects, nothing is kept for it, and any kept before are removed. So a script
  holds nothing but its code, and `$LINENO` and fsh's messages match the file however it's run.
  - Scripts saved with settings lines (`#BjlIdeScriptArgs=` and the others) are still read: the
    lines are hidden in the editor and give the settings, and the next save moves them to the
    store and leaves them out of the file.
  - Kept settings that may be orphans clean themselves up: each change drops those not used for
    180 days and, past 500 scripts, the least recently used; at start the IDE also drops those
    of local scripts that are gone (remote ones go by age). Both limits are in `Config.xml`
    (`runSettingsMaxAgeDays`, `runSettingsMaxEntries`). File > Clear Run Settings forgets them all.
  - `ScriptDocument` is now a script's code and its `RunSettings`; `open` and `save` go through the
    store, and `toText` is gone. fshdb reads the same store.
- Running a selection runs it on its own lines (`ScriptText.atLine`), so `$LINENO`, messages and
  the debugger use the script's line numbers.
- Runs on Java 11 or later (was Java 21), like fsh and the rest of the library, with JavaFX 17 LTS
  (17.0.20; was 21, which needs Java 17). `ScriptDocument` is a final class with the record's
  constructor, accessors, `equals`, `hashCode` and `toString`. On JavaFX 20 or later the debug
  tables' last column takes the spare width; on 17 every column shares it.
- Maven coordinates: `us.bringardner:bjl_shell_ide` → `us.bringardner:fsh-ide`.
- The sources are now the IDE that had grown inside BjlShell, which was ahead of
  this repository's copy (more templates, the recent-files menu, "Show Position",
  editable debug variables, output in an embedded console). It moved here from fsh.
- Package `us.bringardner.shell.ide` → `us.bringardner.fsh.ide`;
  `BjlShellIDE` → `FshIDE`, `BjlShellIDETextArea` → `FshIDETextArea`,
  `BjlShellTreeViewPanel` → `FshTreeViewPanel`.
- Depends on fsh and parley-files instead of bjl_shell and bjl_file_system, and on
  parley-files-swing for the Swing IDE's file chooser.
- The debug-variables table has a Type column (from this repository's old copy).
- Fixed the `native` profile's main class (it named a package that didn't exist).
- The IDE's logic is in a new package, `us.bringardner.fsh.ide.core`, that uses no UI
  toolkit, so a JavaFX (or other) front end can share it: running scripts (`ScriptRun`), the
  debugger (`DebugSession`), the settings saved in scripts (`ScriptDocument`), recent files,
  the syntax check, settings and templates. `Breakpoint`, `Template`, `Configuration` and
  `LegacyPreferences` moved there from `us.bringardner.fsh.ide`; the Swing IDE uses the core.
- Faster and lighter:
  - The syntax check runs once typing pauses, instead of copying and re-checking the
    script every 100 ms in a thread per window that kept running after the window closed.
    The parse tree is only laid out while the debug view is showing.
  - The unsaved-changes mark in the title is updated as you edit, not polled every second.
  - While debugging, the editor and variables view are updated only when the script stops
    at a breakpoint or step, not twice for every statement; the statement log is added in
    batches, shows each statement's first line, and keeps the last 200,000 characters.
  - The spelling dictionary is read once, in the background, and shared by all windows.
  - The window position is saved when the window stops moving, not on every step of a drag.
  - Files are read, and recent files checked, in the background, so a slow remote file
    system no longer freezes the window.
  - Waiting for a script to end no longer polls every 10 ms.

- A JavaFX IDE, `us.bringardner.fsh.ide.fx.FshIdeFx`, beside the Swing one and sharing its core.
  First stage: the editor (RichTextFX, with line numbers and shell colouring), open, save and
  reload on any file system (parley-files-fx's chooser, which can connect to remote ones), recent
  files shared with the Swing IDE, the script's arguments and redirect files, Run (or only the
  selected code) and Stop, and a console (fsh's `ConsoleIO`) that also takes typed input.
- The JavaFX IDE debugs: Debug (stop at breakpoints), Resume, Step Over, Step Into, Suspend and
  Stop. Breakpoints go in the editor's margin (click; right-click for the condition, hit count or
  delete) and stay with their line as text is added or removed above them. The paused line is
  marked, the variables are shown (the script's own; the shell's and the environment's at the
  tick of a box) and can be changed while paused, and the statements run are logged. Syntax errors
  are marked in the margin once typing pauses.
- `core.DebugVariables`: listing and changing a paused script's variables, used by both IDEs.
- The JavaFX IDE has find and replace (a bar under the editor: match case, whole words, regular
  expressions, wrap; Replace All keeps breakpoints on their lines), Go to Line, completion
  (Ctrl+Space: the templates, and the variables the script sets; a template's first field is
  selected to type over), and a Syntax tab with the script's parse tree (double-click to go to a
  line).
- `core.TextSearch`, `core.TemplateText` and `core.Completions`: find and replace, template
  expansion and completions, without a UI, for either IDE.
- The JavaFX IDE folds blocks (if ... fi, for/while/until/select ... done, case ... esac, { ... }):
  a toggle beside each in the margin, and Fold All / Unfold All in the Edit menu. Folding doesn't
  change the script or move breakpoints, and the debugger unfolds a block it stops in.
- The JavaFX IDE checks the spelling of comments and quoted text (not code, variables, options,
  paths, acronyms or camelCase): misspelled words are underlined, and a right-click offers
  suggestions, Ignore and Add to Dictionary. It can be switched off in the Edit menu.
- `core.FoldRegions` and `core.SpellChecking`: the blocks that fold, and spell checking, without a
  UI. The English dictionary is read once and shared by both IDEs; words added to it are kept in
  `~/.fsh-ide/words.txt`.
- The Swing editor's spelling is the JavaFX editor's (`FshSpellingParser`): the same words are underlined
  (comments and quoted text, not code, acronyms or camelCase), a right-click offers suggestions,
  Ignore and Add to Dictionary, Edit > Check Spelling switches it off, and added words go to
  `~/.fsh-ide/words.txt`, which both IDEs read. (It used the spell checker library's own parser, whose
  tooltip links are gone, and whose "Add to dictionary" had no user dictionary and only beeped.)
- A new script is empty, not one blank line (`ScriptDocument.parse("")`).

### Fixed
- `$LINENO`, `BASH_LINENO` and fsh's error messages were one line too high in scripts run by
  `ScriptRun`: it put a `#!fsh` line in front of the code. The code now runs as it is.
- `DebugVariables.set` of `$1`, `$2` ... in a function set the script's parameters (inserting the
  function's among them) and left the function's unchanged. It now sets the running function's
  (the script's outside one), through `ShellContext.setPositionalParameterValues`. `$0` can't be
  set, as in bash.
- Stop works when it is pressed while a run is still setting up its shell: the run used to start
  the script anyway, and a loop then ran on (`ScriptRun` now creates and starts the job under
  `cancel`'s lock, and not at all once it's cancelled).
- The IDE no longer fails to start on Linux and Windows: it used the macOS-only
  screen-top menu bar without checking. Elsewhere the menu bar is now on the window.
- Saved settings and templates in `Config.xml` are read again (they were replaced by the
  defaults on every start). The file is written to a temporary file first.
- Saving no longer deletes non-ASCII characters; scripts are read and written as UTF-8.
  Only invisible characters (control characters, zero-width spaces, byte order marks) are
  removed, and a non-breaking space becomes a space.
- A failed save is reported and leaves the script marked as changed.
- Open, New, Reload and Open Recent ask before discarding unsaved changes. Reload works
  for scripts on remote file systems. A newly opened script no longer counts as changed.
- Breakpoints and compile-error markers stay on their line however the text is edited
  (they used to drift, or stop on the wrong line), and line numbers are always shown.
- A script run with no arguments no longer gets one empty argument.
- Redirect files for stdin, stdout and stderr are closed when the script ends.
- Debugging only the selected code highlights the right lines; the variables view no longer
  reads the script's variables while it's still running; a breakpoint condition that can't
  be evaluated is reported once per run instead of every time it's reached.
- Starting no longer fails on unreadable saved window bounds, and a window saved on a
  screen that's since been unplugged opens on the main screen.
- A script that runs `exit`, or a run that's stopped, no longer closes the IDE.
- A script waiting for input (`read`) can be given it by typing in the output console, and
  can be stopped (it used to wait for ever). Needs fsh's new `ConsoleIO`.
- Ctrl+Shift+Add expands all folds (it collapsed them). Alt+F before Ctrl+F no longer fails.

### Still works
- Settings move from `~/.bjlshellIde/Config.xml` to `~/.fsh-ide/Config.xml`;
  the old file is copied across the first time.
- Preferences saved under the old package's node are copied to the new one on first
  start.
- Scripts saved with argument markers (`#BjlIdeScriptArgs=` and friends) keep their settings:
  they're read, and moved out of the script the next time it's saved.
