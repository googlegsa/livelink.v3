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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import junit.framework.TestCase;

import com.google.enterprise.connector.spi.AuthenticationIdentity;
import com.google.enterprise.connector.spi.AuthorizationResponse;
import com.google.enterprise.connector.spi.Connector;
import com.google.enterprise.connector.spi.Document;
import com.google.enterprise.connector.spi.DocumentList;
import com.google.enterprise.connector.spi.Property;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.Session;
import com.google.enterprise.connector.spi.SpiConstants;
import com.google.enterprise.connector.spi.TraversalManager;
import com.google.enterprise.connector.spi.Value;


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
        conn = LivelinkConnectorFactory.getConnector("connector."); 
        session = (LivelinkSession) conn.login();
        authManager = (LivelinkAuthorizationManager) session.
            getAuthorizationManager();
    }


    /**
     * Authorizes a user for a list of documents.
     * 
     * @param docids a list of document IDs
     * @param username the username to authorize
     */
    private List authorizeDocids(List docids, final String username)
            throws RepositoryException {
        AuthenticationIdentity identity = new AuthenticationIdentity() {
                public String getUsername() { return username; }
                public String getPassword() { return null; }
            };
        return authManager.authorizeDocids(docids, identity);
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
        List authzList; 
        authzList = authorizeDocids(docids, "llglobal");
        mCheckForUser("llglobal", data, authzList); 

        // Put Admin second to tell if we can open up permissions
        // after impersonating llglobal (in a test scenario where
        // the AuthManager uses a single client per instance
        // instead of a client per call to authorizeDocids). Yes,
        // it seems that we can.
        ((IdAuth) data.get(0)).auth = true;
        authzList = authorizeDocids(docids, "Admin");
        mCheckForUser("Admin", data, authzList); 
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
            List actual) throws RepositoryException {
        int i = 0;
        for (Iterator a = actual.iterator(); a.hasNext(); i++) {
            AuthorizationResponse authz = (AuthorizationResponse) a.next();
            String docid = authz.getDocid(); 
            boolean auth = authz.isValid(); 
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
    /*
     * FIXME: I've added a dummy parameter to prevent this test from
     * running. When it was written, each Property was produced on
     * demand, so this code, which only asks for the docid, was fast.
     * Now all of the properties for each Document are created, by
     * the LivelinkResultSetIterator. In particular, this test now
     * fetches all of the content, which slows it down considerably.
     */
    public void testPerformance(int dummy) throws Exception {
        // Traverse the repository for a bunch of ids.
        final int batchHint = 100000;
        TraversalManager tm = session.getTraversalManager();
        tm.setBatchHint(batchHint);
        DocumentList docList = tm.startTraversal();
        ArrayList docids = new ArrayList();
        Document doc;
        while ((doc = docList.nextDocument()) != null) {
            Property p = doc.findProperty(SpiConstants.PROPNAME_DOCID);
            docids.add(p.nextValue().toString());
        }

        long start = 0;
        long end = 0; 

        start = System.currentTimeMillis();
        authorizeDocids(docids, "Admin");
        end = System.currentTimeMillis();
        long adminTime = end - start;
        //System.out.println("authorizeDocids (Admin): docs/time = " + 
        //    docids.size() + "/" + adminTime); 

        start = System.currentTimeMillis();
        authorizeDocids(docids, "llglobal");
        end = System.currentTimeMillis();
        long llglobalTime = end - start;
        //System.out.println("authorizeDocids (llglobal): docs/time = " + 
        //    docids.size() + "/" + llglobalTime); 

        start = System.currentTimeMillis();
        authorizeDocids(docids, "llglobal-external");
        end = System.currentTimeMillis();
        long llglobalExternalTime = end - start;
        //System.out.println(
        //    "authorizeDocids (llglobal-external): docs/time = " + 
        //    docids.size() + "/" + llglobalExternalTime); 
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
