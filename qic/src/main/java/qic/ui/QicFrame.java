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
package qic.ui;

import static java.util.Arrays.asList;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.List;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.TableColumnModel;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.porty.swing.table.model.BeanPropertyTableModel;

import qic.Command;
import qic.Main;
import qic.SearchPageScraper.SearchResultItem;

/**
 * @author thirdy
 *
 */
public class QicFrame extends JFrame {

	private static final long serialVersionUID = 1L;
	
	private final Logger logger = LoggerFactory.getLogger(this.getClass().getName());

	public QicFrame(Main main, String query) {
		super("QIC Search - Simple GUI");
		setLayout(new BorderLayout(5, 5));
		
		RSyntaxTextArea textArea = new RSyntaxTextArea(20, 60);
		textArea.setText("Enter a command in the textfield then press Enter..");
	    textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JSON);
	    textArea.setCodeFoldingEnabled(true);
	    RTextScrollPane sp = new RTextScrollPane(textArea);
		
		JTextField searchTf = new JTextField(100);
		JButton runBtn = new JButton("Run");
		JPanel northPanel = new JPanel();
		northPanel.setLayout(new BoxLayout(northPanel, BoxLayout.X_AXIS));
		northPanel.add(searchTf);
		northPanel.add(runBtn);
		getContentPane().add(northPanel, BorderLayout.NORTH);
//		setPreferredSize(new Dimension(1850, 1000));
//		setMinimumSize(new Dimension(800, 600));
		Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
		setSize(screenSize.width-50,screenSize.height-50);
		setLocationRelativeTo(null);
		
		searchTf.setText("search bo tmpsc ");
		if (query != null) {
			searchTf.setText(query);
		}
		
		JTable table = new JTable();
		table.setDefaultRenderer(List.class, new MultiLineTableCellRenderer());
		
		JTabbedPane tabbedPane = new JTabbedPane();
		tabbedPane.addTab("Table", new JScrollPane(table));
		tabbedPane.addTab("JSON", new JScrollPane(sp));

		BeanPropertyTableModel<SearchResultItem> model = new BeanPropertyTableModel<>(SearchResultItem.class);
		model.setOrderedProperties(
				asList("id", "buyout", "item", "seller", "requirements", "mods", "q","APS","PDPS","EDPS","DPS","ele","phys","ar","ev","ES","blk","crit","lvl"));
		table.setModel(model);
		setColumnWidths(table.getColumnModel(), 
				asList( 1,    15,        230,    230,      100,          420));
		
		getContentPane().add(tabbedPane, BorderLayout.CENTER);
		
		ActionListener runCommand = e -> {
			String tfText = searchTf.getText();

			Worker<Command> pathNotesWorker = new Worker<Command>(
					() -> runQuery(main, tfText),
					command -> {
						String json = command.toJson();
						textArea.setText(json);
						model.setData(command.itemResults);
					},
					ex -> {
						String stackTrace = ExceptionUtils.getStackTrace(ex);
						textArea.setText(stackTrace);
						showError(ex);
					});
			pathNotesWorker.execute();
		};
		
		searchTf.addActionListener(runCommand);
		runBtn.addActionListener(runCommand);
		
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		setVisible(true);
	}

	private Command runQuery(Main main, String tfText) {
		try {
			return main.processLine(tfText);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private void setColumnWidths(TableColumnModel columnModel, List<Integer> widths) {
		for (int i = 0; i < widths.size(); i++) {
			columnModel.getColumn(i).setMinWidth(widths.get(i));
		}
	}
	
	private void showError(Exception e) {
		logger.error("Exception occured: ", e);
		new qic.util.SimpleExceptionHandler().uncaughtException(Thread.currentThread(), e);
	}
}
