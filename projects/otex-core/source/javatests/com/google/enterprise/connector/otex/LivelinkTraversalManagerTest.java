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

package com.google.enterprise.connector.otex;

import com.google.common.collect.ImmutableList;
import com.google.enterprise.connector.otex.client.Client;
import com.google.enterprise.connector.otex.client.ClientValue;
import com.google.enterprise.connector.otex.client.mock.MockClient;
import com.google.enterprise.connector.otex.client.mock.MockClientValue;
import com.google.enterprise.connector.spi.Document;
import com.google.enterprise.connector.spi.DocumentList;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.Session;
import com.google.enterprise.connector.spi.SpiConstants;
import com.google.enterprise.connector.spi.Value;

import junit.framework.TestCase;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tests the construction of the queries for traversing Livelink.
 *
 * Note that many tests provide a candidates predicate of
 * <code>null</code>, which is invalid. This leads to the string
 * "null" appearing in the SQL query as a predicate, which is also
 * invalid. But for our purposes this is OK, since it shows the
 * candidates predicate being included in the query, which is all we
 * need.
 */
public class LivelinkTraversalManagerTest extends TestCase {
    private LivelinkConnector conn;

  private final JdbcFixture jdbcFixture = new JdbcFixture();

  private final LivelinkDateFormat dateFormat =
      LivelinkDateFormat.getInstance();

  @Override
  protected void setUp() throws RepositoryException, SQLException {
    conn = LivelinkConnectorFactory.getConnector("connector.");

    jdbcFixture.setUp();
    jdbcFixture.executeUpdate(
        // Try inserting the DAuditNew entries out of date order, in
        // case that influences the natural order returned by the DB,
        // since we want an ORDER BY to pick the latest date.
        "insert into DAuditNew(EventID, AuditDate) "
        + "values(17, timestamp'2001-01-01 00:00:00')",
        "insert into DAuditNew(EventID, AuditDate) "
        + "values(42, timestamp'2013-04-24 08:00:00')",
        "insert into DAuditNew(EventID, AuditDate) "
        + "values(24, timestamp'2005-10-06 12:34:56')",
        "insert into DTree(DataID, ParentID, OwnerID, SubType, ModifyDate) "
        + "values(24, 6, -2000, 0, timestamp'2001-01-01 00:00:00')",
        "insert into DTree(DataID, ParentID, OwnerID, SubType, ModifyDate) "
        + "values(42, 6, -2000, 144, timestamp'2001-01-01 00:00:00')",
        "insert into DTree(DataID, ParentID, OwnerID, SubType, ModifyDate) "
        + "values(66, 6, -2000, 144, timestamp'2002-02-02 00:00:00')",
        "insert into DTree(DataID, ParentID, OwnerID, SubType, ModifyDate) "
        + "values(6, 2000, -2000, 0, timestamp'2002-02-02 00:00:00')",
        "insert into DTree(DataID, ParentID, OwnerID, SubType, ModifyDate) "
        + "values(2000, -1, -2000, 141, timestamp'2001-01-01 00:00:00')",
        "insert into DTree(DataID, ParentID, OwnerID, SubType, ModifyDate) "
        + "values(2901, -1, -2901, 901, timestamp'2001-01-01 00:00:00')",
        "insert into DTreeAncestors(DataID, AncestorID) "
        + "values(24, 6)",
        "insert into DTreeAncestors(DataID, AncestorID) "
        + "values(42, 6)",
        "insert into DTreeAncestors(DataID, AncestorID) "
        + "values(66, 6)",
        "insert into DTreeAncestors(DataID, AncestorID) "
        + "values(6, 2000)",
        "insert into DTreeAncestors(DataID, AncestorID) "
        + "values(2000, -1)",
        "insert into KUAF(ID, Name, Type, GroupID, UserData) "
        + " values(1001, 'user1', 0, 2001, 'ExternalAuthentication=true' )",
        "insert into WebNodes(DataID, ParentID, OwnerID, SubType, ModifyDate, "
        + "MimeType, UserID) "
        + "values(24, 6, -2000, 0, timestamp'2001-01-01 00:00:00', null, 1001)",
        "insert into WebNodes(DataID, ParentID, OwnerID, SubType, ModifyDate, "
        + "MimeType, DataSize, UserID) "
        + "values(42, 6, -2000, 144, timestamp'2001-01-01 00:00:00', "
        + "'text/xml', 1729, 1001)",
        "insert into WebNodes(DataID, ParentID, OwnerID, SubType, ModifyDate, "
        + "MimeType, DataSize, UserID) "
        + "values(66, 6, -2000, 144, sysdate, 'text/xml', 1729, 1001)",
        "insert into WebNodes(DataID, ParentID, OwnerID, SubType, ModifyDate, "
        + "MimeType, UserID) "
        + "values(2901, -1, -2901, 901, timestamp'2001-01-01 00:00:00', null,"
        + " 1001)");
  }

