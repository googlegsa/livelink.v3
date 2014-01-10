// Copyright (C) 2007-2009 Google Inc.
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

import com.google.enterprise.connector.spi.AuthorizationResponse;
import com.google.enterprise.connector.spi.Connector;
import com.google.enterprise.connector.spi.Document;
import com.google.enterprise.connector.spi.DocumentList;
import com.google.enterprise.connector.spi.Property;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.Session;
import com.google.enterprise.connector.spi.SimpleAuthenticationIdentity;
import com.google.enterprise.connector.spi.SpiConstants;
import com.google.enterprise.connector.spi.TraversalManager;

import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

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
  @Override
  protected void setUp() throws RepositoryException {
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
  private Collection<AuthorizationResponse> authorizeDocids(List<String> docids,
      String username) throws RepositoryException {
    return authManager.authorizeDocids(docids,
        new SimpleAuthenticationIdentity(username));
  }

  /**
   * Tests a couple of known docids for a couple of users.
   *
   * @throws RepositoryException if an error occurs
   */
  public void testAuthorizeDocids() throws RepositoryException {
    ArrayList<IdAuth> data = new ArrayList<IdAuth>();
    // Manually-created test docs (LL 95 on swift)
    data.add(new IdAuth("34314", false)); // not ok for llglobal - Rant.rtf in Admin Workspace
    data.add(new IdAuth("30792", true)); // ok
    data.add(new IdAuth("33040", true)); // ok Public Content ACL
    data.add(new IdAuth("33135", true)); // ok Public Content Default Group
    data.add(new IdAuth("33029", true)); // ok Public Access Document


    ArrayList<String> docids = new ArrayList<String>();
    for (int i = 0; i < data.size(); i++)
      docids.add(data.get(i).id);
    Collection<AuthorizationResponse> authzList =
        authorizeDocids(docids, "llglobal");
    mCheckForUser("llglobal", data, authzList);

    // Put Admin second to tell if we can open up permissions
    // after impersonating llglobal (in a test scenario where
    // the AuthManager uses a single client per instance
    // instead of a client per call to authorizeDocids). Yes,
    // it seems that we can.
    data.get(0).auth = true;
    authzList = authorizeDocids(docids, "Admin");
    mCheckForUser("Admin", data, authzList);
  }

  /**
   * Checks the expected results against the actual results.
   * SPI v1.0.1 change for authorizeDocids returns a collection
   * of only those docs that are authorized, so I modified the
   * test slightly.  "Expected" really becomes a baseline that
   * allows us to check that no authorized docs are missing from
   * the list and no unauthorized docs make it into the list.
   *
   * @param usernamme the current user whose permissions are being checked
   * @param baseline the expected results; a list of AuthIds
   * @param actual the results returned from authorizeDocids
   * @throws RepositoryException if an error occurs
   */
  private void mCheckForUser(String username, Collection<IdAuth> baseline,
      Collection<AuthorizationResponse> actual) throws RepositoryException {
    for (IdAuth authz : baseline) {
      String docid = authz.id;

      AuthorizationResponse found = null;
      for (AuthorizationResponse a : actual) {
        if (docid.equals(a.getDocid())) {
          assertNull("DocId " + docid + " appears in the " +
              "authorized list more than once.", found);
          found = a;
        }
      }

      if (authz.auth) {
        assertNotNull("DocId " + docid + " should have passed " +
            "authorization for user " + username +
            ", but apparently did not.", found);
      } else {
        assertNull("DocId " + docid + " should not have passed " +
            "authorization for user " + username +
            ", but apparently did.", found);
      }
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
   * the LivelinkDocumentListIterator. In particular, this test now
   * fetches all of the content, which slows it down considerably.
   */
  public void testPerformance(int dummy) throws Exception {
    // Traverse the repository for a bunch of ids.
    final int batchHint = 100000;
    TraversalManager tm = session.getTraversalManager();
    tm.setBatchHint(batchHint);
    DocumentList docList = tm.startTraversal();
    ArrayList<String> docids = new ArrayList<String>();
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
    IdAuth(String id, boolean auth) {
      this.id = id;
      this.auth = auth;
    }
  }
}
