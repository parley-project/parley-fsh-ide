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
package us.bringardner.fsh.ide.core;

/**
 * What the breakpoint dialogs' info button shows, in either IDE: how a breakpoint's condition
 * and hit count stop it, as the Debug Adapter Protocol says (and Loupe and DebugSession do).
 */
public final class BreakpointHelp {

	private BreakpointHelp() {
	}

	public static final String TITLE = "How a breakpoint stops";

	/** The specification's section on breakpoints (SourceBreakpoint). */
	public static final String SPEC_URL = "https://microsoft.github.io/debug-adapter-protocol/specification#Types_SourceBreakpoint";

	public static final String SPEC_NAME = "Debug Adapter Protocol specification, SourceBreakpoint";

	public static final String TEXT =
			"This debugger stops as the Debug Adapter Protocol says a debugger does (and as Loupe does).\n"
			+ "\n"
			+ "What the specification says (SourceBreakpoint):\n"
			+ "\n"
			+ "condition: \"The expression for conditional breakpoints.\"\n"
			+ "\n"
			+ "hitCondition: \"The expression that controls how many hits of the breakpoint are ignored.\"\n"
			+ "\n"
			+ "\"If both this property and condition are specified, hitCondition should be evaluated only "
			+ "if the condition is met, and the debug adapter should stop only if both conditions are met.\"\n"
			+ "\n"
			+ "What that means here:\n"
			+ "\n"
			+ "1. Stop when the expression is true: the expression, a command such as "+Breakpoint.CONDITION_EXAMPLE+", "
			+ "is run each time the line is reached, and the breakpoint stops only when it succeeds. "
			+ "An expression that can't run counts as true, so you see the problem.\n"
			+ "\n"
			+ "2. Stop from hit number N: a hit is a time the line is reached (only the times the expression "
			+ "is true, if there is one). The breakpoint stops on hit N and on every hit after it. "
			+ "The hits are counted again from 0 on each run.\n"
			+ "\n"
			+ "3. With both, it stops only when both say to. With neither, it stops every time the line is reached.\n"
			+ "\n"
			+ "Reference: "+SPEC_NAME+"\n"
			+ SPEC_URL+"\n";
}
