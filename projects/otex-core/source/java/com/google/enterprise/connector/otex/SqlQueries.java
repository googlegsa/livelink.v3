// Copyright 2013 Google Inc. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.enterprise.connector.otex;

import com.google.enterprise.connector.otex.client.Client;
import com.google.enterprise.connector.otex.client.ClientValue;
import com.google.enterprise.connector.spi.RepositoryException;

import java.text.MessageFormat;
import java.util.ListResourceBundle;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

class SqlQueries {
  private static final Logger LOGGER =
      Logger.getLogger(SqlQueries.class.getName());

  /** Transforms a boolean into a 0/1 value for use in a ChoiceFormat. */
  public static int choice(boolean selector) {
    return selector ? 1 : 0;
  }

  private final ResourceBundle resources;

  SqlQueries(boolean isSqlServer) {
    resources = ResourceBundle.getBundle(
        "com.google.enterprise.connector.otex.SqlQueries$Resources",
        new Locale(isSqlServer ? "mssql" : "oracle"));
  }

  public ClientValue execute(Client client, String logPrefix, String key,
      Object... parameters) throws RepositoryException {
    String[] columns = resources.getStringArray(key + ".select");
    String view = resources.getString(key + ".from");
    String query = getWhere(logPrefix, key, parameters);
    return client.ListNodes(query, view, columns);
  }

  public ClientValue executeNoThrow(Client client, String logPrefix, String key,
      Object... parameters) throws RepositoryException {
    String[] columns = resources.getStringArray(key + ".select");
    String view = resources.getString(key + ".from");
    String query = getWhere(logPrefix, key, parameters);
    return client.ListNodesNoThrow(query, view, columns);
  }

