/*
 * Copyright (c) 2008, SQL Power Group Inc.
 * 
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in
 *       the documentation and/or other materials provided with the
 *       distribution.
 *     * Neither the name of SQL Power Group Inc. nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package ca.sqlpower.swingui.query;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DateFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.UndoableEditEvent;
import javax.swing.event.UndoableEditListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.Document;
import javax.swing.undo.UndoManager;

import org.apache.log4j.FileAppender;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rtextarea.RTextScrollPane;

import ca.sqlpower.sql.CachedRowSet;
import ca.sqlpower.sql.DataSourceCollection;
import ca.sqlpower.sql.DatabaseListChangeEvent;
import ca.sqlpower.sql.DatabaseListChangeListener;
import ca.sqlpower.sql.SPDataSource;
import ca.sqlpower.swingui.MonitorableWorker;
import ca.sqlpower.swingui.SPSUtils;
import ca.sqlpower.swingui.SwingWorkerRegistry;
import ca.sqlpower.swingui.db.DatabaseConnectionManager;
import ca.sqlpower.swingui.event.TaskTerminationEvent;
import ca.sqlpower.swingui.event.TaskTerminationListener;
import ca.sqlpower.swingui.table.ResultSetTableFactory;
import ca.sqlpower.validation.swingui.StatusComponent;

import com.jgoodies.forms.builder.DefaultFormBuilder;
import com.jgoodies.forms.layout.FormLayout;

/**
 * A "bag of components" that are already wired together to cooperate as a GUI environment
 * for writing, debugging, and executing a SQL query. There are two approaches to using
 * this class:
 * <ol>
 *  <li>Use the provided factory method, which creates an instance of the class
 *      and arranges all the components in the usual way and returns a "ready
 *      to use" Swing component that behaves as an interactive SQL query tool.
 *      The factory method is {@link #createQueryPanel(SwingWorkerRegistry, DataSourceCollection)}.
 *  <li>Use the constructor to create an instance of this class, then use
 *      the public getter methods to retrieve all of the components you want
 *      in your UI, and arrange them yourself in any layout and combination
 *      that you require.
 * </ol>
 */
public class SQLQueryUIComponents {
    
    private static final Logger logger = Logger.getLogger(SQLQueryUIComponents.class);
    
    /**
     * The entry value in the input map that will map a key press to our
     * "Execute" action.
     */
    private static final String EXECUTE_QUERY_ACTION = "Execute Query";

    /**
     * The entry value in the input map that will map a key press to our
     * undo action on the sql edit text area.
     */

    private static final Object UNDO_SQL_EDIT = "Undo SQL Edit";

    /**
     * The entry value in the input map that will map a key press to our
     * redo action on the sql edit text area.
     */
    private static final Object REDO_SQL_EDIT = "Redo SQL Edit";
    
    
    /**
     * A listener for item selection on a combo box containing {@link SPDataSource}s.
     * This will create a new entry in the connection map to store a live connection
     * for the selected database.
     */
    private class DatabaseItemListener implements ItemListener {
        
        public void itemStateChanged(ItemEvent e) {
            if (e.getStateChange() != ItemEvent.SELECTED) {
                return;
            }
            if (!conMap.containsKey(e.getItem())) {
                SPDataSource ds = (SPDataSource)e.getItem();
                try {
                    Connection con = ds.createConnection();
                    conMap.put(ds, new ConnectionAndStatementBean(con));
                } catch (SQLException e1) {
                    SPSUtils.showExceptionDialogNoReport(dialogOwner, Messages.getString("SQLQuery.failedConnectingToDBWithName", ds.getName()), e1);
                    return;
                }
            }
            try {
                autoCommitToggleButton.setSelected(conMap.get(e.getItem()).getConnection().getAutoCommit());
            } catch (SQLException ex) {
                SPSUtils.showExceptionDialogNoReport(dialogOwner, Messages.getString("SQLQuery.failedConnectingToDB"), ex);
            }
            stopButton.setEnabled(conMap.get(e.getItem()).getCurrentStmt() != null);
            executeButton.setEnabled(conMap.get(e.getItem()).getCurrentStmt() == null);
        }
    }
   
    /**
	 * This TextArea stores an exception Message if it ever Happens
     */
    private final JTextArea errorTextArea = new JTextArea();
    
    /**
     * This action will save the text in a document to a user selected file.
     * The text will either append to the file or overwrite the file's contents. 
     */
    private class SaveDocumentAction extends AbstractAction {

    	private final Document doc;
		private final Component parent;
		private final boolean append;

		public SaveDocumentAction(Document doc, Component parent, boolean append) {
			super("Save As...");
			this.doc = doc;
			this.parent = parent;
			this.append = append;
    	}
    	
