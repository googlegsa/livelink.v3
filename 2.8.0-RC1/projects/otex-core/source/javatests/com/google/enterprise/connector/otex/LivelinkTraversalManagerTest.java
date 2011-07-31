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

import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.Session;

import junit.framework.TestCase;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
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
    
    public void setUp() throws RepositoryException {
        conn = LivelinkConnectorFactory.getConnector("connector.");
    }
    
    public void testExcludedNodes1() throws RepositoryException {
        // No excluded nodes configured.

        Session sess = conn.login();
        LivelinkTraversalManager lqtm =
            (LivelinkTraversalManager) sess.getTraversalManager();
        String excluded = lqtm.getExcluded(null);
        
        assertTrue(excluded, excluded.indexOf("SubType not in "
                + "(137,142,143,148,150,154,161,162,201,203,209,210,211,"
                + "345,346,361,374,431,3030004,3030201)") != -1);
    }    

    public void testExcludedNodes2() throws RepositoryException {
        conn.setExcludedVolumeTypes("");
        conn.setExcludedNodeTypes("");
        conn.setExcludedLocationNodes("");

        Session sess = conn.login();
        LivelinkTraversalManager lqtm =
            (LivelinkTraversalManager) sess.getTraversalManager();
        String excluded = lqtm.getExcluded(null);
        
        assertNull(excluded);
    }    

    public void testExcludedNodes3() throws RepositoryException {
        conn.setExcludedVolumeTypes("2001,4104");
        conn.setExcludedNodeTypes("");
        conn.setExcludedLocationNodes("");

        Session sess = conn.login();
        LivelinkTraversalManager lqtm =
            (LivelinkTraversalManager) sess.getTraversalManager();
        String excluded = lqtm.getExcluded(null);
        
        assertNull(excluded);
    }    

    public void testExcludedNodes4() throws RepositoryException {
        conn.setExcludedVolumeTypes("");
        conn.setExcludedNodeTypes(
            "137,142,143,148,150,154,161,162,201,203,209,210,211");
        conn.setExcludedLocationNodes("");

        Session sess = conn.login();
        LivelinkTraversalManager lqtm =
            (LivelinkTraversalManager) sess.getTraversalManager();
        String excluded = lqtm.getExcluded(null);
        
        assertTrue(excluded, excluded.indexOf("SubType not in " +
            "(137,142,143,148,150,154,161,162,201,203,209,210,211)") != -1);
    }    

    public void testExcludedNodes5() throws RepositoryException {
        conn.setExcludedVolumeTypes("148,162");
        conn.setExcludedNodeTypes("");
        conn.setExcludedLocationNodes("");

        Session sess = conn.login();
        LivelinkTraversalManager lqtm =
            (LivelinkTraversalManager) sess.getTraversalManager();
        String excluded = lqtm.getExcluded(null);
        String included = lqtm.getIncluded(null);

        assertNull(excluded);
        assertTrue(included, included.indexOf("-OwnerID not in") != -1);
        assertTrue(included,
            included.indexOf("SubType in (148,162)") != -1);
    }    

    public void testExcludedNodes6() throws RepositoryException {
        conn.setExcludedVolumeTypes("");
        conn.setExcludedNodeTypes("");
        conn.setExcludedLocationNodes("13832");

        Session sess = conn.login();
        LivelinkTraversalManager lqtm =
            (LivelinkTraversalManager) sess.getTraversalManager();
        String excluded = lqtm.getExcluded(null);

        assertTrue(excluded, excluded.indexOf("not (DataID in (13832) or " +
            "DataID in (select DataID from DTreeAncestors where " +
            "null and AncestorID in (13832,-13832)))") != -1);

        conn.setExcludedVolumeTypes("2001,4104");
        conn.setExcludedLocationNodes("13832");

        sess = conn.login();
        lqtm = (LivelinkTraversalManager) sess.getTraversalManager();
        String excluded2 = lqtm.getExcluded(null);

        assertEquals((Object) excluded, excluded2);
    }    

    public void testExcludedNodes7() throws RepositoryException {
        conn.setExcludedVolumeTypes("148,162");
        conn.setExcludedNodeTypes(
            "137,142,143,148,150,154,161,162,201,203,209,210,211");
        conn.setExcludedLocationNodes("");

        Session sess = conn.login();
        LivelinkTraversalManager lqtm =
            (LivelinkTraversalManager) sess.getTraversalManager();
        String excluded = lqtm.getExcluded(null);
        String included = lqtm.getIncluded(null);

        assertTrue(excluded, excluded.indexOf("SubType not in " +
            "(137,142,143,148,150,154,161,162,201,203,209,210,211)") != -1);
        assertTrue(included, included.indexOf("-OwnerID not in") != -1);
        assertTrue(included,
            included.indexOf("SubType in (148,162)") != -1);
    }    

    public void testExcludedNodes8() throws RepositoryException {
        conn.setExcludedVolumeTypes("148,162");
        conn.setExcludedNodeTypes("");
        conn.setExcludedLocationNodes("13832");

        Session sess = conn.login();
        LivelinkTraversalManager lqtm =
            (LivelinkTraversalManager) sess.getTraversalManager();
        String excluded = lqtm.getExcluded(null);
        String included = lqtm.getIncluded(null);

        assertTrue(excluded, excluded.indexOf("not (DataID in (13832) or " +
            "DataID in (select DataID from DTreeAncestors where " +
            "null and AncestorID in (13832,-13832)))") != -1);
        assertTrue(included, included.indexOf("-OwnerID not in") != -1);
        assertTrue(included,
            included.indexOf("SubType in (148,162)") != -1);
    }    

    public void testExcludedNodes9() throws RepositoryException {
        conn.setExcludedVolumeTypes("");
        conn.setExcludedNodeTypes(
            "137,142,143,148,150,154,161,162,201,203,209,210,211");
        conn.setExcludedLocationNodes("13832");

        Session sess = conn.login();
        LivelinkTraversalManager lqtm =
            (LivelinkTraversalManager) sess.getTraversalManager();
        String excluded = lqtm.getExcluded(null);
        String included = lqtm.getIncluded(null);

        assertNull(included, included);
        assertTrue(excluded, excluded.indexOf("SubType not in " +
            "(137,142,143,148,150,154,161,162,201,203,209,210,211) and " +
            "not (DataID in (13832) or DataID in (select DataID from " + 
            "DTreeAncestors where null and " +
            "AncestorID in (13832,-13832)))") != -1);
    }    

    public void testExcludedNodes10() throws RepositoryException {
        conn.setExcludedVolumeTypes("148,162");
        conn.setExcludedNodeTypes(
            "137,142,143,148,150,154,161,162,201,203,209,210,211");
        conn.setExcludedLocationNodes("13832");

        Session sess = conn.login();
        LivelinkTraversalManager lqtm =
            (LivelinkTraversalManager) sess.getTraversalManager();
        String excluded = lqtm.getExcluded(null);
        String included = lqtm.getIncluded(null);

        assertTrue(excluded, excluded.indexOf("SubType not in " +
            "(137,142,143,148,150,154,161,162,201,203,209,210,211) and " +
            "not (DataID in (13832) or DataID in (select DataID from " + 
            "DTreeAncestors where null and " +
            "AncestorID in (13832,-13832)))") != -1);
        assertTrue(included, included.indexOf("-OwnerID not in") != -1);
        assertTrue(included,
            included.indexOf("SubType in (148,162)") != -1);
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

        Session sess = conn.login();
        LivelinkTraversalManager lqtm =
            (LivelinkTraversalManager) sess.getTraversalManager();
        String excluded = lqtm.getExcluded(null);
        String included = lqtm.getIncluded(null);

        assertNull(excluded);
        assertEquals("(DataID in (2000) or DataID in (select DataID from " +
            "DTreeAncestors where null and AncestorID in (2000,-2000)))",
            included);
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
}
