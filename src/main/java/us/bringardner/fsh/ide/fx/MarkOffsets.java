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

/**
 * Keeps a mark (a breakpoint, at the start of its line) on its text while the text around it
 * is edited, the way a Swing document Position does: text inserted at or before the mark
 * pushes it along, text removed around it pulls it back to where the removal was.
 */
final class MarkOffsets {

	private MarkOffsets() {
	}

	/**
	 * Where mark goes when removed characters at position are replaced by inserted ones.
	 */
	static int adjust(int mark, int position, int removed, int inserted) {
		if( mark < position ) {
			return mark;
		}
		if( mark >= position+removed && !(removed > 0 && mark == position)) {
			return mark+inserted-removed;
		}
		// inside the removed text: where it was
		return position;
	}
}
