// Copyright (C) 2006-2007 Google Inc.

package com.google.enterprise.connector.otex;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import junit.framework.TestCase;

import com.google.enterprise.connector.spi.Connector;
import com.google.enterprise.connector.spi.Property;
import com.google.enterprise.connector.spi.PropertyMap;
import com.google.enterprise.connector.spi.QueryTraversalManager;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.ResultSet;
import com.google.enterprise.connector.spi.Session;
import com.google.enterprise.connector.spi.SpiConstants;
import com.google.enterprise.connector.spi.Value;
import com.google.enterprise.connector.spi.ValueType;

/**
 * Tests the LivelinkAuthorizationManager.
 */
/* Until we have a fixed set of test data, this isn't much of a
 * test, since it really needs to know the ids of documents which
 * a given user can/can't see.
 */
public class LivelinkAuthorizationManagerTest extends TestCase {

    /** The Connector. */
    private LivelinkConnector conn;

    /** The Session. */
    private LivelinkSession session;

    /** The AuthorizationManager. */
    private LivelinkAuthorizationManager authManager;
        

    /**
     * Establishes a session and obtains an AuthorizationManager
     * for testing. 
     *
     * @throws RepositoryException if login fails
     */
    public void setUp() throws RepositoryException {
        conn = new LivelinkConnector();
        conn.setHostname(System.getProperty("connector.hostname"));
        try {
            conn.setPort(Integer.parseInt(
                             System.getProperty("connector.port")));
        } catch (NumberFormatException e) {
            // TODO
        }
        conn.setUsername(System.getProperty("connector.username"));
        //System.out.println("session user: " + 
        //    System.getProperty("connector.username"));
        conn.setPassword(System.getProperty("connector.password"));
        conn.setDisplayUrl(System.getProperty("connector.displayUrl"));
        session = (LivelinkSession) conn.login();
        authManager = (LivelinkAuthorizationManager) session.
            getAuthorizationManager();
    }


    /**
     * Tests a couple of known docids for a couple of users.
     *
     * @throws RepositoryException if an error occurs
     */
    public void testAuthorizeDocids() throws RepositoryException {
        ArrayList data = new ArrayList();
        // Manually-created test docs
        data.add(new IdAuth("553119", false)); // not ok for llglobal
        data.add(new IdAuth("555100", true)); // ok

        ArrayList docids = new ArrayList();
        for (int i = 0; i < data.size(); i++)
            docids.add(((IdAuth) data.get(i)).id);
        ResultSet rs; 
        rs = authManager.authorizeDocids(docids, "llglobal");
        mCheckForUser("llglobal", data, rs); 

        // Put Admin second to tell if we can open up permissions
        // after impersonating llglobal (in a test scenario where
        // the AuthManager uses a single client per instance
        // instead of a client per call to authorizeDocids). Yes,
        // it seems that we can.
        ((IdAuth) data.get(0)).auth = true;
        rs = authManager.authorizeDocids(docids, "Admin");
        mCheckForUser("Admin", data, rs); 
    }


    /**
     * Checks the expected results against the actual results.
     *
     * @param usernamme the current user whose permissions are being checked
     * @param expected the expected results; a list of AuthIds
     * @param actual the results returned from authorizeDocids
     * @throws RepositoryException if an error occurs
     */
    private void mCheckForUser(String username, List expected,
            ResultSet actual) throws RepositoryException {
        int i = 0;
        for (Iterator a = actual.iterator(); a.hasNext(); i++) {
            PropertyMap map = (PropertyMap) a.next();
            String docid = map.getProperty(
                SpiConstants.PROPNAME_DOCID).getValue().getString(); 
            boolean auth = map.getProperty(
                SpiConstants.PROPNAME_AUTH_VIEWPERMIT).getValue().
                getBoolean(); 
            String expectedDocid = ((IdAuth) expected.get(i)).id;
            boolean expectedAuth = ((IdAuth) expected.get(i)).auth;

            //System.out.println(docid + " " + auth + " ( " + expectedAuth +
            //    " )"); 
            assertEquals(username, expectedDocid, docid); 
            assertEquals(username, expectedAuth, auth); 
        }
    }


    /**
     * This isn't really a test, just a timing check.
     */
    public void testPerformance() throws Exception {
        // Traverse the repository for a bunch of ids.
        final int batchHint = 100000;
        QueryTraversalManager qtm = session.getQueryTraversalManager();
        qtm.setBatchHint(batchHint);
        ResultSet rs = qtm.startTraversal();
        ArrayList docids = new ArrayList();
        for (Iterator i = rs.iterator(); i.hasNext(); ) {
            Property p = ((PropertyMap) i.next()).getProperty(
                SpiConstants.PROPNAME_DOCID); 
            docids.add(p.getValue().getString()); 
        }

        long start = 0;
        long end = 0; 

        start = System.currentTimeMillis();
        authManager.authorizeDocids(docids, "Admin");
        end = System.currentTimeMillis();
        System.out.println("authorizeDocids (Admin): docs/time = " + 
            docids.size() + "/" + (end - start)); 

        start = System.currentTimeMillis();
        authManager.authorizeDocids(docids, "llglobal");
        end = System.currentTimeMillis();
        System.out.println("authorizeDocids (llglobal): docs/time = " + 
            docids.size() + "/" + (end - start)); 

        start = System.currentTimeMillis();
        authManager.authorizeDocids(docids, "llglobal-external");
        end = System.currentTimeMillis();
        System.out.println(
            "authorizeDocids (llglobal-external): docs/time = " + 
            docids.size() + "/" + (end - start)); 
    }


    /**
     * Utility to hold a docid and the expected authorization for it.
     */
    private static class IdAuth {

        /** The id. */
        String id;

        /** The expected authorization result. */
        boolean auth;

        /**
         * Caches the information.
         *
         * @param id the docid
         * @param auth the authorization
         */
        IdAuth(String id, boolean auth)
        {
            this.id = id;
            this.auth = auth;
        }
    }
}
