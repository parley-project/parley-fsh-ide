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

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiConsumer;

import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;
import org.fxmisc.richtext.model.TwoDimensional.Bias;
import org.fife.com.swabunga.spell.engine.SpellDictionary;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Polygon;
import us.bringardner.fsh.ide.core.Breakpoint;
import us.bringardner.fsh.ide.core.CompileError;
import us.bringardner.fsh.ide.core.FoldRegions;
import us.bringardner.fsh.ide.core.SpellChecking;
import us.bringardner.fsh.ide.core.TemplateText;

/**
 * The script editor: a code area with shell syntax colouring and a gutter. The gutter has
 * line numbers, breakpoints (click to toggle; right-click for a breakpoint's properties),
 * syntax errors (hover for the message) and, while a script is paused, an arrow at its line.
 * Breakpoints stay with their line as text is added or removed above them.
 * <p>
 * Use it on the JavaFX application thread, except {@link #getBreakpoint(int)}.
 */
public class ScriptEditor extends StackPane {

	private final CodeArea area = new CodeArea();
	// each breakpoint and the offset of the start of its line, kept up to date as text changes
	private final Map<Breakpoint,Integer> marks = new LinkedHashMap<>();
	// line -> breakpoint, rebuilt after each change; the script's thread reads it
	private volatile Map<Integer,Breakpoint> breakpoints = Collections.emptyMap();
	private final Map<Integer,String> errors = new TreeMap<>();
	private int currentLine = -1;
	private final List<Runnable> breakpointListeners = new ArrayList<>();
	private BiConsumer<Breakpoint,javafx.scene.input.MouseEvent> onBreakpointMenu;
	// the blocks that can be folded, from the last scan of the text
	private List<FoldRegions.Region> folds = new ArrayList<>();
	// spell checking: the dictionary once it's read, the words found, the words to leave alone
	private boolean spellChecking = true;
	private SpellDictionary dictionary;
	private List<int[]> misspelled = new ArrayList<>();
	private final java.util.Set<String> ignored = new java.util.HashSet<>();

	public ScriptEditor() {
		area.getStyleClass().add("script-editor");
		area.setParagraphGraphicFactory(this::gutter);
		area.multiPlainChanges()
			.successionEnds(Duration.ofMillis(150))
			.subscribe(ignore->highlight());
		area.plainTextChanges().subscribe(c->textChanged(c.getPosition(), c.getRemoved().length(), c.getInserted().length()));
		area.setOnContextMenuRequested(e->{
			int pos = area.hit(e.getX(), e.getY()).getInsertionIndex();
			contextMenu(pos).show(area, e.getScreenX(), e.getScreenY());
			e.consume();
		});
		getChildren().add(new VirtualizedScrollPane<>(area));
		SpellChecking.english().thenAccept(d->Platform.runLater(()->{
			dictionary = d;
			highlight();
		}));
	}

	// ---- the gutter

