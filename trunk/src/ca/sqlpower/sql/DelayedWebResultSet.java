package ca.sqlpower.sql;

import java.sql.*;
import java.util.*;
import ca.sqlpower.util.*;
// The CachedRowSet from Sun is still Early Access, so we can't use it in production
import sun.jdbc.rowset.*;

public class DelayedWebResultSet extends WebResultSet {

	/**
	 * Holds the set of cached resultset objects (keyed on SQL query
	 * string).  The maximum number of members for this cache is
	 * specified as 100.
	 */
	protected static Cache resultCache = new SynchronizedCache(new LeastRecentlyUsedCache(100));

	protected int givenColCount;

	/**
	 * The JDBC Connection that was last passed to the execute()
	 * method, or null if execute hasn't been called yet.  This
	 * instance variable may be moved up to the WebResultSet class in
	 * the future.
	 */
	protected Connection con;

	/**
	 * Controls whether or not this instance of DelayedWebResultSet
	 * will use the cache of query results.  It is almost always best
	 * to use the cache, except:
	 * <ul>
	 *  <li>When the data rendered to this screen is expected to have changed
	 *  <li>When the expected data set is too large to cache in RAM
 	 * </ul>
	 */
	protected boolean cacheEnabled;

	/**
	 * Creates a new <code>DelayedWebResultSet</code> which uses the
	 * query resultset cache.
	 *
	 * @param cols The number of columns the <code>query</code> will
	 * generate when executed.
	 * @param query An SQL query statement.
	 */
	public DelayedWebResultSet(int cols, String query) {
		this(cols, query, true);
	}

	/**
	 * Creates a new <code>DelayedWebResultSet</code>.
	 *
	 * @param cols The number of columns the <code>query</code> will
	 * generate when executed.
	 * @param query An SQL query statement.
	 * @param useCache If true, this DelayedWebResultSet will try to
	 * read from or write to the cache on execute.
	 */
	public DelayedWebResultSet(int cols, String query, boolean useCache) {
		this.sqlQuery=query;
		this.givenColCount=cols;
		this.cacheEnabled=useCache;
		this.con=null;
		initMembers(cols);
	}

	/**
	 * Does nothing.  Provided for subclasses that want to use
	 * different constructor signatures.
	 */
	protected DelayedWebResultSet() {
		super();
		this.sqlQuery=null;
		this.givenColCount=0;
		this.cacheEnabled=true;
		this.con=null;		
	}

	/**
	 * Executes the query that was given in the constructor.  After
	 * this method has been called, the DelayedWebResultSet will
	 * behave exactly like a regular WebResultSet.
	 *
	 * @param con A live JDBC connection.
	 * @throws IllegalStateException if the actual number of columns
	 * generated by executing the query doesn't match the number
	 * (<code>cols</code>) given to the constructor.
	 * @throws SQLException if there is a database error (most often
	 * due to a syntax error in the SQL query).
	 */
	public void execute(Connection con) throws IllegalStateException, SQLException {
		try {
			execute(con, true);
		} catch (SQLException e) {
			System.out.println("dwrs caught sqlexception from query: "+sqlQuery);
			throw e;
		}
	}

	/**
	 * Executes the query that was given in the constructor.  After
	 * this method has been called, the DelayedWebResultSet will
	 * behave exactly like a regular WebResultSet.
	 *
	 * @param con A live JDBC connection.
	 * @param closeOldRS If this argument is <code>true</code>, any
	 * previous (and still-open) ResultSet attached to this
	 * DelayedWebResultSet will be closed before binding the new one.
	 * @throws IllegalStateException if the actual number of columns
	 * generated by executing the query doesn't match the number
	 * (<code>cols</code>) given to the constructor.
	 * @throws SQLException if there is a database error (most often
	 * due to a syntax error in the SQL query).
	 */
	protected void execute(Connection con, boolean closeOldRS)
		throws IllegalStateException, SQLException {
		this.con = con;

		ResultSet newRS=null;

		if(cacheEnabled) {
			System.out.print
				("DelayedResultSet.execute: Finding results in cache of "
				 + resultCache.size()
				 + " items: ");
				
			String cacheKey = sqlQuery 
				+"&"+con.getMetaData().getURL() 
				+"&"+con.getMetaData().getUserName();
			
			CachedRowSet results = (CachedRowSet) resultCache.get(cacheKey);
			if (results != null) {
				results = (CachedRowSet) results.createShared();
				
				// reset cursor, which is likely afterLast right now
				results.beforeFirst();
				
				System.out.println("HIT ("+results.size()+" rows)");
				// we don't want to close cached resultset
				closeOldRS=false;
			} else {
				Statement stmt = null;
				try {
					stmt = con.createStatement();
					results = new CachedRowSet();
					ResultSet rs = stmt.executeQuery(sqlQuery);
					results.populate(rs);
				} finally {
					if (stmt != null) {
						stmt.close();
					}
				}
				resultCache.put(cacheKey, results);
				System.out.println("MISS ("+results.size()+" rows)");
			}
			newRS=results;
		} else {
			// not using cache
			Statement stmt = con.createStatement();
			newRS = stmt.executeQuery(sqlQuery);
		}
		applyResultSet(newRS, closeOldRS);

		columnCountSanityCheck();
	}

	/**
	 * The execute method calls this just before returning to make
	 * sure everything adds up (and the user didn't specify an
	 * incorrect column count).
	 *
	 * @throws IllegalStateException if the given column count differs
	 * from the actual column count generated by the SQL query.
	 */
	protected void columnCountSanityCheck() throws SQLException, IllegalStateException {
		if (rsmd.getColumnCount() != givenColCount) {
			throw new IllegalStateException(
				"The SQL query returned "
					+ rsmd.getColumnCount()
					+ " columns, but the number of columns originally specified was "
					+ givenColCount
					+ ".");
		}
	}

	/**
	 * Just gives back the column count specified in the constructor.
	 *
	 * @return The number of columns that this DelayedWebResultSet has.
	 */
	public int getColumnCount() {
		return givenColCount;
	}

	/**
	 * Returns true if this DelayedWebResultSet is using the result
	 * set cache for query execution.
	 */
	public boolean isCacheEnabled() {
		return cacheEnabled;
	}

	/**
	 * Controls whether this DelayedWebResultSet is using the result
	 * set cache for query execution.  You can't change this value
	 * after calling {@link #execute(Connection)}.
	 *
	 * @param v <code>True</code> if you want the result set to use
	 * the cache; <code>false</code> if you want fresh data directly
	 * from the database.
	 * @throws IllegalStateException if called after <code>execute()</code>.
	 */
	public void setCacheEnabled(boolean v) {
		cacheEnabled=v;
	}

	/**
	 * Behaves like close() in WebResultSet unless the
	 * DelayedWebResultSet result cache is turned on.  In that case,
	 * does nothing because the database resources are already released.
	 */
	public void close() throws SQLException {
		if(!cacheEnabled) {
			super.close();
		}
	}

	public boolean isEmpty() throws SQLException {
		if(! (rs instanceof CachedRowSet) ) {
			throw new UnsupportedOperationException
				("Can't tell if result set is empty unless caching is enabled");
		} else {
			return ((CachedRowSet)rs).size() > 0 ? false : true;
		}
	}
}
