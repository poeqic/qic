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
package qic.launcher;

import java.awt.BorderLayout;
import java.awt.Image;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.imageio.ImageIO;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.airhacks.airfield.AirfieldException;
import com.airhacks.airfield.TakeDown;

/**
 * @author thirdy
 *
 */
public class Main {
	
	public static final String GITHUB_RELEASES_REPO = "https://github.com/poeqic/qic-releases.git";
	public static final String CHANGELOG_URL = "http://poeqic.github.io/changelog.txt";
	public static final String REPO_DIRECTORY_PATH = "./qic-files";
	private String ahkExePath;
	
	private final Logger logger = LoggerFactory.getLogger(this.getClass().getName());

	public void start(String[] args) {
		ahkExePath 		= args.length > 0 ? args[0] : "";
		if (StringUtils.isBlank(ahkExePath)) {
			ahkExePath = ahkPathFromRegexOrDefault("C:/Program Files/AutoHotkey/AutoHotkey.exe");
		}
		String remote 	= args.length > 1 ? args[1] : GITHUB_RELEASES_REPO;
        
        PrintStream takedownPrintStream = new PrintStream(new LoggingOutputStream(logger));
		TakeDown installer = new TakeDown(REPO_DIRECTORY_PATH, remote, takedownPrintStream);
		logger.info("Airfield takedown object successfully initialized");
		boolean updatesAvailable = false;
		try {
			logger.info("Checking for updates..");
			updatesAvailable = installer.updatesAvailable();
			logger.info("updatesAvailable: " + updatesAvailable);
		} catch (AirfieldException e) {
			showErrorAndQuit(e);
		}
		
		if (updatesAvailable) {
			SwingUtilities.invokeLater(() -> startGUI(installer));
		} else {
			runAIC();
		}
	}

	private String ahkPathFromRegexOrDefault(String path) {
		String value = null;
		try {
			value = WinRegistry.readString (
				    WinRegistry.HKEY_LOCAL_MACHINE,             
				   "SOFTWARE\\AutoHotKey",  
				   "InstallDir");								
		} catch (IllegalArgumentException | IllegalAccessException | InvocationTargetException e) {
			e.printStackTrace();
		}
		if (value == null) {
			try {
				value = WinRegistry.readString (
					    WinRegistry.HKEY_LOCAL_MACHINE,             
					   "SOFTWARE\\Wow6432Node\\AutoHotKey",  
					   "InstallDir");								
			} catch (IllegalArgumentException | IllegalAccessException | InvocationTargetException e) {
				e.printStackTrace();
			}
		}
		value = value != null ? value + "/AutoHotKey.exe" : path;
		logger.info("Resolved ahkExePath: " + value);
		File ahkExe = new File(value);
		if (!ahkExe.exists()) {
			showErrorAndQuit(new Exception("Failed to resolve path to AutoHotKey.exe. To specify the path manually, edit run.bat and append the path after 'qic-launcher.jar'. E.g. ..... qic-launcher.jar \"D:\\Program Files\\AHK\\AutoHotKey.exe\""));
		}
		if (ahkExe.isDirectory()) {
			showErrorAndQuit(new Exception(ahkExe + " is a directory. Check your run.bat file."));
		}
		return value;
	}

	private void startGUI(TakeDown installer) {
		TextAreaWithBackground textArea = new TextAreaWithBackground();
		
		JButton launchButton = new JButton("  Launch  ");
		launchButton.setEnabled(false);
		JProgressBar progressBar = new JProgressBar();
		
		launchButton.addActionListener(e -> {
			runAIC();
			System.exit(0);
		});
		
		JPanel southPanel = new JPanel();
		southPanel.setLayout(new BoxLayout(southPanel, BoxLayout.X_AXIS));
		southPanel.add(progressBar);
		southPanel.add(launchButton);
		JFrame frame = new JFrame("QIC Search Updater");
		frame.setLayout(new BorderLayout(5, 5));
		JScrollPane scrollPane = new JScrollPane(textArea);
		scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
		frame.getContentPane().add(scrollPane, BorderLayout.CENTER);
		frame.getContentPane().add(southPanel, BorderLayout.SOUTH);
		frame.setSize(495, 445);
		frame.setLocationRelativeTo(null);
		
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setVisible(true);
		
		textArea.setText("Loading path notes...");
		
		String imgUrl = "http://poeqic.github.io/launcher/images/background.png";
		try {
			Image image = ImageIO.read(new URL(imgUrl));
			if (image != null)
				textArea.setBackgroundImage(image);
		} catch (IOException ex) {
			logger.error("Error while loading background image from: " + imgUrl, ex);
		}
		
		Worker<String> pathNotesWorker = new Worker<String>(
				() -> URLConnectionReader.getText(CHANGELOG_URL),
				s -> textArea.setText(s),
				e -> showErrorAndQuit(e));
		pathNotesWorker.execute();
		
		Worker<Boolean> updaterWorker = new Worker<Boolean>(
				() -> {
				    progressBar.setIndeterminate(true);
				    return installer.installOrUpdate();
				},
				b -> {
					progressBar.setIndeterminate(false);
					launchButton.setEnabled(true);
				},
				e -> showErrorAndQuit(e));
		updaterWorker.execute();

	}

	private void showErrorAndQuit(Exception e) {
		logger.error("Exception occured: ", e);
		new SimpleExceptionHandler().uncaughtException(Thread.currentThread(), e);
		System.exit(0);
	}

	private void runAIC() {
		String ahk = REPO_DIRECTORY_PATH + "/qic.ahk"; 
		logger.info("Running QIC AHK Script: " + ahk);
		try {
			Process p = new ProcessBuilder(ahkExePath, ahk).start();
			logger.info("Successfully started " + ahk + ", launcher is now exiting.");
		} catch (Exception e) {
			showErrorAndQuit(e);
		}
	}

	public static void main(String[] args) {
		System.out.println("Commandline usage:");
		System.out.println("param1: [PATH_TO_AHK_EXE]");
		System.out.println("param2: [PATH_TO_REMOTE_GIT_REPO]");
		System.out.println("otherwise");
		System.out.println("Launcher will use defaults");
		
		Main main = new Main();
		main.start(args);
	}
	
	private class Worker<T> extends SwingWorker<T, Void> {
		Supplier<T> supplier;
		Consumer<T> consumer;
		Consumer<Exception> onException;
		public Worker(Supplier<T> supplier, Consumer<T> consumer, Consumer<Exception> onException) {
			super();
			this.supplier = supplier;
			this.consumer = consumer;
			this.onException = onException;
		}
		@Override public T doInBackground() {
	        return supplier.get();
	    }
		@Override protected void done() {
			try {
				consumer.accept(get());
			} catch (Exception e) {
				onException.accept(e);
			}
		}
	}
}
