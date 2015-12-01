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

import static java.util.stream.Collectors.joining;
import static org.apache.commons.lang3.StringUtils.isNumeric;
import static org.apache.commons.lang3.StringUtils.startsWithAny;
import static org.apache.commons.lang3.StringUtils.substringAfter;
import static org.apache.commons.lang3.StringUtils.substringAfterLast;
import static qic.Command.Status.ERROR;

import java.io.BufferedReader;
import java.io.Console;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

import javax.swing.JOptionPane;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import qic.Command.Status;
import qic.SearchPageScraper.SearchResultItem;
import qic.util.SwingUtil;

/**
 * @author thirdy
 *
 */
public class Main {
	

	public static Properties config;
	public static BlackmarketLanguage language;
	static String ahkPath;
	static String logPath;
	static String ahkScript;
	static boolean DEV_MODE = true;
	BackendClient backendClient = new BackendClient();
	List<SearchResultItem> items = Collections.emptyList();

	private long lastKnownPosition = 0;
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
			new Main();
		} catch (Exception e) {
			JOptionPane.showMessageDialog(null, "Error occured: " + e.getMessage());
			throw e;
		}
    }

	private static void reloadConfig() throws IOException, FileNotFoundException {
		config = loadConfig();
		language = new BlackmarketLanguage();
		ahkPath = config.getProperty("ahkpath");
		if(!new File(ahkPath).exists()) JOptionPane.showMessageDialog(null, "Your AHK path is incorrect: " + ahkPath + ". Update your config.properties file.");
		logPath = config.getProperty("poelogpath");
		if(!new File(logPath).exists()) JOptionPane.showMessageDialog(null, "Your Path of Exile Logs path is incorrect: " + logPath + ". Update your config.properties file.");
		ahkScript = config.getProperty("ahkscript", "qic.ahk");
		DEV_MODE = Boolean.parseBoolean(config.getProperty("devmode", "false"));
	}

	public Main() throws IOException, InterruptedException {

//		CommandLine cmd = new CommandLine(args);
//		String query = cmd.getArguments()[0];
//		String sort = cmd.getNumberOfArguments() == 2 ? cmd.getArguments()[1] : "price_in_chaos";

		File logFile = new File(logPath);
		lastKnownPosition = logFile.length();

		System.out.println("Startup success, now waiting for commands from client.txt");
		System.out.println("Run 'reload' to reload all configurations.");
		
		if (DEV_MODE) {
			pollCommandlineForCommands();
		} else {
			pollFileForCommands(logFile);
		}
	}

	private void pollCommandlineForCommands() throws IOException {
		exit: while (true) {
			String line = JOptionPane.showInputDialog("Enter command (ex: s boots 50life):");
			if (StringUtils.isBlank(line)) {
				break exit;
			}
			line = ":" + line;
			Command command = processLine(line);
			String jsonFile = "result.json";
			writeCommandToFile(command, jsonFile);
			callAHK(jsonFile);
			if (command.status == Status.EXIT) {
				break exit;
			}
		}
	}

	private void pollFileForCommands(File logFile) throws InterruptedException, FileNotFoundException, IOException {
		exit: while (true) {
			Thread.sleep(10);
			long fileLength = logFile.length();
			if (fileLength > lastKnownPosition) {
				RandomAccessFile readWriteFileAccess = new RandomAccessFile(logFile, "rw");
				readWriteFileAccess.seek(lastKnownPosition);
				String line = null;
				while ((line = readWriteFileAccess.readLine()) != null) {
					System.out.println(line);
					Command command = processLine(line);
					String jsonFile = "result.json";
					writeCommandToFile(command, jsonFile);
					callAHK(jsonFile);
					if (command.status == Status.EXIT) {
						break exit;
					}
				}
				lastKnownPosition = readWriteFileAccess.getFilePointer();
				readWriteFileAccess.close();
			}
		}
	}
	
	private void writeCommandToFile(Command command, String jsonFile) throws IOException {
		String json = command.toJson();
		File file = new File(jsonFile);
		FileUtils.writeStringToFile(file , json, "UTF-8", false);
	}

	private Command processLine(String line) throws IOException {
		Command command = new Command(line);
		line = substringAfterLast(line, ":").trim();
		command.command = line;

		if (!startsWithAny(line, new String[]{"#", "@", "$"})) {
			try {
				if (line.equalsIgnoreCase("searchexit") || line.equalsIgnoreCase("sexit")) {
					command.status = Status.EXIT;
				} else if (line.equalsIgnoreCase("searchend") || line.equalsIgnoreCase("se")) {
					command.status = Status.EXIT;
					items = Collections.emptyList();
					location = "";
				} else if (isNumeric(line) && !items.isEmpty()) {
					int idx = Integer.parseInt(line);
					String wtb = items.get(idx).getWTB();
					SwingUtil.copyToClipboard(wtb);
				} else if (line.equalsIgnoreCase("reload")) {
					reloadConfig();
				} else if (line.startsWith("sort") && !items.isEmpty() && !location.isEmpty()) {
					runSearch(line, true);
					command.itemResults = items;
				} else if (line.startsWith("search")) {
					String terms = substringAfter(line, "search").trim();
					if (!terms.isEmpty()) {
						runSearch(terms, false);
						command.itemResults = items;
					}
				} else if (line.startsWith("s ")) {
					String terms = substringAfter(line, "s ").trim();
					if (!terms.isEmpty()) {
						runSearch(terms, false);
						command.itemResults = items;
					}
				}
			} catch (Exception e) {
				command.status = ERROR;
				command.errorShort = e.getMessage();
				command.errorStackTrace = ExceptionUtils.getStackTrace(e);
			}
		}
		return command;
	}

	private List<SearchResultItem> runSearch(String terms, boolean sortOnly) throws Exception {
		String query = terms;
		String sort  = language.parseSortToken(terms);
		String html;
		html = downloadHtml(query, sort, sortOnly);
		SearchPageScraper scraper = new SearchPageScraper(html);
		items = scraper.parse();
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

	private void callAHK(String jsonFile) throws IOException {
		Process p = new ProcessBuilder(ahkPath, ahkScript, jsonFile).start();
	}

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
