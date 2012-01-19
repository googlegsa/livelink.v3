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

import org.h2.jdbcx.JdbcDataSource;

import java.sql.Connection;
import java.sql.Statement;
import java.sql.SQLException;

/** Manages an in-memory H2 database modeling the Livelink database. */
class JdbcFixture {
  public static final String CREATE_TABLE_DTREE = "create table DTree "
      + "(DataID int primary key, ParentID int, PermID int, "
      + "SubType int, ModifyDate timestamp)";

  public static final String CREATE_TABLE_DTREEANCESTORS =
      "create table DTreeAncestors (DataID int, AncestorID int)";

  public static final String CREATE_TABLE_KDUAL =
      "create table KDual (dummy int primary key)";

  public static final String CREATE_TABLE_WEBNODES =
      "create table WebNodes "
      + "(DataID int primary key, ParentID int, PermID int, "
      + "SubType int, ModifyDate timestamp, MimeType varchar)";

  /** The database connection. */
  private Connection jdbcConnection;

  /** Initializes a database connection. */
  public void setUp() throws SQLException {
    // Get a separate in-memory database for each test.
    JdbcDataSource ds = new JdbcDataSource();
    ds.setURL("jdbc:h2:mem:");
    ds.setUser("sa");
    ds.setPassword("");
    jdbcConnection = ds.getConnection();
  }

  public void tearDown() throws SQLException {
    Connection tmp = jdbcConnection;
    jdbcConnection = null; // In case this object is reused.
    tmp.close();
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
