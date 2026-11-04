/**
*	Copyright 2024 Tony Bringardner
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


public class Breakpoint {
	private static int instanceCount=-1;
	private int id;
	private boolean enabled=true;
	// kept up to date by the editor as the text is edited (0-based); read by the script thread
	private volatile int line=-1;
	private String code="";
	private boolean isConditional=false;
	private String condition="";
	private int targetHitCount=-1;
	private int actualHitCount = -1;
	private boolean isHitCount=false;
	private volatile boolean conditionErrorReported=false;
	
	
	/** How a breakpoint is changed, in either IDE's Breakpoints tab (its tooltip, its rows', its Edit... button's). */
	public static final String EDIT_HINT = "Double-click a breakpoint (or select it and click Edit...) to give it a condition or a hit count, or to delete it";

	/** An example condition, shown in an empty condition box in either IDE's breakpoint dialog. */
	public static final String CONDITION_EXAMPLE = "[ $i -gt 3 ]";

	public Breakpoint() {
		synchronized (Breakpoint.class) {
			id = ++instanceCount;	
		}		
	}
	
	




	public int getId() {
		return id;
	}
	
	public String getCode() {
		return code;
	}
	public void setCode(String code) {
		this.code = code;
	}
	public boolean isConditional() {
		return isConditional;
	}
	public void setConditional(boolean isConditional) {
		this.isConditional = isConditional;
	}
	public boolean isHitCount() {
		return isHitCount;
	}
	public void setHitCount(boolean isHitCount) {
		this.isHitCount = isHitCount;
	}
	/** When it stops, for the Breakpoints tables: "when [ $i -gt 3 ]", "from hit 5", both, or "" (every time). */
	public String stopsText() {
		String when = hasCondition() ? "when "+condition.trim() : "";
		String hit = hasHitCount() ? "from hit "+targetHitCount : "";
		return when.isEmpty() || hit.isEmpty() ? when+hit : when+", "+hit;
	}

	/** What the hit count means, for the dialogs' tooltips. */
	public static final String HIT_COUNT_HINT = "Counts the times the line is reached (only those when the expression is true, "
			+ "if there is one) and stops on hit number N and every hit after it";

	/** A condition is set (and not blank). */
	public boolean hasCondition() {
		return isConditional && condition != null && !condition.isBlank();
	}

	/** A hit count is set. */
	public boolean hasHitCount() {
		return isHitCount && targetHitCount > 0;
	}

	/**
	 * A hit: the line was reached with the breakpoint on (and its condition, if it has one, true).
	 * Counts it, as Loupe's BreakpointTable.hit does.
	 * @return true if it's to stop: there's no hit count, or this is hit number N or one after it
	 */
	public boolean countHit() {
		actualHitCount++;
		return !hasHitCount() || actualHitCount >= targetHitCount;
	}

	/** @param incHitCount not used any more: a hit is counted by {@link #countHit()} */
	public boolean isEnabled(boolean incHitCount) {
		return enabled;
	}
	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}
	
	public int getLine() {
		return line;
	}
	public void setLine(int line) {
		this.line = line;
	}
	public String getCondition() {
		return condition;
	}
	public void setCondition(String condition) {
		this.condition = condition;
	}
	public int getHitCount() {
		return targetHitCount;
	}
	public void setHitCount(int hitCount) {
		this.targetHitCount = hitCount;
	}
	
	public void reset() {
		actualHitCount = 0;
		conditionErrorReported = false;
	}

	/** True the first time it's called after reset(), so a bad condition is reported once per run. */
	public boolean reportConditionError() {
		boolean ret = !conditionErrorReported;
		conditionErrorReported = true;
		return ret;
	}
	
}