	private Node gutter(int line) {
		if( isCollapsed(line)) {
			// folded away: RichTextFX hides the line's text but not its gutter, so give it none
			return new javafx.scene.layout.Region();
		}
		// a breakpoint or error, then where the script is paused, each in its own slot
		StackPane marker = new StackPane();
		marker.setMinWidth(14);
		marker.setPrefWidth(14);
		marker.setAlignment(Pos.CENTER);
		Breakpoint bp = breakpoints.get(line);
		if( bp != null ) {
			Circle c = new Circle(5);
			boolean on = bp.isEnabled(false);
			c.setFill(on ? Color.web("#d93636") : Color.TRANSPARENT);
			c.setStroke(Color.web("#a32020"));
			c.getStyleClass().add("breakpoint-marker");
			marker.getChildren().add(c);
		}
		String error = errors.get(line);
		if( error != null ) {
			Label x = new Label("!");
			x.getStyleClass().add("error-marker");
			Tooltip.install(marker, new Tooltip(error));
			marker.getChildren().add(x);
		}
		StackPane here = new StackPane();
		here.setMinWidth(10);
		here.setPrefWidth(10);
		if( line == currentLine ) {
			Polygon arrow = new Polygon(0, 0, 8, 5, 0, 10);
			arrow.setFill(Color.web("#2e8b57"));
			here.getChildren().add(arrow);
		}
		marker.setOnMouseClicked(e->{
			if( e.getButton() == MouseButton.SECONDARY || e.isControlDown()) {
				Breakpoint b = breakpoints.get(line);
				if( b != null && onBreakpointMenu != null ) {
					onBreakpointMenu.accept(b, e);
				}
			} else if( e.getButton() == MouseButton.PRIMARY ) {
				toggleBreakpoint(line);
			}
			e.consume();
		});
		Label fold = new Label();
		fold.setMinWidth(12);
		fold.setPrefWidth(12);
		fold.getStyleClass().add("fold-toggle");
		if( foldAt(line) != null ) {
			boolean folded = isFolded(line);
			fold.setText(folded ? "\u25b8" : "\u25be");
			fold.setTooltip(new Tooltip(folded ? "Unfold" : "Fold"));
			fold.setOnMouseClicked(e->{
				toggleFold(line);
				e.consume();
			});
		}
		// the line number (drawn here rather than by RichTextFX, which adds a "+" to folded lines)
		Label number = new Label(""+(line+1));
		number.getStyleClass().add("lineno");
		number.setMinWidth(34);
		number.setAlignment(Pos.CENTER_RIGHT);
		number.setPadding(new javafx.geometry.Insets(0, 4, 0, 0));
		HBox box = new HBox(marker, here, number, fold);
		box.setAlignment(Pos.CENTER_LEFT);
		return box;
	}

	/** Draws the gutter again (RichTextFX only redraws a line's gutter when the line changes). */
	private void redrawGutter() {
		area.setParagraphGraphicFactory(this::gutter);
	}

	// ---- breakpoints

	private void textChanged(int position, int removed, int inserted) {
		if( marks.isEmpty()) {
			return;
		}
		for(Map.Entry<Breakpoint,Integer> e : marks.entrySet()) {
			e.setValue(MarkOffsets.adjust(e.getValue(), position, removed, inserted));
		}
		if( refreshBreakpoints()) {
			redrawGutter();
			fireBreakpointsChanged();
		}
	}

	/**
	 * Puts each breakpoint on the line its mark is now on; two that end up on the same line
	 * (the text between them was deleted) become one.
	 * @return true if any changed line
	 */
	private boolean refreshBreakpoints() {
		boolean changed = false;
		Map<Integer,Breakpoint> map = new TreeMap<>();
		int length = area.getLength();
		for(Iterator<Map.Entry<Breakpoint,Integer>> it = marks.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<Breakpoint,Integer> e = it.next();
			int offset = Math.max(0, Math.min(e.getValue(), length));
			int line = area.offsetToPosition(offset, Bias.Forward).getMajor();
			if( map.containsKey(line)) {
				it.remove();
				changed = true;
				continue;
			}
			if( line != e.getKey().getLine()) {
				e.getKey().setLine(line);
				changed = true;
			}
			map.put(line, e.getKey());
		}
		breakpoints = Collections.unmodifiableMap(map);
		return changed;
	}

	/** The breakpoint on line (0-based), or null. Safe from any thread. */
	public Breakpoint getBreakpoint(int line) {
		return breakpoints.get(line);
	}

	/** Breakpoints by line (0-based); a snapshot, safe from any thread. */
	public Map<Integer,Breakpoint> getBreakpoints() {
		return breakpoints;
	}