  public String getWhere(String logPrefix, String key, Object... parameters) {
    String pattern = resources.getString(key + ".where");
    LOGGER.log(Level.FINEST, "QUERY KEY: {0}", key);
    String query = MessageFormat.format(pattern, parameters);
    if (logPrefix != null && LOGGER.isLoggable(Level.FINEST))
      LOGGER.finest(logPrefix + ": " + query);
    return query;
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

  /** Select list column for AuditDate; Oracle still lacks milliseconds. */
  private static final String AUDIT_DATE_ORACLE =
      "TO_CHAR(AuditDate, 'YYYY-MM-DD HH24:MI:SS') as GoogleAuditDate";

  /** Select list column for AuditDate with milliseconds for SQL Server. */
  private static final String AUDIT_DATE_SQL_SERVER =
      "CONVERT(VARCHAR(23), AuditDate, 121) as GoogleAuditDate";

  /** The ordered, derived view for DTree on Oracle. */
  private static final String DTREE_VIEW_ORACLE =
      "(select * from DTree" + ORDER_BY + ")";

  /** The ordered, derived view for DAuditNew on Oracle. */
  private static final String DAUDITNEW_VIEW_ORACLE = "(select b.*, "
      + AUDIT_DATE_ORACLE + " from DAuditNew b" + DELETE_ORDER_BY + ")";

  /** The derived view for DAuditNew on SQL Server. */
  private static final String DAUDITNEW_VIEW_SQL_SERVER = "(select b.*, "
      + AUDIT_DATE_SQL_SERVER + " from DAuditNew b)";

  public static class Resources extends ListResourceBundle {
    @Override
    protected Object[][] getContents() {
      return new Object[][] {
        { "Genealogist.getParent.select",
          new String[] {
            "ParentID",
            "DataID" } },
        { "Genealogist.getParent.from",
          "DTree" },
        { "Genealogist.getParent.where",
          "DataID in ({0,number,#},{1,number,#})" },

        { "HybridGenealogist.getParents.select",
          new String[] {
            "DataID",
            "ParentID",
            "StepParentID" } },
        { "HybridGenealogist.getParents.from",
          // The StepParentID column uses a correlated subquery
          // to lookup the parent object ID for volumes.
          "(select b.*, (select ParentID from DTree c "
          + "where -b.DataID = c.DataID and c.ParentID <> -1) "
          + "as StepParentID from DTree b)" },
        { "HybridGenealogist.getParents.where",
          "DataID in ({0})" },

        { "LivelinkTraversalManager.getDescendants.where",
          "(DataID in ({0}) or "
          + "DataID in (select DataID from DTreeAncestors where "
          + "DataID in ({1}) and AncestorID in ({2})))" },

        { "LivelinkTraversalManager.getMatching.where",
          // Candidates
          "DataID in ({0})"

          // Included nodes
          // If we have an explict list of start locations, build a
          // query that includes only those and their descendants.
          + "{1,choice,0#'{2,choice,0#|1# and {3}}'"
          // If we don't have an explicit list of start points,
          // just avoid the excluded volume types.
          // FIXME: I think this else is wrong. The excludedVolumeTypes
          // should always be applied. For example, you might exclude
          // everything in projects. We should not exclude the start
          // nodes themselves, though.
          + "|1#'{4,choice,0#|1# and -OwnerID not in (select DataID from DTree "
          + "where SubType in ({5}))}'}"

          // Excluded nodes
          // TODO(jlacey): This doesn't account for hidden items.
          + "{6,choice,0#|1# and SubType not in ({7})}"
          + "{8,choice,0#|1# and not {9}}"

          // sqlWhereCondition
          + "{10,choice,0#|1# and ({11})}"

          // highestModifyDate: The correct timestamp literal syntax
          // must be supplied by the caller.
          + " and ModifyDate <= {12}"

          + "{13,choice,0#|1#" + ORDER_BY + "}" },

        { "LivelinkTraversalManager.getMatchingDescendants.where",
          // The correct timestamp literal syntax must be supplied by
          // the caller.
          "DataID in ({0}) and ModifyDate <= {1}" + ORDER_BY },

        { "LivelinkAuthorizationManager.getExcludedVolumeId.select",
          new String[] {
            "DataID",
            "PermID" } },
        { "LivelinkAuthorizationManager.getExcludedVolumeId.from",
          "DTree" },
        { "LivelinkAuthorizationManager.getExcludedVolumeId.where",
          "SubType = {0}{1,choice,0#|1# and DataID in ({2})}" },

        { "LivelinkAuthorizationManager.addAuthorizedDocids.select",
          new String[] {
            "DataID",
            "PermID" } },
        { "LivelinkAuthorizationManager.addAuthorizedDocids.from",
          "DTree" },
        { "LivelinkAuthorizationManager.addAuthorizedDocids.where",
          "DataID in ({0})"

          // If we are excluding deleted documents from the result
          // set, add a subquery to eliminate those docids that are in
          // the DeletedDocs table. Open Text uses ParentID in the
          // Undelete OScript code, so we will, too.
          + "{1,choice,0#|1#' and ParentID <> {2,number,#}'}"

          // If we are excluding items in the workflow volume, add a
          // subquery to eliminate those.
          + "{3,choice,0#|1#' and OwnerID <> {4,number,#}'}"

          // If we are excluding hidden items, add a subquery to
          // eliminate those.
          + "{5,choice,0#|1#'"
          // This is a correlated subquery (using the implicit "a"
          // range variable added by LAPI) to only check for the
          // documents we're authorizing.
          + " and Catalog <> {6,number,#} and DataID not in "
          + "(select Anc.DataID from DTreeAncestors Anc join DTree T "
          + "on Anc.AncestorID = T.DataID where Anc.DataID = a.DataID "
          + "and T.Catalog = {6,number,#}"
          // Explicitly included items and their descendants are allowed
          // even if they are hidden.
          + "{7,choice,0#|1# and Anc.AncestorID not in ({8}) "
          + "and Anc.AncestorID not in (select AncestorID from DTreeAncestors "
          + "where DataID in ({9}))}"
          + ")'}" },
      };
    }
  }

  public static class Resources_mssql extends ListResourceBundle {
    @Override
    protected Object[][] getContents() {
      return new Object[][] {
        // These validation queries are not optimal, but they are so
        // fast that it's not worth whitelisting additional views to
        // optimize them.
        { "LivelinkConnector.validateDTreeAncestors.select",
          new String[] {
            "DataID" } },
        { "LivelinkConnector.validateDTreeAncestors.from",
          "DTreeAncestors" },
        { "LivelinkConnector.validateDTreeAncestors.where",
          "DataID in (select TOP 1 DataID from DTreeAncestors)" },

        { "LivelinkConnector.validateIncludedLocationStartDate.select",
          new String[] {
            "ModifyDate as minModifyDate",
            "DataID" } },
        { "LivelinkConnector.validateIncludedLocationStartDate.from",
          "DTree" },
        { "LivelinkConnector.validateIncludedLocationStartDate.where",
          // Select the min ModifyDate using TOP 1 with ORDER BY
          // ModifyDate -- which avoids selecting multiple rows where
          // ModifyDate = min(ModifyDate) -- among items that
          // DTreeAncestors says are in the Items to Index.
          "DataID in (select top 1 DataID from DTree where "
          + "DataID in (select DataID from DTreeAncestors "
          + "where AncestorID in ({0})) or DataID in ({1}) "
          + "order by ModifyDate)" },

        { "LivelinkConnector.getMissingEnterpriseWorkspaceAncestors.select",
          new String[] {
            "DataID" } },
        { "LivelinkConnector.getMissingEnterpriseWorkspaceAncestors.from",
          "DTree" },
        { "LivelinkConnector.getMissingEnterpriseWorkspaceAncestors.where",
          // The correlated subquery runs against the inner DTree, not
          // the outer DTree, so we use "b" to refer to it, rather than
          // using the "a" range variable that LAPI adds behind
          // the scenes. This query is designed to be very fast and
          // avoid table scans. For performance reasons, we don't
          // restrict this query by subtype.
          "DataID in (select DataID from DTree b where "
          + "not exists (select DataID from DTreeAncestors "
          + "where DataID = b.DataID and AncestorID = {0,number,#}) "
          + "and OwnerID = {1,number,#} and DataID <> {0,number,#})" },

        { "LivelinkConnector.validateSqlWhereCondition.select",
          new String[] {
            "DataID",
            "PermID" } },
        { "LivelinkConnector.validateSqlWhereCondition.from",
          "WebNodes" },
        { "LivelinkConnector.validateSqlWhereCondition.where",
          "DataID in (select TOP 1 DataID from WebNodes where {0})" },

        { "LivelinkTraversalManager.getLastAuditEvent.select",
          new String[] {
            "GoogleAuditDate as AuditDate",
            "EventID",
            "DataID" } },
        { "LivelinkTraversalManager.getLastAuditEvent.from",
          DAUDITNEW_VIEW_SQL_SERVER },
        { "LivelinkTraversalManager.getLastAuditEvent.where",
          // Using a descending order by with top 1 is no faster.
          "EventID in (select max(EventID) from DAuditNew)" },

        { "LivelinkTraversalManager.getCandidates.select",
          new String[] {
            "ModifyDate",
            "DataID" } },
        { "LivelinkTraversalManager.getCandidates.from",
          "DTree" },
        { "LivelinkTraversalManager.getCandidates.where",
          // The double ORDER_BY is required because the first applies
          // to the subquery using TOP, and the second ensures that the
          // returned results are sorted (for accurate checkpoints).
          "DataID in (select top {3,number,#} DataID from DTree"
          + "{0,choice,0#|1#' where "
          + "(ModifyDate > ''''{1}'''' or (ModifyDate = ''''{1}'''' "
          + "and DataID > {2,number,#}))'}"
          + ORDER_BY + ")" + ORDER_BY },

        { "LivelinkTraversalManager.getDeletes.select",
          new String[] {
            "GoogleAuditDate as AuditDate",
            "EventID",
            "DataID" } },
        { "LivelinkTraversalManager.getDeletes.from",
          DAUDITNEW_VIEW_SQL_SERVER },
        { "LivelinkTraversalManager.getDeletes.where",
          // AuditID = 2 is the same as "AuditStr = 'Delete'", except
          // that as of the July 2008 monthly patch for Livelink 9.7.1,
          // "Delete" would run afoul of the ListNodesQueryBlackList.
          // Only include delete events after the checkpoint.
          // Exclude items with a SubType we know we excluded when indexing.
          "EventID in (select top {4,number,#} EventID from DAuditNew where "
          + "AuditID = 2 and (AuditDate > ''{0}'' or "
          + "(AuditDate = ''{0}'' and EventID > {1,number,#}))"
          + "{2,choice,0#|1 and SubType not in ({3})}"
          + DELETE_ORDER_BY + ")" + DELETE_ORDER_BY},
      };
    }
  }

  public static class Resources_oracle extends ListResourceBundle {
    @Override
    protected Object[][] getContents() {
      return new Object[][] {
        { "LivelinkConnector.validateDTreeAncestors.select",
          new String[] {
            "DataID" } },
        { "LivelinkConnector.validateDTreeAncestors.from",
          "DTreeAncestors" },
        { "LivelinkConnector.validateDTreeAncestors.where",
          "rownum = 1" },

        { "LivelinkConnector.validateIncludedLocationStartDate.select",
          new String[] {
            "ModifyDate as minModifyDate",
            "DataID" } },
        { "LivelinkConnector.validateIncludedLocationStartDate.from",
          "DTree" },
        { "LivelinkConnector.validateIncludedLocationStartDate.where",
          "ModifyDate in (select min(T.ModifyDate) from DTree T join "
          + "DTreeAncestors Anc on T.DataID = Anc.DataID where "
          + "Anc.AncestorID in ({0}) or T.DataID in ({1})) "
          + "and rownum = 1" },

        { "LivelinkConnector.getMissingEnterpriseWorkspaceAncestors.select",
          new String[] {
            "DataID" } },
        { "LivelinkConnector.getMissingEnterpriseWorkspaceAncestors.from",
          "DTree" },
        { "LivelinkConnector.getMissingEnterpriseWorkspaceAncestors.where",
          // For the correlated subquery to work, we have to refer to
          // DTree using the "a" range variable that LAPI adds behind
          // the scenes. This query is designed to be very fast and
          // avoid table scans. For performance reasons, we don't
          // restrict this query by subtype.
          "not exists (select DataID from DTreeAncestors "
          + "where DataID = a.DataID and AncestorID = {0,number,#}) "
          + "and OwnerID = {1,number,#} and DataID <> {0,number,#} "
          + "and rownum = 1" },

        { "LivelinkConnector.validateSqlWhereCondition.select",
          new String[] {
            "DataID",
            "PermID" } },
        { "LivelinkConnector.validateSqlWhereCondition.from",
          "WebNodes" },
        { "LivelinkConnector.validateSqlWhereCondition.where",
          "({0}) and rownum = 1" },

        { "LivelinkTraversalManager.getLastAuditEvent.select",
          new String[] {
            "GoogleAuditDate as AuditDate",
            "EventID",
            "DataID" } },
        { "LivelinkTraversalManager.getLastAuditEvent.from",
          DAUDITNEW_VIEW_ORACLE },
        { "LivelinkTraversalManager.getLastAuditEvent.where",
          // Using a descending order by with rownum = 1 is a little
          // faster, but that would required whitelisting another view.
          "EventID in (select max(EventID) from DAuditNew)" },

        { "LivelinkTraversalManager.getCandidates.select",
          new String[] {
            "ModifyDate",
            "DataID" } },
        { "LivelinkTraversalManager.getCandidates.from",
          DTREE_VIEW_ORACLE },
        { "LivelinkTraversalManager.getCandidates.where",
          // The inner format containing a # avoids thousands separators
          // in the DataID value, but that requires quoting the choice
          // subformat, which triggers some crazy quoting rules.
          "{0,choice,0#|1#'"
          + "(ModifyDate > TIMESTAMP''''{1}'''' or "
          + "(ModifyDate = TIMESTAMP''''{1}'''' and DataID > {2,number,#})) "
          + "and '}"
          + "rownum <= {3,number,#}" },

        { "LivelinkTraversalManager.getDeletes.select",
          new String[] {
            "GoogleAuditDate as AuditDate",
            "EventID",
            "DataID", } },
        { "LivelinkTraversalManager.getDeletes.from",
          DAUDITNEW_VIEW_ORACLE },
        { "LivelinkTraversalManager.getDeletes.where",
          // AuditID = 2 is the same as "AuditStr = 'Delete'", except
          // that as of the July 2008 monthly patch for Livelink 9.7.1,
          // "Delete" would run afoul of the ListNodesQueryBlackList.
          // Only include delete events after the checkpoint.
          // Exclude items with a SubType we know we excluded when indexing.
          "AuditID = 2 and (AuditDate > TIMESTAMP''{0}'' or "
          + "(AuditDate = TIMESTAMP''{0}'' and EventID > {1,number,#}))"
          + "{2,choice,0#|1# and SubType not in ({3})} "
          + "and rownum <= {4,number,#}" }
      };
    }
  }
}
