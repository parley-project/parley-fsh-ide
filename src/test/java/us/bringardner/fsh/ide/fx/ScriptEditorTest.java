package us.bringardner.fsh.ide.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import us.bringardner.fsh.ide.core.Breakpoint;
import us.bringardner.fsh.ide.core.CompileError;

/** The editor's breakpoints, errors and current line. */
public class ScriptEditorTest {

	private static ScriptEditor editor(String text) throws Exception {
		return Fx.call(()->{
			ScriptEditor e = new ScriptEditor();
			e.setText(text);
			return e;
		});
	}

	@Test
	public void breakpointsFollowTheirLine() throws Exception {
		ScriptEditor e = editor("echo a\necho b\necho c\necho d\n");
		Breakpoint bp = Fx.call(()->e.addBreakpoint(2));
		assertNotNull(bp);

		Fx.run(()->e.getCodeArea().insertText(0, "echo x\necho y\n"));
		assertEquals(Set.of(4), Fx.call(()->e.getBreakpoints().keySet()));
		assertEquals(4, bp.getLine());

		Fx.run(()->e.getCodeArea().deleteText(0, "echo x\necho y\n".length()));
		assertSame(bp, Fx.call(()->e.getBreakpoint(2)));

		// a new line typed at the start of its line moves it down with its statement
		Fx.run(()->e.getCodeArea().insertText(2, 0, "\n"));
		assertSame(bp, Fx.call(()->e.getBreakpoint(3)));

		// text added below doesn't move it
		Fx.run(()->e.getCodeArea().appendText("echo e\n"));
		assertSame(bp, Fx.call(()->e.getBreakpoint(3)));
	}

	@Test
	public void twoThatMeetBecomeOne() throws Exception {
		ScriptEditor e = editor("echo a\necho b\necho c\n");
		Fx.run(()->{
			e.addBreakpoint(0);
			e.addBreakpoint(1);
			e.getCodeArea().deleteText(0, "echo a\n".length());
		});
		assertEquals(1, Fx.call(()->e.getBreakpoints().size()));
	}

	@Test
	public void blankLinesTakeNone() throws Exception {
		ScriptEditor e = editor("echo a\n\necho c\n");
		assertNull(Fx.call(()->e.addBreakpoint(1)));
		assertEquals(0, Fx.call(()->e.getBreakpoints().size()));
	}

	@Test
	public void toggleAndListeners() throws Exception {
		ScriptEditor e = editor("echo a\necho b\n");
		int[] calls = {0};
		Fx.run(()->{
			e.addBreakpointListener(()->calls[0]++);
			e.toggleBreakpoint(1);
		});
		assertEquals(1, Fx.call(()->e.getBreakpoints().size()));
		Fx.run(()->e.toggleBreakpoint(1));
		assertEquals(0, Fx.call(()->e.getBreakpoints().size()));
		assertEquals(2, calls[0]);
		// a new text clears them
		Fx.run(()->{
			e.addBreakpoint(0);
			e.setText("echo z\n");
		});
		assertTrue(Fx.call(()->e.getBreakpoints().isEmpty()));
	}

	@Test
	public void errorsAndTheCurrentLine() throws Exception {
		ScriptEditor e = editor("echo a\nfi\n");
		Fx.run(()->e.setErrors(List.of(new CompileError(2, 0, "unexpected fi"), new CompileError(99, 0, "past the end"))));
		assertEquals("unexpected fi", Fx.call(()->e.errorAt(1)));
		assertNull(Fx.call(()->e.errorAt(0)));
		Fx.run(()->e.setErrors(List.of()));
		assertNull(Fx.call(()->e.errorAt(1)));

		Fx.run(()->e.setCurrentLine(1));
		assertEquals(List.of("current-line"), Fx.call(()->List.copyOf(e.getCodeArea().getParagraph(1).getParagraphStyle())));
		Fx.run(()->e.setCurrentLine(-1));
		assertTrue(Fx.call(()->e.getCodeArea().getParagraph(1).getParagraphStyle().isEmpty()));
	}
}
