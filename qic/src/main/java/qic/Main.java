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

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.joining;
import static org.apache.commons.lang3.StringUtils.substringAfter;
import static qic.Command.Status.ERROR;

import java.awt.BorderLayout;
import java.awt.event.ActionListener;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qic.BlackmarketLanguage.ParseResult;
import qic.Command.Status;
import qic.SearchPageScraper.SearchResultItem;
import qic.util.CommandLine;
import qic.util.SessProp;
import qic.util.Util;

/**
 * TODO, REFACTOR!!!
 * 
 * @author thirdy
 *
 */
public class Main {
	
	private final static Logger logger = LoggerFactory.getLogger(Main.class.getName());
	
	public static BlackmarketLanguage language;
	BackendClient backendClient = new BackendClient();
	SessProp sessProp = new SessProp();
	Long searchDuration = null; 
	List<String> invalidSearchTerms = null; 

	public static void main(String[] args) throws Exception {
		logger.info("QIC (Quasi-In-Chat) Search 0.2");
		logger.info("QIC is 100% free and open source licensed under GPLv2");
		logger.info("Created by the contributors of: https://github.com/poeqic");
		logger.info("");
		logger.info("Project Repo: https://github.com/poeqic/qic");
		logger.info("Project Page: https://github.com/poeqic/qic");
		logger.info("");
		logger.info("QIC is fan made tool and is not affiliated with Grinding Gear Games in any way.");

		try {
			reloadConfig();
			new Main(args);
		} catch (Exception e) {
			JOptionPane.showMessageDialog(null, "Error occured: " + e.getMessage());
			throw e;
		}
    }

	private static void reloadConfig() throws IOException, FileNotFoundException {
		language = new BlackmarketLanguage();
	}

