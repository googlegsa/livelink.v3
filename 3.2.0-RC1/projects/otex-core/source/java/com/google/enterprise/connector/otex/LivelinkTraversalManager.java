// Copyright 2007 Google Inc.
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

import static com.google.enterprise.connector.otex.SqlQueries.choice;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.enterprise.connector.otex.client.Client;
import com.google.enterprise.connector.otex.client.ClientValue;
import com.google.enterprise.connector.spi.DocumentList;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.SpiConstants;
import com.google.enterprise.connector.spi.TraversalManager;
import com.google.enterprise.connector.spi.TraversalContext;
import com.google.enterprise.connector.spi.TraversalContextAware;
import com.google.enterprise.connector.util.EmptyDocumentList;
import com.google.enterprise.connector.util.TraversalTimer;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

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
   * can filter out duplicates.
   */
  static final Field[] DEFAULT_FIELDS;

  /**
   * The prefix for generated column aliases for the additional SQL
   * {@code SELECT} expressions. Must not collide with anything in
   * {@code DEFAULT_FIELDS}, but that is very unlikely since a numeric
   * suffix is appended to this value.
   */
  private static final String FIELD_ALIAS_PREFIX = "alias";

  /**
   * The fields for this connector instance, which may include custom
   * fields from the advanced configuration properties.
   */
  private final Field[] fields;

  /**
   * The columns needed in the database query, which are obtained
   * from the field names in <code>fields</code>.
   */
  private final String[] selectList;

  /**
   * True if this connector instance will track deleted items and
   * submit delete actions to the GSA.
   */
  private final boolean deleteSupported;

  /**
   * Genealogist used to discover ancestor nodes for documents.
   * This is null if using DTreeAncestors or there are no explicit
   * included or excluded nodes.
   */
  @VisibleForTesting
  final Genealogist genealogist;

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

    // Workaround LAPI NumberFormatException/NullPointerException bug
    // returning negative longs.
    list.add(Field.fromExpression("GoogleDataSize as DataSize", "DataSize"));

    list.add(new Field("PermID"));

    // Make sure the alias prefix does not collide. This test is more
    // stringent than we need (we only use "alias4, alias5, etc., so
    // "aliasFoo" wouldn't cause problems). But we control the values,
    // so there's no point in working harder to detect real collisions.
    for (Field field : list) {
      assert !field.fieldName.startsWith(FIELD_ALIAS_PREFIX) : field.fieldName;
    }

    DEFAULT_FIELDS = list.toArray(new Field[0]);
  }

  /** The WebNodes derived view to use with the full {@code selectList}. */
  private static final String WEBNODES_VIEW_RESULTS = "(select b.*, "
      + "case when DataSize < 0 then 0 else DataSize end as GoogleDataSize "
      + "from WebNodes b)";

  /** The connector contains configuration information. */
  private final LivelinkConnector connector;

  /**
   * The traversal client provides access to the server as the
   * traversal user.
   */
  private final Client traversalClient;

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

  /** The SQL queries resource bundle wrapper. */
  private final SqlQueries sqlQueries;

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
      Client traversalClient, String traversalUsername, Client sysadminClient,
      ContentHandler contentHandler) throws RepositoryException {
    this.connector = connector;
    this.currentUsername = traversalUsername;
    this.traversalClient = traversalClient;
    this.sysadminClient = sysadminClient;
    this.contentHandler = contentHandler;

    this.isSqlServer = connector.isSqlServer();
    this.sqlQueries = new SqlQueries(this.isSqlServer);

    // Check to see if we will track Deleted Documents.
    this.deleteSupported = connector.getTrackDeletedItems();

    this.fields = getFields();
    this.selectList = getSelectList();

    // Cache a Genealogist, if we need one.
    String startNodes = connector.getIncludedLocationNodes();
    String excludedNodes = connector.getExcludedLocationNodes();
    if (connector.getUseDTreeAncestors()
        || ((startNodes == null || startNodes.length() == 0)
            && (excludedNodes == null || excludedNodes.length() == 0))) {
      this.genealogist = null;
    } else {
      // We use sysadminClient here to match the behavior of using
      // DTreeAncestors when the traversal user does not have
      // permission for intermediate nodes.
      this.genealogist = Genealogist.getGenealogist(connector.getGenealogist(),
          sysadminClient, startNodes, excludedNodes,
          connector.getGenealogistMinCacheSize(),
          connector.getGenealogistMaxCacheSize());
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
    if (deleteSupported)
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
      ClientValue results = getLastAuditEvent();
      if (results.size() > 0) {
        checkpoint.setDeleteCheckpoint(
            dateFormat.parse(results.toString(0, "AuditDate")),
            results.toValue(0, "EventID"));
      } else {
        LOGGER.fine("Unable to establish initial Deleted Items " +
            "Checkpoint: No query results.");
        checkpoint.setDeleteCheckpoint(new Date(), null);
      }
    } catch (Exception e) {
      LOGGER.warning("Error establishing initial Deleted Items " +
          "Checkpoint: " + e.getMessage());
      try {
        checkpoint.setDeleteCheckpoint(new Date(), null);
      } catch (RepositoryException ignored) {
        // Shouldn't get here with null Value parameter.
        throw new AssertionError();
      }
    }
  }

  /**
   * A separate method for testability, because the caller handles all
   * exceptions, making it hard to test the query for correctness.
   */
  @VisibleForTesting
  ClientValue getLastAuditEvent() throws RepositoryException {
    return sqlQueries.execute(sysadminClient, null,
        "LivelinkTraversalManager.getLastAuditEvent");
  }

  /**
   * Gets a SQL condition that matches descendants of the starting nodes,
   * including the starting nodes themselves, from among the candidates.
   *
   * @param startNodes a comma-separated string of object IDs
   * @param candidatesList a comma-separated string of candidate object IDs
   * @return a SQL conditional expression string
   */
  /*
   * With Oracle 10, we could use a CONNECT BY query:
   *
   *    DataID in (select connect_by_root DataID DataID
   *        from DTree where DataID in (<ancestorNodes>)
   *        start with DataID in (<candidatesList>)
   *        connect by DataID = prior ParentID)
   *
   * This failed, however, at a customer site, for unknown reasons.
   * The corresponding SQL Server query, a recursive CTE of the form
   * "with ... as (select ...) select ...", is not possible due to the
   * ListNodes "select {columns} from {view} a where {query}" format.
   */
  private String getDescendants(String startNodes, String candidatesList) {
    String ancestorNodes = Genealogist.getAncestorNodes(startNodes);
    return sqlQueries.getWhere(null,
        "LivelinkTraversalManager.getDescendants",
        startNodes, candidatesList, ancestorNodes);
  }

  @VisibleForTesting
  Field[] getFields() {
    Map<String, String> selectExpressions =
        connector.getIncludedSelectExpressions();
    Field[] fields =
        new Field[DEFAULT_FIELDS.length + selectExpressions.size()];
    System.arraycopy(DEFAULT_FIELDS, 0, fields, 0, DEFAULT_FIELDS.length);
    int i = DEFAULT_FIELDS.length;
    for (Map.Entry<String, String> select : selectExpressions.entrySet()) {
      // The map value is the select expression. We pick an arbitrary
      // column alias for the SQL expression (because the property name
      // might not be a valid SQL identifier) and record it as the field
      // name for the recarray. The map key is the property name.
      fields[i++] = Field.fromExpression(select.getValue() + " "
          + FIELD_ALIAS_PREFIX + i, FIELD_ALIAS_PREFIX + i, select.getKey());
    }
    return fields;
  }

  @VisibleForTesting
  String[] getSelectList() {
    String[] selectList = new String[fields.length];
    for (int i = 0; i < fields.length; i++)
      selectList[i] = fields[i].selectExpression;
    return selectList;
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
    if (Strings.isNullOrEmpty(checkpoint)) {
      checkpoint = getStartCheckpoint();
    }

    if (LOGGER.isLoggable(Level.FINE))
      LOGGER.fine("RESUME TRAVERSAL: " + batchSize + " rows from " +
          checkpoint + ".");

    // Ping the Livelink Server. If I can't talk to the server, this
    // will throw an exception, signalling a retry after an error
    // delay.
    traversalClient.GetCurrentUserID();

    return listNodes(checkpoint);
  }

  /** {@inheritDoc} */
  public void setTraversalContext(TraversalContext traversalContext) {
    this.traversalContext = traversalContext;
  }

  /**
   * This method essentially executes queries of the following form
   *
   * <pre>
   *     SELECT <em>columns</em>
   *     FROM WebNodes
   *     WHERE <em>after_last_checkpoint</em>
   *         AND <em>included_minus_excluded</em>
   *     ORDER BY ModifyDate, DataID
   * </pre>
   *
   * and only reads the first <code>batchSize</code> rows. The ORDER
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
    TraversalTimer timer = new TraversalTimer(traversalContext);
    while (timer.isTicking()) {
      ClientValue candidates = getCandidates(checkpoint, batchsz);
      ClientValue deletes = getDeletes(checkpoint, batchsz);
      ClientValue results = null;

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
        for (int i = 0; i < numInserts; i++) {
          buffer.append(candidates.toInteger(i, "DataID"));
          buffer.append(',');
        }
        buffer.deleteCharAt(buffer.length() - 1);
        results = getResults(buffer.toString());
        numInserts = (results == null) ? 0 : results.size();
      }

      if ((numInserts + numDeletes) > 0) {
        if (LOGGER.isLoggable(Level.FINE)) {
          LOGGER.fine("RESULTSET: " + numInserts + " rows.  " +
              "DELETESET: " + numDeletes + " rows.");
        }
        return new LivelinkDocumentList(connector, traversalClient,
            contentHandler, results, fields, deletes,
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
    return new EmptyDocumentList(checkpoint.toString());
  }

  /**
   * Filters the candidates down and returns the main recarray needed
   * for the DocumentList.
   *
   * @param candidatesList a comma-separated string of candidate object IDs
   * @return the main query results
   */
  @VisibleForTesting
  ClientValue getResults(String candidatesList) throws RepositoryException {
    if (genealogist == null) {
      // We're either using DTreeAncestors, or we don't need it.
      return getMatching(candidatesList, true, WEBNODES_VIEW_RESULTS,
          selectList, traversalClient);
    } else {
      // We're not using DTreeAncestors but we need the ancestors.
      // If there's a SQL WHERE condition, we need to consistently
      // run it against WebNodes. Otherwise, DTree is enough here.
      String sqlWhereCondition = connector.getSqlWhereCondition();
      String view =
          (Strings.isNullOrEmpty(sqlWhereCondition)) ? "DTree" : "WebNodes";
      ClientValue matching = getMatching(candidatesList, false, view,
          new String[] { "DataID" }, sysadminClient);
      return (matching.size() == 0) ? null : getMatchingDescendants(matching);
    }
  }

  /**
   * Filters the candidates. This method will apply hierarchical
   * restrictions using DTreeAncestors, but not does not filter the
   * results by hierarchy without DTreeAncestors.
   *
   * @param candidatesList a comma-separated string of candidate object IDs
   * @param sortResults {@code true} to use an ORDER BY clause on the query,
   *     or {@code false} to let the database use any order
   * @param view the database view to select from
   * @param columns the select list
   * @param client the Livelink client to use to execute the query
   * @return the matching results, which may be the main query results,
   *     or which may need to have the hierarchical filtering applied
   */
  private ClientValue getMatching(String candidatesList,
      boolean sortResults, String view, String[] columns, Client client)
      throws RepositoryException {
    return client.ListNodes(getMatchingQuery(candidatesList, sortResults),
        view, columns);
  }

  @VisibleForTesting
  String getMatchingQuery(String candidatesList, boolean sortResults) {
    String startNodes = connector.getIncludedLocationNodes();
    String excludedVolumes = connector.getExcludedVolumeTypes();
    String excludedNodeTypes = connector.getExcludedNodeTypes();
    String excludedLocationNodes = connector.getExcludedLocationNodes();
    String sqlWhereCondition = connector.getSqlWhereCondition();

    return sqlQueries.getWhere("RESULTS QUERY",
        "LivelinkTraversalManager.getMatching",
        /* 0 */ candidatesList,
        /* 1 */ choice(Strings.isNullOrEmpty(startNodes)), // [sic]
        /* 2 */ choice(connector.getUseDTreeAncestors()),
        /* 3 */ getDescendants(startNodes, candidatesList),
        /* 4 */ choice(!Strings.isNullOrEmpty(excludedVolumes)),
        /* 5 */ excludedVolumes,
        /* 6 */ choice(!Strings.isNullOrEmpty(excludedNodeTypes)),
        /* 7 */ excludedNodeTypes,
        /* 8 */ choice(connector.getUseDTreeAncestors()
            && !Strings.isNullOrEmpty(excludedLocationNodes)),
        /* 9 */ getDescendants(excludedLocationNodes, candidatesList),
        /* 10 */ choice(!Strings.isNullOrEmpty(sqlWhereCondition)),
        /* 11 */ sqlWhereCondition,
        /* 12 */ choice(sortResults));
  }

  /**
   * Filters the matches according to their ancestors, but avoids the
   * use of DTreeAncestors.
   *
   * @param matching the candidates matching the non-hierarchical filters
   * @return the main query results
   */
  private ClientValue getMatchingDescendants(ClientValue matching)
      throws RepositoryException {
    String descendants;
    // We are using the same genealogist for multiple traversal batches, which
    // might be done from different threads (although never concurrently).
    synchronized (genealogist) {
      descendants = genealogist.getMatchingDescendants(matching);
    }
    if (descendants != null) {
      String query = sqlQueries.getWhere(null,
          "LivelinkTraversalManager.getMatchingDescendants", descendants);
      return traversalClient.ListNodes(query, WEBNODES_VIEW_RESULTS,
          selectList);
    } else {
      return null;
    }
  }

  /*
   * We need to use the sysadminClient to avoid getting no
   * candidates when the traversal user does not have permission for
   * any of the potential candidates.
   */
  private ClientValue getCandidates(Checkpoint checkpoint,
      int batchsz) throws RepositoryException {
    String insertDate = (checkpoint.insertDate != null)
        ? dateFormat.toSqlString(checkpoint.insertDate) : null;
    return sqlQueries.execute(sysadminClient, "CANDIDATES QUERY",
        "LivelinkTraversalManager.getCandidates",
        choice(checkpoint.insertDate != null), insertDate,
        checkpoint.insertDataId, batchsz);
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
  private ClientValue getDeletes(Checkpoint checkpoint, int batchsz)
      throws RepositoryException {
    if (deleteSupported == false) {
      return null;
    }

    String deleteDate = (isSqlServer)
        ? dateFormat.toSqlMillisString(checkpoint.deleteDate)
        : dateFormat.toSqlString(checkpoint.deleteDate);
    String excludedNodeTypes = connector.getExcludedNodeTypes();
    return sqlQueries.execute(sysadminClient, "DELETE CANDIDATES QUERY",
        "LivelinkTraversalManager.getDeletes",
        deleteDate, checkpoint.deleteEventId,
        choice(!Strings.isNullOrEmpty(excludedNodeTypes)), excludedNodeTypes,
        batchsz);
  }
}