	/** Adds a breakpoint on line (0-based), unless the line is blank; returns it (or the one there). */
	public Breakpoint addBreakpoint(int line) {
		Breakpoint bp = breakpoints.get(line);
		if( bp != null || line < 0 || line >= area.getParagraphs().size()) {
			return bp;
		}
		String code = area.getParagraph(line).getText().trim();
		if( code.isEmpty()) {
			return null;
		}
		bp = new Breakpoint();
		bp.setCode(code);
		bp.setLine(line);
		marks.put(bp, area.getAbsolutePosition(line, 0));
		refreshBreakpoints();
		redrawGutter();
		fireBreakpointsChanged();
		return bp;
	}

	public void removeBreakpoint(Breakpoint bp) {
		if( marks.remove(bp) != null ) {
			refreshBreakpoints();
			redrawGutter();
			fireBreakpointsChanged();
		}
	}

	/** Adds a breakpoint on line, or removes the one there. */
	public void toggleBreakpoint(int line) {
		Breakpoint bp = breakpoints.get(line);
		if( bp != null ) {
			removeBreakpoint(bp);
		} else {
			addBreakpoint(line);
		}
	}

	/** Shows a breakpoint's changed properties (enabled or not). */
	public void breakpointChanged() {
		redrawGutter();
		fireBreakpointsChanged();
	}

	/** Called when breakpoints are added, removed, moved or changed. */
	public void addBreakpointListener(Runnable l) {
		breakpointListeners.add(l);
	}

	private void fireBreakpointsChanged() {
		for(Runnable l : breakpointListeners) {
			l.run();
		}
	}

	/** Called with a breakpoint whose marker was right-clicked. */
	public void setOnBreakpointMenu(BiConsumer<Breakpoint,javafx.scene.input.MouseEvent> handler) {
		this.onBreakpointMenu = handler;
	}

	// ---- syntax errors and the current line

	/** Shows these syntax errors in the gutter (their lines are 1-based); replaces any shown. */
	public void setErrors(List<CompileError> list) {
		errors.clear();
		for(CompileError e : list) {
			if( e.line >= 1 ) {
				errors.merge(e.line-1, e.msg, (a, b)->a+"\n"+b);
			}
		}
		redrawGutter();
	}

	/** The syntax error on line (0-based), or null. */
	String errorAt(int line) {
		return errors.get(line);
	}

	/** Marks line (0-based) as where the script is paused, and shows it; -1 for none. */
	public void setCurrentLine(int line) {
		if( currentLine >= 0 && currentLine < area.getParagraphs().size()) {
			List<String> style = new ArrayList<>(area.getParagraph(currentLine).getParagraphStyle());
			style.remove("current-line");
			area.setParagraphStyle(currentLine, style);
		}
		currentLine = line;
		if( line >= 0 && line < area.getParagraphs().size()) {
			// the paused line is shown, even inside a folded block
			reveal(line);
			List<String> style = new ArrayList<>(area.getParagraph(line).getParagraphStyle());
			style.add("current-line");
			area.setParagraphStyle(line, style);
			area.showParagraphInViewport(line);
		}
		redrawGutter();
	}

	public int getCurrentLine() {
		return currentLine;
	}

	/** The (0-based) line the caret is on. */
	public int getCaretLine() {
		return area.getCurrentParagraph();
	}

	// ---- text

	/** Colours the text, marks misspelled words, and finds the blocks that fold. */
	private void highlight() {
		String text = area.getText();
		List<FoldRegions.Region> found = FoldRegions.find(text);
		boolean foldsChanged = !sameRegions(found, folds);
		folds = found;
		misspelled = spellChecking && dictionary != null
				? SpellChecking.misspelled(text, w->ignored.contains(w) || dictionary.isCorrect(w))
				: new ArrayList<>();
		if( !text.isEmpty()) {
			StyleSpans<Collection<String>> spans = ShellHighlighter.compute(text);
			if( !misspelled.isEmpty()) {
				spans = spans.overlay(misspelledSpans(text.length()), (a, b)->{
					if( b.isEmpty()) {
						return a;
					}
					List<String> both = new ArrayList<>(a);
					both.addAll(b);
					return both;
				});
			}
			area.setStyleSpans(0, spans);
		}
		if( foldsChanged ) {
			redrawGutter();
		}
	}

