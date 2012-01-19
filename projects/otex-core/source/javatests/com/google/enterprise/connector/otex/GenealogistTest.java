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

import java.util.Arrays;
import java.sql.SQLException;

/**
 * Constructs a mock database hierarchy and runs the tests using the
 * {@code Genealogist} implementation. Subclasses override
 * {@code getClassUnderTest} to run these tests with subclass
 * implementations.
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

  /** The mock client used by the Genealogist classes under test. */
  private Client client;

  /** The database connection. */
  private JdbcFixture jdbcFixture = new JdbcFixture();

  /** Inserts database test data. */
  protected void setUp() throws SQLException {
    // Get a separate in-memory database for each test.
    jdbcFixture.setUp();

    // Insert the test data.
    jdbcFixture.executeUpdate(JdbcFixture.CREATE_TABLE_DTREE);
    insertRows(TREE_NODES);

    MockClientFactory factory =
        new MockClientFactory(jdbcFixture.getConnection());
    client = factory.createClient();
  }

  private void insertRows(int[][] rows) throws SQLException {
    String[] sqls = new String[rows.length];
    int i = 0;
    for (int[] row : rows) {
      sqls[i++] = "insert into DTree(DataID, ParentID) values ("
          + row[0] + "," + row[1] + ")";
    }
    jdbcFixture.executeUpdate(sqls);
  }

  protected void tearDown() throws SQLException {
    jdbcFixture.tearDown();
  }

  /** Gets the class under test. Subclasses should override this method. */
  protected Class getClassUnderTest() {
    return Genealogist.class;
  }

  /** Core helper method that runs a test for a given implementation. */
  private void testGenealogist(String includedNodes, String excludedNodes,
      Integer[] matchingNodes, String matchingDescendants)
      throws RepositoryException {
    Genealogist genealogist = Genealogist.getGenealogist(
        getClassUnderTest().getName(),
        client, includedNodes, excludedNodes, 10, 10);
    testMatching(genealogist, matchingNodes, matchingDescendants);
  }

  /** Helper method to test matching nodes. */
  private void testMatching(Genealogist genealogist,
      Integer[] matchingNodes, String matchingDescendants)
      throws RepositoryException {
    Integer[][] matchingValues = new Integer[matchingNodes.length][];
    for (int i = 0; i < matchingNodes.length; i++) {
      matchingValues[i] = new Integer[] { matchingNodes[i] };
    }
    MockClientValue matching =
        new MockClientValue(new String[] { "DataID" }, matchingValues);

    Object desc = genealogist.getMatchingDescendants(matching);
    assertEquals(matchingDescendants, desc);
  }

  /** Helper method to test basic trees. */
  private void testBasic(String includedNodes, String excludedNodes,
      String matchingDescendants) throws RepositoryException {
    testGenealogist(includedNodes, excludedNodes, new Integer[] {
        1000, 1001, 1010, 2000, 2001, 2002, 3000, 3010, 3100, 10100, },
        matchingDescendants);
  }

  /** Tests disjoint included and excluded nodes. */
  public void testEverything() throws RepositoryException {
    testBasic("", "", "1000,1001,1010,2000,2001,2002,3000,3010,3100,10100");
  }

  /** Tests disjoint included and excluded nodes. */
  public void testIncluded() throws RepositoryException {
    testBasic("10", "2", "1000,1001,1010,10100");
  }

  /** Tests excluded nodes. */
  public void testExcluded() throws RepositoryException {
    testBasic("", "2,3", "1000,1001,1010,10100");
  }

  /** Tests included nodes with nested excluded nodes. */
  public void testIncludedExcluded()
      throws RepositoryException {
    testBasic("1", "101", "1000,1001");
  }

  /** Tests excluded nodes with nested included nodes. */
  public void testExcludedIncluded()
      throws RepositoryException {
    testBasic("101", "1", "1010,10100");
  }

  /** Helper method to test stepparent nodes (that is, volume nodes). */
  private void testStepparents(String includedNodes, String excludedNodes,
      String matchingDescendants) throws SQLException, RepositoryException {
    insertRows(new int[][] {
        { 4, -1 }, { 40, 4 }, { -40, -1 }, { 400, -40 }, { 4000, 400 } });
    testGenealogist(includedNodes, excludedNodes, new Integer[] {
        4, 40, 400, 1000, 1001, 1010, 4000, 10100, },
        matchingDescendants);
  }

  /** Tests included nodes with stepparents. */
  public void testIncludedWithStepparents()
      throws SQLException, RepositoryException {
    testStepparents("4", "", "4,40,400,4000");
  }

  /** Tests included stepparents. */
  public void testIncludedStepparents()
      throws SQLException, RepositoryException {
    testStepparents("40", "", "40,400,4000");
  }

  /** Tests excluded stepparents. */
  public void testExcludedStepparents()
      throws SQLException, RepositoryException {
    testStepparents("", "1,2,3,4", null);
  }

  /** Tests included nodes with excluded stepparents. */
  public void testIncludedExcludedStepparents()
      throws SQLException, RepositoryException {
    testStepparents("4", "40", "4");
  }

  /** Tests included nodes with excluded volume. */
  public void testExcludedVolumeStepparents()
      throws SQLException, RepositoryException {
    testStepparents("4", "-40", "4");
  }

  /** Tests included nodes with excluded volume descendants. */
  public void testExcludedStepchildren()
      throws SQLException, RepositoryException {
    testStepparents("4", "400", "4,40");
  }

  /** Tests orphan nodes. */
  public void testOrphans()
      throws SQLException, RepositoryException {
    // We include two orphaned nodes here because doing so found a
    // ConcurrentModificationException bug during development.
    insertRows(new int[][] {
          { 4, -1 }, { 400, 40 }, { 4000, 400 },
          { 5, -1 }, { 500, 50 }, { 5000, 500 } });
    testGenealogist("", "2,3", new Integer[] {
        1000, 1001, 1010, 2000, 2001, 2002, 3000, 3010, 4000, 5000, 10100 },
        "1000,1001,1010,10100");
  }

  /** Tests orphan nodes as the deepest nodes in the batch. */
  public void testDeepestOrphans()
      throws SQLException, RepositoryException {
    // We include two orphaned nodes here because doing so found a
    // ConcurrentModificationException bug during development.
    insertRows(new int[][] {
          { 4, -1 }, { 400, 40 }, { 4000, 400 },
          { 5, -1 }, { 500, 50 }, { 5000, 500 } });

    // It is critical here that the orphans be at least as deep
    // (relative to the missing nodes) as the other matching nodes
    // (relative to the included/excluded nodes).
    testGenealogist("10", "20,30", new Integer[] {
        1000, 1001, 1010, 2000, 2001, 2002, 3000, 3010, 4000, 5000 },
        "1000,1001,1010");
  }

  /** Test the caching of found ancestor nodes. */
  public void testCaching()
      throws SQLException, RepositoryException {
    Genealogist genealogist = Genealogist.getGenealogist(
        getClassUnderTest().getName(), client, "1", "", 10, 10);

    assertEquals(0, genealogist.nodeCount);
    assertEquals(0, genealogist.queryCount);

    // Seed the cache with a small tree of known parents.
    genealogist.includedCache.addAll(
        Arrays.asList(new Integer[] { 1, 10, 100, 101 }));
    assertEquals("4 entries, 0 hits, 0 misses",
                 genealogist.includedCache.statistics().toString());

    // Test the grandchild of a cached node. One new parent entry, 1010,
    // should be added.
    testMatching(genealogist, new Integer[] { 10100 }, "10100");
    assertEquals(1, genealogist.nodeCount);
    assertEquals(2, genealogist.queryCount);
    assertEquals("5 entries, 1 hits, 2 misses",
                 genealogist.includedCache.statistics().toString());
    assertTrue(genealogist.includedCache.contains(new Integer(1010)));

    // Test nodes that should be fully cached. They should all hit,
    // so no new queries should run. The number of hits should go up,
    // while the number of entries and misses should remain the same.
    testMatching(genealogist, new Integer[] { 1, 10, 100, 101, 1010 },
                 "1,10,100,101,1010");
    assertEquals(6, genealogist.nodeCount);
    assertEquals(2, genealogist.queryCount);
    assertEquals("5 entries, 7 hits, 2 misses",
                 genealogist.includedCache.statistics().toString());
  }
}
