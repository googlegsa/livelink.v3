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

import com.google.common.collect.ObjectArrays;
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

  /**
   * TODO(jlacey): Temporary method to support TOP in the select list
   * with SQL Server. It works with ROWNUM queries in Oracle, too, so
   * that a single call can support both variations easily. The
   * parameter in the select list goes away when we support OTCS 10, but
   * we still need to reconcile the positions of the TOP and rownum
   * parameters.
   */
  public ClientValue executeLimit(Client client, String logPrefix, String key,
      int limit, Object... parameters) throws RepositoryException {
    String[] columns = resources.getStringArray(key + ".select");
    if (columns[0].contains("{0")) {
      columns[0] = MessageFormat.format(columns[0], limit);
    } else {
      parameters = ObjectArrays.concat(parameters, limit);
    }
    String view = resources.getString(key + ".from");
    String query = getWhere(logPrefix, key, parameters);
    return client.ListNodes(query, view, columns);
  }

  public String getWhere(String logPrefix, String key, Object... parameters) {
    String pattern = resources.getString(key + ".where");
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
      "TO_CHAR(AuditDate, 'YYYY-MM-DD HH24:MI:SS') as AuditDate";

  /** Select list column for AuditDate with milliseconds for SQL Server. */
  private static final String AUDIT_DATE_SQL_SERVER =
      "CONVERT(VARCHAR(23), AuditDate, 121) as AuditDate";

  public static class Resources extends ListResourceBundle {
    protected Object[][] getContents() {
      return new Object[][] {
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
          // everything in projects.
          + "|1#'{4,choice,0#|1# and -OwnerID not in (select DataID from DTree "
          + "where SubType in ({5}))}'}"

          // Excluded nodes
          // TODO(jlacey): This doesn't account for hidden items.
          + "{6,choice,0#|1# and SubType not in ({7})}"
          + "{8,choice,0#|1# and not {9}}"

          // sqlWhereCondition
          + "{10,choice,0#|1# and ({11})}"

          + "{12,choice,0#|1#" + ORDER_BY + "}" },

        { "LivelinkTraversalManager.getMatchingDescendants.where",
          "DataID in ({0})" + ORDER_BY },
      };
    }
  }

  public static class Resources_mssql extends ListResourceBundle {
    protected Object[][] getContents() {
      return new Object[][] {
        { "LivelinkTraversalManager.forgeInitialDeleteCheckpoint.select",
          new String[] {
            AUDIT_DATE_SQL_SERVER,
            "EventID" } },
        { "LivelinkTraversalManager.forgeInitialDeleteCheckpoint.from",
          "DAuditNew" },
        { "LivelinkTraversalManager.forgeInitialDeleteCheckpoint.where",
          "EventID in (select max(EventID) from DAuditNew)" },

        { "LivelinkTraversalManager.getCandidates.select",
          new String[] {
            "top {0,number,#} ModifyDate",
            "DataID" } },
        { "LivelinkTraversalManager.getCandidates.from",
          "DTree" },
        { "LivelinkTraversalManager.getCandidates.where",
          "{0,choice,0#1=1|1#'"
          + "(ModifyDate > ''''{1}'''' or (ModifyDate = ''''{1}'''' "
          + "and DataID > {2,number,#}))'}"
          + ORDER_BY },

        { "LivelinkTraversalManager.getDeletes.select",
          new String[] {
            "top {0,number,#} " + AUDIT_DATE_SQL_SERVER,
            "EventID",
            "DataID" } },
        { "LivelinkTraversalManager.getDeletes.from",
          "DAuditNew" },
        { "LivelinkTraversalManager.getDeletes.where",
          // AuditID = 2 is the same as "AuditStr = 'Delete'", except
          // that as of the July 2008 monthly patch for Livelink 9.7.1,
          // "Delete" would run afoul of the ListNodesQueryBlackList.
          // Only include delete events after the checkpoint.
          // Exclude items with a SubType we know we excluded when indexing.
          "AuditID = 2 and (AuditDate > ''{0}'' or "
          + "(AuditDate = ''{0}'' and EventID > {1,number,#}))"
          + "{2,choice,0#|1 and SubType not in ({3})}"
          + DELETE_ORDER_BY },
      };
    }
  }

  public static class Resources_oracle extends ListResourceBundle {
    protected Object[][] getContents() {
      return new Object[][] {
        { "LivelinkTraversalManager.forgeInitialDeleteCheckpoint.select",
          new String[] {
            AUDIT_DATE_ORACLE,
            "EventID" } },
        { "LivelinkTraversalManager.forgeInitialDeleteCheckpoint.from",
          "DAuditNew" },
        { "LivelinkTraversalManager.forgeInitialDeleteCheckpoint.where",
          "EventID in (select max(EventID) from DAuditNew)" },

        { "LivelinkTraversalManager.getCandidates.select",
          new String[] {
            "ModifyDate",
            "DataID" } },
        { "LivelinkTraversalManager.getCandidates.from",
          "(select * from DTree" + ORDER_BY + ")" },
        { "LivelinkTraversalManager.getCandidates.where",
          // The inner format containing a # avoids thousands separators
          // in the DataID value, but that requires quoting the choice
          // subformat, which triggers some crazy quoting rules.
          "{0,choice,0#|1#'"
          + "(ModifyDate > TIMESTAMP''''{1}'''' or "
          + "(ModifyDate = TIMESTAMP''''{1}'''' and DataID > {2,number,#}))'} "
          + "and rownum <= {3,number,#}" },

        { "LivelinkTraversalManager.getDeletes.select",
          new String[] {
            AUDIT_DATE_ORACLE,
            "EventID",
            "DataID", } },
        { "LivelinkTraversalManager.getDeletes.from",
          "(select * from DAuditNew" + DELETE_ORDER_BY + ")" },
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
