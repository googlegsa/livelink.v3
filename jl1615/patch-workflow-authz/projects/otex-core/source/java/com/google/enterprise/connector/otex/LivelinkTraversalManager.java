// Copyright (C) 2007-2008 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.enterprise.connector.otex;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Map;
import java.util.StringTokenizer;

import com.google.enterprise.connector.spi.Document;
import com.google.enterprise.connector.spi.DocumentList;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.SpiConstants;
import com.google.enterprise.connector.spi.TraversalManager;
import com.google.enterprise.connector.spi.TraversalContext;
import com.google.enterprise.connector.spi.TraversalContextAware;
import com.google.enterprise.connector.otex.client.Client;
import com.google.enterprise.connector.otex.client.ClientFactory;
import com.google.enterprise.connector.otex.client.ClientValue;

/**
 * This implementation of <code>TraversalManager</code> requires
 * the <code>LAPI_DOCUMENTS.ListNodes</code> method, which
 * is an undocumented LAPI method.
 *
 * The SQL queries used here are designed to work with SQL Server 7.0
 * or later, and with Oracle 8i Release 2 (8.1.6). This enables the
 * code to work with Livelink 9.0 or later. The exception to this is
 * that Sybase, which is supported by Livelink 9.2.0.1 and earlier, is
 * not supported here.
 */
