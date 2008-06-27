// Copyright (C) 2007-2008 Google Inc.
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
        String included = lqtm.getIncluded(null);

        assertTrue(excluded, excluded.indexOf("SubType not in " +
            "(137,142,143,148,150,154,161,162,201,203,209,210,211)") != -1);
    }    


    public void testExcludedNodes2() throws RepositoryException {
        conn.setExcludedVolumeTypes("");
        conn.setExcludedNodeTypes("");
        conn.setExcludedLocationNodes("");

        Session sess = conn.login();
        LivelinkTraversalManager lqtm =
            (LivelinkTraversalManager) sess.getTraversalManager();
        String excluded = lqtm.getExcluded(null);
        String included = lqtm.getIncluded(null);

        assertTrue(excluded, excluded.indexOf("SubType not in") == -1);
    }    


    public void testExcludedNodes3() throws RepositoryException {
        conn.setExcludedVolumeTypes("2001,4104");
        conn.setExcludedNodeTypes("");
        conn.setExcludedLocationNodes("");

        Session sess = conn.login();
        LivelinkTraversalManager lqtm =
            (LivelinkTraversalManager) sess.getTraversalManager();
        String excluded = lqtm.getExcluded(null);
        String included = lqtm.getIncluded(null);

        assertTrue(excluded, excluded.indexOf("SubType not in") == -1);
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
        String included = lqtm.getIncluded(null);

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

        assertTrue(excluded, excluded.indexOf("SubType not in") == -1);
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

        assertTrue(excluded, excluded.indexOf("DataID not in " +
            "(select DataID from DTreeAncestors where " +
            "null and AncestorID in (13832))") != -1);

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

        assertTrue(excluded, excluded.indexOf("DataID not in " +
            "(select DataID from DTreeAncestors where " +
            "null and AncestorID in (13832))") != -1);
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

        assertEquals("", included);
        assertTrue(excluded, excluded.indexOf("SubType not in " +
            "(137,142,143,148,150,154,161,162,201,203,209,210,211) and " +
            "DataID not in (select DataID from " + 
            "DTreeAncestors where null and AncestorID in (13832))") != -1);
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
            "DataID not in (select DataID from " + 
            "DTreeAncestors where null and AncestorID in (13832))") != -1);
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

        assertTrue(excluded, excluded.indexOf("SubType not in") == -1);
        assertEquals("(DataID in (2000) or DataID in (select DataID from " +
            "DTreeAncestors where null and AncestorID in (2000,-2000)))",
            included);
    }


    /**
     * Tests that subtype numbers in the showHiddenItems property are
     * ignored.
     *
     * @throws RepositoryException if an unexpected error occurs
     */
    public void testHiddenItemSubtypes() throws RepositoryException {
        conn.setExcludedVolumeTypes("");
        conn.setExcludedNodeTypes("");
        conn.setExcludedLocationNodes("");

        Session sess = conn.login();
        LivelinkTraversalManager lqtm =
            (LivelinkTraversalManager) sess.getTraversalManager();
        String excluded = lqtm.getExcluded(null);

        assertTrue(excluded, excluded.indexOf("null and AncestorID") == -1); 
        assertTrue(excluded, excluded.indexOf("SubType not in") == -1); 

        conn.setShowHiddenItems("{ 1234 }");

        sess = conn.login();
        lqtm = (LivelinkTraversalManager) sess.getTraversalManager();
        String excluded2 = lqtm.getExcluded(null);

        assertEquals((Object) excluded, excluded2);
    }
}
