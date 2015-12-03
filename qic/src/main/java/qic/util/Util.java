package qic.util;

import org.apache.commons.lang3.StringUtils;

public class Util {
	
	public static String removeThoseDamnWhiteSpace(String s) {
		s = StringUtils.deleteWhitespace(s);
		StringBuilder sb = new StringBuilder();
		char[] charArray = s.toCharArray();
		for (char c : charArray) {
			if (!Character.isSpaceChar(c)) {
				sb.append(c);
			}
		}
		return sb.toString();
	}
	
}
