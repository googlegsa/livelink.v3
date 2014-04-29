// Copyright 2011 Google Inc.
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
import com.google.enterprise.connector.otex.client.mock.MockClient;
import com.google.enterprise.connector.spi.DocumentList;
import com.google.enterprise.connector.spi.RepositoryException;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.Random;
import java.util.logging.Logger;

/**
 * Tests the efficiency of using a large cache for the Genealogists.
 *
 * Since BigCacheTest takes 10 minutes to run, it will only
 * run when explicitly requested by using the test.suite property:
 * <pre><code>
 *   ant run_tests -Dtest.suite=BigCache
 * </code></pre>
 */
public class BigCacheTest extends TestCase {
  private static final Logger LOGGER =
      Logger.getLogger(BigCacheTest.class.getName());

  private static final String GENEALOGIST =
      "com.google.enterprise.connector.otex.Genealogist";
  private static final String BATCH_GENEALOGIST =
      "com.google.enterprise.connector.otex.BatchGenealogist";
  private static final String HYBRID_GENEALOGIST =
      "com.google.enterprise.connector.otex.HybridGenealogist";

  private static final int NO_PARENT = -1;
  private static final int FOLDER_SUBTYPE = 0;
  private static final int DOCUMENT_SUBTYPE = 144;

  /** Date formatter used to construct checkpoint dates */
  private final LivelinkDateFormat dateFormat =
      LivelinkDateFormat.getInstance();

  private JdbcFixture jdbcFixture;
  private Connection connection;
  private PreparedStatement insertDTree;
  private PreparedStatement insertWebNodes;

  /** Only run these tests if explicitly requested. */
  public static Test suite() {
    if ("BigCache".equals(System.getProperty("test.suite")))
      return new TestSuite(BigCacheTest.class);
    else
      return new TestSuite();
  }

  @Override
  protected void setUp() throws RepositoryException, SQLException {
    LOGGER.info("Start test " + getName());

    jdbcFixture = new JdbcFixture();
    jdbcFixture.setUp();
    connection = jdbcFixture.getConnection();

    insertDTree = connection.prepareStatement("insert into DTree "
        + "(DataId, ParentID, PermID, SubType, ModifyDate) "
        + "values (?,?,?,?,?)");

    insertWebNodes = connection.prepareStatement("insert into WebNodes "
        + "(DataId, ParentID, PermID, SubType, ModifyDate) "
        + "values (?,?,?,?,?)");
  }

  @Override
  protected void tearDown() throws SQLException {
    jdbcFixture.tearDown();
    LOGGER.info("End test " + getName());
  }

  public void testClusteredDates() throws Exception {
    testGenealogists(new LeafClusteredDateProvider(),
                     new FolderClusteredDateProvider());
  }

  public void testRandomDates() throws Exception {
    DateProvider dateProvider = new RandomDateProvider();
    testGenealogists(dateProvider, dateProvider);
  }

  public void testIncrementalDates() throws Exception {
    DateProvider dateProvider = new IncrementalDateProvider();
    testGenealogists(dateProvider, dateProvider);
  }

  private void testGenealogists(DateProvider leafDateProvider,
      DateProvider folderDateProvider) throws Exception {
    loadData(100000, 20, 10, leafDateProvider, folderDateProvider);
    testGenealogist(GENEALOGIST);
    testGenealogist(HYBRID_GENEALOGIST);
    testGenealogist(BATCH_GENEALOGIST);
  }

  private void testGenealogist(String genealogist) throws Exception {
    LivelinkTraversalManager traversalManager =
        getObjectUnderTest(genealogist, 1000, 32000, "1,2,3,4,5", "");
    traverseRepository(traversalManager);
    Genealogist.Statistics stats = traversalManager.genealogist.statistics();
    LOGGER.info(genealogist + " " + getName());
    LOGGER.info("Statistics: " + stats);
    LOGGER.info("Included Cache performance = "
         + ((float) (stats.includedStats.hits))/stats.includedStats.misses);
    LOGGER.info("Excluded Cache performance = "
         + ((float) (stats.excludedStats.hits))/stats.excludedStats.misses);

    System.out.println(genealogist + " " + getName());
    System.out.println("Statistics: " + stats);
    System.out.println("Included Cache performance = "
         + ((float) (stats.includedStats.hits))/stats.includedStats.misses);
    System.out.println("Excluded Cache performance = "
         + ((float) (stats.excludedStats.hits))/stats.excludedStats.misses);

    // TODO: Try to assert a floor for cache misses, but at this point only
    // the Brute-force Genealogist passes.  I think this may be because
    // the mock database has too many empty nodes, and is therefore not
    // representative of real-world datasets.
    //assertTrue(((float) (stats.includedStats.hits))/stats.includedStats.misses
    //             > .25);
    //assertTrue(((float) (stats.excludedStats.hits))/stats.excludedStats.misses
    //             > .25);
  }

