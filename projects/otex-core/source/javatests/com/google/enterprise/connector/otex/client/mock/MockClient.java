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

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.logging.Logger;

import com.google.enterprise.connector.spi.RepositoryDocumentException;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.otex.LivelinkException;
import com.google.enterprise.connector.otex.LivelinkIOException;
import com.google.enterprise.connector.otex.client.Client;
import com.google.enterprise.connector.otex.client.ClientValue;
import com.google.enterprise.connector.otex.client.ClientValueFactory;

/**
 * A mock client implementation.
 */
public class MockClient implements Client {
    /** The logger for this class. */
    private static final Logger LOGGER =
        Logger.getLogger(MockClient.class.getName());

    private final Connection jdbcConnection;

    public MockClient() {
      this(null);
    }

    public MockClient(Connection jdbcConnection) {
      this.jdbcConnection = jdbcConnection;
    }

    /** {@inheritDoc} */
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
    public int GetCurrentUserID() throws RepositoryException {
        return 1000;
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation returns an LLCookie with an invalid value.
     */
    public ClientValue GetCookieInfo() throws RepositoryException {
      ClientValue llcookie = new MockClientValue(
          new String[] { "Name", "Value" },
          new Object[] { "LLCookie", "llcookie value goes here" });
      return new MockClientValue(new Object[] { llcookie });
    }

    /** {@inheritDoc} */
    public ClientValue GetUserOrGroupByIDNoThrow(int id)
            throws RepositoryException {
        return new MockClientValue(
            new String[] { "Name" }, new String[] { "Admin" });
    }

    /** {@inheritDoc} */
    public ClientValue GetUserInfo(String username)
            throws RepositoryException {
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
    public ClientValue AccessEnterpriseWS() throws RepositoryException {
        return new MockClientValue(new String[] { "ID", "VolumeID" },
            new Integer[] { new Integer(2000), new Integer(-2000) });
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation returns an empty <code>ClientValue</code>.
     */
    public ClientValue ListNodes(String query, String view, String[] columns)
            throws RepositoryException {
        LOGGER.fine("Entering MockClient.ListNodes");

        String[] fields;
        Object[][] values;
        if (query.startsWith("SubType =") && view.equals("DTree")) {
            // This is the authZ excluded volume types query.
            fields = new String[] { "DataID", "PermID" };

            // Some of the tests ask for invalid subtypes, namely 666,
            // or invalid volume types, namely 2001 and 4104. Those
            // queries needs to return no values. Other than that,
            // 9999 should be returned.
            if (query.indexOf("2001,4104") != -1
                    || query.indexOf("666") != -1) {
                values = new Object[0][0];
            } else {
                values = new Object[][] {
                    new Object[] { new Integer(9999), new Integer(0) } };
            }
        } else if (view.equals("DTreeAncestors")) {
            // This is the check for an empty DTreeAncestors table.
            // The returned value doesn't matter.
            fields = new String[] { "DataID" };
            values = new Object[][] {
                new Object[] { new Integer(4104) } };
        } else if (columns.length == 1 &&
                columns[0].endsWith("minModifyDate")) {
            // This is the check for missing entries in the
            // DTreeAncestors table, and the optimized startDate. The
            // returned value doesn't matter.
            fields = new String[] { "minModifyDate" };
            values = new Object[][] {
                new Object[] { new Date() } };
        } else if (query.startsWith("DataID = ") && view.equals("WebNodes")) {
            // This is the Retriever fetching meta-data.
            fields = getRecArrayFieldNames(columns);
            int objectId =
                Integer.parseInt(query.substring("DataID = ".length()));
            // TODO(johnsonb): It seems like if these are going to
            // throw exceptions or return no data(?) from ListNodes,
            // that you need different MockConstants to distinguish
            // these from object IDs that throw exceptions from
            // FetchVersion.
            if (objectId == MockConstants.IO_OBJECT_ID) {
              throw new LivelinkIOException(new RuntimeException(
                   "Simulated Server did not accept open request"), LOGGER);
            } else if (objectId == MockConstants.DOCUMENT_OBJECT_ID) {
              // No rows.
              values = new Object[0][0];
            } else {
              values = new Object[][] {
                new Object[] { new Integer(objectId), new Integer(0),
                               new Date(), new String("text/html"),
                               new Integer(200), new Integer(100) } };
            }
        } else if ((columns.length == 1 && columns[0].equals("ParentID")) ||
                (columns.length == 3 &&
                    columns[2].indexOf("ParentID <> -1") != -1)) {
            // This is a Genealogist query. Use the database.
            try {
                Statement stmt = jdbcConnection.createStatement();
                ResultSet rs =
                    stmt.executeQuery(getSqlQuery(query, view, columns));
                ResultSetMetaData rsmd = rs.getMetaData();
                fields = getResultSetColumns(rsmd);
                values = getResultSetValues(rs, rsmd);
            } catch (SQLException e) {
                throw new RepositoryException("Database error", e);
            }
        } else {
            fields = new String[0];
            values = new Object[0][0];
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
    private String[] getResultSetColumns(ResultSetMetaData rsmd)
            throws SQLException {
        int count = rsmd.getColumnCount();
        String[] columns = new String[count];
        for (int i = 0; i < count; i++) {
            // Correct for the uppercasing of column names in H2.
            columns[i] = rsmd.getColumnName(i + 1)
                .replace("DATA", "Data")
                .replace("STEP", "Step")
                .replace("PARENT", "Parent");
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
    public ClientValue GetObjectInfo(int volumeId, int objectId) {
        return new MockClientValue(new String[0], new Object[0]);
    }

    /** {@inheritDoc} */
    public ClientValue GetObjectAttributesEx(ClientValue objectIdAssoc,
            ClientValue categoryIdAssoc) throws RepositoryException {
        try {
            return new MockClientValue(new String[0], new Object[0]);
        } catch (RuntimeException e) {
            throw new LivelinkException(e, LOGGER);
        }
    }

    /** {@inheritDoc} */
    public ClientValue AttrListNames(ClientValue categoryVersion,
            ClientValue attributeSetPath) throws RepositoryException {
        try {
            return new MockClientValue(new Object[0]);
        } catch (RuntimeException e) {
            throw new LivelinkException(e, LOGGER);
        }
    }

    /** {@inheritDoc} */
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
    public void ImpersonateUser(String username) throws RepositoryException {
        LOGGER.fine("Entering MockClient.ImpersonateUser");
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation has no effect.
     */
    public void ImpersonateUserEx(String username, String domain)
        throws RepositoryException {
        LOGGER.fine("Entering MockClient.ImpersonateUserEx");
    }
}
