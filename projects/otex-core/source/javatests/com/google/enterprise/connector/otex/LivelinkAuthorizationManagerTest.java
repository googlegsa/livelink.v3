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

import java.util.Arrays;
import java.util.List;

import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.Session;

import junit.framework.TestCase;

/**
 * Tests the construction of the queries for authorizing documents.
 *
 * Note that many tests provide a candidates predicate of
 * <code>null</code>, which is invalid. This leads to the string
 * "null" appearing in the SQL query as a predicate, which is also
 * invalid. But for our purposes this is OK, since it shows the
 * candidates predicate being included in the query, which is all we
 * need.
 */
public class LivelinkAuthorizationManagerTest extends TestCase {
  private LivelinkConnector conn;
    
  public void setUp() throws RepositoryException {
    conn = LivelinkConnectorFactory.getConnector("connector.");
  }

  /**
   * Tests that showing hidden items doesn't include a query restriction.
   *
   * @throws RepositoryException if an unexpected error occurs
   */
  public void testShowHiddenItems() throws RepositoryException {
    // This just needs to be a non-empty array of strings.
    List<String> docids = Arrays.asList(new String[] { "42" });

    Session sess = conn.login();
    LivelinkAuthorizationManager lam =
        (LivelinkAuthorizationManager) sess.getAuthorizationManager();
    String query = lam.getDocidQuery(docids.iterator());

    assertTrue(query, query.indexOf("Catalog") == -1);
  }

  /**
   * Tests that subtype numbers in the showHiddenItems property are
   * ignored.
   *
   * @throws RepositoryException if an unexpected error occurs
   */
  public void testHiddenItemSubtypes() throws RepositoryException {
    // This just needs to be a non-empty array of strings.
    List<String> docids = Arrays.asList(new String[] { "42" });

    conn.setShowHiddenItems("false");

    Session sess = conn.login();
    LivelinkAuthorizationManager lam =
        (LivelinkAuthorizationManager) sess.getAuthorizationManager();
    String query = lam.getDocidQuery(docids.iterator());

    assertTrue(query, query.indexOf("Catalog = 2") != -1);
        
    conn.setShowHiddenItems("{ 1234 }");

    sess = conn.login();
    lam = (LivelinkAuthorizationManager) sess.getAuthorizationManager();
    String query2 = lam.getDocidQuery(docids.iterator());

    assertEquals((Object) query, query2);
  }
}