		public void actionPerformed(ActionEvent e) {
			JFileChooser chooser = new JFileChooser();
			chooser.addChoosableFileFilter(SPSUtils.LOG_FILE_FILTER);
			chooser.addChoosableFileFilter(SPSUtils.TEXT_FILE_FILTER);
			chooser.setFileFilter(SPSUtils.LOG_FILE_FILTER);
			int retval = chooser.showSaveDialog(parent);
			if (retval == JFileChooser.APPROVE_OPTION) {
				if (logger.isDebugEnabled()) {
					try {
						logger.debug("Log has length " + doc.getLength() + " and text " + doc.getText(0, doc.getLength()) + " when writing to file.");
					} catch (BadLocationException e1) {
						throw new RuntimeException(e1);
					}
				}
				logger.debug("Are we appending? " + append);
				
				String filePath = chooser.getSelectedFile().getAbsolutePath();
				if (!chooser.getSelectedFile().getName().contains(".")) {
					if (chooser.getFileFilter() == SPSUtils.TEXT_FILE_FILTER) {
						filePath = filePath + ".txt";
					} else {
						filePath = filePath + ".log";
					}
				}
				if (append) {
					FileAppender appender = null;
					Logger logAppender = null;
					try {
						appender = new FileAppender(new PatternLayout("%m\n"), filePath);
						logAppender = Logger.getLogger("SQLQueryUIComponents Log Appender");
						logAppender.addAppender(appender);
						logAppender.info(doc.getText(0, doc.getLength()));
					} catch (Exception e1) {
						throw new RuntimeException(e1);
					} finally {
						if (logAppender != null && appender != null) {
							logAppender.removeAppender(appender);
						}
					}
				} else {
					try {
						FileWriter writer = new FileWriter(filePath);
						writer.write(doc.getText(0, doc.getLength()));
						writer.flush();
						writer.close();
					} catch (Exception e1) {
						throw new RuntimeException(e1);
					}
				}
			}
		}
    	
    }
   
    /**
     * This mouse listener will be attached to the log in the results area to give users
     * an easy way to save the log to a file.
     */
    private final MouseListener logPopUpMouseListener = new MouseListener() {
    	
    	private JCheckBoxMenuItem checkBoxMenuItem = new JCheckBoxMenuItem("Append", true);
    	
		public void mouseReleased(MouseEvent e) {
			logger.debug("Mouse released on log pop-up");
			showPopup(e);
		}
	
		public void mousePressed(MouseEvent e) {
			showPopup(e);	
		}
	
		public void mouseExited(MouseEvent e) {
			showPopup(e);
		}
	
		public void mouseEntered(MouseEvent e) {
			showPopup(e);
		}
	
		public void mouseClicked(MouseEvent e) {
			showPopup(e);
		}
		
		private void showPopup(MouseEvent e) {
			if (e.isPopupTrigger()) {
				JPopupMenu logPopupMenu = new JPopupMenu();
				logPopupMenu.add(new JMenuItem(new SaveDocumentAction(logTextArea.getDocument(), logTextArea, checkBoxMenuItem.isSelected())));
				logPopupMenu.add(checkBoxMenuItem);
				logPopupMenu.show(e.getComponent(), e.getX(), e.getY());
				logPopupMenu.setVisible(true);
			}
		}
	};
    
    /**
     * This will execute the sql statement in the sql text area. The
     * SQL statement used in execution will be stored with this swing
     * worker. If a different SQL statement is to be executed later
     * a new worker should be created.
     */
    private class ExecuteSQLWorker extends MonitorableWorker {
        
        private List<CachedRowSet> resultSets = new ArrayList<CachedRowSet>();
        private List<Integer> rowsAffected = new ArrayList<Integer>(); 
        private final String sqlString;
        private final int rowLimit;
		private final SPDataSource ds;
		private boolean hasStarted;
		private boolean isFinished;
		private long startExecutionTime;
        
        /**
         * Constructs a new ExecuteSQLWorker that will use the
         * given SQL statement as the string to execute on.
         */
        public ExecuteSQLWorker(SwingWorkerRegistry registry, String sqlStatement) {
        	super(registry);
        	sqlString = sqlStatement;
        	if(sqlString.equals("")) {
        		logger.debug("Empty String");
        		// if the string is empty there will be no execute so we need to reset the Panel from here.
        		firstResultPanel.removeAll();
        		firstResultPanel.revalidate();
        	} 
        	
        	ds = (SPDataSource)databaseComboBox.getSelectedItem();
        	
        	try {
                rowLimitSpinner.commitEdit();
            } catch (ParseException e1) {
                // If the spinner can't parse it's current value set it to it's previous
                // value to keep it an actual number.
                rowLimitSpinner.setValue(rowLimitSpinner.getValue());
            }
            rowLimit = ((Integer) rowLimitSpinner.getValue()).intValue();
            logger.debug("Row limit is " + rowLimit);
            
            executeButton.setEnabled(false);
            stopButton.setEnabled(true);
        }

        @Override
        public void cleanup() throws Exception {
        	try {
        		long finishExecutionTime = System.currentTimeMillis();
        		DateFormat formatter = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.LONG);
        		logTextArea.append("Executed at " + formatter.format(new Date(startExecutionTime)) + ", took " + (finishExecutionTime - startExecutionTime) + " milliseconds\n");
        		Throwable e = getDoStuffException();
        		if (e != null) {
        			String errorMessage = createErrorStringMessage(e);
        			logTextArea.append(errorMessage + "\n");
        			logger.error(e.getStackTrace());
        			return;
        		}
        		createResultSetTables(resultSets, sqlString);

        		resultSets.clear();
        		for (Integer i : rowsAffected) {
        			logTextArea.append(Messages.getString("SQLQuery.rowsAffected", i.toString()));
        			logTextArea.append("\n");
        		}  
        	} finally {
        		logTextArea.append("\n");
        		executeButton.setEnabled(true);
        		stopButton.setEnabled(false);
        		isFinished = true;
        	}
        }