  @Override
  protected void tearDown() throws SQLException {
    jdbcFixture.tearDown();
  }

  private void assertIncludedEmpty(String included) {
    assertFalse(included, included.contains("and (DataID in"));
    assertFalse(included, included.contains("and -OwnerID not in"));
  }

  private void assertExcludedEmpty(String excluded) {
    assertFalse(excluded, excluded.contains("and SubType not in"));
    assertFalse(excluded, excluded.contains("and not"));
  }

  public void testGetLastAuditEvent() throws RepositoryException {
    Session sess = conn.login();
    LivelinkTraversalManager ltm =
        (LivelinkTraversalManager) sess.getTraversalManager();

    ClientValue results = ltm.getLastAuditEvent();
    assertEquals(1, results.size());
    // toInteger works with H2 (we check the type in the production code).
    assertEquals(42, results.toInteger(0, "EventID"));
    // The fractional seconds are OK, because LivelinkDateFormat.parse
    // handles multiple variations in the timestamp strings.
    assertEquals("2013-04-24 08:00:00.0", results.toString(0, "AuditDate"));
  }

  /**
   * A helper method for the tests. The checkCandidatesTimeWarp method
   * was intentionally left with higher-level arguments to test the
   * extraction of the necessary dates. So the tests cover a little
   * more code, at the cost of this extra helper method to make the
   * tests simpler again.
   *
   * @param firstCandidateDate the ModifyDate of the first result row
   * @param lastCandidateDate the ModifyDate of the last result row,
   *     usually valid or invalid in opposition to {@code
   *     firstCandidateDate}, to make sure the check isn't using this
   *     value
   * @param checkpointDate the supposed starting point for the batch
   */
  private void checkCandidatesTimeWarp(Date firstCandidateDate,
      int firstCandidateId, Date lastCandidateDate, int lastCandidateId,
      String checkpoint) throws RepositoryException {
    Session sess = conn.login();
    LivelinkTraversalManager ltm =
        (LivelinkTraversalManager) sess.getTraversalManager();
    ClientValue candidates = new MockClientValue(
        new String[] { "ModifyDate", "DataID" },
        new Object[][] { { firstCandidateDate, firstCandidateId },
                         { lastCandidateDate, lastCandidateId } });
    ltm.checkCandidatesTimeWarp(candidates, new Checkpoint(checkpoint));
  }

  private void failCandidatesTimeWarp(Date firstCandidateDate,
      int firstCandidateId, Date lastCandidateDate, int lastCandidateId,
      String checkpoint) throws RepositoryException {
    try {
      checkCandidatesTimeWarp(firstCandidateDate, firstCandidateId,
          lastCandidateDate, lastCandidateId, checkpoint);
      fail("Expected a RepositoryException");
    } catch (RepositoryException expected) {
      assertTrue(expected.getMessage(),
          expected.getMessage().contains("CANDIDATES TIME WARP"));
    }
  }

  /** Checking time warp is disabled by default. */
  public void testCandidatesTimeWarp_default() throws RepositoryException {
    Date first = dateFormat.parse("2013-08-26 16:31:00");
    checkCandidatesTimeWarp(first, 0, first, 0, "2014-01-01 00:00:00,0");
  }

