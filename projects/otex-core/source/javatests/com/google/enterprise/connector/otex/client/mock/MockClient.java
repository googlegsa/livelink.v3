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

package com.google.enterprise.connector.otex.client.mock;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.enterprise.connector.otex.LivelinkException;
import com.google.enterprise.connector.otex.LivelinkIOException;
import com.google.enterprise.connector.otex.client.Client;
import com.google.enterprise.connector.otex.client.ClientValue;
import com.google.enterprise.connector.otex.client.ClientValueFactory;
import com.google.enterprise.connector.spi.RepositoryDocumentException;
import com.google.enterprise.connector.spi.RepositoryException;

import org.h2.jdbcx.JdbcDataSource;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * A mock client implementation.
 */
public class MockClient implements Client {
    /** The logger for this class. */
    private static final Logger LOGGER =
        Logger.getLogger(MockClient.class.getName());

  /** A list of valid usernames, used by GetUserInfo. */
  private static final ImmutableList<String> VALID_USERNAMES =
      ImmutableList.of("Admin", "llglobal");

    private final Connection jdbcConnection;

    public static Connection getConnection() {
      try {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:test");
        ds.setUser("sa");
        ds.setPassword("");
        return ds.getConnection();
      } catch (SQLException e) {
        throw new RuntimeException(e);
      }
    }

    public MockClient() {
      this.jdbcConnection = getConnection();
    }

