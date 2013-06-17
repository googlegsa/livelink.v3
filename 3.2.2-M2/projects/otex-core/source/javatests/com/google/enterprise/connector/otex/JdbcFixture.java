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

import com.google.enterprise.connector.otex.client.mock.MockClient;

import org.h2.jdbcx.JdbcDataSource;

import java.sql.Connection;
import java.sql.Statement;
import java.sql.SQLException;

/** Manages an in-memory H2 database modeling the Livelink database. */
class JdbcFixture {
  private static final String CREATE_TABLE_DAUDITNEW = "create table DAuditNew "
      + "(EventID int primary key, AuditID int, DataID int, "
      + "SubType int, AuditDate timestamp)";

  private static final String CREATE_TABLE_DTREE = "create table DTree "
      + "(DataID int primary key, ParentID int, PermID int, "
      + "SubType int, ModifyDate timestamp, Name varchar, "
      + "DComment varchar, CreateDate timestamp, CreatedBy int, "
      + "OwnerID int, UserID int, Catalog int default 0)";

  private static final String CREATE_TABLE_DTREEANCESTORS =
      "create table DTreeAncestors (DataID int, AncestorID int)";

  private static final String CREATE_TABLE_KDUAL =
      "create table KDual (dummy int primary key)";

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
  protected void setUp() throws SQLException {
    // Get a named in-memory database for sharing across connections.
    // We delete all objects from the database for each test in tearDown.
    jdbcConnection = MockClient.getConnection();

    executeUpdate(
        CREATE_TABLE_DAUDITNEW,
        CREATE_TABLE_DTREE,
        CREATE_TABLE_DTREEANCESTORS,
        CREATE_TABLE_KDUAL,
        CREATE_TABLE_WEBNODES);
  }

  protected void tearDown() throws SQLException {
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
}