	private StyleSpans<Collection<String>> misspelledSpans(int length) {
		StyleSpansBuilder<Collection<String>> b = new StyleSpansBuilder<>();
		int last = 0;
		for(int[] r : misspelled) {
			b.add(Collections.emptyList(), r[0]-last);
			b.add(Collections.singletonList("misspelled"), r[1]-r[0]);
			last = r[1];
		}
		b.add(Collections.emptyList(), length-last);
		return b.create();
	}

	private static boolean sameRegions(List<FoldRegions.Region> a, List<FoldRegions.Region> b) {
		if( a.size() != b.size()) {
			return false;
		}
		for(int i=0; i < a.size(); i++) {
			if( a.get(i).start != b.get(i).start || a.get(i).end != b.get(i).end ) {
				return false;
			}
		}
		return true;
	}

	// ---- folding

	/** The block that starts on line (0-based), or null. */
	FoldRegions.Region foldAt(int line) {
		for(FoldRegions.Region r : folds) {
			if( r.start == line ) {
				return r;
			}
		}
		return null;
	}

	private boolean isCollapsed(int paragraph) {
		return paragraph >= 0 && paragraph < area.getParagraphs().size()
				&& area.getParagraph(paragraph).getParagraphStyle().contains("collapse");
	}

	/** True if the block starting on line is folded (the lines after it are hidden). */
	public boolean isFolded(int line) {
		return isCollapsed(line+1);
	}

	/** Folds the block starting on line (0-based): it shows just its first line. */
	public void fold(int line) {
		FoldRegions.Region r = foldAt(line);
		if( r != null && !isFolded(line) && !isCollapsed(line)) {
			area.foldParagraphs(r.start, r.end);
			redrawGutter();
		}
	}

	/** Unfolds the block starting on line (0-based). */
	public void unfold(int line) {
		if( isFolded(line)) {
			area.unfoldParagraphs(line);
			redrawGutter();
		}
	}

	public void toggleFold(int line) {
		if( isFolded(line)) {
			unfold(line);
		} else {
			fold(line);
		}
	}

	/** Folds every block, the inner ones first. */
	public void foldAll() {
		List<FoldRegions.Region> list = new ArrayList<>(folds);
		Collections.reverse(list);
		for(FoldRegions.Region r : list) {
			fold(r.start);
		}
	}

	public void unfoldAll() {
		for(FoldRegions.Region r : new ArrayList<>(folds)) {
			unfold(r.start);
		}
	}

	/** Shows line if it's inside a folded block. */
	private void reveal(int line) {
		while( isCollapsed(line)) {
			int start = line-1;
			while( start > 0 && isCollapsed(start)) {
				start--;
			}
			area.unfoldParagraphs(start);
		}
	}

	// ---- spelling

	/** Turns spell checking of comments and quoted text on or off. */
	public void setSpellChecking(boolean on) {
		spellChecking = on;
		highlight();
	}

	public boolean isSpellChecking() {
		return spellChecking;
	}

	/** True once the dictionary has been read (it's read in the background). */
	boolean isDictionaryReady() {
		return dictionary != null;
	}

	/** The misspelled words, each [start, end). */
	List<int[]> misspelledWords() {
		return misspelled;
	}

	/** The misspelled word at pos, as [start, end), or null. */
	int[] misspelledAt(int pos) {
		for(int[] r : misspelled) {
			if( pos >= r[0] && pos <= r[1] ) {
				return r;
			}
		}
		return null;
	}

	/** Leaves word alone from now on (until the IDE closes). */
	void ignore(String word) {
		ignored.add(word);
		highlight();
	}