  public void testCandidatesTimeWarp_disabled() throws RepositoryException {
    conn.setCandidatesTimeWarpFuzz(-1);

    Date first = dateFormat.parse("2013-08-26 16:31:00");
    checkCandidatesTimeWarp(first, 0, first, 0, "2014-01-01 00:00:00,0");
  }

  public void testCandidatesTimeWarp_enabledBadDate()
      throws RepositoryException {
    conn.setCandidatesTimeWarpFuzz(0);

    Date first = dateFormat.parse("2013-08-26 16:31:00");
    Date last = dateFormat.parse("2014-08-26 16:31:00");
    failCandidatesTimeWarp(first, 0, last, 0, "2014-01-01 00:00:00,0");
  }

  public void testCandidatesTimeWarp_enabledBadId() throws RepositoryException {
    conn.setCandidatesTimeWarpFuzz(0);

    Date first = dateFormat.parse("2014-01-01 00:00:00");
    failCandidatesTimeWarp(first, 0, first, 1000, "2014-01-01 00:00:00,999");
  }

  /**
   * The last candidate is impossibly set before the first candidate
   * and the checkpoint.
   */
  public void testCandidatesTimeWarp_enabledGoodDate()
      throws RepositoryException {
    conn.setCandidatesTimeWarpFuzz(0);

    Date first = dateFormat.parse("2014-01-26 16:31:00");
    Date last = dateFormat.parse("2013-08-26 16:31:00");
    checkCandidatesTimeWarp(first, 0, last, 0, "2014-01-01 00:00:00,0");
  }

  /**
   * The last candidate is impossibly set before the first candidate
   * and the checkpoint.
   */
  public void testCandidatesTimeWarp_enabledGoodId()
      throws RepositoryException {
    conn.setCandidatesTimeWarpFuzz(0);

    Date first = dateFormat.parse("2014-01-01 00:00:00");
    checkCandidatesTimeWarp(first, 1000, first, 0, "2014-01-01 00:00:00,999");
  }

  /**
   * Livelink only stores timestamps to the nearest second, but LAPI
   * 9.7 and earlier constructs a Date object that includes
   * milliseconds, which are taken from the current time.
   */
  public void testCandidatesTimeWarp_enabledMilliseconds()
      throws RepositoryException {
    conn.setCandidatesTimeWarpFuzz(0);

    Date first = dateFormat.parse("2014-01-01 00:00:00.999");
    Date last = dateFormat.parse("2014-01-01 00:00:01.999");
    failCandidatesTimeWarp(first, 999, last, 999, "2014-01-01 00:00:00,1000");
  }

  /**
   * Sets the fuzz to 30 days and test an even later candidate. The
   * last candidate is impossibly set before the first candidate,
   * inside the 30 day fuzz.
   */
  public void testCandidatesTimeWarp_futureFailure()
      throws RepositoryException {
    conn.setCandidatesTimeWarpFuzz(30);

    Date first = dateFormat.parse("2014-08-26 16:31:00");
    Date last = dateFormat.parse("2014-01-26 16:31:00");
    failCandidatesTimeWarp(first, 0, last, 0, "2014-01-01 00:00:00,0");
  }

  /**
   * Sets the first candidate inside the 30 day fuzz, and the last
   * candidate outside it (which is fine).
   */
  public void testCandidatesTimeWarp_futureSuccess()
      throws RepositoryException {
    conn.setCandidatesTimeWarpFuzz(30);

    Date first = dateFormat.parse("2014-01-26 16:31:00");
    Date last = dateFormat.parse("2014-08-26 16:31:00");
    checkCandidatesTimeWarp(first, 0, last, 0, "2014-01-01 00:00:00,0");
  }

  /** Calls the like-named method under test. */
  private String getMatchingQuery() throws RepositoryException {
    Session sess = conn.login();
    LivelinkTraversalManager ltm =
        (LivelinkTraversalManager) sess.getTraversalManager();
    return ltm.getMatchingQuery(null, new Date(), false);
  }

