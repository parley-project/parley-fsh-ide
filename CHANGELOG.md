# Changelog

## 1.1.0 (unreleased)

Needs swing-widgets, fx-widgets, parley-files-swing, parley-files-fx and fsh 1.1.0.

### Changed

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
- The Swing editor's spelling tooltip's "Add to dictionary" now works (it had no user dictionary,
  so adding only beeped). Added words go to `~/.fsh-ide/words.txt`, which both IDEs read.
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
