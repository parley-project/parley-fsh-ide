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
package us.bringardner.fsh.studio;

import java.util.ArrayList;
import java.util.List;


public class CodeFormatter {
	private static int [] count(char ch, String line) {
		List<Integer> ary = new ArrayList<>();
		int pos = line.indexOf(ch);
		while(pos>=0) {
			ary.add(pos);
			pos = line.indexOf(ch,pos+1);
		}

		int [] ret = new int[ary.size()];
		for (int idx = 0; idx < ret.length; idx++) {
			ret[idx] = ary.get(idx);
		}
		return ret; 
	}

	public static String format(String code) {
		StringBuilder ret = new StringBuilder();
		StringBuilder pad = new StringBuilder();
		code = code.trim();
		List<String> comments = new ArrayList<>();
		code = removeComments(code,comments);
		code = code.replaceAll("[\r]", "");
		String lines[] = code.split("[\n]");
		int empty = 0;
		for (int idx = 0; idx < lines.length; idx++) {
			String line = lines[idx].trim();
			if( line.isEmpty()) {
				if( ++empty > 2) {
					continue;
				}
			} else {
				empty = 0;
			}
			String cmt = "";
			int cmtIdx = line.indexOf("//");
			if( cmtIdx>=0) {
				cmt = line.substring(cmtIdx);
				line = line.substring(0, cmtIdx);
			}
			int start[] = count('{', line);
			int end[] = count('}', line);
			if( start.length< end.length) {
				pad.setLength(pad.length()-(end.length-start.length));
			} 
			ret.append(pad.toString());
			ret.append(line);
			ret.append(cmt);
			ret.append('\n');
			
			if( start.length> end.length) {
				for(int mv= start.length - end.length; mv > 0; mv--) {
					pad.append('\t');
				}
			} 

		}

		return replaceComments(ret.toString(),comments);
	}

	private static final String MARKER = "~~Comment%d~~"; 
	
	private static String replaceComments(String code,List<String> comments) {
		for(int idx=0,sz=comments.size(); idx < sz; idx++ ) {
			String marker = String.format(MARKER, idx);
			code = code.replace(marker, comments.get(idx));
		}
		return code;
	}

	/*
	 * 
	 */
	private static String removeComments(String code, List<String> comments) {
		int start = code.indexOf("/*");
		while( start >=0) {
			int end = code.indexOf("*/");
			if( end < start ) {
				break;
			}
			end +=2;
			String comment = code.substring(start, end);
			String marker = String.format(MARKER, comments.size());
			code = code.substring(0,start)+marker+code.substring(end);
			comments.add(comment);
			start = code.indexOf("[/][*]");
		}
		//  put them back if they don't cause problems
		for(int idx=0,sz=comments.size(); idx < sz; idx++ ) {
			String comment = comments.get(idx);
			if( comment.indexOf('{') < 0 && comment.indexOf('}') < 0) {
				String marker = String.format(MARKER, idx);
				code = code.replace(marker, comment);
			}
		}
		return code;
	}

	public static void main(String[] args)  {
		String code = "\r\n" + 
				"    \r\n" + 
				"module ruler(len) {\r\n" + 
				"	for(pos = [.1 : .1 : len]) {\r\n" + 
				"		color(\"yellow\") translate([pos-0.02,.01,0]) cube([.05,.2,.1]);		\r\n" + 
				"	}\r\n\n" + 
				"/*" +
				"* a muliy line {\n comment"+
				"\n*/\r\n"+
				"	for(pos = [.5 : .5 : len]) {\r\n" + 
				"		color(\"red\") translate([pos-0.02,.01,0]) cube([.05,.4,.1]);		\r\n" + 
				"	}\r\n" + 
				"	\r\n" + 
				"	for(pos = [1 : 1 : len]) {\r\n" + 
				"	color(\"green\") {\r\n" + 
				"				translate([pos-0.02,.01,0]) cube([.05,.4,.1]);\r\n" + 
				"		linear_extrude(.2) {\r\n" + 
				"					translate([pos-(.3),.5,.1]) text(text=str(pos), size=.4);\r\n" + 
				"			}\r\n" + 
				"		}\r\n" + 
				"		\r\n" + 
				"	}\r\n" + 
				"}"

				;
		
		
		String fmt = format(code);
		System.out.println(fmt);

	}

}