	/** Adds word to the user's dictionary (~/.fsh-ide/words.txt). */
	void addToDictionary(String word) throws java.io.IOException {
		if( dictionary != null ) {
			SpellChecking.addUserWord(dictionary, word);
			highlight();
		}
	}

	/** The right-click menu at pos: spelling suggestions if a misspelled word is there, then cut, copy and paste. */
	ContextMenu contextMenu(int pos) {
		ContextMenu menu = new ContextMenu();
		int[] word = misspelledAt(pos);
		if( word != null && dictionary != null ) {
			String w = area.getText(word[0], word[1]);
			List<String> suggestions = SpellChecking.suggestions(dictionary, w, 6);
			for(String s : suggestions) {
				MenuItem item = new MenuItem(s);
				item.setOnAction(e->area.replaceText(word[0], word[1], s));
				menu.getItems().add(item);
			}
			if( suggestions.isEmpty()) {
				MenuItem none = new MenuItem("No suggestions");
				none.setDisable(true);
				menu.getItems().add(none);
			}
			MenuItem ignore = new MenuItem("Ignore \""+w+"\"");
			ignore.setOnAction(e->ignore(w));
			MenuItem add = new MenuItem("Add \""+w+"\" to Dictionary");
			add.setOnAction(e->{
				try {
					addToDictionary(w);
				} catch (java.io.IOException ex) {
					ignore(w);
				}
			});
			menu.getItems().addAll(new SeparatorMenuItem(), ignore, add, new SeparatorMenuItem());
		}
		MenuItem cut = new MenuItem("Cut");
		cut.setOnAction(e->area.cut());
		MenuItem copy = new MenuItem("Copy");
		copy.setOnAction(e->area.copy());
		MenuItem paste = new MenuItem("Paste");
		paste.setOnAction(e->area.paste());
		menu.getItems().addAll(cut, copy, paste);
		return menu;
	}

	/** Replaces the text, puts the caret at the start, and forgets the undo history and breakpoints. */
	public void setText(String text) {
		marks.clear();
		breakpoints = Collections.emptyMap();
		errors.clear();
		currentLine = -1;
		area.replaceText(text);
		area.moveTo(0);
		area.requestFollowCaret();
		area.getUndoManager().forgetHistory();
		highlight();
		redrawGutter();
		fireBreakpointsChanged();
	}

	public String getText() {
		return area.getText();
	}

	/** The selected text, or null if there's none. */
	public String getSelectedText() {
		String s = area.getSelectedText();
		return s == null || s.isEmpty() ? null : s;
	}

	/** The (0-based) line the selection starts on. */
	public int getSelectionStartLine() {
		return area.offsetToPosition(area.getSelection().getStart(), Bias.Forward).getMajor();
	}

	public CodeArea getCodeArea() {
		return area;
	}

	/** Selects [start, end) and scrolls to it. */
	public void select(int start, int end) {
		area.selectRange(start, end);
		area.requestFollowCaret();
	}

	/**
	 * Puts the caret at the start of line (1-based) and scrolls to it.
	 * @return false if there's no such line
	 */
	public boolean goToLine(int line) {
		if( line < 1 || line > area.getParagraphs().size()) {
			return false;
		}
		area.moveTo(line-1, 0);
		area.showParagraphInViewport(line-1);
		area.requestFollowCaret();
		return true;
	}

	/**
	 * Replaces [start, end) with an expanded template, then selects its first field (to type
	 * over) or puts the caret where the template says, else after it.
	 */
	public void insert(int start, int end, TemplateText.Expansion e) {
		area.replaceText(start, end, e.text);
		if( e.cursor >= 0 ) {
			area.moveTo(start+e.cursor);
		} else if( !e.fields.isEmpty()) {
			area.selectRange(start+e.fields.get(0)[0], start+e.fields.get(0)[1]);
		} else {
			area.moveTo(start+e.text.length());
		}
		area.requestFollowCaret();
	}
}
