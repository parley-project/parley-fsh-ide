# fsh-ide

An editor and debugger for shell scripts. It uses
[fsh](https://github.com/parley-project/parley-fsh), the FileSource Shell, as the
interpreter. fsh aims to be close to bash/POSIX, so the IDE works for those scripts
too. Scripts can be opened from and saved to any
[parley-files](https://github.com/parley-project/parley-files) file system.

<img src="https://raw.githubusercontent.com/parley-project/parley-brand/master/src/main/resources/us/bringardner/parley/brand/parley-icon-terminal-128.png" alt="The fsh-ide icon: the Parley tower with a terminal badge" width="96" align="right">

**An example of what [Parley](https://github.com/parley-project/parley-parent#what-parley-can-help-you-build)
can help you build.** fsh-ide is an application, not part of the Parley library: it uses Parley
the same way your own application would. It wears the Parley brand from
[parley-brand](https://github.com/parley-project/parley-brand), with a terminal badge.

Formerly BjlShellIde (`us.bringardner:bjl_shell_ide`).

## Features

- Syntax-aware editor with templates, auto-completion and spell checking
- Run a script or just the selected lines, with output in an embedded console
- Breakpoints (optionally conditional), variable watchpoints (stop when the script reads a
  variable, writes it, or both), step into/over/return, and a variables view where values can
  be edited while paused
- Parse-tree views of the script

## The IDE

`us.bringardner.fsh.ide.FshIDE` (the jar's main class) is the IDE, a Swing application. It uses
the UI-free `us.bringardner.fsh.ide.core` package for running, debugging, files and settings.

A JavaFX IDE was built beside it until November 2026. It is no longer part of fsh-ide; the tag
`javafx-final` is the last commit that has it.

## Requirements

- Java 11 or later, like fsh and the rest of the library
- fsh, which brings parley-files and its FTP and SFTP file systems
- parley-files-swing and swing-widgets

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
and kept in Java preferences. The script editors'
syntax colors are part of the theme: New theme... and Edit theme... there have a Script editor
page, where a ready-made scheme can be loaded and any color changed. A new theme starts with the
colors of the theme it is made from; the built-in themes have the light colors, or the Parley dark
ones for the Dark theme.

The script editors and the console have their own font, apart from the look's. View > Editor
Font... chooses it, a fixed-width family and a size, for every window, kept in Java
preferences. View > Bigger Text and Smaller Text (⌘+ and ⌘−, Ctrl on Windows and Linux) change the
size in the window you're in, for now only: other windows and the font kept don't change. Reset Text
Size (⌘0) puts the window back to the font chosen with Editor Font....

Folding is on the bracket keys: ⌥⌘[ folds the block the caret is in and
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
Run > Clear ALL Run Settings forgets them all.

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
log of every statement run isn't kept, and watchpoints don't stop the script (the Loupe
protocol has no watchpoints yet); breakpoint conditions, hit counts, steps and editing
variables work as before.

## Debugging on another machine

**Run > Debug Remote...** debugs a script that runs on another machine. It asks for
the machine, the port, and what to debug there:

| | On the other machine | Here |
|---|---|---|
| The script in the editor, run there | fsh-loupe's adapter, listening: `java -cp ... us.bringardner.fsh.loupe.FshAdapterMain --listen 0.0.0.0:4711` | The editor's script is sent and runs there, with that machine's files and environment; its breakpoints are the editor's |
| A script file on that machine | The same adapter | The script is read and run there (give its path there). The editor shows it while it is debugged, stopped before its first command: set breakpoints, then Resume |
| A script waiting there for a debugger | The script itself: `fsh --wait-for-debugger 0.0.0.0:4711 script arguments` (fsh-loupe on fsh's class path) | The IDE attaches. The editor shows the script, stopped before its first command. Its output stays where it was started |

While the editor shows another machine's script, the window's own script (and its breakpoints)
is kept, and put back when the session ends; Save and the unsaved-changes question are still
about the window's own script. The arguments field goes to a script the IDE starts there; the
redirect fields must be empty (an attach ignores them). Stop ends the script, also one the IDE
attached to; closing the connection without Stop (the IDE quits, the network drops) leaves an
attached script running and ends one the IDE started.

**Protocol.** The IDE speaks Loupe to the adapter unless DAP is chosen in the dialog: it is a
Debug Adapter Protocol client too (`DapSession`, in `core`), for an adapter that listens on a
port and speaks DAP. fsh-loupe's ports take either, so nothing changes on the other machine.
Over DAP the adapter doesn't give a script's text (parley-loupe's bridge has no DAP `source`
request), so a script file there, or one attached to, is debugged without being shown: the
editor keeps its own script, and the IDE says so. A script's launch is described in fsh-loupe's
terms (`source`, `script`, `args`), which another language's adapter won't know.

The connection has **no login and no encryption**, and whoever can reach the port can run
commands as the user the adapter runs as: use it only on a network where everyone who can
reach the port is trusted. The last settings are kept in `~/.fsh-ide/Config.xml`.
`RemoteDebug` and `AdapterSession`, in `core`, hold the settings and the session and have no UI
(`LoupeSession` and `DapSession` are the two sessions).

As through the adapter in this JVM: no standard input for a script the IDE starts, no log of
every statement, no watchpoints. The variables of a remote script are shown but
can't be changed. A stop inside a sourced
file shows that file's line number on the main script (only the main script's text is shown).

## License

Apache License 2.0. See [LICENSE](LICENSE).