    public void testExcludedNodes1() throws RepositoryException {
        // No excluded nodes configured.

        String query = getMatchingQuery();

        assertTrue(query, query.indexOf("and SubType not in "
                + "(137,142,143,148,150,154,161,162,201,203,209,210,211,"
                + "345,346,361,374,431,441,3030004,3030201)") != -1);
    }

    public void testExcludedNodes2() throws RepositoryException {
        conn.setExcludedVolumeTypes("");
        conn.setExcludedNodeTypes("");
        conn.setExcludedLocationNodes("");

        String query = getMatchingQuery();

        assertExcludedEmpty(query);
    }

    public void testExcludedNodes3() throws RepositoryException {
        conn.setExcludedVolumeTypes("2001,4104");
        conn.setExcludedNodeTypes("");
        conn.setExcludedLocationNodes("");

        String query = getMatchingQuery();

        assertExcludedEmpty(query);
    }

    public void testExcludedNodes4() throws RepositoryException {
        conn.setExcludedVolumeTypes("");
        conn.setExcludedNodeTypes(
            "137,142,143,148,150,154,161,162,201,203,209,210,211");
        conn.setExcludedLocationNodes("");

        String query = getMatchingQuery();

        assertTrue(query, query.indexOf("and SubType not in " +
            "(137,142,143,148,150,154,161,162,201,203,209,210,211)") != -1);
    }

    public void testExcludedNodes5() throws RepositoryException {
        conn.setExcludedVolumeTypes("148,162");
        conn.setExcludedNodeTypes("");
        conn.setExcludedLocationNodes("");

        String query = getMatchingQuery();

        assertExcludedEmpty(query);
        assertTrue(query, query.indexOf("and -OwnerID not in") != -1);
        assertTrue(query,
            query.indexOf("SubType in (148,162)") != -1);
    }

    public void testExcludedNodes6() throws RepositoryException {
        conn.setExcludedVolumeTypes("");
        conn.setExcludedNodeTypes("");
        conn.setExcludedLocationNodes("13832");

        String query = getMatchingQuery();

        String expectedExcluded = "and not (DataID in (13832) or " +
            "DataID in (select DataID from DTreeAncestors where " +
            "DataID in (null) and AncestorID in (13832,-13832)))";
        assertTrue(query, query.indexOf(expectedExcluded) != -1);

        conn.setExcludedVolumeTypes("2001,4104");
        conn.setExcludedLocationNodes("13832");

        String query2 = getMatchingQuery();

        assertTrue(query2, query2.indexOf(expectedExcluded) != -1);
    }

    public void testExcludedNodes7() throws RepositoryException {
        conn.setExcludedVolumeTypes("148,162");
        conn.setExcludedNodeTypes(
            "137,142,143,148,150,154,161,162,201,203,209,210,211");
        conn.setExcludedLocationNodes("");

        String query = getMatchingQuery();

        assertTrue(query, query.indexOf("and SubType not in " +
            "(137,142,143,148,150,154,161,162,201,203,209,210,211)") != -1);
        assertTrue(query, query.indexOf("and -OwnerID not in") != -1);
        assertTrue(query,
            query.indexOf("SubType in (148,162)") != -1);
    }

    public void testExcludedNodes8() throws RepositoryException {
        conn.setExcludedVolumeTypes("148,162");
        conn.setExcludedNodeTypes("");
        conn.setExcludedLocationNodes("13832");

        String query = getMatchingQuery();

        assertTrue(query, query.indexOf("and not (DataID in (13832) or " +
            "DataID in (select DataID from DTreeAncestors where " +
            "DataID in (null) and AncestorID in (13832,-13832)))") != -1);
        assertTrue(query, query.indexOf("and -OwnerID not in") != -1);
        assertTrue(query,
            query.indexOf("SubType in (148,162)") != -1);
    }

    public void testExcludedNodes9() throws RepositoryException {
        conn.setExcludedVolumeTypes("");
        conn.setExcludedNodeTypes(
            "137,142,143,148,150,154,161,162,201,203,209,210,211");
        conn.setExcludedLocationNodes("13832");

        String query = getMatchingQuery();

        assertIncludedEmpty(query);
        assertTrue(query, query.indexOf("and SubType not in " +
            "(137,142,143,148,150,154,161,162,201,203,209,210,211) and " +
            "not (DataID in (13832) or DataID in (select DataID from " + 
            "DTreeAncestors where DataID in (null) and " +
            "AncestorID in (13832,-13832)))") != -1);
    }

