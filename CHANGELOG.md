# Changelog

## 1.0.0 (unreleased)

First release as fsh-ide. Formerly BjlShellIde (`us.bringardner:bjl_shell_ide`).

### Changed
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
- The Swing editor's spelling tooltip's "Add to dictionary" now works (it had no user dictionary,
  so adding only beeped). Added words go to `~/.fsh-ide/words.txt`, which both IDEs read.
- A new script is empty, not one blank line (`ScriptDocument.parse("")`).

### Fixed
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
- Script argument markers (`#BjlIdeScriptArgs=` and friends) in saved scripts are
  unchanged, so existing scripts keep their settings.
