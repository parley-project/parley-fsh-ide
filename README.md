# fsh-ide

An editor and debugger for shell scripts. It uses
[fsh](https://github.com/tony-bringardner/fsh), the FileSource Shell, as the
interpreter. fsh aims to be close to bash/POSIX, so the IDE works for those scripts
too. Scripts can be opened from and saved to any
[parley-files](https://github.com/tony-bringardner/parley-files) file system.

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

- Java 21
- fsh, which brings parley-files and its FTP and SFTP file systems
- For the Swing IDE: parley-files-swing
- For the JavaFX IDE: JavaFX 21, RichTextFX and parley-files-fx

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

## License

Apache License 2.0. See [LICENSE](LICENSE).