    public void testExcludedNodes10() throws RepositoryException {
        conn.setExcludedVolumeTypes("148,162");
        conn.setExcludedNodeTypes(
            "137,142,143,148,150,154,161,162,201,203,209,210,211");
        conn.setExcludedLocationNodes("13832");

        String query = getMatchingQuery();

        assertTrue(query, query.indexOf("and SubType not in " +
            "(137,142,143,148,150,154,161,162,201,203,209,210,211) and " +
            "not (DataID in (13832) or DataID in (select DataID from " + 
            "DTreeAncestors where DataID in (null) and " +
            "AncestorID in (13832,-13832)))") != -1);
        assertTrue(query, query.indexOf("and -OwnerID not in") != -1);
        assertTrue(query,
            query.indexOf("SubType in (148,162)") != -1);
    }

    /**
     * As an aside, excludedVolumeTypes and includedLocationNodes are
     * mutually exclusive, and includedLocationNodes wins.
     */
    public void testExcludedNodes11() throws RepositoryException {
        conn.setExcludedVolumeTypes("148,162");
        conn.setExcludedNodeTypes("");
        conn.setExcludedLocationNodes("");
        conn.setIncludedLocationNodes("2000");

        String query = getMatchingQuery();

        assertExcludedEmpty(query);
        assertTrue(query, query.indexOf(
            " and (DataID in (2000) or DataID in (select DataID from "
            + "DTreeAncestors where DataID in (null) "
            + "and AncestorID in (2000,-2000)))") != -1);
    }

    /** Test the default exclusions. */
    public void testExcludedNodes12() throws RepositoryException {
        String query = getMatchingQuery();

        assertTrue(query, query.indexOf("and SubType not in "
            + "(137,142,143,148,150,154,161,162,201,203,209,210,211,"
            + "345,346,361,374,431,441,3030004,3030201)") != -1);
        assertTrue(query, query.indexOf("and -OwnerID not in") != -1);
        assertTrue(query,
            query.indexOf("SubType in (148,161,162,901)") != -1);
    }

  public void testUseDTreeAncestors_true() throws RepositoryException {
    conn.setExcludedLocationNodes("13832");
    conn.setIncludedLocationNodes("2000");
    conn.setUseDTreeAncestors(true);

    String query = getMatchingQuery();

    // The included and excluded conditions are nearly identical, but
    // the excluded portion starts with "and NOT".
    assertTrue(query, query.contains(
        "and not (DataID in (13832) or DataID in (select DataID from "
        + "DTreeAncestors where DataID in (null) "
        + "and AncestorID in (13832,-13832)))"));
    assertTrue(query, query.contains(
        " and (DataID in (2000) or DataID in (select DataID from "
        + "DTreeAncestors where DataID in (null) "
        + "and AncestorID in (2000,-2000)))"));
  }

  public void testUseDTreeAncestors_false() throws RepositoryException {
    conn.setExcludedLocationNodes("13832");
    conn.setIncludedLocationNodes("2000");
    conn.setUseDTreeAncestors(false);

    String query = getMatchingQuery();

    assertFalse(query, query.contains("DTreeAncestors"));
  }

    /** Tests a null select expressions map. */
    public void testIncludedSelectExpressions_null()
            throws RepositoryException {
        testIncludedSelectExpressions(null);
    }

    /** Tests an empty select expressions map. */
    public void testIncludedSelectExpressions_empty()
            throws RepositoryException {
        testIncludedSelectExpressions(Collections.<String, String>emptyMap());
    }

    /** Tests a singleton select expressions map. */
    public void testIncludedSelectExpressions_singleton()
            throws RepositoryException {
        testIncludedSelectExpressions(
            Collections.singletonMap("propname", "'a string literal'"));
    }

