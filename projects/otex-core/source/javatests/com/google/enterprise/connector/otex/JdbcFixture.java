// Copyright 2011 Google Inc.
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

import static com.google.enterprise.connector.otex.IdentityUtils.LOGIN_MASK;

import com.google.enterprise.connector.otex.client.Client;
import com.google.enterprise.connector.otex.client.mock.MockClient;
import org.junit.After;
import org.junit.Before;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/** Manages an in-memory H2 database modeling the Livelink database. */
class JdbcFixture {
  private static final String CREATE_TABLE_DAUDITNEW = "create table DAuditNew "
      + "(EventID bigint primary key, AuditID int, DataID int, "
      + "SubType int, AuditDate timestamp)";

  private static final String CREATE_TABLE_DTREE = "create table DTree "
      + "(DataID int primary key, ParentID int, PermID int, "
      + "SubType int, ModifyDate timestamp, Name varchar, "
      + "DComment varchar, CreateDate timestamp, CreatedBy int, "
      + "OwnerID int, UserID int, Catalog int default 0)";

  private static final String CREATE_TABLE_DTREEACL =
      "create table DTreeACL (DataID int, RightID int, "
          + "Name varchar, Permissions int, See int)";

  private static final String CREATE_TABLE_DTREEANCESTORS =
      "create table DTreeAncestors (DataID int, AncestorID int)";

  private static final String CREATE_TABLE_KDUAL =
      "create table KDual (dummy int primary key)";

  private static final String CREATE_TABLE_KUAF =
      "create table KUAF "
      + "(ID int, Name varchar, Type int, GroupID int, UserData varchar," +
      " UserPrivileges int, Deleted int default 0)";

  private static final String CREATE_TABLE_KUAFCHILDREN =
      "create table KUAFChildren "
      + "(ID int, ChildID int)";

  // TODO(jlacey): Turn this into a joined view on DTree and DVersData.
  private static final String CREATE_TABLE_WEBNODES =
      "create table WebNodes "
      + "(DataID int primary key, ParentID int, PermID int, "
      + "SubType int, ModifyDate timestamp, Name varchar, "
      + "DComment varchar, CreateDate timestamp, OwnerName varchar, "
      + "OwnerID int, UserID int, Catalog int default 0, "
      + "MimeType varchar, DataSize bigint)";

  /** The database connection. */
  private Connection jdbcConnection;

  /** Initializes a database connection. */
  @Before
  public void setUp() throws SQLException {
    // Get a named in-memory database for sharing across connections.
    // We delete all objects from the database for each test in tearDown.
    jdbcConnection = MockClient.getConnection();

    executeUpdate(
        CREATE_TABLE_DAUDITNEW,
        CREATE_TABLE_DTREE,
        CREATE_TABLE_DTREEACL,
        CREATE_TABLE_DTREEANCESTORS,
        CREATE_TABLE_KDUAL,
        CREATE_TABLE_KUAF,
        CREATE_TABLE_KUAFCHILDREN,
        CREATE_TABLE_WEBNODES);

    executeUpdate(
        "insert into KUAF(ID, Name, Type, GroupID, UserData, UserPrivileges) "
            + " values(1000, 'Admin', 0, 2001, NULL,"
            + (Client.PRIV_PERM_BYPASS | LOGIN_MASK) + ")");
  }

  @After
  public void tearDown() throws SQLException {
    try {
      executeUpdate("drop all objects");
    } finally {
      Connection tmp = jdbcConnection;
      jdbcConnection = null; // In case this object is reused.
      tmp.close();
    }
  }

  public Connection getConnection() throws SQLException {
    return jdbcConnection;
  }

  public void executeUpdate(String... sqls) throws SQLException {
    Statement stmt = jdbcConnection.createStatement();
    try {
      for (String sql : sqls) {
        stmt.executeUpdate(sql);
      }
    } finally {
      stmt.close();
    }
  }

  protected void addUser(int userId, String name) throws SQLException {
    // No special privileges to user, just log-in enabled.
    addUser(userId, name, LOGIN_MASK);
  }

  protected void addUser(int userId, String name, int privileges)
      throws SQLException {
    executeUpdate("insert into KUAF(ID, Name, Type, UserData, UserPrivileges) "
        + "values(" + userId + ",'" + name + "', 0, NULL, " + privileges + ")");
  }

  protected void addGroup(int userId, String name) throws SQLException {
    // no privileges to the group
    executeUpdate("insert into KUAF(ID, Name, Type, UserData, UserPrivileges) "
        + "values(" + userId + ",'" + name + "' ,1 , NULL, 0)");
  }

  protected void addGroupMembers(int groupId, int... userIds)
      throws SQLException {
    for (int i = 0; i < userIds.length; i++) {
      executeUpdate("insert into KUAFChildren(ID, ChildID) "
          + "values(" + groupId + ", " + userIds[i] + ")");
    }
  }

  protected void setUserData(int userId, String userData) throws SQLException {
    String value = (userData == null) ? "NULL" : "'" + userData + "'";
    executeUpdate("UPDATE KUAF SET UserData = " + value
        + " where ID = " + userId);
  }

  protected void deleteUser(int userId) throws SQLException {
    executeUpdate("update KUAF set "
        + "Name = Name || ' (Delete) ' || ID, Deleted = 1, UserPrivileges = 0 "
        + "where ID = " + userId);
  }

  protected void deleteGroup(int groupId) throws SQLException {
    executeUpdate("update KUAF set "
        + "Name = Name || ' (Delete) ' || ID, Deleted = 1 "
        + "where ID = " + groupId);
  }
}
