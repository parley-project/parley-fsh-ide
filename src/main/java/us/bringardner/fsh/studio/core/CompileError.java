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
package us.bringardner.fsh.studio.core;

/** A syntax error found in a script. */
public class CompileError {
	/** 1-based */
	public final int line;
	/** 0-based */
	public final int col;
	public final String msg;

	public CompileError(int line, int col, String msg) {
		this.line = line;
		this.col = col;
		this.msg = msg;
	}

	@Override
	public String toString() {
		return line+","+col+" "+msg;
	}
}