	public Main(String[] args) throws IOException, InterruptedException {
		CommandLine cmd = new CommandLine(args);
		boolean guiEnabled = cmd.hasFlag("-gui");
		guiEnabled = guiEnabled || cmd.getNumberOfArguments() == 0;

		logger.info("guiEnabled: " + guiEnabled);
		
		if (guiEnabled) {
			showGui(cmd.getArgument(0));
		} else {
			if (cmd.getNumberOfArguments() == 0) {
				throw new IllegalArgumentException("First arguement needed, and should be the query. e.g. 'search chest 100life 6s5L'. "
						+ "Enclosed in double quoutes if needed.");
			}
			String query = cmd.getArgument(0);
			logger.info("Query: " + query);
			
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
		
		JTextField searchTf = new JTextField(100);
		JButton runBtn = new JButton("Run");
		frame.getContentPane().add(new JScrollPane(sp), BorderLayout.CENTER);
		JPanel northPanel = new JPanel();
		northPanel.setLayout(new BoxLayout(northPanel, BoxLayout.X_AXIS));
		northPanel.add(searchTf);
		northPanel.add(runBtn);
		frame.getContentPane().add(northPanel, BorderLayout.NORTH);
		frame.setSize(1000, 700);
		frame.setLocationRelativeTo(null);
		
		searchTf.setText("search bo tmpsc gloves 4L 60res");
		if (query != null) {
			searchTf.setText(query);
		}
		
		ActionListener runCommand = e -> {
			try {
				String tfText = searchTf.getText();
				textArea.setText("Running command: " + tfText);
				Command command = processLine(tfText);
				String json = command.toJson();
				textArea.setText(json);
				writeToFile(json);
			} catch (Exception ex) {
				String stackTrace = ExceptionUtils.getStackTrace(ex);
				textArea.setText(stackTrace);
			}
		};
		
		searchTf.addActionListener(runCommand);
		runBtn.addActionListener(runCommand);
		
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setVisible(true);
	}

	private void writeToFile(String contents) throws IOException {
		Util.overwriteFile("results.json", contents);
	}

	private Command processLine(String line) throws IOException {
		Command command = new Command(line);
		searchDuration = null;
		invalidSearchTerms = new LinkedList<>();
		try {
			if (line.equalsIgnoreCase("searchend") || line.equalsIgnoreCase("se")) {
				command.status = Status.EXIT;
				sessProp.clear();
			} else if (line.equalsIgnoreCase("reload")) {
				reloadConfig();
			} else if (line.startsWith("sort")&& !sessProp.getLocation().isEmpty()) {
				command.itemResults = runSearch(line, true);
				command.searchDuration = searchDuration;
			} else if (line.startsWith("search")) {
				String terms = substringAfter(line, "search").trim();
				if (!terms.isEmpty()) {
					command.itemResults = runSearch(terms, false);
					command.searchDuration = searchDuration;
				}
			} else if (line.startsWith("s ")) {
				String terms = substringAfter(line, "s ").trim();
				if (!terms.isEmpty()) {
					command.itemResults = runSearch(terms, false);
					command.searchDuration = searchDuration;
				}
			}
			command.league = sessProp.getLeague();
			command.invalidSearchTerms = invalidSearchTerms;
			command.status = Status.SUCCESS;
		} catch (Exception e) {
			e.printStackTrace();
			command.status = ERROR;
			command.errorMessage = e.getMessage();
			command.errorStackTrace = ExceptionUtils.getStackTrace(e);
		}
		return command;
	}

	private List<SearchResultItem> runSearch(String terms, boolean sortOnly) throws Exception {
		String html = downloadHtml(terms, sortOnly);
		SearchPageScraper scraper = new SearchPageScraper(html);
		List<SearchResultItem> items = scraper.parse();
		logger.info("items found: " + items.size());
		return items;
	}

	public String downloadHtml(String terms, boolean sortOnly) throws Exception {
		long start = System.currentTimeMillis();
		
		String regex = "([^\\s]*=\".*?\")";
		List<String> customHttpKeyVals = Util.regexMatches(regex, terms, 1);
		String customHttpKeyVal = customHttpKeyVals.stream()
				.map(s -> StringUtils.remove(s, '"'))
				.collect(Collectors.joining("&")); 
		String query = terms.replaceAll(regex, " ");
		
		ParseResult sortParseResult = language.parseSortToken(query);
		String sort = sortParseResult.result;
		sort = sort == null ? "price_in_chaos" : sort;
		invalidSearchTerms.addAll(sortParseResult.invalidSearchTerms);
		
		if (!sortOnly) {
			logger.info("Query: " + query);
			ParseResult queryParseResult = language.parse(query);
			String payload = queryParseResult.result;
			invalidSearchTerms.addAll(queryParseResult.invalidSearchTerms);
			payload = asList(payload, customHttpKeyVal).stream().filter(StringUtils::isNotBlank).collect(joining("&"));
			logger.info("Unencoded payload: " + payload);
			payload = asList(payload.split("&")).stream().map(Util::encodeQueryParm).collect(joining("&"));
			String location  = submitSearchForm(payload);
			String league = language.parseLeagueToken(query);
			sessProp.setLocation(location);
			sessProp.setLeague(league);
			sessProp.saveToFile();
		}

		logger.info("sort: " + sort);
		String searchPage = ajaxSort(sort);
		long end = System.currentTimeMillis();

		long duration = end - start;
		logger.info("Took " + duration + " ms");
		searchDuration = Long.valueOf(duration);
		return searchPage;
	}

	private String ajaxSort(String sort) throws Exception {
		String searchPage = "";
		sort = URLEncoder.encode(sort, "UTF-8");
		sort = "sort=" + sort + "&bare=true";
		searchPage = backendClient.postXMLHttpRequest(sessProp.getLocation(), sort);
		return searchPage;
	}

	private String submitSearchForm(String payload) throws Exception {
		String url = "http://poe.trade/search";
		String location = backendClient.post(url, payload);
		return location;
	}


}
