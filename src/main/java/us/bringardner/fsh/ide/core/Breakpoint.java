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
	/** When it stops, for the Breakpoints tables: "when [ $i -gt 3 ]", "on hit 5", both, or "" (every time). */
	public String stopsText() {
		String when = hasCondition() ? "when "+condition.trim() : "";
		String hit = hasHitCount() ? "on hit "+targetHitCount : "";
		return when.isEmpty() || hit.isEmpty() ? when+hit : when+", or "+hit;
	}

	/** A condition is set (and not blank). */
	public boolean hasCondition() {
		return isConditional && condition != null && !condition.isBlank();
	}

	/** A hit count is set. */
	public boolean hasHitCount() {
		return isHitCount && targetHitCount > 0;
	}

	/**
	 * The line was reached (by a run, with the breakpoint on): counts it.
	 * @return true if this is the hit count's time (the Nth, just once); false if there's no hit count
	 */
	public boolean countHit() {
		return ++actualHitCount == targetHitCount && hasHitCount();
	}

	public boolean isEnabled(boolean incHitCount) {
		boolean ret = enabled;
		if( enabled && incHitCount && targetHitCount>0) {
			ret = ++actualHitCount == targetHitCount;
		}
		
		return ret;
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
