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

public class LivelinkQueryTraversalManagerTest extends TestCase {
    private LivelinkConnector conn;
    
    public void setUp() {
        conn = new LivelinkConnector("com.google.enterprise.connector." +
            "otex.client.mock.MockClientFactory");
    }

    
    public void testExcludedNodes1() throws RepositoryException {
        // No excluded nodes configured.

        Session sess = conn.login();
        LivelinkQueryTraversalManager lqtm =
            (LivelinkQueryTraversalManager) sess.getQueryTraversalManager();
        String excluded = lqtm.getExcluded();

        assertNull(excluded, excluded);
    }    


    public void testExcludedNodes2() throws RepositoryException {
        conn.setExcludedNodeTypes("");
        conn.setExcludedVolumeTypes("");
        conn.setExcludedLocationNodes("");

        Session sess = conn.login();
        LivelinkQueryTraversalManager lqtm =
            (LivelinkQueryTraversalManager) sess.getQueryTraversalManager();
        String excluded = lqtm.getExcluded();

        assertNull(excluded, excluded);
    }    


    public void testExcludedNodes3() throws RepositoryException {
        conn.setExcludedVolumeTypes("2001,4104");

        Session sess = conn.login();
        LivelinkQueryTraversalManager lqtm =
            (LivelinkQueryTraversalManager) sess.getQueryTraversalManager();
        String excluded = lqtm.getExcluded();

        assertNull(excluded, excluded);
    }    


    public void testExcludedNodes4() throws RepositoryException {
        conn.setExcludedNodeTypes(
            "137,142,143,148,150,154,161,162,201,203,209,210,211");

        Session sess = conn.login();
        LivelinkQueryTraversalManager lqtm =
            (LivelinkQueryTraversalManager) sess.getQueryTraversalManager();
        String excluded = lqtm.getExcluded();

        assertEquals((Object) "SubType not in " +
            "(137,142,143,148,150,154,161,162,201,203,209,210,211)", excluded);
    }    


    public void testExcludedNodes5() throws RepositoryException {
        conn.setExcludedVolumeTypes("148,162");

        Session sess = conn.login();
        LivelinkQueryTraversalManager lqtm =
            (LivelinkQueryTraversalManager) sess.getQueryTraversalManager();
        String excluded = lqtm.getExcluded();

        assertTrue(excluded, excluded.indexOf("SubType not in") == -1);
        assertTrue(excluded, excluded.indexOf("DTreeAncestors") != -1);
    }    


    public void testExcludedNodes6() throws RepositoryException {
        conn.setExcludedLocationNodes("13832");

        Session sess = conn.login();
        LivelinkQueryTraversalManager lqtm =
            (LivelinkQueryTraversalManager) sess.getQueryTraversalManager();
        String excluded = lqtm.getExcluded();

        assertEquals((Object) "DataID not in (select DataID from " + 
            "DTreeAncestors where AncestorID in (13832))", excluded);

        conn.setExcludedVolumeTypes("2001,4104");
        conn.setExcludedLocationNodes("13832");

        sess = conn.login();
        lqtm = (LivelinkQueryTraversalManager) sess.getQueryTraversalManager();
        String excluded2 = lqtm.getExcluded();

        assertEquals((Object) excluded, excluded2);
    }    


    public void testExcludedNodes7() throws RepositoryException {
        conn.setExcludedNodeTypes(
            "137,142,143,148,150,154,161,162,201,203,209,210,211");
        conn.setExcludedVolumeTypes("148,162");

        Session sess = conn.login();
        LivelinkQueryTraversalManager lqtm =
            (LivelinkQueryTraversalManager) sess.getQueryTraversalManager();
        String excluded = lqtm.getExcluded();

        assertTrue(excluded, excluded.startsWith("SubType not in " +
            "(137,142,143,148,150,154,161,162,201,203,209,210,211)"));
        assertTrue(excluded, excluded.indexOf("DTreeAncestors") != -1);
    }    


    public void testExcludedNodes8() throws RepositoryException {
        conn.setExcludedVolumeTypes("148,162");
        conn.setExcludedLocationNodes("13832");

        Session sess = conn.login();
        LivelinkQueryTraversalManager lqtm =
            (LivelinkQueryTraversalManager) sess.getQueryTraversalManager();
        String excluded = lqtm.getExcluded();

        assertTrue(excluded, excluded.indexOf("SubType not in") == -1);
        assertTrue(excluded, excluded.indexOf("DTreeAncestors") != -1);
        assertTrue(excluded, excluded.endsWith(",13832))"));
    }    


    public void testExcludedNodes9() throws RepositoryException {
        conn.setExcludedNodeTypes(
            "137,142,143,148,150,154,161,162,201,203,209,210,211");
        conn.setExcludedLocationNodes("13832");

        Session sess = conn.login();
        LivelinkQueryTraversalManager lqtm =
            (LivelinkQueryTraversalManager) sess.getQueryTraversalManager();
        String excluded = lqtm.getExcluded();

        assertEquals((Object) "SubType not in " +
            "(137,142,143,148,150,154,161,162,201,203,209,210,211) and " +
            "DataID not in (select DataID from " + 
            "DTreeAncestors where AncestorID in (13832))", excluded);
    }    


    public void testExcludedNodes10() throws RepositoryException {
        conn.setExcludedNodeTypes(
            "137,142,143,148,150,154,161,162,201,203,209,210,211");
        conn.setExcludedVolumeTypes("148,162");
        conn.setExcludedLocationNodes("13832");

        Session sess = conn.login();
        LivelinkQueryTraversalManager lqtm =
            (LivelinkQueryTraversalManager) sess.getQueryTraversalManager();
        String excluded = lqtm.getExcluded();

        assertTrue(excluded, excluded.startsWith("SubType not in " +
            "(137,142,143,148,150,154,161,162,201,203,209,210,211) and " +
            "DataID not in (select DataID from " + 
            "DTreeAncestors where AncestorID in ("));
        assertTrue(excluded, excluded.endsWith(",13832))"));
    }    
}