        @Override
        public void doStuff() throws Exception {
        	startExecutionTime = System.currentTimeMillis();
        	hasStarted = true;
            logger.debug("Starting execute action of \"" + sqlString + "\".");
            if (ds == null) {
                return;
            }
            if (sqlString.trim().length() == 0) {
            	return;
            }
            Connection con = null;
            Statement stmt = null;
            try {
            	con = conMap.get(ds).getConnection();
                stmt = con.createStatement();
                conMap.get(ds).setCurrentStmt(stmt);
                
                stmt.setMaxRows(rowLimit);
                logger.debug("Executing statement " + sqlString);
                boolean sqlResult = stmt.execute(sqlString);
                logger.debug("Finished execution");
                boolean hasNext = true;
                
                while (hasNext) {
                    if (sqlResult) {
                        ResultSet rs = stmt.getResultSet();
                        CachedRowSet rowSet = new CachedRowSet();
                        logger.debug("Populating cached row set");
                        rowSet.populate(rs);
                        logger.debug("Result set row count is " + rowSet.size());
                        resultSets.add(rowSet);
                        rowsAffected.add(new Integer(rowSet.size()));
                        rs.close();
                    } else {
                        rowsAffected.add(new Integer(stmt.getUpdateCount()));
                        logger.debug("Update count is : " + stmt.getUpdateCount());
                    }
                    sqlResult = stmt.getMoreResults();
                    hasNext = !((sqlResult == false) && (stmt.getUpdateCount() == -1));
                }
                logger.debug("Finished Execute method");
            } finally {
                if (stmt != null) {
                    try {
                        stmt.close();
                    } catch (SQLException ex) {
                        ex.printStackTrace();
                    }
                    conMap.get(ds).setCurrentStmt(null);
                }
            }
        }

		public Integer getJobSize() {
			//This is unknown
			return null;
		}

		public String getMessage() {
			return Messages.getString("SQLQuery.workerMessage", ds.getName());
		}

		public int getProgress() {
			//The job size is always unknown for fetching from the db.
			return 0;
		}

		public boolean hasStarted() {
			return hasStarted;
		}

		public boolean isFinished() {
			return isFinished;
		}
		