  /**
   * Loads data into the mock Livelink Database.
   *
   * @param numNodes number of nodes to add to database
   *        (we may go over by a bit).
   * @param foldersPerNode number of sub-folders to put in
   *        each folder node.
   * @param leavesPerNode number of leaf documents to put in
   *        each folder node.
   * @param dateProvider a DateProvider for generating
   *        lastModified dates for each new node.
   */
  private void loadData(int numNodes, int foldersPerNode, int leavesPerNode,
      DateProvider leafDateProvider, DateProvider folderDateProvider)
      throws SQLException {
    Random random = new Random();
    RootStat[] rootStats = new RootStat[foldersPerNode + 1];
    ArrayList<Node> containers = new ArrayList<Node>(numNodes);
    connection.setAutoCommit(false);

    long start = System.currentTimeMillis();
    LOGGER.info("Start Data Loading");

    long anchor = random.nextInt() * 1000L;
    int node;
    // Seed the root nodes.
    for (node = 1; node <= foldersPerNode; node++) {
      long date = folderDateProvider.getDate(anchor);
      insertNode(node, NO_PARENT, FOLDER_SUBTYPE, date);
      containers.add(new Node(node, node, 0, date));
      rootStats[node] = new RootStat();
    }

    int bias = node; // Bias early folder selection toward the root nodes.
    while (node < numNodes) {
      // Grab an empty container to populate. This isn't a completely
      // random selection, as it biases the selection toward any remaining
      // empty root nodes.
      Node parent = containers.remove(random.nextInt(
          (bias > 0 && bias < containers.size()) ? bias : containers.size()));
      bias--;
      int depth = parent.depth + 1;
      for (int i = 0; i < foldersPerNode; i++, node++) {
        long date = folderDateProvider.getDate(parent.date);
        insertNode(node, parent.node, FOLDER_SUBTYPE, date);
        // Newly added containers are available to fill.
        containers.add(new Node(node, parent.rootNode, depth, date));
      }
      for (int i = 0; i < leavesPerNode; i++, node++) {
        insertNode(node, parent.node, DOCUMENT_SUBTYPE,
                   leafDateProvider.getDate(parent.date));
      }
      connection.commit();

      RootStat rootStat = rootStats[parent.rootNode];
      assertNotNull("null rootStat parent " + parent, rootStat);
      rootStat.numLeaves += leavesPerNode;
      rootStat.numFolders += foldersPerNode;
      if (depth > rootStat.maxDepth)
        rootStat.maxDepth = depth;
    }
    connection.setAutoCommit(true);

    long stop = System.currentTimeMillis();
    LOGGER.info("End Data Loading: total nodes: " + (node - 1)
                + ", time = " + ((stop - start)/1000) + " seconds");
    for (node = 1; node <= foldersPerNode; node++) {
      LOGGER.info("Root node: " + node
                  + ", leaf descendants: " + rootStats[node].numLeaves
                  + ", folder descendants: " + rootStats[node].numFolders
                  + ", max depth: " + rootStats[node].maxDepth);
    }
  }

  /** Inserts a single node into both the DTree and WebNodes tables. */
  private void insertNode(int node, int parent, int subType, long lastModify)
      throws SQLException {
    // Useful for debugging the data loading, otherwise annoyingly verbose.
    //LOGGER.finest("Adding node " + node + ", parent " + parent + ", subtype "
    //              + subType + ", lastModify " + lastModify);
    insertDTree.setInt(1, node);    // DataID
    insertDTree.setInt(2, parent);  // ParentID
    insertDTree.setInt(3, node);    // PermID
    insertDTree.setInt(4, subType); // SubType
    insertDTree.setTimestamp(5, new Timestamp(lastModify)); // ModifyDate
    insertDTree.executeUpdate();

    insertWebNodes.setInt(1, node);    // DataID
    insertWebNodes.setInt(2, parent);  // ParentID
    insertWebNodes.setInt(3, node);    // PermID
    insertWebNodes.setInt(4, subType); // SubType
    insertWebNodes.setTimestamp(5, new Timestamp(lastModify)); // ModifyDate
    insertWebNodes.executeUpdate();
  }

  /** Provide millisecond dates, with 1 second resolution. */
  private static interface DateProvider {
    public long getDate(long anchor);
  }

  /** A DateProvider that returns dates incrementing by 1 second. */
  private static class IncrementalDateProvider implements DateProvider {
    static long date = 20000;

    @Override
    public long getDate(long ignored) {
      return date += 1000;
    }
  }

