// Copyright 2010 Google Inc.
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

import com.google.enterprise.connector.otex.client.Client;
import com.google.enterprise.connector.otex.client.ClientValue;
import com.google.enterprise.connector.otex.client.mock.MockClientFactory;
import com.google.enterprise.connector.otex.client.mock.MockClientValue;
import com.google.enterprise.connector.spi.RepositoryException;

import junit.framework.TestCase;

import org.h2.jdbcx.JdbcDataSource;

import java.sql.Connection;
import java.sql.Statement;
import java.sql.SQLException;

/**
 * Constructs a mock hierarchy by overriding the getParent and
 * getParents methods and tests each Genealogist implementation.
 */
public class GenealogistTest extends TestCase {
  /* The DTree nodes. */
  private static final int[][] TREE_NODES = {
    { 1, -1 }, { 2, -1 }, { 3, -1 },
    { 10, 1 }, { 20, 2 }, { 30, 3 }, { 31, 3 },
    { 100, 10 }, { 101, 10 },
    { 200, 20 },
    { 300, 30 }, { 301, 30 }, { 310, 31 },
    { 1000, 100 }, { 1001, 100 }, { 1010, 101 },
    { 2000, 200 }, { 2001, 200 }, { 2002, 200 },
    { 3000, 300 }, { 3010, 301 }, { 3100, 310 },
    { 10100, 1010 } };

  /* The includedLocationNodes property value. */
  private static final String INCLUDED_NODES = "10";

  /* The excludedLocationNodes property value. */
  private static final String EXCLUDED_NODES = "2";

  /* The nodes to call getMatchingDescendants with. */
  private static final Integer[][] MATCHING_NODES = {
    { 1000 }, { 1001 }, { 1010 }, { 2000 }, { 2001 }, { 2002 },
    { 3000 }, { 3010 }, { 3100 }, {10100 }, };

  /** The mock client used by the Genealogist classes under test. */
  private Client client;

  protected void setUp() {
    // Get a separate in-memory database for each test.
    JdbcDataSource ds = new JdbcDataSource();
    ds.setURL("jdbc:h2:mem:");
    ds.setUser("sa");
    ds.setPassword("");
    try {
      Connection conn = ds.getConnection();

      // Insert the test data.
      Statement stmt = conn.createStatement();
      stmt.executeUpdate(
          "create table DTree (DataID int primary key, ParentID int)");
      for (int[] row : TREE_NODES) {
        stmt.executeUpdate("insert into DTree(DataID, ParentID) values ("
            + row[0] + "," + row[1] + ")");
      }

      MockClientFactory factory = new MockClientFactory(conn);
      client = factory.createClient();
    } catch (SQLException e) {
      throw new RuntimeException("MockClient initialization error", e);
    }
  }

  /** Helper method that runs the test for a given implementation. */
  private void testGenealogist(Genealogist gen) throws RepositoryException {
    MockClientValue matching =
        new MockClientValue(new String[] { "DataID" }, MATCHING_NODES);
    Object desc = gen.getMatchingDescendants(matching);
    assertEquals("1000,1001,1010,10100", desc);
  }

  public void testBase() throws RepositoryException {
    testGenealogist(
        new Genealogist(client, INCLUDED_NODES, EXCLUDED_NODES, 10));
  }

  public void testHybrid() throws RepositoryException {
    testGenealogist(
        new HybridGenealogist(client, INCLUDED_NODES, EXCLUDED_NODES, 10));
  }

  public void testBatch() throws RepositoryException {
    testGenealogist(
        new BatchGenealogist(client, INCLUDED_NODES, EXCLUDED_NODES, 10));
  }
}