		/**
		 * This will create the an error Message String similar to the details in the Exception Dialog.
		 */
		public String createErrorStringMessage(Throwable e) {
			StringWriter stringWriter = new StringWriter();
			PrintWriter traceWriter = new PrintWriter(stringWriter);
			stringWriter.write(Messages.getString("SQLQuery.queryFailed"));
			e.printStackTrace(traceWriter);
			return stringWriter.toString();
		}
        
    }
    
    /**
     * This is the Panel that holds the first result JTable, This is normally used when multiple queries
     * not enabled and you wish to return this panel instead of the tabbedResult panel.
     */
    private JPanel firstResultPanel;
    
    /**
     * The component whose nearest Window ancestor will own any dialogs
     * popped up by the query tool.
     */
    private final JComponent dialogOwner;

	/**
	 * The worker that the execute action runs on to query the database and
	 * create the result sets. If this is null there is no currently executing
	 * worker.
	 */
    private ExecuteSQLWorker sqlExecuteWorker;
    
    private final TaskTerminationListener sqlExecuteTerminationListener = new TaskTerminationListener() {
		public void taskFinished(TaskTerminationEvent e) {
			executeQuery(null);
		}
	};
    
    /**
     * This stores the next SQL statement to be run when the currently executing worker
     * is running.
     */
    private String queuedSQLStatement;
    
    /**
     * The action for executing and displaying a user's query.
     */
    private final AbstractAction executeAction; 
    
    /**
     * A mapping of data sources to live connections. These connections will be left
     * open until the panel's ancestor is closed. The connections are kept open so 
     * auto commit can be turned off and users can enter multiple queries before 
     * committing or rolling back. Additionally, it will allow switching of data
     * sources while keeping the commit or rollback execution sequence preserved.
     */
    private Map<SPDataSource, ConnectionAndStatementBean> conMap;
    
    /**
     * The text area users can enter SQL queries to get data from the database.
     */
    private final RSyntaxTextArea queryArea;
    
    /**
     * A combo box of available connections the user have specified. The selected
     * one will have the query run on it when the user hits the execute button.
     */
    private final JComboBox databaseComboBox;
    
    /**
     * A JSpinner for the user to enter the row limit of a query.
     */
    private final JSpinner rowLimitSpinner;
    
    /**
     * Toggles auto commit on an off for the selected connection.
     */
    private final JToggleButton autoCommitToggleButton;
    
    /**
     * Commits the changes made on the currently selected connection.
     */
    private final JButton commitButton;
    
    /**
     * Rolls back the changes made on the currently selected connection.
     */
    private final JButton rollbackButton;
    
    
    private JButton undoButton; 
    private JButton redoButton; 

    private JTabbedPane resultTabPane;
    private JTextArea logTextArea;
    private static final ImageIcon ICON = new ImageIcon(StatusComponent.class.getClassLoader().getResource("ca/sqlpower/swingui/query/search.png"));
    private ArrayList<JTable> resultJTables;
    
    /**
     * This maps the JTables to the SQL statement that created them.
     * Multiple tables can share the same string.
     */
    private final Map<JTable, String> tableToSQLMap;
    
    private SwingWorkerRegistry swRegistry;
    private final DataSourceCollection dsCollection;
    
    /**
     * The undo manager for the text area containing the SQL statement.
     */
    private UndoManager undoManager;
    
    private Action undoSQLStatementAction = new AbstractAction(Messages.getString("SQLQuery.undo")){

        public void actionPerformed(ActionEvent arg0) {
            if (undoManager.canUndo()) {
                undoManager.undo();
            }
            
        }
    };
        
    private Action redoSQLStatementAction = new AbstractAction(Messages.getString("SQLQuery.redo")){

        public void actionPerformed(ActionEvent arg0) {
            if (undoManager.canRedo()) {
                undoManager.redo();
            }
            
        }
    };
    
    
    /**
     * This recreates the database combo box when the list of databases changes.
     */
    private DatabaseListChangeListener dbListChangeListener = new DatabaseListChangeListener() {

        public void databaseAdded(DatabaseListChangeEvent e) {
        	logger.debug("dataBase added");
            databaseComboBox.addItem(e.getDataSource());
            databaseComboBox.revalidate();
        }

        public void databaseRemoved(DatabaseListChangeEvent e) {
        	logger.debug("dataBase removed");
            if (databaseComboBox.getSelectedItem() != null && databaseComboBox.getSelectedItem().equals(e.getDataSource())) {
                databaseComboBox.setSelectedItem(null);
            }
            
            databaseComboBox.removeItem(e.getDataSource());
            databaseComboBox.revalidate();
        }
        
    };
    /**
     * This is the method that will close the dialog and remove any connections in the dialog
     */
    public void closingDialogOwner(){
    	
    	logger.debug("attempting to close");
        boolean commitedOrRollBacked = closeConMap();
        if(commitedOrRollBacked){
        	logger.debug("removing DatabaseListChangeListener and closing window");
        	disconnectListeners();
        	Window w = SwingUtilities.getWindowAncestor(dialogOwner);
        	if(w != null) {
        		w.setVisible(false);
        	}
        }
    }

    /**
     * Closes all of the connections in the connection mapping. If the connection being closed is not
     * in an auto-commit state an option dialog will be displayed to roll back or commit the changes.
     */
	public boolean closeConMap() {
		boolean commitedOrRollBacked = true;
		for (Map.Entry<SPDataSource, ConnectionAndStatementBean> entry : conMap.entrySet()) {
            try {	
                Connection con = entry.getValue().getConnection();
                if (!con.getAutoCommit() && entry.getValue().isConnectionUncommitted()) {
                	commitedOrRollBacked = false;
                    int result = JOptionPane.showOptionDialog(dialogOwner, Messages.getString("SQLQuery.commitOrRollback", entry.getKey().getName()),
                            Messages.getString("SQLQuery.commitOrRollbackTitle"), JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null,
                            new Object[] {Messages.getString("SQLQuery.commit"), Messages.getString("SQLQuery.rollback"), "Cancel"}, Messages.getString("SQLQuery.commit"));
                    if (result == JOptionPane.OK_OPTION) {
                        con.commit();
                        commitedOrRollBacked = true;
                        con.close();
                    } else if (result == JOptionPane.NO_OPTION) {
                        con.rollback();
                        commitedOrRollBacked = true;
                        con.close();
                    }else if(result == JOptionPane.CANCEL_OPTION) {
                    	//Do nothing
                    }
                }
                
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }

		return commitedOrRollBacked;
	}

    /**
     * Listens to when the an window is added or removed. This will clean up open
     * connections and remove handlers when the window is removed.
     */
    private WindowListener windowListener = new WindowAdapter(){

		public void windowClosing(WindowEvent arg0) {
			closingDialogOwner();			
		}
	};

    /**
     * This Listener listens to anything that drops onto the queryTextArea
     */
    private class QueryTextAreaDropListener implements DropTargetListener {
    	
    	private final JTextArea queryArea;
    	
    	public QueryTextAreaDropListener(JTextArea textArea){
    		queryArea = textArea;
    	}

		public void dragEnter(DropTargetDragEvent dtde) {
			logger.debug("We are in drag enter");
		}

		public void dragExit(DropTargetEvent dte) {
			logger.debug("We are in drag Exit");
		}

		public void dragOver(DropTargetDragEvent dtde) {
			// this would be better if there was a visible indication on the text area
			// of the caret position during the drag-over
			queryArea.setCaretPosition(queryArea.viewToModel(dtde.getLocation()));
		}

		public void drop(DropTargetDropEvent dtde) {

			DataFlavor[] flavours = dtde.getTransferable().getTransferDataFlavors();

			String[] droppedStrings = null;
			boolean isCommaSeperated = false;

			// find the first acceptable data flavor
			try {
				for (int i = 0; i < flavours.length; i++) {
					String mimeType = flavours[i].getMimeType();
					//if the type is DBTree
					if (mimeType.equals("application/x-java-serialized-object; class=\"[Ljava.lang.String;\"")) {
						dtde.acceptDrop(DnDConstants.ACTION_COPY);
						logger.debug("Accepting drop of type: " + mimeType);
						droppedStrings = (String[]) dtde.getTransferable().getTransferData(flavours[i]);
						isCommaSeperated = true;
						break;
					//if the type is text
					} else if (mimeType.equals("application/x-java-serialized-object; class=java.lang.String")) {
						dtde.acceptDrop(DnDConstants.ACTION_COPY);
						logger.debug("Accepting drop of type: " + mimeType);
						String text = (String) dtde.getTransferable().getTransferData(flavours[i]);
						droppedStrings = new String[] { text };
						break;
					//if the type is file
					} else if (mimeType.equals("application/x-java-file-list; class=java.util.List")) {
						dtde.acceptDrop(DnDConstants.ACTION_COPY);
						List<?> fileList = (List<?>)dtde.getTransferable().getTransferData(flavours[i]);
						droppedStrings = new String[fileList.size()];
						for(int j = 0; j < droppedStrings.length; j++) {
							StringBuffer fileContent = new StringBuffer();
						    try {
						        BufferedReader in = new BufferedReader(new FileReader(((File)fileList.get(j))));
						        String str;
						        while ((str = in.readLine()) != null) {
						        	fileContent.append(str);
						        	fileContent.append("\n");
						        }
						        droppedStrings[j] = fileContent.toString();
						        in.close();  
						    } catch (IOException e) {
						    	logger.debug(" Can't open file " + ((File)fileList.get(j)).getName());
						    }
						}
						break;
					} else {
						logger.debug("Unsupported flavour: " + mimeType + ". continuing...");
					}
				}
			} catch (UnsupportedFlavorException e) {
				dtde.dropComplete(false);
				throw new IllegalStateException(
						"DnD system says it doesn't support a data flavour"
								+ " it already offered to us!", e);
			} catch (IOException e) {
				dtde.dropComplete(false);
				throw new RuntimeException("Drop failed due to an I/O error", e);
			}

			if (droppedStrings == null) {
				logger.debug("No supported data flavours found. Rejecting drop.");
				dtde.rejectDrop();
				return;
			}

			StringBuilder buf = new StringBuilder();
			boolean first = true;
			for (String name : droppedStrings) {
				if (!first && isCommaSeperated) {
					buf.append(", ");
				}
				buf.append(name);
				first = false;
			}
			queryArea.insert(buf.toString(), queryArea.getCaretPosition());
			dtde.dropComplete(true);

		}

		public void dropActionChanged(DropTargetDragEvent dtde) {
			logger.debug("We are in dropActionChanged");
		}
    }
    
    /**
     * This button will execute the sql statements in the text area.
     */
    private JButton executeButton;

    /**
     * This button will stop the execution of the currently executing statement
     * on the selected data source's connection that this panel holds.
     */
    private JButton stopButton;
    
    /**
     *  This button will clear the QueryTextField
     */
    private JButton clearButton;
    
    /**
     * Creates a SQLQueryEntryPanel and attaches a drag and drop listener
     * to a DB Tree.
     */
    
    /**
     * A JButton that opens up the DataBaseConnectionManager
     */
    private JButton dbcsManagerButton;
 
    /**
     * Creates a DataBaseConnectionManager so we can edit delete and add connections on the button 
     */
    private DatabaseConnectionManager dbConnectionManager;
    
    /**
     * A list of listeners that get notified when tables are
     * added or removed from the components.
     */
    private final List<TableChangeListener> tableListeners;

    /**
     * This is the document used for searching across the current result sets. This will be
     * recreated each time new results are created as it is attached to the result set JTables.
     */
	private Document searchDocument;
	
	/**
	 * If true the search field will be shown on each result tab directly above the table. If
	 * this is false then a search field can be created by retrieving the search document
	 * from the tables.
	 * <p>
	 * This is set to true by default.
	 */
	private boolean showSearchOnResults = true;
 
    /**
	 * Creates all of the components of a query tool, but does not lay them out
	 * in any physical configuration. Once you have created one of these
	 * component collections, you can obtain all of the individual pieces and
	 * put together a user interface in any way you like.
	 * <p>
	 * If you just want an easy way to build a full-featured query UI and don't
	 * want to customize its internals, see
	 * {@link #createQueryPanel(SwingWorkerRegistry, DataSourceCollection)}.
	 * 
	 * @param swRegistry
	 *            The registry with which all background tasks will be
	 *            registered. This argument must not be null.
	 * @param ds
	 *            The collection of data sources that will be available for
	 *            querying from the UI. This argument must not be null.
	 * @param panel
	 *            The component whose nearest Window ancestor will own any
	 *            dialogs generated by the parts of the query tool.
	 */
    public SQLQueryUIComponents(SwingWorkerRegistry s, DataSourceCollection ds, JComponent dialogOwner) {
        super();
        this.dialogOwner = dialogOwner;
        this.swRegistry = s;
        this.dsCollection = ds;
        this.errorTextArea.setEditable(false);
		dsCollection.addDatabaseListChangeListener(dbListChangeListener);
        resultTabPane = new JTabbedPane();
        firstResultPanel = new JPanel(new BorderLayout());
        logTextArea = new JTextArea();
        logTextArea.setEditable(false);
        logTextArea.addMouseListener(logPopUpMouseListener);
        resultTabPane.add(Messages.getString("SQLQuery.log"), new JScrollPane(logTextArea));
        
        resultJTables = new ArrayList<JTable>();
        tableToSQLMap = new HashMap<JTable, String>();
        tableListeners = new ArrayList<TableChangeListener>();
        dbConnectionManager = new DatabaseConnectionManager(ds);
        
        executeAction = new AbstractSQLQueryAction(dialogOwner, Messages.getString("SQLQuery.execute")) {

            public void actionPerformed(ActionEvent e) {
            	
            	executeQuery(queryArea.getText());
            }

        };
        
        autoCommitToggleButton = new JToggleButton(new AbstractSQLQueryAction(dialogOwner, Messages.getString("SQLQuery.autoCommit")) {
        
            public void actionPerformed(ActionEvent e) {
            	
            	if(databaseComboBox.getSelectedItem() == null){
            		return;
            	}
                Connection con = conMap.get(databaseComboBox.getSelectedItem()).getConnection();
                if (con == null) {
                    return;
                }
                try {
                    boolean isPressed = autoCommitToggleButton.getModel().isSelected();
                    if (isPressed && conMap.get(databaseComboBox.getSelectedItem()).isConnectionUncommitted()) {
                        int result = JOptionPane.showOptionDialog(dialogOwner, Messages.getString("SQLQuery.commitOrRollbackBeforeAutoCommit"),
                                Messages.getString("SQLQuery.commitOrRollbackTitle"), JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null,
                                new Object[] {Messages.getString("SQLQuery.commit"), Messages.getString("SQLQuery.cancel"), Messages.getString("SQLQuery.rollback")}, Messages.getString("SQLQuery.commit"));
                        if (result == JOptionPane.OK_OPTION) {
                            commitCurrentDB();
                        } else if (result == JOptionPane.CANCEL_OPTION) {
                            rollbackCurrentDB();
                        } else {
                            ((JToggleButton)e.getSource()).setSelected(con.getAutoCommit());
                            return;
                        }
                         
                        
                    }
                    con.setAutoCommit(isPressed);
                    logger.debug("The auto commit button is toggled " + isPressed);
                } catch (SQLException ex) {
                    SPSUtils.showExceptionDialogNoReport(dialogOwner, Messages.getString("SQLQuery.failedAutoCommit"), ex);
                }
        
            }
        
        });
        
        autoCommitToggleButton.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                if (autoCommitToggleButton.isSelected()) {
                    commitButton.setEnabled(false);
                    rollbackButton.setEnabled(false);
                } else {
                    commitButton.setEnabled(true);
                    rollbackButton.setEnabled(true);
                }
            }
        });
        
        commitButton = new JButton(new AbstractSQLQueryAction(dialogOwner, Messages.getString("SQLQuery.commit")) {
            public void actionPerformed(ActionEvent e) {
            	if(databaseComboBox.getSelectedItem() == null){
            		return;
            	}
                commitCurrentDB();
            }});
        
        rollbackButton = new JButton(new AbstractSQLQueryAction(dialogOwner, Messages.getString("SQLQuery.rollback")){
            public void actionPerformed(ActionEvent e) {
            	if(databaseComboBox.getSelectedItem() == null){
            		return;
            	}
                rollbackCurrentDB();
            }});
        
        
        rowLimitSpinner = new JSpinner(new SpinnerNumberModel(Integer.MAX_VALUE, 0, Integer.MAX_VALUE, 1));
        
        queryArea = new RSyntaxTextArea();
        queryArea.restoreDefaultSyntaxHighlightingColorScheme();
        queryArea.setSyntaxEditingStyle(RSyntaxTextArea.SQL_SYNTAX_STYLE);
        
        
        undoManager = new UndoManager();
        queryArea.getDocument().addUndoableEditListener(new UndoableEditListener() {
            public void undoableEditHappened(UndoableEditEvent e) {
                undoManager.addEdit(e.getEdit());
            }
        });
        queryArea.getActionMap().put(UNDO_SQL_EDIT, undoSQLStatementAction);
        queryArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()), UNDO_SQL_EDIT);
        
        queryArea.getActionMap().put(REDO_SQL_EDIT, redoSQLStatementAction);
        queryArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask() + InputEvent.SHIFT_MASK), REDO_SQL_EDIT);
        
        conMap = new HashMap<SPDataSource, ConnectionAndStatementBean>();
        
        databaseComboBox = new JComboBox(dsCollection.getConnections().toArray());
        databaseComboBox.setSelectedItem(null);
        databaseComboBox.addItemListener(new DatabaseItemListener());
        
        dialogOwner.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask())
                , EXECUTE_QUERY_ACTION);
        dialogOwner.getActionMap().put(EXECUTE_QUERY_ACTION, executeAction);
        
        executeButton = new JButton(executeAction);
        
        stopButton = new JButton(new AbstractSQLQueryAction(dialogOwner, Messages.getString("SQLQuery.stop")) {
            public void actionPerformed(ActionEvent arg0) {
                ConnectionAndStatementBean conBean = conMap.get(databaseComboBox.getSelectedItem());
                if (conBean != null) {
                    Statement stmt = conBean.getCurrentStmt();
                    if (stmt != null) {
                        try {
                            logger.debug("stmt is being cancelled...supposely");
                            stmt.cancel();
                            if (sqlExecuteWorker != null) {
                            	queuedSQLStatement = null;
                                sqlExecuteWorker.kill();
                                sqlExecuteWorker = null;
                            }
                        } catch (SQLException e) {
                            SPSUtils.showExceptionDialogNoReport(dialogOwner, Messages.getString("SQLQuery.stopException", ((SPDataSource)databaseComboBox.getSelectedItem()).getName()), e);
                        }
                    }
                }
            }
             });
         clearButton = new JButton(new AbstractSQLQueryAction(dialogOwner, Messages.getString("SQLQuery.clear")){
            public void actionPerformed(ActionEvent arg0) {
                queryArea.setText("");
            }});
         
         dbcsManagerButton = new JButton(new AbstractAction() {
        
            public void actionPerformed(ActionEvent e) {
                Window w = SwingUtilities.getWindowAncestor(dbcsManagerButton);
                dbConnectionManager.showDialog(w);
        
            }
        
        });
         dbcsManagerButton.setText(Messages.getString("SQLQuery.manageConnections"));
         
         undoButton= new JButton (undoSQLStatementAction);
         redoButton= new JButton (redoSQLStatementAction);
         new DropTarget(queryArea, new QueryTextAreaDropListener(queryArea));
    }
    
    /**
     * Executes a given query with the help of a worker. This will also clear
     * the results tabs before execution.
     * 
     * NOTE: If a query is currently executing then the query passed in will
     * execute after the current query is complete. Additionally, if there is 
     * a query already waiting to execute it will be REPLACED by the new query.
     * ie the previous query waiting to execute will not be run.
     */
    public synchronized void executeQuery(String sql) {
    	if (sqlExecuteWorker != null && !sqlExecuteWorker.isFinished()) {
    		if (sql != null) {
    			queuedSQLStatement = sql;
    		}
    		return;
    	} else if (sqlExecuteWorker != null && sqlExecuteWorker.isFinished()) {
    		if (sql != null) {
    			queuedSQLStatement = null;
    		} else if (sql == null && queuedSQLStatement != null) {
    			String tempSQL = sql;
    			sql = queuedSQLStatement;
   				queuedSQLStatement = tempSQL;
    		}
    		sqlExecuteWorker.removeTaskTerminationListener(sqlExecuteTerminationListener);
    		sqlExecuteWorker = null;
    	}
    	
    	if (sql == null) {
    		return;
    	}
    	
    	ConnectionAndStatementBean conBean = conMap.get(databaseComboBox.getSelectedItem());
    	try {
    		if(conBean!= null) {
    			if (!conBean.getConnection().getAutoCommit()) {
    				conBean.setConnectionUncommitted(true);
    			}
    		}
    	} catch (SQLException e1) {
    		SPSUtils.showExceptionDialogNoReport(dialogOwner, Messages.getString("SQLQuery.failedRetrievingConnection", ((SPDataSource)databaseComboBox.getSelectedItem()).getName()), e1);
    	}
    	
    	logger.debug("Executing SQL " + sql);
    	sqlExecuteWorker = new ExecuteSQLWorker(swRegistry, sql);
    	sqlExecuteWorker.addTaskTerminationListener(sqlExecuteTerminationListener);
    	new Thread(sqlExecuteWorker).start();
    }

    /**
	 * Builds the UI of the {@link SQLQueryUIComponents}. If you just want an
	 * easy way to build a full-featured query UI and don't want to customize
	 * its internals, you have come to the right place.
	 * 
	 * @param swRegistry
	 *            The registry with which all background tasks will be
	 *            registered. This argument must not be null.
	 * @param ds
	 *            The collection of data sources that will be available for
	 *            querying from the UI. This argument must not be null.
	 */
    public static JComponent createQueryPanel(SwingWorkerRegistry swRegistry, DataSourceCollection ds, Window owner) {
    	return createQueryPanel(swRegistry, ds, owner, null, null);
    }

	/**
	 * Builds the UI of the {@link SQLQueryUIComponents}. If you just want an
	 * easy way to build a full-featured query UI and don't want to customize
	 * its internals, you have come to the right place. This also allows a SQL
	 * string to initialize the query UI with.
	 * 
	 * @param swRegistry
	 *            The registry with which all background tasks will be
	 *            registered. This argument must not be null.
	 * @param dsCollection
	 *            The collection of data sources that will be available for
	 *            querying from the UI. This argument must not be null.
	 * 
	 * @param ds
	 *            The data source that the initial query will be executed on.
	 *            This data source must be contained in the dsCollection and not
	 *            null for the query to be executed. If the data source is null
	 *            then the query will not be executed.
	 * 
	 * @param initialSQL
	 *            The string that will be executed immediately when the query
	 *            tool is shown. If this is null then no query will be executed.
	 */
    public static JComponent createQueryPanel(SwingWorkerRegistry swRegistry, DataSourceCollection dsCollection, Window owner, SPDataSource ds, String initialSQL) {
        
        JPanel defaultQueryPanel = new JPanel();
        SQLQueryUIComponents queryParts = new SQLQueryUIComponents(swRegistry, dsCollection, defaultQueryPanel);
        queryParts.addWindowListener(owner);
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.add(queryParts.getExecuteButton());
        toolbar.add(queryParts.getStopButton());
        toolbar.add(queryParts.getClearButton());
        toolbar.addSeparator();
        toolbar.add(queryParts.getAutoCommitToggleButton());
        toolbar.add(queryParts.getCommitButton());
        toolbar.add(queryParts.getRollbackButton());
        toolbar.addSeparator();
        toolbar.add(queryParts.getUndoButton());
        toolbar.add(queryParts.getRedoButton());
        
        FormLayout textAreaLayout = new FormLayout(
                "pref:grow, 10dlu, pref, 10dlu, pref, 10dlu, pref"
                , "pref, pref, fill:max(100dlu;pref):grow");
        DefaultFormBuilder textAreaBuilder = new DefaultFormBuilder(textAreaLayout, defaultQueryPanel);
        textAreaBuilder.setDefaultDialogBorder();
        textAreaBuilder.append(toolbar, 7);
        textAreaBuilder.nextLine();
        textAreaBuilder.append(queryParts.getDatabaseComboBox());
        textAreaBuilder.append(queryParts.getDbcsManagerButton());
        textAreaBuilder.append(Messages.getString("SQLQuery.rowLimit"));
        JSpinner rowlimitSpinner = queryParts.getRowLimitSpinner();
        rowlimitSpinner.setValue(new Integer(1000));
        textAreaBuilder.append(rowlimitSpinner);
        textAreaBuilder.nextLine();
        textAreaBuilder.append(new RTextScrollPane(300,200, queryParts.getQueryArea(), true), 7);
        
        
        JSplitPane queryPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        
        queryPane.add(defaultQueryPanel, JSplitPane.TOP);
       
   
        queryPane.add(queryParts.getResultTabPane(), JSplitPane.BOTTOM);
        
        if (ds != null && initialSQL != null && dsCollection.getConnections().contains(ds)) {
        	queryParts.getDatabaseComboBox().setSelectedItem(ds);
        	queryParts.getQueryArea().setText(initialSQL);
        	queryParts.executeQuery(initialSQL);
        }
        
        return queryPane;
  
    }
    
    
    /**
     * If the connection to the database currently selected in the combo box is not in 
     * auto commit mode then any changes will be committed.
     */
    private void commitCurrentDB() {
        ConnectionAndStatementBean conBean = conMap.get(databaseComboBox.getSelectedItem());
        Connection con = conBean.getConnection();
        if (con == null) {
            return;
        }
        try {
            if (!con.getAutoCommit()) {
                con.commit();
                conBean.setConnectionUncommitted(false);
            }
        } catch (SQLException ex) {
            SPSUtils.showExceptionDialogNoReport(dialogOwner, Messages.getString("SQlQuery.failedCommit"), ex);
        }
    }
    
    /**
     * If the connection to the database currently selected in the combo box is not in 
     * auto commit mode then any changes will be rolled back.
     */
    private void rollbackCurrentDB() {
        ConnectionAndStatementBean conBean = conMap.get(databaseComboBox.getSelectedItem());
        Connection con = conBean.getConnection();
        if (con == null) {
            return;
        }
        try {
            if (!con.getAutoCommit()) {
                con.rollback();
                conBean.setConnectionUncommitted(false);
            }
        } catch (SQLException ex) {
            SPSUtils.showExceptionDialogNoReport(dialogOwner, Messages.getString("SQLQuery.failedRollback"), ex);
        }
    }
    
    /**
     * Creates all of the JTables for the result tab and adds them to the result tab.
     * @throws SQLException 
     */
    private synchronized void createResultSetTables(List<CachedRowSet> resultSets, String query) throws SQLException {
    	clearResultTables();

    	searchDocument = new DefaultStyledDocument();
    	for (CachedRowSet rs : resultSets) {
    		ResultSet r = rs.createShared();
    		JComponent tempTable;
    		FormLayout tableAreaLayout = new FormLayout("pref, 3dlu, pref:grow", "pref, fill:min(pref;50dlu):grow");
    		DefaultFormBuilder tableAreaBuilder = new DefaultFormBuilder(tableAreaLayout);

    		if (showSearchOnResults) {
    			JLabel searchLabel = new JLabel(ICON);
    			searchLabel.setToolTipText("Search");
    			JTextField tableFilterTextField = new JTextField(searchDocument, null, 0);
    			tableAreaBuilder.append(searchLabel, tableFilterTextField);
    		}
    		tempTable = ResultSetTableFactory.createResultSetJTableWithSearch(r, searchDocument);

    		tableAreaBuilder.nextLine();
    		JScrollPane tableScrollPane = new JScrollPane(tempTable);
    		tableAreaBuilder.append(tableScrollPane, 3);

    		resultJTables.add((JTable)tempTable);
    		tableToSQLMap.put(((JTable)tempTable), query);
    		JPanel tempResultPanel = tableAreaBuilder.getPanel();
    		resultTabPane.add(Messages.getString("SQLQuery.result"), tempResultPanel);
    		resultTabPane.setSelectedIndex(1);

    	}
    	for (JTable table : resultJTables) {
    		for (TableChangeListener l : tableListeners) {
    			l.tableAdded(new TableChangeEvent(this, table));
    		}
    	}
    }

	private void clearResultTables() {
		tableToSQLMap.clear();
    	for (JTable table : resultJTables) {
    		for (int i = tableListeners.size() - 1; i >= 0; i--) {
    			tableListeners.get(i).tableRemoved(new TableChangeEvent(this, table));
    		}
    	}
    	resultJTables.clear();
    	
    	if(resultTabPane.getComponentCount() > 1) {
    		for(int i = resultTabPane.getComponentCount()-1; i >= 1; i--){
    			resultTabPane.remove(i);
    		}
    	}
	}
    
    public void addWindowListener(Window container){
    	container.addWindowListener(windowListener);
    }


    public JButton getExecuteButton() {
    	return executeButton;
    }

    public JButton getStopButton() {
    	return stopButton;
    }

    public JButton getClearButton() {
    	return clearButton;
    }

    public JToggleButton getAutoCommitToggleButton() {
    	return autoCommitToggleButton;
    }

    public JButton getCommitButton() {
    	return commitButton;
    }

    public JButton getRollbackButton() {
    	return rollbackButton;
    }

    public JButton getUndoButton() {
    	return undoButton;
    }

    public JButton getRedoButton() {
    	return redoButton;
    }

    public JComboBox getDatabaseComboBox() {
    	return databaseComboBox;
    }

    public JButton getDbcsManagerButton() {
    	return dbcsManagerButton;
    }

    public JSpinner getRowLimitSpinner() {
    	return rowLimitSpinner;
    }

    public RSyntaxTextArea getQueryArea() {
    	return queryArea;
    }

    public JTabbedPane getResultTabPane(){
    	return resultTabPane;
    }

    public ArrayList<JTable> getResultTables (){
    	return resultJTables;
    }

    public void addTableChangeListener(TableChangeListener l) {
    	tableListeners.add(l);
    }

    public void removeTableChangeListener(TableChangeListener l) {
    	tableListeners.remove(l);
    }

    public JTextArea getLogTextArea () {
    	return logTextArea;
    }

    public JPanel getFirstResultPanel() {
    	return firstResultPanel;
    }
    
    /**
     * This will return the query that made the JTable's result set.
     * If this returns null then the table has already been removed from the
     * results tab.
     */
    public String getQueryForJTable(JTable table) {
    	return tableToSQLMap.get(table);
    }
    
    public void disconnectListeners() {
    	dsCollection.removeDatabaseListChangeListener(dbListChangeListener);
    }
    
    public Document getSearchDocument() {
		return searchDocument;
	}
    
    public void setShowSearchOnResults(boolean showSearchOnResults) {
		this.showSearchOnResults = showSearchOnResults;
	}
}