    /** Tests a larger select expressions map. */
    public void testIncludedSelectExpressions_multiple()
            throws RepositoryException {
      Map<String, String> map = new HashMap<String, String>();
      // There is no syntax checking on these values. I'm just giving
      // strange but plausible values.
      map.put("propname", "'a string literal'");
      map.put("siblingCount",
          "(select count(*)-1 from DTree d where d.ParentID = a.ParentID)");
      map.put("google:folder", "storedProcedure(DataID)");
      testIncludedSelectExpressions(map);
    }

    /**
     * Tests setting the included select expressions, comparing the
     * resulting fields array and select list array to the expected
     * output.
     *
     * @param map the included select expressions, may be {@code null}
     */
    private void testIncludedSelectExpressions(Map<String, String> map)
            throws RepositoryException {
        conn.setIncludedSelectExpressions(map);
        Map<String, String> selectExpressions =
            conn.getIncludedSelectExpressions();
        assertNotNull(selectExpressions);
        int size = (map == null) ? 0 : map.size();
        assertEquals(selectExpressions.toString(),
            size, selectExpressions.size());

        Session sess = conn.login();
        LivelinkTraversalManager ltm =
            (LivelinkTraversalManager) sess.getTraversalManager();

        Field[] fields = ltm.getFields();
        assertEquals(LivelinkTraversalManager.DEFAULT_FIELDS.length + size,
            fields.length);
        String expected = 
            Arrays.toString(LivelinkTraversalManager.DEFAULT_FIELDS);
        String actual = Arrays.toString(fields);
        assertTrue("Expected: " + expected + "; but got: " + actual,
            actual.startsWith(expected.substring(0, expected.length() - 1)));

        String[] selectList = ltm.getSelectList();
        assertEquals(fields.length, selectList.length);
  }

  public void testGetResults() throws RepositoryException {
    Session sess = conn.login();
    LivelinkTraversalManager ltm =
        (LivelinkTraversalManager) sess.getTraversalManager();

    // Not sure why, but 6 and 2000 are not in the WebNodes data.
    // 2901 is, but it's an excluded volume type.
    ClientValue results = ltm.getResults("6,24,42,2000,2901", new Date());
    assertEquals(2, results.size());
  }

  private LivelinkTraversalManager getObjectUnderTest(Client traversalClient)
      throws RepositoryException {
    conn.login();
    return new LivelinkTraversalManager(conn, traversalClient, "Admin",
        new MockClient(), conn.getContentHandler(traversalClient));
  }

  /**
   * Asserts that the given DocumentList contains exactly the expected
   * documents and nothing else.
   */
  private void assertDocumentListEquals(List<String> expectedDocids,
      DocumentList list) throws RepositoryException {
    assertNotNull(list);
    for (String docid : expectedDocids) {
      Document doc = list.nextDocument();
      assertNotNull(doc);
      assertEquals(docid,
          Value.getSingleValueString(doc, SpiConstants.PROPNAME_DOCID));
    }
    assertNull(list.nextDocument());
  }

  /**
   * Checks the first traversal query with a single batch repository,
   * including asserting that the checkpoint is the expected value
   * after the entire list is retrieved (i.e., nextDocument has
   * returned null). This test is here rather than in
   * LivelinkDocumentListTest because it's the
   * LivelinkTraversalManager that creates the underlying recarray and
   * checkpoint.
   */
  private void testStartTraversal(boolean useDTreeAncestors)
      throws SQLException, RepositoryException {
    LivelinkTraversalManager ltm = getObjectUnderTest(useDTreeAncestors, null);

    // 66 is modified later, and appears in the candidates, but appears
    // updated because of its current date in WebNodes, so it's left
    // out of the results.
    DocumentList list = ltm.startTraversal();
    assertDocumentListEquals(ImmutableList.of("24", "42"), list);

    // When nextDocument returns null, the advance checkpoint is used,
    // which includes 66 with its original ModifyDate, since that was
    // in the candidates.
    // TODO(jlacey): We could query these values as the max(ModifyDate)
    // from DTree when it matches WebNodes, and from DAuditNew.
    assertEquals("2002-02-02 00:00:00,66,2013-04-24 08:00:00.000,9999999999",
        list.checkpoint());
  }

