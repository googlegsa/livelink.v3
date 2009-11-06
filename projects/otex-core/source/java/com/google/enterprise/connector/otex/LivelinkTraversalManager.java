// Copyright (C) 2007-2009 Google Inc.
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

import java.util.ArrayList;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.StringTokenizer;

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

  /**
   * True if this connector instance will track deleted items and
   * submit delete actions to the GSA.
   */
  private static boolean deleteSupported;

  static {
    // ListNodes requires the DataID and PermID columns to be
    // included here. This implementation requires DataID,
    // ModifyDate, MimeType, Name, SubType, OwnerID, and DataSize.
    ArrayList<Field> list = new ArrayList<Field>();

    list.add(new Field("DataID", "ID", SpiConstants.PROPNAME_DOCID));
    list.add(new Field("ModifyDate", "ModifyDate",
            SpiConstants.PROPNAME_LASTMODIFIED));
    list.add(new Field("MimeType", "MimeType",
            SpiConstants.PROPNAME_MIMETYPE));
    list.add(new Field("Name", "Name", SpiConstants.PROPNAME_TITLE));

    list.add(new Field("DComment", "Comment"));
    list.add(new Field("CreateDate", "CreateDate"));
    list.add(new Field("OwnerName", "CreatedBy"));
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

  /** Select list column for AuditDate; Oracle still lacks milliseconds. */
  String AUDIT_DATE_ORACLE =
      "TO_CHAR(AuditDate, 'YYYY-MM-DD HH24:MI:SS') as AuditDate";

  /** Select list column for AuditDate with milliseconds for SQL Server. */
  String AUDIT_DATE_SQL_SERVER =
      "CONVERT(VARCHAR(23), AuditDate, 121) as AuditDate";

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

  /** A concrete strategy for retrieving the content from the server. */
  private final ContentHandler contentHandler;

  /** The number of results to return in each batch. */
  private volatile int batchSize = 100;

  /** Date formatter used to construct checkpoint dates */
  private final LivelinkDateFormat dateFormat =
      LivelinkDateFormat.getInstance();

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
      client.ImpersonateUserEx(traversalUsername, connector.getDomainName());
      this.sysadminClient  = clientFactory.createClient();
      this.currentUsername = traversalUsername;
    } else {
      this.sysadminClient = client;
      this.currentUsername = username;
    }

    this.isSqlServer = connector.isSqlServer();
    this.contentHandler = getContentHandler();

    // Check to see if we will track Deleted Documents.
    deleteSupported = connector.getTrackDeletedItems();
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
    // Checkpoint we are forging.
    Checkpoint checkpoint = new Checkpoint();

    // If we have an earliest starting point, forge a checkpoint
    // that reflects it. Otherwise, start at first object in the
    // Livelink database.
    Date startDate = connector.getStartDate();
    if (startDate != null)
      checkpoint.setInsertCheckpoint(startDate, 0);

    // We don't care about any existing Delete events in the audit
    // logs, since we're just starting the traversal.
    forgeInitialDeleteCheckpoint(checkpoint);

    String startCheckpoint = checkpoint.toString();
    if (LOGGER.isLoggable(Level.FINE))
      LOGGER.fine("START CHECKPOINT: " + startCheckpoint);
    return startCheckpoint;
  }

  /**
   *  Forge a delete checkpoint from the last event in the audit log.
   */
  private void forgeInitialDeleteCheckpoint(Checkpoint checkpoint) {
    try {
      String auditDate;
      if (isSqlServer)
        auditDate = AUDIT_DATE_SQL_SERVER;
      else
        auditDate = AUDIT_DATE_ORACLE;

      String query = "EventID in (select max(EventID) from DAuditNew)";
      String[] columns = { auditDate, "EventID" };
      String view = "DAuditNew";
      ClientValue results =
          sysadminClient.ListNodes(query, view, columns);
      if (results.size() > 0) {
        checkpoint.setDeleteCheckpoint(
            dateFormat.parse(results.toString(0, "AuditDate")),
            results.toValue(0, "EventID"));
      } else {
        checkpoint.setDeleteCheckpoint(new Date(), null);
      }
    } catch (Exception e) {
      LOGGER.warning( "Error establishing initial Deleted Items " +
          "Checkpoint: " + e.getMessage());
      try {
        checkpoint.setDeleteCheckpoint(new Date(), null);
      } catch (RepositoryException ignored) {
        // Shouldn't get here with null Value parameter.
        throw new java.lang.AssertionError();
      }
    }
  }

  /**
   * Builds a SQL conditional expression that includes all
   * the starting nodes and all descendants of the starting
   * nodes.
   *
   * The list of traversal starting locations is derived
   * either from an explicit list of start nodes
   * (<em>includedLocationNodes</em>) or an implicit
   * list of all non-excluded Volumes.
   *
   * If explicit starting nodes are not provided, we return a SQL
   * expression of the following form:
   *
   * <pre>
   *     (DataID in (<em>includedLocationNodes</em>) or
   *      DataID in
   *         (select DataID from DTreeAncestors where AncestorID in
   *             (<em>includedLocationNodes</em>)))
   * </pre>
   *
   * Otherwise, we just avoid items in the excluded volume types
   * (but not items in otherwise included subvolumes), and return
   * a SQL expression of the following form:
   *
   * <pre>
   *     -OwnerID not in (select DataID from DTree where SubType in
   *         (<em>excludedVolumeTypes</em>))
   * </pre>
   *
   * @return the SQL conditional expression
   */
  String getIncluded(String candidatesPredicate) {
    StringBuilder buffer = new StringBuilder();

    String startNodes = connector.getIncludedLocationNodes();
    if (startNodes != null && startNodes.length() > 0) {
      // If we have an explict list of start locations, build a
      // query that includes only those and their descendants.
      String ancestorNodes = getAncestorNodes(startNodes);
      buffer.append("(DataID in (");
      buffer.append(startNodes);
      buffer.append(") or DataID in (select DataID from ");
      buffer.append("DTreeAncestors where ");
      buffer.append(candidatesPredicate);
      buffer.append(" and AncestorID in (");
      buffer.append(ancestorNodes);
      buffer.append(")))");
    } else {
      String excludedVolumes = connector.getExcludedVolumeTypes();
      if (excludedVolumes != null && excludedVolumes.length() > 0) {
        // If we don't have an explicit list of start points,
        // just avoid the excluded volume types.
        buffer.append("-OwnerID not in (select DataID from DTree ");
        buffer.append("where SubType in (");
        buffer.append(excludedVolumes);
        buffer.append("))");
      }
    }

    String included = buffer.toString();
    if (LOGGER.isLoggable(Level.FINER))
      LOGGER.finer("INCLUDED: " + included);
    return included;
  }

  static String getAncestorNodes(String startNodes) {
    // Projects, Discussions, Channels, and TaskLists have a rather
    // strange behavior.  Their contents have a VolumeID that is the
    // same as the container's ObjectID, and an AncestorID that is the
    // negation the container's ObjectID.  To catch that, I am going
    // to create a superset list that adds the negation of everything
    // in the specified the specified list.  We believe this is safe,
    // as negative values of standard containers (folders, compound
    // docs, etc) simply should not exist, so we shouldn't get any
    // false positives.
    StringBuilder buffer = new StringBuilder();
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
   * FIXME: This SQL expression doesn't account for hidden items.
   *
   * The returned expression is simplified in the obvious way when
   * one or more of the configuration parameters is null or empty.
   *
   * @return the SQL conditional expression
   */
  /* This method has package access so that it can be unit tested. */
  String getExcluded(String candidatesPredicate)  {
    StringBuilder buffer = new StringBuilder();

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
    String startCheckpoint = getStartCheckpoint();
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
    // Resume with no checkpoint is the same as Start.
    if (checkpoint == null || checkpoint.length() == 0)
      return startTraversal();

    if (LOGGER.isLoggable(Level.FINE))
      LOGGER.fine("RESUME TRAVERSAL: " + batchSize + " rows from " +
          checkpoint + ".");

    // Ping the Livelink Server.  If I can't talk to the server,
    // I will consider this a transient Exception (server down,
    // network error, etc).  In that case, return null, signalling
    // no new documents available at this time.
    try {
      client.GetCurrentUserID();  // ping()
    } catch (RepositoryException e) {
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
   * number of rows. The second also implicitly applies permissions.
   *
   * If the first query returns no candidates, there is nothing to
   * return. If the second query returns no results, we need to try
   * again with the next batch of candidates.
   *
   * @param checkpointStr a checkpoint string, or <code>null</code>
   * if a new traversal should be started
   * @return a batch of results starting at the checkpoint, if there
   * is one, or the beginning of the traversal order, otherwise
   */
  private DocumentList listNodes(String checkpointStr)
      throws RepositoryException {
    Checkpoint checkpoint = new Checkpoint(checkpointStr);
    int batchsz = batchSize;

    // If we have an old style checkpoint, or one that is missing a
    // delete stamp, and we are doing deletes, forge a delete checkpoint.
    if (deleteSupported && checkpoint.deleteDate == null) {
      forgeInitialDeleteCheckpoint(checkpoint);
    }

    // If our available content appears to be sparsely distributed
    // across the repository, we want to give ourself a chance to
    // accelerate through the sparse regions, grabbing larger sets
    // of candidates looking for something applicable.  However,
    // we cannot do this indefinitely or we will run afoul of the
    // Connector Manager's thread timeout.
    long startTime = System.currentTimeMillis();
    long maxTimeSlice = 1000L * ((traversalContext != null) ?
        traversalContext.traversalTimeLimitSeconds() / 2 : 60 * 4);

    while (System.currentTimeMillis() - startTime < maxTimeSlice) {

      ClientValue candidates, deletes, results = null;
      if (isSqlServer) {
        candidates = getCandidatesSqlServer(checkpoint, batchsz);
        deletes = getDeletesSqlServer(checkpoint, batchsz);
      } else {
        candidates = getCandidatesOracle(checkpoint, batchsz);
        deletes = getDeletesOracle(checkpoint, batchsz);
      }

      int numInserts = (candidates == null) ? 0 : candidates.size();
      int numDeletes = (deletes == null) ? 0 : deletes.size();

      if ((numInserts + numDeletes) == 0) {
        if (checkpoint.hasChanged()) {
          break;      // Force a new checkpoint.
        } else {
          LOGGER.fine("RESULTSET: no rows.");
          return null;  // No new documents available.
        }
      }

      // Apply the inclusion, exclusions, and permissions to the
      // candidates.
      if (numInserts > 0) {
        if (LOGGER.isLoggable(Level.FINE))
          LOGGER.fine("CANDIDATES SET: " + numInserts + " rows.");

        // Remember the last insert candidate, so we may advance
        // passed all the candidates for the next batch.
        checkpoint.setAdvanceCheckpoint(
            candidates.toDate(numInserts - 1, "ModifyDate"),
            candidates.toInteger(numInserts - 1, "DataID"));

        StringBuilder buffer = new StringBuilder();
        buffer.append("DataID in (");
        for (int i = 0; i < numInserts; i++) {
          buffer.append(candidates.toInteger(i, "DataID"));
          buffer.append(',');
        }
        buffer.setCharAt(buffer.length() - 1, ')');
        results = getResults(buffer.toString());
        numInserts = (results == null) ? 0 : results.size();
      }

      if ((numInserts + numDeletes) > 0) {
        if (LOGGER.isLoggable(Level.FINE)) {
          LOGGER.fine("RESULTSET: " + numInserts + " rows.  " +
              "DELETESET: " + numDeletes + " rows.");
        }
        return new LivelinkDocumentList(connector, client,
            contentHandler, results, FIELDS, deletes,
            traversalContext, checkpoint, currentUsername);
      }

      // If nothing is passing our filter, we probably have a
      // sparse database.  Grab larger candidate sets, hoping
      // to run into anything interesting.
      batchsz = Math.min(1000, batchsz * 10);

      // Advance the checkpoint to the end of this batch of
      // candidates and grab the next batch.
      checkpoint.advanceToEnd();
      if (LOGGER.isLoggable(Level.FINER))
        LOGGER.finer("SKIPPING PAST " + checkpoint.toString());
    }

    // We searched for awhile, but did not find any candidates that
    // passed the restrictions.  However, there are still more candidates
    // to consider.  Indicate to the Connector Manager that this batch
    // has no documents, but to reschedule us immediately to keep looking.
    LOGGER.fine("RESULTSET: 0 rows, so far.");
    return new LivelinkDocumentList(checkpoint);
  }

  /**
   * The sort order of the traversal. We need a complete ordering
   * based on the modification date, in order to get incremental
   * crawling without duplicates.
   */
  private static final String ORDER_BY = " order by ModifyDate, DataID";

  /**
   * The sort order of the deleted documents traversal. We need a
   * complete ordering based on the event date, in order to get
   * incremental crawling without duplicates.
   */
  private static final String DELETE_ORDER_BY =
      " order by AuditDate, EventID";

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
    StringBuilder buffer = new StringBuilder();
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
  private ClientValue getCandidatesSqlServer(Checkpoint checkpoint,
      int batchsz) throws RepositoryException {
    StringBuilder buffer = new StringBuilder();
    if ((checkpoint == null) || (checkpoint.insertDate == null))
      buffer.append("1=1");
    else {
      String modifyDate = dateFormat.toSqlString(checkpoint.insertDate);
      buffer.append("(ModifyDate > '");
      buffer.append(modifyDate);
      buffer.append("' or (ModifyDate = '");
      buffer.append(modifyDate);
      buffer.append("' and DataID > ");
      buffer.append(checkpoint.insertDataId);
      buffer.append("))");
    }
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
  private ClientValue getCandidatesOracle(Checkpoint checkpoint, int batchsz)
      throws RepositoryException {
    StringBuilder buffer = new StringBuilder();
    if ((checkpoint != null) && (checkpoint.insertDate != null)) {
      /* The TIMESTAMP literal, part of the SQL standard, was first
       * supported by Oracle 9i, and not at all by SQL Server. SQL
       * Server doesn't require a prefix on timestamp literals, and
       * we're using TO_DATE with Oracle in order to work with Oracle
       * 8i, and therefore with Livelink 9.0 or later.
       */
      String modifyDate = dateFormat.toSqlString(checkpoint.insertDate);
      buffer.append("(ModifyDate > TO_DATE('");
      buffer.append(modifyDate);
      buffer.append(
          "', 'YYYY-MM-DD HH24:MI:SS') or (ModifyDate = TO_DATE('");
      buffer.append(modifyDate);
      buffer.append("', 'YYYY-MM-DD HH24:MI:SS') and DataID > ");
      buffer.append(checkpoint.insertDataId);
      buffer.append(")) and ");
    }
    buffer.append("rownum <= ");
    buffer.append(batchsz);

    String query = buffer.toString();
    String view = "(select * from DTree" + ORDER_BY + ")";
    String[] columns = new String[] { "ModifyDate", "DataID" };
    if (LOGGER.isLoggable(Level.FINEST))
      LOGGER.finest("CANDIDATES QUERY: " + query);

    return sysadminClient.ListNodes(query, view, columns);
  }

  /** Fetches the list of Deleted Items candidates for SQL Server. */
  /*
   * I try limit the list of delete candidates to those
   * recently deleted (via a checkpoint) and items only of
   * SubTypes we would have indexed in the first place.
   * Unfortunately, Livelink loses an items ancestral history
   * when recording the delete event in the audit logs, so
   * I cannot determine if the deleted item came from
   * an explicitly included location, or an explicitly
   * excluded location.
   */
  private ClientValue getDeletesSqlServer(Checkpoint checkpoint, int batchsz)
      throws RepositoryException {
    if (deleteSupported == false) {
      return null;
    }

    StringBuilder buffer = new StringBuilder();
    // This is the same as "(AuditStr = 'Delete'", except that as
    // of the July 2008 monthly patch for Livelink 9.7.1, "Delete"
    // would run afoul of the ListNodesQueryBlackList.
    buffer.append("AuditID = 2");

    // Only include delete events after the checkpoint.
    String deleteDate = dateFormat.toSqlMillisString(checkpoint.deleteDate);
    buffer.append(" and (AuditDate > '");
    buffer.append(deleteDate);
    buffer.append("' or (AuditDate = '");
    buffer.append(deleteDate);
    buffer.append("' and EventID > ");
    buffer.append(checkpoint.deleteEventId);
    buffer.append("))");

    // Exclude items with a SubType we know we excluded when indexing.
    String excludedNodeTypes = connector.getExcludedNodeTypes();
    if (excludedNodeTypes != null && excludedNodeTypes.length() > 0) {
      buffer.append(" and SubType not in (");
      buffer.append(excludedNodeTypes);
      buffer.append(')');
    }

    buffer.append(DELETE_ORDER_BY);

    String query = buffer.toString();
    String view = "DAuditNew";
    String[] columns = {
      "top " + batchsz + " " + AUDIT_DATE_SQL_SERVER, "EventID",
      "DataID" };
    if (LOGGER.isLoggable(Level.FINEST))
      LOGGER.finest("DELETE CANDIDATES QUERY: " + query);

    return sysadminClient.ListNodes(query, view, columns);
  }

  /** Fetches the list of Deleted Items candidates for Oracle. */
  /*
   * I try limit the list of delete candidates to those
   * recently deleted (via a checkpoint) and items only of
   * SubTypes we would have indexed in the first place.
   * Unfortunately, Livelink loses an items ancestral history
   * when recording the delete event in the audit logs, so
   * I cannot determine if the deleted item came from
   * an explicitly included location, or an explicitly
   * excluded location.
   */
  private ClientValue getDeletesOracle(Checkpoint checkpoint, int batchsz)
      throws RepositoryException {
    if (deleteSupported == false) {
      return null;
    }

    StringBuilder buffer = new StringBuilder();
    // This is the same as "(AuditStr = 'Delete'", except that as
    // of the July 2008 monthly patch for Livelink 9.7.1, "Delete"
    // would run afoul of the ListNodesQueryBlackList.
    buffer.append("AuditID = 2");

    // Only include delete events after the checkpoint.
    String deleteDate = dateFormat.toSqlString(checkpoint.deleteDate);
    buffer.append(" and (AuditDate > TO_DATE('");
    buffer.append(deleteDate);
    buffer.append("', 'YYYY-MM-DD HH24:MI:SS') or (AuditDate = TO_DATE('");
    buffer.append(deleteDate);
    buffer.append("', 'YYYY-MM-DD HH24:MI:SS') and EventID > ");
    buffer.append(checkpoint.deleteEventId);
    buffer.append("))");

    // Exclude items with a SubType we know we excluded when indexing.
    String excludedNodeTypes = connector.getExcludedNodeTypes();
    if (excludedNodeTypes != null && excludedNodeTypes.length() > 0) {
      buffer.append(" and SubType not in (");
      buffer.append(excludedNodeTypes);
      buffer.append(')');
    }

    buffer.append(" and rownum <= ");
    buffer.append(batchsz);

    String query = buffer.toString();
    String view = "(select * from DAuditNew" + DELETE_ORDER_BY + ")";
    String[] columns = new String[] {
      AUDIT_DATE_ORACLE, "EventID", "DataID" };
    if (LOGGER.isLoggable(Level.FINEST))
      LOGGER.finest("DELETE CANDIDATES QUERY: " + query);

    return sysadminClient.ListNodes(query, view, columns);
  }
}
