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
package qic;

import static org.apache.commons.lang3.StringUtils.startsWithAny;
import static org.apache.commons.lang3.StringUtils.substringAfter;
import static org.apache.commons.lang3.StringUtils.substringAfterLast;
import static qic.Command.Status.ERROR;

import java.awt.BorderLayout;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.List;
import java.util.Properties;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;

import qic.Command.Status;
import qic.SearchPageScraper.SearchResultItem;
import qic.util.CommandLine;
import qic.util.SwingUtil;

/**
 * @author thirdy
 *
 */
public class Main {
	
	public static Properties config;
	public static BlackmarketLanguage language;
	BackendClient backendClient = new BackendClient();

	private String location = "";

	public static void main(String[] args) throws Exception {
		System.out.println("QIC (Quasi-In-Chat) Search 0.2");
		System.out.println("QIC is 100% free and open source licensed under GPLv2");
		System.out.println("Created by the contributors of: https://github.com/poeqic");
		System.out.println();
		System.out.println("Project Repo: https://github.com/poeqic/qic");
		System.out.println("Project Page: https://github.com/poeqic/qic");
		System.out.println();
		System.out.println("QIC is fan made tool and is not affiliated with Grinding Gear Games in any way.");

		try {
			reloadConfig();
			new Main(args);
		} catch (Exception e) {
			JOptionPane.showMessageDialog(null, "Error occured: " + e.getMessage());
			throw e;
		}
    }

	private static void reloadConfig() throws IOException, FileNotFoundException {
		config = loadConfig();
		language = new BlackmarketLanguage();
	}

	public Main(String[] args) throws IOException, InterruptedException {
		CommandLine cmd = new CommandLine(args);
		boolean guiEnabled = cmd.hasFlag("-gui");

		System.out.println("guiEnabled: " + guiEnabled);
		
		if (guiEnabled) {
			showGui(cmd.getArgument(0));
		} else {
			if (cmd.getNumberOfArguments() == 0) {
				throw new IllegalArgumentException("First arguement needed, and should be the query. e.g. 'search chest 100life 6s5L'. "
						+ "Enclosed in double quoutes if needed.");
			}
			String query = cmd.getArgument(0);
			System.out.println("Query: " + query);
			
			Command command = processLine(query);
			String json = command.toJson();
			writeToFile(json);
		}
	}

	private void showGui(String query) {
		JFrame frame = new JFrame("QIC Search - Simple GUI");
		frame.setLayout(new BorderLayout(5, 5));
		
		RSyntaxTextArea textArea = new RSyntaxTextArea(20, 60);
		textArea.setText("Enter a command in the textfield below then press Enter..");
	    textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JSON);
	    textArea.setCodeFoldingEnabled(true);
	    RTextScrollPane sp = new RTextScrollPane(textArea);
		
		JTextField tf = new JTextField(100);
		frame.getContentPane().add(new JScrollPane(sp), BorderLayout.CENTER);
		frame.getContentPane().add(tf, BorderLayout.SOUTH);
		frame.setSize(1000, 700);
		frame.setLocationRelativeTo(null);
		
		if (query != null) {
			tf.setText(query);
		}
		
		tf.addActionListener(e -> {
			try {
				String tfText = tf.getText();
				textArea.setText("Running command: " + tfText);
				Command command = processLine(tfText);
				String json = command.toJson();
				textArea.setText(json);
				writeToFile(json);
			} catch (Exception ex) {
				String stackTrace = ExceptionUtils.getStackTrace(ex);
				textArea.setText(stackTrace);
			}
		});
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setVisible(true);
	}

	private void writeToFile(String contents) throws IOException {
		String jsonFile = "results.json";
		File file = new File(jsonFile);
		FileUtils.writeStringToFile(file , contents, "UTF-8", false);
	}

	private Command processLine(String line) throws IOException {
		Command command = new Command(line);

		try {
			if (line.equalsIgnoreCase("searchend") || line.equalsIgnoreCase("se")) {
				command.status = Status.EXIT;
				location = "";
			} else if (line.equalsIgnoreCase("reload")) {
				reloadConfig();
			} else if (line.startsWith("sort")&& !location.isEmpty()) {
				command.itemResults = runSearch(line, true);
			} else if (line.startsWith("search")) {
				String terms = substringAfter(line, "search").trim();
				if (!terms.isEmpty()) {
					command.itemResults = runSearch(line, false);
				}
			} else if (line.startsWith("s ")) {
				String terms = substringAfter(line, "s ").trim();
				if (!terms.isEmpty()) {
					command.itemResults = runSearch(line, false);
				}
			}
		} catch (Exception e) {
			command.status = ERROR;
			command.errorShort = e.getMessage();
			command.errorStackTrace = ExceptionUtils.getStackTrace(e);
		}
		return command;
	}

	private List<SearchResultItem> runSearch(String terms, boolean sortOnly) throws Exception {
		String query = terms;
		String sort  = language.parseSortToken(terms);
		String html;
		html = downloadHtml(query, sort, sortOnly);
		SearchPageScraper scraper = new SearchPageScraper(html);
		List<SearchResultItem> items = scraper.parse();
		System.out.println("items found: " + items.size());
		return items;
	}

//	private Stream<SearchResultItem> updateDisplay(int page) throws IOException {
//		if(items.isEmpty()) {
//			setDisplayMessage("Result: " + 0);
//			return;
//		}

//		int skip = (pageSize * page) % items.size();
//		if (skip >= items.size()) {
//			--currentPage;
//			return;
//		}
//		Stream<SearchResultItem> result = items.stream().skip(skip).limit(pageSize);
//		Stream<SearchResultItem> result = items.stream();
//		return result;
//	}

//	private void callAHK(String jsonFile) throws IOException {
//		Process p = new ProcessBuilder(ahkPath, ahkScript, jsonFile).start();
//	}

	public String downloadHtml(String query, String sort, boolean sortOnly) throws Exception {
		long start = System.currentTimeMillis();

		if (!sortOnly) {
			String queryPrefix = config.getProperty("queryprefix");
			String finalQuery = queryPrefix + " " + query;
			System.out.println("finalQuery: " + finalQuery);
			String payload = language.parse(finalQuery);
			location  = submitSearchForm(payload);
		}

		System.out.println("sort: " + sort);
		String searchPage = ajaxSort(sort);
		long end = System.currentTimeMillis();

		System.out.println("Took " + (end - start) + " ms");
		// Add a bit of delay, just in case
		Thread.sleep(30);
		return searchPage;
	}

	private String ajaxSort(String sort) throws Exception {
		String searchPage = "";
		sort = URLEncoder.encode(sort, "UTF-8");
		sort = "sort=" + sort + "&bare=true";
		searchPage = backendClient.postXMLHttpRequest(location, sort);
		return searchPage;
	}

	private String submitSearchForm(String payload) throws Exception {
		String url = "http://poe.trade/search";
		String location = backendClient.post(url, payload);
		return location;
	}

    private static Properties loadConfig() throws IOException, FileNotFoundException {
		Properties config = new Properties();
		try (BufferedReader br = new BufferedReader(new FileReader(new File("config.properties")))) {
			config.load(br);
		}
		return config;
	}
}
