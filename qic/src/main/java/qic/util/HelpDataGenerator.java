/*
 * Copyright (C) 2015 thirdy
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package qic.util;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import qic.BlackmarketLanguage;

/**
 * @author thirdy
 *
 */
public class HelpDataGenerator {

	/**
	 * @param args
	 * @throws IOException 
	 */
	public static void main(String[] args) throws IOException {
		BlackmarketLanguage bmlang = new BlackmarketLanguage();
		List<String[]> data = new LinkedList<>();
		bmlang.getDictionaries().entrySet().stream()
			.map(HelpDataGenerator::toDatum)
			.forEach(data::addAll);
		String json = Util.toJsonPretty(data);
		json = "var dataSet = " + json;
		Util.overwriteFile("help/js/help-data.js", json);
	}
	
	public static List<String[]> toDatum(Entry<String, Map<String, String>> es) {
		String file = es.getKey();
		return es.getValue().entrySet().stream()
			.map(e -> new String[] { e.getKey(), file, e.getValue() })
			.collect(Collectors.toList());
	}

}