    /** {@inheritDoc} */
    @Override
    public ClientValueFactory getClientValueFactory()
            throws RepositoryException {
        return new MockClientValueFactory();
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation returns an assoc containing a
     * CharacterEncoding of CHARACTER_ENCODING_NONE and a
     * ServerVersion of "9.5.0".
     */
    @Override
    public ClientValue GetServerInfo() {
        return new MockClientValue(
            new String[] { "CharacterEncoding", "ServerVersion" },
            new Object[] { new Integer(CHARACTER_ENCODING_NONE), "9.5.0" });
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation returns 1000, the user ID of Admin.
     */
    @Override
    public int GetCurrentUserID() throws RepositoryException {
        return 1000;
    }

    private final AtomicInteger cookieCount = new AtomicInteger();

    /**
     * {@inheritDoc}
     * <p>
     * This implementation returns an LLCookie with an invalid value.
     * A distinct value is returned on each call.
     */
    @Override
    public ClientValue GetCookieInfo() throws RepositoryException {
      String value = "llcookie value goes here" + cookieCount.getAndIncrement();
      ClientValue llcookie = new MockClientValue(
          new String[] { "Name", "Value" },
          new Object[] { "LLCookie", value });
      return new MockClientValue(new Object[] { llcookie });
    }

    /** {@inheritDoc} */
    @Override
    public ClientValue GetUserOrGroupByIDNoThrow(int id)
            throws RepositoryException {
      String query = "ID=" + id;
      String view = "KUAF";
      String[] columns = new String[] {"Name", "Type", "GroupID", "UserData"};
      ClientValue user = executeQuery(query, view, columns);

      if (user.size() > 0) {
        // Need to return a Assoc LLValue, whereas the above is a table.
        String name = user.toString(0, "Name");
        int type = user.toInteger(0, "Type");
        int groupId = user.toInteger(0, "GroupID");
        ClientValue userData = toAssoc(user.toString(0, "UserData"));
        return new MockClientValue(
            new String[] {"Name", "Type", "GroupID", "UserData"},
            new Object[] {name, type, groupId, userData});
      } else {
        return null;
      }
    }

    public ClientValue toAssoc(String fieldData)
        throws RepositoryException {
      if (Strings.isNullOrEmpty(fieldData)) {
        return new MockClientValue();
      }

      String[] data = fieldData.split(",");
      String[] names = new String[data.length];
      String[] values = new String[data.length];
      int i = 0;
      for (String value : data) {
        int index = value.indexOf("=");
        if (index > 0) {
          names[i] = value.substring(0, index);
          values[i] = value.substring(index + 1);
          i++;
        }
      }

      if (i > 0) {
        return new MockClientValue(
            Arrays.copyOf(names, i),
            Arrays.copyOf(values, i));
      } else {
        return new MockClientValue();
      }
    }

    /** {@inheritDoc} */
    @Override
    public ClientValue GetUserInfo(String username)
            throws RepositoryException {
        if (!VALID_USERNAMES.contains(username)) {
            throw new RepositoryException(
                "Could not get the specified user or group.");
        }
        int privileges;
        if (username.equals("Admin"))
            privileges = PRIV_PERM_BYPASS;
        else
            privileges = 0;
        return new MockClientValue(
            new String[] { "UserPrivileges" },
            new Integer[] { new Integer(privileges) });
    }

    /** {@inheritDoc} */
    @Override
    public ClientValue AccessEnterpriseWS() throws RepositoryException {
        return new MockClientValue(new String[] { "ID", "VolumeID" },
            new Integer[] { new Integer(2000), new Integer(-2000) });
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation returns an empty <code>ClientValue</code>.
     */
    @Override
    public ClientValue ListNodes(String query, String view, String[] columns)
            throws RepositoryException {
        LOGGER.fine("Entering MockClient.ListNodes");

        // Match the more stringent requirements in recent updates (6
        // or 7) of Livelink 9.7.1 and OTCS 10.
        boolean foundIdColumn = false;
        for (String column : columns) {
          // This is an attempt to match the SQL injection
          // protections, which quote the column names (which must
          // therefore be identifiers and not keywords, numbers, or
          // function calls) and which only allow multiple words for
          // column aliases. We're actually a little more strict:
          // columns names must be alphabetic, not alphanumeric, and
          // column aliases require the AS keyword (just to help
          // enforce a consistent coding style).
          // Using case-insensitive regexp matching with (?i:...).
          if (!column.matches("(?i:[a-z]+( as ([a-z]+))?)")) {
            throw new RepositoryException("Invalid SQL column: " + column);
          }

          // Check for the required ID column. We throw in a little
          // case-sensitivity check here.
          if (column.equals("DataID") || column.equals("PermID")) {
            foundIdColumn = true;
          }
        }
        if (!foundIdColumn) {
          throw new RepositoryException("DataID or PermID must be selected: "
              + Joiner.on(',').join(columns));
        }

        // Rewrite a date to string expression from SQL Server and
        // Oracle syntax to H2 syntax.
        String sqlServer = "CONVERT(VARCHAR(23), AuditDate, 121)";
        String oracle = "TO_CHAR(AuditDate, 'YYYY-MM-DD HH24:MI:SS')";
        String h2 = "CONVERT(AuditDate, VARCHAR(23))";
        view = view.replace(sqlServer, h2).replace(oracle, h2);

        return executeQuery(query, view, columns);
    }

    private ClientValue executeQuery(String query, String view,
        String[] columns) throws RepositoryException {
      String[] fields;
      Object[][] values;
      try {
          Statement stmt = jdbcConnection.createStatement();
          try {
              ResultSet rs =
                  stmt.executeQuery(getSqlQuery(query, view, columns));
              ResultSetMetaData rsmd = rs.getMetaData();
              fields = getResultSetColumns(rsmd, columns);
              values = getResultSetValues(rs, rsmd);
          } finally {
              stmt.close();
          }
      } catch (SQLException e) {
          throw new RepositoryException("Database error", e);
      }
      return new MockClientValue(fields, values);
    }

    /** Extracts field names from any select expressions. */
    private String[] getRecArrayFieldNames(String[] columns) {
      String[] fields = new String[columns.length];
      for (int i = 0; i < columns.length; i++) {
        String[] tokens = columns[i].split("\\W+");
        // In a select expression, the name of the field is at the end.
        // Otherwise, the single token is the original field name.
        fields[i] = tokens[tokens.length - 1];
      }
      return fields;
    }

    /**
     * Constructs a SQL query string from the {@code ListNodes}
     * arguments. The constructed string <strong>must</strong> match the
     * string constructed by LAPI.
     */
    private String getSqlQuery(String query, String view, String[] columns) {
        assert columns != null && columns.length > 0;

        StringBuilder buffer = new StringBuilder();
        buffer.append("select ");
        for (String column : columns) {
            buffer.append(column);
            buffer.append(',');
        }
        buffer.deleteCharAt(buffer.length() - 1);
        buffer.append(" from ");
        buffer.append(view);
        buffer.append(" a where ");
        buffer.append(query);
        return buffer.toString();
    }

    /** Gets the array of column names from the result set metadata. */
    private String[] getResultSetColumns(ResultSetMetaData rsmd,
        String[] origColumns) throws SQLException {
      int count = rsmd.getColumnCount();
      String[] columns = new String[count];
      // This is a bit hacky, but all our select expressions have an alias.
      for (int i = 0; i < count; i++) {
        // Correct for the uppercasing of column names in H2. Note that
        // H2 follows the JDBC 4.0 madness that the SQL column names are
        // returned by getColumnLabel instead of getColumnName.
        String columnName = rsmd.getColumnLabel(i + 1);
        for (String origName : origColumns) {
          // Extract the column name or alias from the select expression.
          int index = origName.lastIndexOf(' ');
          String alias =
              (index == -1) ? origName : origName.substring(index + 1);

          if (alias.equalsIgnoreCase(columnName)) {
            columns[i] = alias;
            break;
          }
        }
      }
      return columns;
    }

    /** Gets the array of row values from the result set. */
    private Object[][] getResultSetValues(ResultSet rs, ResultSetMetaData rsmd)
            throws SQLException {
        ArrayList<Object[]> rows = new ArrayList<Object[]>();
        int count = rsmd.getColumnCount();
        while (rs.next()) {
            Object[] row = new Object[count];
            for (int i = 0; i < count; i++) {
                row[i] = rs.getObject(i + 1);
            }
            rows.add(row);
        }
        return rows.toArray(new Object[0][0]);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Version of ListNodes() that does not throw exceptions.
     */
    @Override
    public ClientValue ListNodesNoThrow(String query, String view,
            String[] columns) {
        try {
            return ListNodes(query, view, columns);
        } catch (RepositoryException e) {
            return null;
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation returns an empty assoc.
     */
    @Override
    public ClientValue GetObjectInfo(int volumeId, int objectId) {
        return new MockClientValue(new String[0], new Object[0]);
    }

    /** {@inheritDoc} */
    @Override
    public ClientValue GetObjectAttributesEx(ClientValue objectIdAssoc,
            ClientValue categoryIdAssoc) throws RepositoryException {
        try {
            return new MockClientValue(new String[0], new Object[0]);
        } catch (RuntimeException e) {
            throw new LivelinkException(e, LOGGER);
        }
    }

    /** {@inheritDoc} */
    @Override
    public ClientValue AttrListNames(ClientValue categoryVersion,
            ClientValue attributeSetPath) throws RepositoryException {
        try {
            return new MockClientValue(new Object[0]);
        } catch (RuntimeException e) {
            throw new LivelinkException(e, LOGGER);
        }
    }

    /** {@inheritDoc} */
    @Override
    public ClientValue AttrGetInfo(ClientValue categoryVersion,
            String attributeName, ClientValue attributeSetPath)
            throws RepositoryException {
        try {
            return new MockClientValue(new String[0], new Object[0]);
        } catch (RuntimeException e) {
            throw new LivelinkException(e, LOGGER);
        }
    }

    /** {@inheritDoc} */
    @Override
    public ClientValue AttrGetValues(ClientValue categoryVersion,
            String attributeName, ClientValue attributeSetPath)
            throws RepositoryException {
        try {
            return new MockClientValue(new Object[0]);
        } catch (RuntimeException e) {
            throw new LivelinkException(e, LOGGER);
        }
    }

    /** {@inheritDoc} */
    @Override
    public ClientValue ListObjectCategoryIDs(ClientValue objectIdAssoc)
            throws RepositoryException {
        try {
            return new MockClientValue(new Object[0]);
        } catch (RuntimeException e) {
            throw new LivelinkException(e, LOGGER);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation simulates problems accessing content for
     * some "special" docids; otherwise it does nothing.
     */
    @Override
    public void FetchVersion(int volumeId, int objectId, int versionNumber,
            File path) throws RepositoryException {
        LOGGER.fine("Entering MockClient.FetchVersion");
        // TODO: Make sure that the file exists and is empty.

        throwFetchVersionException(objectId);
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation simulates problems accessing content for
     * some "special" docids, otherwise it simply closes the output stream.
     */
    @Override
    public void FetchVersion(int volumeId, int objectId, int versionNumber,
            OutputStream out) throws RepositoryException {
        LOGGER.fine("Entering MockClient.FetchVersion");
        throwFetchVersionException(objectId);
        try {
            out.close();
        } catch (IOException e) {
            throw new LivelinkException(e, LOGGER);
        }
    }

  /**
   * Simulates throwing an exception for some "special" docids.
   * Otherwise it does nothing.
   */
  private void throwFetchVersionException(int objectId)
      throws RepositoryException {
    if (objectId == MockConstants.DOCUMENT_OBJECT_ID) {
      throw new RepositoryDocumentException(new RuntimeException(
              "Simulated Premature end-of-data on socket"));
    } else if (objectId == MockConstants.IO_OBJECT_ID) {
      throw new LivelinkIOException(new RuntimeException(
              "Simulated Server did not accept open request"), LOGGER);
    } else if (objectId == MockConstants.REPOSITORY_OBJECT_ID) {
      throw new LivelinkException("This document version is not yet "
          + "available.  It will be uploaded from a remote location "
          + "at a later time. Please try again later.", LOGGER);
    }
  }

    /** {@inheritDoc} */
    @Override
    public ClientValue GetVersionInfo(int volumeId, int objectId,
            int versionNumber) throws RepositoryException {
        LOGGER.fine("Entering MockClient.GetVersionInfo");
        return null; // FIXME; stub implementation to get it to compile
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation has no effect.
     */
    @Override
    public void ImpersonateUser(String username) throws RepositoryException {
        LOGGER.fine("Entering MockClient.ImpersonateUser");
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation has no effect.
     */
    @Override
    public void ImpersonateUserEx(String username, String domain)
        throws RepositoryException {
        LOGGER.fine("Entering MockClient.ImpersonateUserEx");
    }

  @Override
  public ClientValue GetObjectRights(int objectId) throws RepositoryException {
    String query = "DataID=" + objectId;
    String view = "DTreeACL";
    String[] columns = new String[] {"DataID as ID", "RightID", "Permissions"};
    return executeQuery(query, view, columns);
  }
}