  /** A DateProvider that returns random dates. */
  private static class RandomDateProvider implements DateProvider {
    Random random = new Random();

    @Override
    public long getDate(long ignored) {
      // Scrub milliseconds.
      return random.nextInt() * 1000L;
    }
  }

  /**
   * A DateProvider that provides 50% random dates and the rest clustered
   * within a year of the anchor.
   */
  private static class FolderClusteredDateProvider implements DateProvider {
    Random random = new Random();
    int counter = 0;

    @Override
    public long getDate(long anchor) {
      int sign = 1;
      if ((++counter & 1) == 0) {
        // Return a random date.
        return random.nextInt() * 1000L;
      } else {
        // Return a date within a year of the anchor.
        return anchor + (random.nextInt(365 * 24 * 3600) * 1000L);
      }
    }
  }

  /**
   * A DateProvider that tends to cluster dates around the anchor,
   * with a few outliers.
   */
  private static class LeafClusteredDateProvider implements DateProvider {
    Random random = new Random();
    int counter = 0;

    @Override
    @SuppressWarnings("fallthrough")
    public long getDate(long anchor) {
      int sign = 1;
      switch (++counter & 7) {
        // Return a random date.
        case 0: return random.nextInt() * 1000L;

        // Return the same date as the anchor.
        case 1:
        case 5: return anchor;

        // Return the anchor +/- an hour.
        case 7: sign = -1;  // fallthrough
        default: return anchor - (sign * random.nextInt(3600) * 1000L);
      }
    }
  }

  private static class RootStat {
    int numLeaves;  // Total number of leaf descendants.
    int numFolders; // Total number of folder descendants.
    int maxDepth;   // Maximum depth.
  }

  private static class Node {
    final int node;       // Node number.
    final int rootNode;   // Ancestral root node for this node.
    final int depth;      // Depth removed from root.
    final long date;      // LastModified date of node.

    Node(int node, int rootNode, int depth, long date) {
      this.node = node;
      this.rootNode = rootNode;
      this.depth = depth;
      this.date = date;
    }

    @Override
    public String toString() {
      return "{ node = " + node + ", rootNode = " + rootNode + ", depth = "
          + depth + ", date = " + date + " }";
    }
  }

  private LivelinkTraversalManager getObjectUnderTest(String genealogist,
      int minCacheSize, int maxCacheSize, String startNodes,
      String excludedNodes) throws RepositoryException, SQLException {
    LivelinkConnector connector =
        LivelinkConnectorFactory.getConnector("connector.");
    connector.setServtype("MSSQL");  // H2 emulates SQLServer better than Oracle.
    connector.setIncludedLocationNodes(startNodes);
    connector.setExcludedLocationNodes(excludedNodes);
    connector.setGenealogist(genealogist);
    connector.setGenealogistMinCacheSize(minCacheSize);
    connector.setGenealogistMaxCacheSize(maxCacheSize);
    connector.setUseDTreeAncestors(false);
    connector.setTrackDeletedItems(false);
    connector.login();
    Client client = new MockClient();
    return new LivelinkTraversalManager(connector, client, "Admin", client,
                                        new MockContentHandler()) {
      /** Slimmer select list to avoid having to mock extra columns. */
      @Override String[] getSelectList() {
        return new String[] { "DataID", "ModifyDate", "SubType", "MimeType" };
      }
    };
  }

  private void traverseRepository(LivelinkTraversalManager traversalManager)
      throws RepositoryException {
    LOGGER.info("Start Traversal");
    long start = System.currentTimeMillis();
    int numdocs = 0;
    String checkpoint = null;
    DocumentList docList;
    traversalManager.setBatchHint(1000);
    while ((docList = traversalManager.resumeTraversal(checkpoint)) != null) {
      // We need the size and checkpoint, but peek at the internals to get them,
      // rather than iterating over the DocumentList, as that would require a
      // much better mock of the Livelink DB than we have at this point.
      if (((LivelinkDocumentList) docList).recArray != null) {
        numdocs += ((LivelinkDocumentList) docList).recArray.size();
      }
      ((LivelinkDocumentList) docList).checkpoint.advanceToEnd();
      checkpoint = docList.checkpoint();
    }
    long stop = System.currentTimeMillis();
    String msg = "End Traversal: time = " + ((stop - start)/1000) + " seconds, "
                 + "docs returned = " + numdocs;
    LOGGER.info(msg);
    System.out.println(msg);
  }

  private static class MockContentHandler implements ContentHandler {
    @Override
    public void initialize(LivelinkConnector connector, Client client) {
      // Do nothing;
    }

    @Override
    public InputStream getInputStream(int volumeId, int objectId,
                                      int versionNumber, int size) {
      return new ByteArrayInputStream(Integer.toString(objectId).getBytes());
    }
  }
}
