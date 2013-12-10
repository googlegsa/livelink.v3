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

import com.google.common.collect.ImmutableSet;
import com.google.enterprise.connector.otex.client.Client;
import com.google.enterprise.connector.otex.client.ClientFactory;
import com.google.enterprise.connector.spi.AuthenticationIdentity;
import com.google.enterprise.connector.spi.AuthorizationManager;
import com.google.enterprise.connector.spi.AuthorizationResponse;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.Session;
import com.google.enterprise.connector.spi.SimpleAuthenticationIdentity;

import junit.framework.TestCase;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
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
  private final JdbcFixture jdbcFixture = new JdbcFixture();

  private LivelinkConnector conn;

  LivelinkAuthorizationManager lam;

  Client client;

  @Override
  protected void setUp() throws SQLException, RepositoryException {
    jdbcFixture.setUp();
    jdbcFixture.executeUpdate(
        // TODO(jlacey): Include permission checking in MockClient.ListNodes.
        "insert into DTree(Name, DataID, ParentID, OwnerID, SubType) "
        + "values('Workflow', 1729, -1, -1729, 161)",
        "insert into DTree(Name, DataID, ParentID, OwnerID, SubType) "
        + "values('Livelink Undelete Workspace', 13832, -1, -13832, 402)",
        "insert into DTree(DataID, ParentID, OwnerID, SubType) "
        + "values(9999, -1, -9999, 500)",
        "insert into DTree(DataID, ParentID, OwnerID, SubType) "
        + "values(6667, -1, -6667, 667)",
        "insert into DTreeAncestors(DataID, AncestorID) "
        + "values(4104, 2002)", // The values do not matter.
        "insert into DTree(DataID, ParentID, OwnerID, SubType) "
        + "values(2100, 2000, -2000, 144)",
        "insert into DTree(DataID, ParentID, OwnerID, SubType, Catalog) "
        + "values(2101, 2000, -2000, 144, 2)",
        "insert into DTree(DataID, ParentID, OwnerID, SubType) "
        + "values(2102, 2000, -1729, 144)",
        "insert into DTree(DataID, ParentID, OwnerID, SubType) "
        + "values(3299, 2000, -2000, 144)");

    conn = LivelinkConnectorFactory.getConnector("connector.");
  }

  @Override
  protected void tearDown() throws SQLException {
    jdbcFixture.tearDown();
  }

  /** Continue the setup after initializing the connector. */
  protected void afterInit() throws RepositoryException {
    Session sess = conn.login();
    lam = (LivelinkAuthorizationManager) sess.getAuthorizationManager();
    ClientFactory clientFactory = conn.getClientFactory();
    client = clientFactory.createClient();
  }

  public void testGetDocids() throws RepositoryException {
    final String docid = "hello, world";
    Iterator<String> it = Collections.nCopies(1001, docid).iterator();

    afterInit();

    // The first request returns 1000 comma-separated copies of the docid.
    String docids = lam.getDocids(it);
    assertNotNull(docids);
    assertEquals(docid.length() * 1000 + 999, docids.length());

    // The second request has just one docid left.
    assertEquals(docid, lam.getDocids(it));

    // The third request has none left.
    assertNull(lam.getDocids(it));
  }

  /**
   * Tests that showing hidden items doesn't include a query restriction.
   *
   * @throws RepositoryException if an unexpected error occurs
   */
  public void testShowHiddenItems_true() throws RepositoryException {
    afterInit();

    AuthenticationIdentity identity = new SimpleAuthenticationIdentity("fred");
    Collection<AuthorizationResponse> responses =
        lam.authorizeDocids(ImmutableSet.of("2100", "2101"),
            identity);
    assertPermittedDocs(ImmutableSet.of("2100", "2101"), responses);
  }

  public void testShowHiddenItems_false() throws RepositoryException {
    conn.setShowHiddenItems("false");
    afterInit();

    AuthenticationIdentity identity = new SimpleAuthenticationIdentity("fred");
    Collection<AuthorizationResponse> responses =
        lam.authorizeDocids(ImmutableSet.of("2100", "2101"),
            identity);
    assertPermittedDocs(ImmutableSet.of("2100"), responses);
  }

  /**
   * Tests that subtype numbers in the showHiddenItems property are
   * ignored.
   *
   * @throws RepositoryException if an unexpected error occurs
   */
  public void testHiddenItemSubtypes() throws RepositoryException {
    // Treated as false, does not ignore documents.
    conn.setShowHiddenItems("{ 144 }");
    afterInit();

    AuthenticationIdentity identity = new SimpleAuthenticationIdentity("fred");
    Collection<AuthorizationResponse> responses =
        lam.authorizeDocids(ImmutableSet.of("2100", "2101"),
            identity);
    assertPermittedDocs(ImmutableSet.of("2100"), responses);
  }

  /** Tests the default excluded volumes. */
  public void testExcludedVolumes1() throws RepositoryException {
    afterInit();

    assertEquals(0, lam.getExcludedVolumeId(402, null, client));
    assertEquals(1729, lam.getExcludedVolumeId(161, null, client));
  }

  /**
   * Tests the reverse of default excluded volumes, with the Undelete
   * volume but not the Workflow volume.
   */
  public void testExcludedVolumes2() throws RepositoryException {
    conn.setExcludedVolumeTypes("162,402,109");

    afterInit();

    assertEquals(13832, lam.getExcludedVolumeId(402, null, client));
    assertEquals(0, lam.getExcludedVolumeId(161, null, client));
  }

  /**
   * Tests a match against excludedLocationNodes.
   */
  public void testExcludedVolumes3() throws RepositoryException {
    conn.setExcludedVolumeTypes("162,402,109");
    conn.setExcludedLocationNodes("9999");

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
    assertEquals(13832, lam.getExcludedVolumeId(402, null, client));
  }

  /**
   * Tests a volume excluded by ID that does not exist.
   */
  public void testExcludedVolumes6() throws RepositoryException {
    conn.setExcludedLocationNodes("6667");

    afterInit();

    assertEquals(0, lam.getExcludedVolumeId(666, null, client));

    // But another subtype does exist...
    assertEquals(6667, lam.getExcludedVolumeId(667, null, client));
  }

  /** Tests a small authZ request with partially authorized results. */
  public void testAuthorizeDocids_small() throws RepositoryException {
    afterInit();

    AuthenticationIdentity identity = new SimpleAuthenticationIdentity("fred");
    Collection<AuthorizationResponse> responses =
        lam.authorizeDocids(ImmutableSet.of("2100", "2101", "2102", "2103"),
            identity);
    assertPermittedDocs(ImmutableSet.of("2100", "2101"), responses);
  }

  /**
   * Tests a large authZ request that will be split across queries
   * (1000 docids per query), with partially authorized results.
   */
  public void testAuthorizeDocids_large() throws RepositoryException {
    afterInit();

    AuthenticationIdentity identity = new SimpleAuthenticationIdentity("fred");
    ImmutableSet.Builder<String> builder = new ImmutableSet.Builder<String>();
    for (int i = 2100; i < 3300; i++) {
      builder.add(String.valueOf(i));
    }
    Collection<AuthorizationResponse> responses =
        lam.authorizeDocids(builder.build(), identity);
    assertPermittedDocs(ImmutableSet.of("2100", "2101", "3299"),
        responses);
  }

  private void assertPermittedDocs(ImmutableSet<String> expected,
      Collection<AuthorizationResponse> responses) {
    ImmutableSet.Builder<String> builder = new ImmutableSet.Builder<String>();
    for (AuthorizationResponse response : responses) {
      if (response.isValid()) {
        builder.add(response.getDocid());
      }
    }
    assertEquals(expected, builder.build());
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
        @Override public Collection<AuthorizationResponse> authorizeDocids(
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