  public void testStartTraversalWithDTreeAncestors()
      throws SQLException, RepositoryException {
    testStartTraversal(true);
  }

  public void testStartTraversalWithGenealogist()
      throws SQLException, RepositoryException {
    testStartTraversal(false);
  }

  /** Positive test to set a baseline for testResumeTraversalPingError. */
  public void testResumeTraversal() throws RepositoryException {
    LivelinkTraversalManager ltm = getObjectUnderTest(new MockClient());

    DocumentList list = ltm.resumeTraversal("2011-10-16 14:13:52,12345");
    assertNotNull(list);
    assertNull(list.nextDocument());
  }

  /**
   * Tests that if GetCurrentUserID throws an exception, so does
   * resumeTraversal. Legacy code returned null and triggered the
   * retry delay instead of the error wait.
   */
  public void testResumeTraversalPingError() throws RepositoryException {
    final RepositoryException expected = new RepositoryException();
    LivelinkTraversalManager ltm = getObjectUnderTest(new MockClient() {
        @Override
        public int GetCurrentUserID() throws RepositoryException {
          throw expected;
        }
      });

    try {
      DocumentList list = ltm.resumeTraversal("2011-10-16 14:13:52,12345");
      fail("Expected an exception, but got " + list);
    } catch (RepositoryException e) {
      assertSame(expected, e);
    }
  }

  private LivelinkTraversalManager getObjectUnderTest(
      boolean useDTreeAncestors, String sqlWhereCondition)
      throws RepositoryException, SQLException {
    // This is a little twisted. We need to call login after setting
    // includedLocationNodes for it to sanitize the value, but we don't
    // want to validate the sqlWhereCondition, since we're explicitly
    // testing the traversal manager here.
    conn.setIncludedLocationNodes("6");
    conn.login();
    conn.setUseDTreeAncestors(useDTreeAncestors);
    conn.setSqlWhereCondition(sqlWhereCondition);

    Client client = new MockClient();
    return new LivelinkTraversalManager(conn, client, "Admin", client,
        conn.getContentHandler(client));
  }

  private void testSqlWhereCondition(boolean useDTreeAncestors,
      String sqlWhereCondition, int expectedRows) throws Exception {
    LivelinkTraversalManager ltm =
        getObjectUnderTest(useDTreeAncestors, sqlWhereCondition);
    ClientValue results = ltm.getResults("24,42", new Date());

    if (expectedRows == 0) {
      assertNullOrEmpty(results);
    } else {      
      assertEquals(expectedRows, results.size());
    }
  }

  private void assertNullOrEmpty(ClientValue value) {
    if (value != null) {
      assertEquals(0, value.size());
    }
  }

  public void testSqlWhereCondition_none_dta() throws Exception {
    testSqlWhereCondition(true, "", 2);
  }

  public void testSqlWhereCondition_none_gen() throws Exception {
    testSqlWhereCondition(false, "", 2);
  }

  public void testSqlWhereCondition_empty_dta() throws Exception {
    testSqlWhereCondition(true, "DataID = 0", 0);
  }

  public void testSqlWhereCondition_empty_gen() throws Exception {
    testSqlWhereCondition(false, "DataID = 0", 0);
  }

  public void testSqlWhereCondition_condition_dta() throws Exception {
    testSqlWhereCondition(true, "DataID > 40", 1);
  }

  public void testSqlWhereCondition_condition_gen() throws Exception {
    testSqlWhereCondition(false, "DataID > 40", 1);
  }

  /** Tests selecting a column that only exists in WebNodes and not DTree. */
  public void testSqlWhereCondition_webnodes_dta() throws Exception {
    testSqlWhereCondition(true, "MimeType is not null", 1);
  }

  /** Tests selecting a column that only exists in WebNodes and not DTree. */
  public void testSqlWhereCondition_webnodes_gen() throws Exception {
    testSqlWhereCondition(false, "MimeType is not null", 1);
  }
}