class LivelinkTraversalManager
        implements TraversalManager, TraversalContextAware {

    /** The logger for this class. */
    private static final Logger LOGGER =
        Logger.getLogger(LivelinkTraversalManager.class.getName());

    /**
     * The primary store for property names that we want to map from
     * the database to the Document. This is an array of
     * <code>Field</code> objects that include the record array field
     * name, the record array field type, and the Google and Livelink
     * property names. If no property names are provided, then the
     * field will not be returned in the property map.
     *
     * Given package access so that LivelinkConnector.setIncludedObjectInfo()
     * my filter out duplicates.
     */
    protected static final Field[] FIELDS;

    /**
     * The columns needed in the database query, which are obtained
     * from the field names in <code>FIELDS</code>.
     */
    private static final String[] SELECT_LIST;

    static {
        // ListNodes requires the DataID and PermID columns to be
        // included here. This implementation requires DataID,
        // ModifyDate, MimeType, Name, SubType, OwnerID, and DataSize.
        ArrayList list = new ArrayList();

        list.add(new Field("DataID", "ID", SpiConstants.PROPNAME_DOCID));
        list.add(new Field("ModifyDate", "ModifyDate",
                           SpiConstants.PROPNAME_LASTMODIFIED));
        list.add(new Field("MimeType", "MimeType",
                           SpiConstants.PROPNAME_MIMETYPE));

        list.add(new Field("DComment", "Comment"));
        list.add(new Field("CreateDate", "CreateDate"));
        list.add(new Field("OwnerName", "CreatedBy"));
        list.add(new Field("Name", "Name"));
        list.add(new Field("SubType", "SubType"));
        list.add(new Field("OwnerID", "VolumeID"));
        list.add(new Field("UserID", "UserID"));

        list.add(new Field("DataSize"));
        list.add(new Field("PermID"));

        FIELDS = (Field[]) list.toArray(new Field[0]);

        SELECT_LIST = new String[FIELDS.length];
        for (int i = 0; i < FIELDS.length; i++)
            SELECT_LIST[i] = FIELDS[i].fieldName;
    }

    /**
     * Constructs a checkpoint string from the given date and object ID.
     *
     * @param modifyDate the date for the checkpoint
     * @param objectId the object ID for the checkpoint
     * @return a checkpoint string of the form "yyyy-mm-dd hh:mm:ss,n",
     *     where n is the object ID
     */
    static String getCheckpoint(Date modifyDate, int objectId) {
        return LivelinkDateFormat.getInstance().toSqlString(modifyDate) +
            ',' + objectId;
    }

    
    /** The connector contains configuration information. */
    private final LivelinkConnector connector;

    /** The client provides access to the server. */
    private final Client client;

    /**
     * The admin client provides access to the server for the
     * candidates query. If we use the traversal user, there's a bad
     * interaction between the SQL query limits (e.g., TOP 100, ROWNUM
     * &lt;= 100) and Livelink's user permissions checks. If the
     * traversal user doesn't have permission to see an entire batch,
     * we have no candidates, and we don't even have a checkpoint at
     * the end that we could skip past. So when there is a traversal
     * user, we get the candidates as the system administrator.
     */
    private final Client sysadminClient;

    /**
     * The current user, either the system administrator or an
     * impersonated traversal user.
     */
    private final String currentUsername;

    /** The database type, either SQL Server or Oracle. */
    /* XXX: We could use the state or strategy pattern if this gets messy. */
    private final boolean isSqlServer;

    /** A starting checkpoint, for efficiency, or <code>null</code. */
    private final String startCheckpoint;
       
    /** A concrete strategy for retrieving the content from the server. */
    private final ContentHandler contentHandler;

    /** The number of results to return in each batch. */
    private volatile int batchSize = 100;

    /** The TraversalContext from TraversalContextAware Interface */
    private TraversalContext traversalContext = null;

    LivelinkTraversalManager(LivelinkConnector connector,
            ClientFactory clientFactory) throws RepositoryException {
        this.connector = connector;
        this.client = clientFactory.createClient();

        // Get the current username to compare to the configured
        // traversalUsername and publicContentUsername.
        String username = null;
        try {
            int id = client.GetCurrentUserID();
            ClientValue userInfo = client.GetUserOrGroupByIDNoThrow(id);
            if (userInfo != null)
                username = userInfo.toString("Name");
        } catch (LivelinkException e) {
            // Ignore exceptions, which is conservative. Worst case is
            // that we will impersonate the already logged in user
            // and gratuitously check the permissions on all content.
        }

        // If there is a separately specified traversal user (different
        // than our current user), then impersonate that traversal user
        // when building the list of documents to index.
        String traversalUsername = connector.getTraversalUsername();
        if (traversalUsername != null && !traversalUsername.equals(username)) {
            client.ImpersonateUserEx(traversalUsername,
                connector.getDomainName());
            this.sysadminClient  = clientFactory.createClient();
            this.currentUsername = traversalUsername;
        } else {
            this.sysadminClient = client;
            this.currentUsername = username;
        }

        this.isSqlServer = isSqlServer();
        this.startCheckpoint = getStartCheckpoint();
        this.contentHandler = getContentHandler();
    }


    /**
     * Determines whether the database type is SQL Server or Oracle.
     *
     * @return <code>true</code> for SQL Server, or <code>false</code>
     * for Oracle.
     */
    /*
     * We need to use the sysadminClient because this method uses
     * <code>ListNodes</code> but does not select either DataID or
     * PermID, or even use the DTree table at all.
     */
    private boolean isSqlServer() throws RepositoryException {
        String servtype = connector.getServtype();
        if (servtype == null) {
            // Autodetection of the database type. First, ferret out
            // generic errors when connecting or using ListNodes.
            String query = "1=1"; // ListNodes requires a WHERE clause.
            String[] columns = { "42" };
            ClientValue results =
                sysadminClient.ListNodes(query, "KDual", columns);

            // Then check an Oracle-specific query.
            // We use ListNodesNoThrow() to avoid logging our expected error.
            results = sysadminClient.ListNodesNoThrow(query, "dual", columns);
            boolean isOracle = (results != null);

            if (LOGGER.isLoggable(Level.CONFIG)) {
                LOGGER.config("AUTO DETECT SERVTYPE: " +
                    (isOracle ? "Oracle" : "MSSQL"));
            }
            return !isOracle;
        } else {
            // This is basically startsWithIgnoreCase.
            boolean matches = servtype.regionMatches(true, 0, "MSSQL", 0, 5);
            if (LOGGER.isLoggable(Level.CONFIG)) {
                LOGGER.config("CONFIGURED SERVTYPE: " +
                    (matches ? "MSSQL" : "Oracle"));
            }
            return matches;
        }
    }


    /**
     * Gets the startDate checkpoint.  We attempt to forge an initial
     * checkpoint based upon information gleaned from any startDate or
     * includedLocationNodes specified in the configuration.
     *
     * @return the checkpoint string, or null if indexing the entire DB.
     */
    /*
     * We need to use the sysadminClient because this method uses
     * <code>ListNodes</code> but does not and cannot select either
     * DataID or PermID.
     */
    private String getStartCheckpoint() {
        // If we have a specified startDate, use it to seed 
        // our minimum modification timestamp.
        Date startDate = connector.getStartDate();

        // If the user specified "Items to index", fetch the earliest
        // modification time for any of those items.  We can forge 
        // a start checkpoint that skips over any ancient history in
        // the LL database.  This executes a SQL query of the form:
        //   select min(ModifyDate) from DTreeAncestors join DTree
        //   on DTreeAncestors.DataID = DTree.DataID
        //   where AncestorID in (items to index)
        //   or DataID in (items to index)
        // [Note the actual query is slightly more cryptic to avoid a
        // SQL error caused by the range variable inserted into the
        // query by ListNodes, and by range variables introduced here
        // to make it easier to test the query in SQL Server
        // Enterprise Manager or Query Analyzer.]
        String startNodes = connector.getIncludedLocationNodes();
        if (startNodes != null && startNodes.length() > 0) {
            String ancestorNodes = getAncestorNodes(startNodes);
            try {
                String query = "1=1";
                String[] columns = { "minModifyDate" };
                String view = "(select min(ModifyDate) as minModifyDate from " +
                    "DTreeAncestors Anc join DTree T on Anc.DataID = " +
                    "T.DataID where AncestorID in (" + ancestorNodes + ") " +
                    "or T.DataID in (" + startNodes + "))";
                if (LOGGER.isLoggable(Level.FINEST))
                    LOGGER.finest("START CHECKPOINT VIEW: " + view);

                ClientValue results =
                    sysadminClient.ListNodes(query, view, columns);
                if (results.size() > 0) {
                    Date minDate = results.toDate(0, "minModifyDate");
                    if (LOGGER.isLoggable(Level.FINEST)) {
                        LOGGER.finest("START CHECKPOINT COMPARING: " +
                            startDate + " TO: " + minDate);
                    }
                    if (startDate == null || minDate.after(startDate))
                        startDate = minDate;
                } else {
                    if (LOGGER.isLoggable(Level.FINER))
                        LOGGER.finer("START CHECKPOINT EMPTY JOIN");
                }
            } catch (Exception e) {
                // Possible non-date comes back if startNodes yields nothing.
                if (LOGGER.isLoggable(Level.FINER))
                    LOGGER.log(Level.FINER, "START CHECKPOINT ERROR: " +
                        startDate, e);
            }
        }

        // If we actually found an earliest starting point, forge a
        // checkpoint that reflects it. Otherwise, start at first
        // object in the Livelink database.
        return (startDate != null) ? getCheckpoint(startDate, 0) : null;
    }

    /**
     * Builds a SQL conditional expression that includes all
     * the starting nodes and all descendants of the starting
     * nodes.
     *
     * The list of traversal starting locations is derived
     * either from an explict list of start nodes
     * (<em>includedLocationNodes</em>) or an implicit
     * list of all non-excluded Volumes.
     *
     * If explicit starting nodes are not provided, then we build a list 
     * of implicit starting points based upon all available volume roots,
     * less those that are explicitly excluded (excludedVolumeTypes).
     *
     * This will then return a SQL expression of the general form:
     *
     * <pre>
     *     (DataID in (<em>includedLocationNodes</em>) or
     *      DataID in
     *         (select DataID from DTreeAncestors where AncestorID in
     *             <em>includedLocationNodes</em>))
     * </pre>
     * 
     * @return the SQL conditional expression
     */
    String getIncluded(String candidatesPredicate) {

        // If we have an explict list of start locations, build a
        // query that includes only those and their descendants.
        String startNodes = connector.getIncludedLocationNodes();
        String ancestorNodes;
        if (startNodes != null && startNodes.length() > 0) {
            ancestorNodes = getAncestorNodes(startNodes);
        } else {
            // If we don't have an explicit list of start points,
            // build an implicit list from the list of all volumes,
            // minus those that are explicitly excluded.
            startNodes = getStartingVolumes(candidatesPredicate);
            ancestorNodes = startNodes;
        }

        StringBuffer buffer = new StringBuffer();
        buffer.append("(DataID in (");
        buffer.append(startNodes);
        buffer.append(") or DataID in (select DataID from ");
        buffer.append("DTreeAncestors where ");
        buffer.append(candidatesPredicate);
        buffer.append(" and AncestorID in (");
        buffer.append(ancestorNodes);
        buffer.append(")))");
        String included = buffer.toString();

        if (LOGGER.isLoggable(Level.FINER))
            LOGGER.finer("INCLUDED: " + included);
        return included;
    }

    private String getAncestorNodes(String startNodes) {
        // Projects, Discussions, Channels, and TaskLists have a rather
        // strange behaviour.  Their contents have a VolumeID that is the
        // same as the container's ObjectID, and an AncestorID that is the
        // negation the container's ObjectID.  To catch that, I am going
        // to create a superset list that adds the negation of everything
        // in the specified the specified list.  We believe this is safe,
        // as negative values of standard containers (folders, compound 
        // docs, etc) simply should not exist, so we shouldn't get any 
        // false positives.
        StringBuffer buffer = new StringBuffer();
        StringTokenizer tok =
            new StringTokenizer(startNodes, ":;,. \t\n()[]\"\'");
        while (tok.hasMoreTokens()) {
            String objId = tok.nextToken();
            if (buffer.length() > 0)
                buffer.append(',');
            buffer.append(objId);
            try {
                int intId = Integer.parseInt(objId);
                buffer.append(',');
                buffer.append(-intId);
            } catch (NumberFormatException e) {}
        }
        return buffer.toString();
    }

    /**
     * Returns a SQL subquery that should expand to the lists of 
     * volumes to traverse.  These are all root-level 
     * volumes that are not of an excluded volume type. The basic form
     * of the subquery is
     *
     * <pre>
     *          select DataID from DTree where DataID in
     *             (select AncestorID from DTreeAncestors
     *              group by DataID having count(*) = 1) and not
     *             SubType in (<em>excludedVolumeTypes</em>)
     * </pre>
     *
     * @return SQL sub-expression
     */
    public String getStartingVolumes(String candidatesPredicate) {
        StringBuffer buffer = new StringBuffer();

        buffer.append("select DataID from DTree where DataID in ");
        buffer.append("(select DataID from DTreeAncestors where ");
        buffer.append(candidatesPredicate);
        buffer.append(
                "or DataID in (select AncestorID from DTreeAncestors where ");
        buffer.append(candidatesPredicate);
        buffer.append(") group by DataID having count(*) = 1)");
  
        String excludedVolumes = connector.getExcludedVolumeTypes();
        if (excludedVolumes != null && excludedVolumes.length() > 0) {
            buffer.append(" and (SubType not in (");
            buffer.append(excludedVolumes);
            buffer.append("))");
        }

        return buffer.toString();
    }

    /**
     * Gets a SQL conditional expression that excludes nodes that
     * should not be traversed. This returns a SQL expression of the
     * form
     *
     * <pre>
     *     SubType not in (<em>excludedNodeTypes</em>) and
     *     DataID not in
     *         (select DataID from DTreeAncestors where AncestorID in
     *                 (<em>excludedLocationNodes</em>))
     * </pre>
     *
     * The returned expression is simplified in the obvious way when
     * one or more of the configuration parameters is null or empty.
     *
     * @return the SQL conditional expression
     */
    /* This method has package access so that it can be unit tested. */
    String getExcluded(String candidatesPredicate)  {
        StringBuffer buffer = new StringBuffer();

        String excludedNodeTypes = connector.getExcludedNodeTypes();
        if (excludedNodeTypes != null &&
                excludedNodeTypes.length() > 0) {
            buffer.append("SubType not in (");
            buffer.append(excludedNodeTypes);
            buffer.append(')');
        }

        String excludedLocationNodes = connector.getExcludedLocationNodes();
        if (excludedLocationNodes != null &&
                excludedLocationNodes.length() > 0) {
            if (buffer.length() > 0)
                buffer.append(" and ");

            buffer.append("DataID not in (select DataID from ");
            buffer.append("DTreeAncestors where ");
            buffer.append(candidatesPredicate);
            buffer.append(" and AncestorID in (");
            buffer.append(excludedLocationNodes);
            buffer.append("))");
        }

        // TODO: This doesn't handle the subtypes yet. If
        // showHiddenItems is a list of subtypes, then hidden items
        // will just not be indexed.
        HashSet showHiddenItems = connector.getShowHiddenItems();
        if (!showHiddenItems.contains("all"))
        {
            // I'm using Anc here rather than simply A to avoid
            // conceptually interferring with the A range variable
            // that LAPI adds behind the scenes.
            // XXX: We need to qualify the reference to DataID in the
            // candidatesPredicate with Anc. We "know" that DataID
            // appears at the beginning of candidatesPredicate, so we
            // just leave a hanging "Anc." just before that. See
            // listNodes, where the predicate is created. Sigh.
            String hidden = String.valueOf(Client.DISPLAYTYPE_HIDDEN);
            if (buffer.length() > 0)
                buffer.append(" and ");
            buffer.append("Catalog <> ");
            buffer.append(hidden);
            buffer.append(" and DataID not in (select Anc.DataID ");
            buffer.append("from DTreeAncestors Anc join DTree T ");
            buffer.append("on Anc.AncestorID = T.DataID where Anc.");
            buffer.append(candidatesPredicate);
            buffer.append(" and T.Catalog = ");
            buffer.append(hidden);

            // TODO: This is rework from getIncluded, but I didn't
            // want to merge the two methods on the fly because it
            // would break the tests.
            String startNodes = connector.getIncludedLocationNodes();
            if (startNodes != null && startNodes.length() > 0) {
                String ancestorNodes = getAncestorNodes(startNodes);
                buffer.append(" and Anc.AncestorID not in (");
                buffer.append(startNodes);
                buffer.append(") and Anc.AncestorID not in ");
                buffer.append("(select AncestorID from DTreeAncestors ");
                buffer.append("where DataID in (");
                buffer.append(ancestorNodes);
                buffer.append("))");
            }

            buffer.append(')');
        }

        String excluded = (buffer.length() > 0) ? buffer.toString() : null;
        if (LOGGER.isLoggable(Level.FINER))
            LOGGER.finer("EXCLUDED: " + excluded);
        return excluded;
    }


    /**
     * Gets a new instance of the configured content handler class.
     *
     * @return a new instance of the configured content handler class
     * @throws RepositoryException if the class cannot be instantiated
     * or initialized
     */
    private ContentHandler getContentHandler() throws RepositoryException {
        ContentHandler contentHandler;
        String contentHandlerClass = connector.getContentHandler();
        try {
            contentHandler = (ContentHandler)
                Class.forName(contentHandlerClass).newInstance();
        } catch (Exception e) {
            throw new LivelinkException(e, LOGGER);
        }
        contentHandler.initialize(connector, client);
        return contentHandler;
    }


    /**
     * Sets the batch size. This implementation limits the actual
     * batch size to 1000, due to SQL syntax limits in Oracle.
     *
     * @param hint the new batch size
     * @throws IllegalArgumentException if the hint is less than zero
     */
    public void setBatchHint(int hint) {
        if (hint < 0)
            throw new IllegalArgumentException();
        else if (hint == 0)
            batchSize = 100; // We could ignore it, but we reset the default.
        else if (hint > 1000)
            batchSize = 1000;
        else
            batchSize = hint;
    }


    /** {@inheritDoc} */
    public DocumentList startTraversal() throws RepositoryException {
        // startCheckpoint will either be an initial checkpoint or null
        if (LOGGER.isLoggable(Level.INFO)) {
            LOGGER.info("START TRAVERSAL: " + batchSize + " rows" + 
                (startCheckpoint == null ? "" : " from " + startCheckpoint) +
                ".");
        }
        return listNodes(startCheckpoint);
    }


    /** {@inheritDoc} */
    public DocumentList resumeTraversal(String checkpoint)
            throws RepositoryException {
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("RESUME TRAVERSAL: " + batchSize + " rows from " +
                checkpoint + ".");
        }
        
        // Ping the Livelink Server.  If I can't talk to the server,
        // I will consider this a transient Exception (server down,
        // network error, etc).  In that case, return null, signalling
        // no new documents available at this time.
        try {
            client.GetCurrentUserID(); 	// ping()
        } catch (RepositoryException e) {
            try { Thread.sleep(5000); } catch (Exception ex) {};
            return null;
        }
        
        return listNodes(checkpoint);
    }

    /** {@inheritDoc} */
    public void setTraversalContext(TraversalContext traversalContext) {
        this.traversalContext = traversalContext;
    }

    /**
     * This method uses <code>LAPI_DOCUMENTS.ListNodes</code>, which
     * is an undocumented LAPI method. This method essentially
     * executes a query of the form
     *
     * <pre>
     *     SELECT <em>columns</em> FROM <em>view</em> WHERE <em>query</em>
     * </pre>
     *
     * where <em>columns</em>, <em>view</em>, and <em>query</em> are
     * three of the parameters to <code>ListNodes</code>.
     *
     * <p>
     * We want to execute queries of the following form
     *
     * <pre>
     *     SELECT <em>columns</em>
     *     FROM WebNodes 
     *     WHERE <em>after_last_checkpoint</em>
     *         AND <em>included_minus_excluded</em>
     *     ORDER BY ModifyDate, DataID
     * </pre>
     *
     * and only read the first <code>batchSize</code> rows. The ORDER
     * BY clause is passed to the <code>ListNodes</code> method as
     * part of the WHERE clause. The <em>after_last_checkpoint</em>
     * condition is empty for <code>startTraversal</code>, or
     *
     * <pre>
     *     ModifyDate > <em>X</em> or
     *         (ModifyDate = <em>X</em> and DataID > <em>Y</em>)
     * </pre>
     *
     * for <code>resumeTraversal</code> where <em>X</em> is the
     * ModifyDate of the checkpoint and <em>Y</em> is the DataID of
     * the checkpoint. The <em>included_minus_excluded</em> condition
     * accounts for the configured included and excluded volume types,
     * subtypes, and nodes.
     *
     * <p>
     * The details are little messy because there is not a single
     * syntax supported by both Oracle and SQL Server for limiting the
     * number of rows returned, the included and excluded logic is
     * complicated, and the obvious queries perform badly, especially
     * on Oracle.
     * 
     * For Oracle, we use ROWNUM, and for SQL
     * Server, we use TOP, which requires SQL Server 7.0. The
     * <code>ROW_NUMBER()</code> function is supported by Oracle and
     * also by SQL Server 2005, but that's too limiting, and it's
     * likely to be much slower than TOP or ROWNUM. The standard SQL
     * <code>SET ROWCOUNT</em> statement is supported by SQL Server
     * 6.5 and by Sybase, but not by Oracle, and I don't know of a way
     * to execute it from LAPI. A final complication is that ROWNUM
     * limits the rows before the ORDER BY clause is applied, so a
     * subquery is needed to do the ordering before the limit is
     * applied.
     *
     * To address the performance issues, the query is broken into two
     * pieces. The first applies the row limits and
     * <em>after_last_checkpoint</em> condition to arrive at a list of
     * candidates. The second applies the inclusions and exclusions,
     * using the candidates to avoid subqueries that select a large
     * number of rows.
     * 
     * If the first query returns no candidates, there is nothing to
     * return. If the second query returns no results, we need to try
     * again with the next batch of candidates.
     *
     * @param checkpoint a checkpoint string, or <code>null</code> if
     * a new traversal should be started
     * @return a batch of results starting at the checkpoint, if there
     * is one, or the beginning of the traversal order, otherwise
     */
    private DocumentList listNodes(String checkpoint)
            throws RepositoryException {
        int batchsz = batchSize;
        while (true) {
            ClientValue candidates;
            if (isSqlServer)
                candidates = getCandidatesSqlServer(checkpoint, batchsz);
            else
                candidates = getCandidatesOracle(checkpoint, batchsz);
            if (candidates.size() == 0) {
                LOGGER.fine("RESULTSET: no rows.");
                return null;
            } else 
                LOGGER.fine("CANDIDATES SET: " + candidates.size() + " rows.");

            StringBuffer buffer = new StringBuffer();
            buffer.append("DataID in (");
            for (int i = 0; i < candidates.size(); i++) {
                buffer.append(candidates.toInteger(i, "DataID"));
                buffer.append(',');
            }
            buffer.setCharAt(buffer.length() - 1, ')');
            String candidatesPredicate = buffer.toString();

            ClientValue results = getResults(candidatesPredicate);
            if (results.size() == 0) {
                // Reset the checkpoint here to match the last
                // candidate, so that we will get the next batch of
                // candidates the next time through the loop.
                int row = candidates.size() - 1; // last row
                checkpoint = getCheckpoint(
                    candidates.toDate(row, "ModifyDate"),
                    candidates.toInteger(row, "DataID"));
                if (LOGGER.isLoggable(Level.FINER))
                    LOGGER.finer("SKIPPING PAST " + checkpoint);
                // If nothing is passing our filter, we probably have a
                // sparse database.  Grab larger candidate sets, hoping
                // to run into anything interesting.
                batchsz = Math.min(1000, batchsz * 10);
            } else {
                if (LOGGER.isLoggable(Level.FINE))
                    LOGGER.fine("RESULTSET: " + results.size() + " rows.");
                return new LivelinkDocumentList(connector, client,
                    contentHandler, results, FIELDS, traversalContext,
                    checkpoint, currentUsername);
            }
        }
    }


    /**
     * The sort order of the traversal. We need a complete ordering
     * based on the modification date, in order to get incremental
     * crawling without duplicates.
     *
     * @see #getRestriction
     */
    private static final String ORDER_BY = " order by ModifyDate, DataID";


    /**
     * Filters the candidates down and returns the main recarray needed
     * for the DocumentList.
     * 
     * @return the main query results
     * @throws RepositoryException
     */
    private ClientValue getResults(String candidatesPredicate)
            throws RepositoryException {
        String included = getIncluded(candidatesPredicate);
        String excluded = getExcluded(candidatesPredicate);
        StringBuffer buffer = new StringBuffer();
        buffer.append(candidatesPredicate);
        if (included != null) {
            buffer.append(" and ");
            buffer.append(included);
        }
        if (excluded != null) {
            buffer.append(" and ");
            buffer.append(excluded);
        }
        buffer.append(ORDER_BY);

        String query = buffer.toString();
        String view = "WebNodes";
        String[] columns = SELECT_LIST;
        if (LOGGER.isLoggable(Level.FINEST))
            LOGGER.finest("RESULTS QUERY: " + query);

        return client.ListNodes(query, view, columns);
    }


    /*
     * We need to use the sysadminClient to avoid getting no
     * candidates when the traversal user does not have permission for
     * any of the potential candidates.
     */
    private ClientValue getCandidatesSqlServer(String checkpoint, int batchsz)
            throws RepositoryException {
        StringBuffer buffer = new StringBuffer();
        if (checkpoint == null)
            buffer.append("1=1");
        else 
            buffer.append(getRestriction(checkpoint));
        buffer.append(ORDER_BY);

        String query = buffer.toString();
        String view = "DTree";
        String[] columns = {
            "top " + batchsz +  " ModifyDate", "DataID" };
        if (LOGGER.isLoggable(Level.FINEST))
            LOGGER.finest("CANDIDATES QUERY: " + query);

        return sysadminClient.ListNodes(query, view, columns);
    }


    /*
     * We could use FIRST_ROWS(<batchSize>), but that doesn't help
     * when there is a sort on the query. Simple tests show that it
     * would allow us to eliminate the outer query with ROWNUM and get
     * equal performance, except that it doesn't limit the number of
     * rows, and LAPI materializes the entire result set.
     * 
     * We need to use the sysadminClient to avoid getting no
     * candidates when the traversal user does not have permission for
     * any of the potential candidates.
     */
    private ClientValue getCandidatesOracle(String checkpoint, int batchsz)
            throws RepositoryException {
        StringBuffer buffer = new StringBuffer();
        buffer.append("(select ModifyDate, DataID from DTree");
        if (checkpoint != null) {
            buffer.append(" where ");
            buffer.append(getRestriction(checkpoint));
        }
        buffer.append(ORDER_BY);
        buffer.append(')');

        String query = "rownum <= " + batchsz;
        String view = buffer.toString();
        String[] columns = new String[] { "*" };
        if (LOGGER.isLoggable(Level.FINEST))
            LOGGER.finest("CANDIDATES VIEW: " + view);

        return sysadminClient.ListNodes(query, view, columns);
    }


    /**
     * Gets a SQL condition representing the given checkpoint. This
     * condition depends on the sort order of the traversal.
     *
     * @param checkpoint
     * @return a SQL condition returning items following the checkpoint
     * @see #ORDER_BY
     */
    /*
     * The TIMESTAMP literal, part of the SQL standard, was first
     * supported by Oracle 9i, and not at all by SQL Server. SQL
     * Server doesn't require a prefix on timestamp literals, and
     * we're using TO_DATE with Oracle in order to work with Oracle
     * 8i, and therefore with Livelink 9.0 or later.
     *
     * TODO: Validate the checkpoint. We have checkpoints coming from
     * the Connector Manager, from a configured starting date, or from
     * the earliest modification date of the included location nodes.
     */
    private String getRestriction(String checkpoint)
            throws RepositoryException {
        int index = checkpoint.indexOf(',');
        if (index == -1) {
            throw new LivelinkException("Invalid checkpoint " + checkpoint,
                LOGGER);
        } else {
            String modifyDate = checkpoint.substring(0, index);

            // In future, we may embellish the checkpoint with additional items.
            String dataId;
            int idEnd = checkpoint.indexOf(',', index + 1);
            if (idEnd > index)
                dataId = checkpoint.substring(index + 1, idEnd);
            else
                dataId = checkpoint.substring(index + 1);

            if (isSqlServer) {
                return "(ModifyDate > '" + modifyDate +
                    "' or (ModifyDate = '" + modifyDate +
                    "' and DataID > " + dataId + "))";
            } else {
                return "(ModifyDate > TO_DATE('" + modifyDate +
                    "', 'YYYY-MM-DD HH24:MI:SS') or (ModifyDate = TO_DATE('" +
                    modifyDate + "', 'YYYY-MM-DD HH24:MI:SS') and DataID > " +
                    dataId + "))";
            }
        }
    }
}
