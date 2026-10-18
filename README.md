# fsh-ide

An editor and debugger for shell scripts. It uses
[fsh](https://github.com/tony-bringardner/fsh), the FileSource Shell, as the
interpreter. fsh aims to be close to bash/POSIX, so the IDE works for those scripts
too. Scripts can be opened from and saved to any
[parley-files](https://github.com/tony-bringardner/parley-files) file system.

<img src="https://raw.githubusercontent.com/tony-bringardner/parley-brand/master/src/main/resources/us/bringardner/parley/brand/parley-icon-terminal-128.png" alt="The fsh-ide icon: the Parley tower with a terminal badge" width="96" align="right">

**An example of what [Parley](https://github.com/tony-bringardner/parley-parent#what-parley-can-help-you-build)
can help you build.** fsh-ide is an application, not part of the Parley library: it uses Parley
the same way your own application would. It wears the Parley brand from
[parley-brand](https://github.com/tony-bringardner/parley-brand), with a terminal badge.

Formerly BjlShellIde (`us.bringardner:bjl_shell_ide`).

## Features

- Syntax-aware editor with templates, auto-completion and spell checking
- Run a script or just the selected lines, with output in an embedded console
- Breakpoints (optionally conditional), step into/over/return, and a variables
  view where values can be edited while paused
- Parse-tree views of the script

## Two front ends

- **Swing**: `us.bringardner.fsh.ide.FshIDE` (the jar's main class), the full IDE.
- **JavaFX**: `us.bringardner.fsh.ide.fx.FshIdeFx`. The editor (line numbers, shell colouring,
  syntax errors marked as you type), find and replace, go to line, completion of templates and
  variables (Ctrl+Space), open/save on any file system through parley-files-fx's chooser, recent
  files (shared with the Swing IDE), script arguments and redirects, Run/Stop, a console that
  takes typed input, debugging (breakpoints with conditions and hit counts that follow their
  line, stepping, the paused line marked, variables editable while paused, the statement log)
  the script's syntax tree, folding of if/for/while/case/{} blocks, and spell checking of
  comments and quoted text (suggestions on right-click; your own words go in
  `~/.fsh-ide/words.txt`).

Both use the UI-free `us.bringardner.fsh.ide.core` package for running, debugging, files and
settings.

## Requirements

- Java 11 or later, like fsh and the rest of the library
- fsh, which brings parley-files and its FTP and SFTP file systems
- For the Swing IDE: parley-files-swing
- For the JavaFX IDE: JavaFX 17 LTS (17.0.20), RichTextFX and parley-files-fx

## Build and run

```sh
mvn package                 # jar in target/
mvn -Pjdbc package          # also bundle the JDBC file system
mvn -Pnative package        # GraalVM native binary named fsh-ide
java -cp ... us.bringardner.fsh.ide.FshIDE
```

## Settings

Templates and settings are kept in `~/.fsh-ide/Config.xml`; window positions and
recent files in Java preferences. Both are copied across from BjlShellIde's
locations the first time fsh-ide starts.

Settings… (⌘, on macOS, in the application menu; Ctrl+, at the end of the File menu elsewhere) sets the look: the theme and font, shared with the other Parley applications,
and kept in Java preferences; the JavaFX IDE uses what the Swing IDE saved. The script editors'
syntax colors are part of the theme: New theme... and Edit theme... there have a Script editor
page, where a ready-made scheme can be loaded and any color changed. A new theme starts with the
colors of the theme it is made from; the built-in themes have the light colors, or the Parley dark
ones for the Dark theme.

The script editors and the console have their own font, apart from the look's: View > Bigger
Text and Smaller Text (⌘+ and ⌘−, Ctrl on Windows and Linux) change its size, Reset Text Size (⌘0)
goes back to 13 points, and View > Editor Font... chooses a fixed-width font. Both IDEs keep it in
Java preferences (the JavaFX IDE has the size keys; the font is chosen in the Swing IDE).

Folding is on the bracket keys, the same in both IDEs: ⌥⌘[ folds the block the caret is in and
⌥⌘] unfolds it; with Shift, all blocks (Ctrl and Alt on Windows and Linux).

## Arguments and redirects

A script's arguments and its stdin, stdout and stderr files are not written into the script:
the script file holds only its code, so it runs the same (and `$LINENO` and error messages give
the same lines) in fsh-ide, fshdb, fsh or bash. They're kept per user in
`~/.fsh-ide/run-settings`, by the script's full path, when you save the script; a script with
none has nothing kept, and clearing them and saving removes what was kept. fshdb uses them too.

What's kept for scripts that were deleted, renamed outside the IDE or are on servers you no
longer use cleans itself up: settings not used for 180 days go, at most 500 scripts are kept
(the least recently used go first), and at start the IDE drops those of local scripts that are
gone. `runSettingsMaxAgeDays` and `runSettingsMaxEntries` in `Config.xml` change the limits;
File > Clear Run Settings forgets them all.

Scripts saved by earlier versions carry them in comment lines (`#BjlIdeScriptArgs=` ...); those
are read, and the next save moves them out of the script.

## Debugging through a Loupe adapter

**Run > Debug Through the Loupe Adapter** debugs the script on fsh's Loupe adapter
([fsh-loupe](../fsh-loupe), started in this JVM) instead of through fsh's hooks directly.
The IDE then speaks the [Loupe](../parley-loupe/PROTOCOL.md) protocol, the same one other
tools (and editors, through parley-loupe's DAP bridge) use. `LoupeSession`, in `core`, is the
client and has no UI; it can also talk to an adapter that is a program (`exec`) or listening
on a socket (`connect`).

Through the adapter the script has no standard input, its streams can't be redirected, and the
log of every statement run isn't kept; breakpoint conditions, hit counts, steps and editing
variables work as before.

## License

Apache License 2.0. See [LICENSE](LICENSE).
