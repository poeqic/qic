package qic.util;

import static org.apache.commons.lang3.StringUtils.substringAfter;
import static org.apache.commons.lang3.StringUtils.substringBefore;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
	
	 public static List<String> regexMatches(String regex, String s, int group) {
		 return regexMatches(regex, s, group, Pattern.CASE_INSENSITIVE);
	 }
	public static List<String> regexMatches(String regex, String s, int group, int flags) {
		 List<String> allMatches = new ArrayList<String>();
		 Matcher m = Pattern.compile(regex, flags)
		     .matcher(s);
		 while (m.find()) {
		   allMatches.add(m.group(group));
		 }
		 return allMatches;
	 }
	
	public static String encodeQueryParm(String queryParam) {
		String key = substringBefore(queryParam, "=");
		String value = substringAfter(queryParam, "=");
		try {
			value = URLEncoder.encode(value, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e); 
		}
		return key + "=" + value;
	}
}
