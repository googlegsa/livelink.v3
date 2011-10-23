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

import com.google.enterprise.connector.otex.client.Client;
import com.google.enterprise.connector.otex.client.ClientFactory;
import com.google.enterprise.connector.spi.AuthenticationIdentity;
import com.google.enterprise.connector.spi.AuthorizationManager;
import com.google.enterprise.connector.spi.AuthorizationResponse;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.Session;

import junit.framework.TestCase;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

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

  LivelinkAuthorizationManager lam;

  Client client;

  public void setUp() throws RepositoryException {
    conn = LivelinkConnectorFactory.getConnector("connector.");
  }


  /** Continue the setup after initializing the connector. */
  protected void afterInit() throws RepositoryException {
    Session sess = conn.login();
    lam = (LivelinkAuthorizationManager) sess.getAuthorizationManager();
    ClientFactory clientFactory = conn.getClientFactory();
    client = clientFactory.createClient();
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

  /** Tests the default excluded volumes. */
  public void testExcludedVolumes1() throws RepositoryException {
    afterInit();

    assertEquals(0, lam.getExcludedVolumeId(402, null, client));
    assertEquals(9999, lam.getExcludedVolumeId(161, null, client));
  }

  /**
   * Tests the reverse of default excluded volumes, with the Undelete
   * volume but not the Workflow volume.
   */
  public void testExcludedVolumes2() throws RepositoryException {
    conn.setExcludedVolumeTypes("162,402,109");

    afterInit();

    assertEquals(9999, lam.getExcludedVolumeId(402, null, client));
    assertEquals(0, lam.getExcludedVolumeId(161, null, client));
  }

  /**
   * Tests a match against excludedLocationNodes.
   */
  public void testExcludedVolumes3() throws RepositoryException {
    conn.setExcludedVolumeTypes("162,402,109");
    conn.setExcludedLocationNodes("1729,13832");

    afterInit();

    assertEquals(9999, lam.getExcludedVolumeId(500, null, client));
  }

  /**
   * Tests no match against either excludedVolumeTypes or
   * excludedLocationNodes.
   */
  public void testExcludedVolumes4() throws RepositoryException {
    conn.setExcludedVolumeTypes("162,402,109");
    conn.setExcludedLocationNodes("2001,4104");

    afterInit();

    assertEquals(0, lam.getExcludedVolumeId(500, null, client));
  }

  /**
   * Tests an excluded volume type that does not exist.
   */
  public void testExcludedVolumes5() throws RepositoryException {
    conn.setExcludedVolumeTypes("162,402,666");

    afterInit();

    assertEquals(0, lam.getExcludedVolumeId(666, null, client));

    // But another subtype does exist...
    assertEquals(9999, lam.getExcludedVolumeId(402, null, client));
  }

  /**
   * Tests a volume excluded by ID that does not exist.
   */
  public void testExcludedVolumes6() throws RepositoryException {
    conn.setExcludedLocationNodes("1729,13832");

    afterInit();

    assertEquals(0, lam.getExcludedVolumeId(666, null, client));

    // But another subtype does exist...
    assertEquals(9999, lam.getExcludedVolumeId(667, null, client));
  }

  /** Tests the default authorization manager. */
  public void testDefaultAuthorizationManager() throws RepositoryException {
    LivelinkAuthorizationManager pluggable =
        new LivelinkAuthorizationManager();

    afterInit();

    assertNotNull(lam);
    assertNotSame(pluggable, lam);
  }

  /** Tests configuring a custom authorization manager. */
  public void testPluggableAuthorizationManager() throws RepositoryException {
    LivelinkAuthorizationManager pluggable =
        new LivelinkAuthorizationManager();
    conn.setAuthorizationManager(pluggable);

    afterInit();

    assertSame(pluggable, lam);
  }

  /**
   * Tests configuring a custom authorization manager that implements
   * AuthorizationManager but does not extend
   * LivelinkAuthorizationManager.
   */
  public void testPlainAuthorizationManager() throws RepositoryException {
    AuthorizationManager pluggable = new AuthorizationManager() {
        public Collection<AuthorizationResponse> authorizeDocids(
            Collection<String> docids, AuthenticationIdentity identity) {
          return Collections.<AuthorizationResponse>emptySet();
        }
      };
    conn.setAuthorizationManager(pluggable);

    Session sess = conn.login();
    AuthorizationManager authZ = sess.getAuthorizationManager();

    assertFalse(authZ.getClass().toString(),
        authZ instanceof LivelinkAuthorizationManager);
    assertSame(pluggable, authZ);
  }
}
