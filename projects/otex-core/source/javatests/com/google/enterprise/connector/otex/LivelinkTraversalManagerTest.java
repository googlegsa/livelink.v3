// Copyright (C) 2007 Google Inc.
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
        String excluded = lqtm.getExcluded();
        String included = lqtm.getIncluded();
        //System.out.println("testExcludedNodes1: included = " + included);
        //System.out.println("testExcludedNodes1: excluded = " + excluded);

        assertEquals(excluded, "SubType not in " +
            "(137,142,143,148,150,154,161,162,201,203,209,210,211)");
    }    


    public void testExcludedNodes2() throws RepositoryException {
        conn.setExcludedVolumeTypes("");
        conn.setExcludedNodeTypes("");
        conn.setExcludedLocationNodes("");

        Session sess = conn.login();
        LivelinkTraversalManager lqtm =
            (LivelinkTraversalManager) sess.getTraversalManager();
        String excluded = lqtm.getExcluded();
        String included = lqtm.getIncluded();
        //System.out.println("testExcludedNodes2: included = " + included);
        //System.out.println("testExcludedNodes2: excluded = " + excluded);

        assertNull(excluded, excluded);
    }    


    public void testExcludedNodes3() throws RepositoryException {
        conn.setExcludedVolumeTypes("2001,4104");
        conn.setExcludedNodeTypes("");
        conn.setExcludedLocationNodes("");

        Session sess = conn.login();
        LivelinkTraversalManager lqtm =
            (LivelinkTraversalManager) sess.getTraversalManager();
        String excluded = lqtm.getExcluded();
        String included = lqtm.getIncluded();
        //System.out.println("testExcludedNodes3: included = " + included);
        //System.out.println("testExcludedNodes3: excluded = " + excluded);

        assertNull(excluded, excluded);
    }    


    public void testExcludedNodes4() throws RepositoryException {
        conn.setExcludedVolumeTypes("");
        conn.setExcludedNodeTypes(
            "137,142,143,148,150,154,161,162,201,203,209,210,211");
        conn.setExcludedLocationNodes("");

        Session sess = conn.login();
        LivelinkTraversalManager lqtm =
            (LivelinkTraversalManager) sess.getTraversalManager();
        String excluded = lqtm.getExcluded();
        String included = lqtm.getIncluded();
        //System.out.println("testExcludedNodes4: included = " + included);
        //System.out.println("testExcludedNodes4: excluded = " + excluded);

        assertEquals((Object) "SubType not in " +
            "(137,142,143,148,150,154,161,162,201,203,209,210,211)", excluded);
    }    


    public void testExcludedNodes5() throws RepositoryException {
        conn.setExcludedVolumeTypes("148,162");
        conn.setExcludedNodeTypes("");
        conn.setExcludedLocationNodes("");

        Session sess = conn.login();
        LivelinkTraversalManager lqtm =
            (LivelinkTraversalManager) sess.getTraversalManager();
        String excluded = lqtm.getExcluded();
        String included = lqtm.getIncluded();
        //System.out.println("testExcludedNodes5: included = " + included);
        //System.out.println("testExcludedNodes5: excluded = " + excluded);

        assertNull(excluded);
        assertTrue(included, included.indexOf("SubType not in (148,162)") != -1);
        assertTrue(included, included.indexOf("DTreeAncestors") != -1);
    }    


    public void testExcludedNodes6() throws RepositoryException {
        conn.setExcludedVolumeTypes("");
        conn.setExcludedNodeTypes("");
        conn.setExcludedLocationNodes("13832");

        Session sess = conn.login();
        LivelinkTraversalManager lqtm =
            (LivelinkTraversalManager) sess.getTraversalManager();
        String excluded = lqtm.getExcluded();

        assertEquals((Object) "DataID not in (select DataID from " + 
            "DTreeAncestors where AncestorID in (13832))", excluded);

        conn.setExcludedVolumeTypes("2001,4104");
        conn.setExcludedLocationNodes("13832");

        sess = conn.login();
        lqtm = (LivelinkTraversalManager) sess.getTraversalManager();
        String excluded2 = lqtm.getExcluded();

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
        String excluded = lqtm.getExcluded();
        String included = lqtm.getIncluded();
        //System.out.println("testExcludedNodes7: included = " + included);
        //System.out.println("testExcludedNodes7: excluded = " + excluded);

        assertEquals(excluded, "SubType not in " +
            "(137,142,143,148,150,154,161,162,201,203,209,210,211)");

        assertTrue(included, included.indexOf("SubType not in (148,162)") != -1);
        assertTrue(included, included.indexOf("DTreeAncestors") != -1);
    }    


    public void testExcludedNodes8() throws RepositoryException {
        conn.setExcludedVolumeTypes("148,162");
        conn.setExcludedNodeTypes("");
        conn.setExcludedLocationNodes("13832");

        Session sess = conn.login();
        LivelinkTraversalManager lqtm =
            (LivelinkTraversalManager) sess.getTraversalManager();
        String excluded = lqtm.getExcluded();
        String included = lqtm.getIncluded();
        //System.out.println("testExcludedNodes8: included = " + included);
        //System.out.println("testExcludedNodes8: excluded = " + excluded);

        assertEquals(excluded, "DataID not in (select DataID from " +
                     "DTreeAncestors where AncestorID in (13832))");
        assertTrue(included, included.indexOf("SubType not in (148,162)") != -1);
        assertTrue(included, included.indexOf("DTreeAncestors") != -1);
    }    


    public void testExcludedNodes9() throws RepositoryException {
        conn.setExcludedVolumeTypes("");
        conn.setExcludedNodeTypes(
            "137,142,143,148,150,154,161,162,201,203,209,210,211");
        conn.setExcludedLocationNodes("13832");

        Session sess = conn.login();
        LivelinkTraversalManager lqtm =
            (LivelinkTraversalManager) sess.getTraversalManager();
        String excluded = lqtm.getExcluded();
        String included = lqtm.getIncluded();
        //System.out.println("testExcludedNodes8: included = " + included);
        //System.out.println("testExcludedNodes8: excluded = " + excluded);

        assertEquals((Object) "SubType not in " +
            "(137,142,143,148,150,154,161,162,201,203,209,210,211) and " +
            "DataID not in (select DataID from " + 
            "DTreeAncestors where AncestorID in (13832))", excluded);
    }    


    public void testExcludedNodes10() throws RepositoryException {
        conn.setExcludedVolumeTypes("148,162");
        conn.setExcludedNodeTypes(
            "137,142,143,148,150,154,161,162,201,203,209,210,211");
        conn.setExcludedLocationNodes("13832");

        Session sess = conn.login();
        LivelinkTraversalManager lqtm =
            (LivelinkTraversalManager) sess.getTraversalManager();
        String excluded = lqtm.getExcluded();
        String included = lqtm.getIncluded();
        //System.out.println("testExcludedNodes10: included = " + included);
        //System.out.println("testExcludedNodes10: excluded = " + excluded);

        assertEquals((Object) "SubType not in " +
            "(137,142,143,148,150,154,161,162,201,203,209,210,211) and " +
            "DataID not in (select DataID from " + 
            "DTreeAncestors where AncestorID in (13832))", excluded);

        assertTrue(included, included.indexOf("SubType not in (148,162)") != -1);
        assertTrue(included, included.indexOf("DTreeAncestors") != -1);
    }    
}
