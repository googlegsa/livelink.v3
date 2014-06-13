// Copyright 2010 Google Inc.
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

import static com.google.enterprise.connector.spi.SpiConstants.CaseSensitivityType.EVERYTHING_CASE_SENSITIVE;
import static com.google.enterprise.connector.spi.SpiConstants.PrincipalType.UNKNOWN;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.isA;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

import com.google.common.collect.ImmutableSet;
import com.google.enterprise.connector.otex.client.Client;
import com.google.enterprise.connector.otex.client.ClientFactory;
import com.google.enterprise.connector.otex.client.ClientValue;
import com.google.enterprise.connector.otex.client.mock.MockClient;
import com.google.enterprise.connector.otex.client.mock.MockClientValue;
import com.google.enterprise.connector.otex.client.mock.MockConstants;
import com.google.enterprise.connector.spi.Document;
import com.google.enterprise.connector.spi.DocumentList;
import com.google.enterprise.connector.spi.Principal;
import com.google.enterprise.connector.spi.Property;
import com.google.enterprise.connector.spi.RepositoryDocumentException;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.SpiConstants;
import com.google.enterprise.connector.spiimpl.PrincipalValue;

import junit.framework.TestCase;

import java.sql.SQLException;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

public class LivelinkDocumentListTest extends TestCase {
  private static final int USER_ID = 1999;
  private static final int GROUP_ID = 2999;

  private static final String GLOBAL_NAMESPACE = "globalNS";
  private static final String LOCAL_NAMESPACE = "localNS";

  private final JdbcFixture jdbcFixture = new JdbcFixture();

  @Override
  protected void setUp() throws SQLException {
    jdbcFixture.setUp();
    // the tests use following for id values
    // doc id - 1 to 999
    // user id - 1000 to 1999 
    // group id - 2000 to 2999 
    jdbcFixture.executeUpdate(
        "insert into KUAF(ID, Name, Type, GroupID, UserData) "
            + " values(1001, 'user1', 0, 2001, "
            + "'ExternalAuthentication=true' )",
        "insert into KUAF(ID, Name, Type, GroupID, UserData) "
            + " values(1002, 'user2', 0, 2001, "
            + "'ExternalAuthentication=true' )",
        "insert into KUAF(ID, Name, Type, GroupID, UserData) "
            + " values(1003, 'user3', 0, 2002, NULL )",
        "insert into KUAF(ID, Name, Type, GroupID, UserData) "
            + " values(1666, '', 0, 0, NULL )",
        "insert into KUAF(ID, Name, Type, GroupID, UserData) "
            + " values(2001, 'group1', 1, 0, NULL )",
        "insert into KUAF(ID, Name, Type, GroupID, UserData) "
            + " values(2002, 'group2', 1, 0, NULL )");
  }

  @Override
  protected void tearDown() throws SQLException {
    jdbcFixture.tearDown();
  }

  /**
   * Creates a default connector instance.
   *
   * @return a new LivelinkConnector instance
   */
  private LivelinkConnector getConnector()
      throws RepositoryException {
    return getConnector(null, null);
  }

  /**
   * Creates a connector instance with an optional custom property.
   *
   * @param property an optional property name, may be null
   * @param value a property value
   * @return a new LivelinkConnector instance
   */
  private LivelinkConnector getConnector(String property, String value)
      throws RepositoryException {
    LivelinkConnector connector = new LivelinkConnector(
        "com.google.enterprise.connector.otex.client.mock.MockClientFactory");
    connector.setServer(System.getProperty("connector.server"));
    connector.setPort(System.getProperty("connector.port"));
    connector.setUsername(System.getProperty("connector.username"));
    connector.setPassword(System.getProperty("connector.password"));
    connector.setDisplayUrl("http://localhost/");
    connector.setDisplayPatterns(Collections.singletonMap("default", ""));
    connector.setShowHiddenItems("true");
    connector.setIncludedCategories("all,searchable");
    connector.setExcludedCategories("none");
    connector.setFeedType("content");
    connector.setUnsupportedFetchVersionTypes("");
    connector.setGoogleGlobalNamespace(GLOBAL_NAMESPACE);
    connector.setGoogleLocalNamespace(LOCAL_NAMESPACE);

    if (property != null) {
      if (property.equals("publicContentUsername")) {
        connector.setPublicContentUsername(value);
        connector.setPublicContentAuthorizationManager(
            new LivelinkAuthorizationManager());
      } else if (property.equals("unsupportedFetchVersionTypes")) {
        connector.setUnsupportedFetchVersionTypes(value);
      }
    }

    connector.login();
    return connector;
  }

  /**
   * Creates a LivelinkDocumentList containing documents for the tests.
   *
   * @param docInfo the object IDs (DataID) for the returned documents,
   *     usually one of the values from MockConstants, alternating with
   *     the file sizes (DataSize) for the returned documents
   */
  private DocumentList getObjectUnderTest(int... docInfo)
      throws RepositoryException {
    LivelinkConnector connector = getConnector();

    return getObjectUnderTest(connector, docInfo);
  }

  /**
   * Creates a LivelinkDocumentList containing documents for the tests.
   *
   * @param connector the connector to use, instead of instantiating a
   *     generic one automatically
   * @param docInfo the object IDs (DataID) for the returned documents,
   *     usually one of the values from MockConstants, alternating with
   *     the file sizes (DataSize) for the returned documents
   */
  private DocumentList getObjectUnderTest(LivelinkConnector connector,
      int... docInfo) throws RepositoryException {
    ClientFactory clientFactory = connector.getClientFactory();
    Client client = clientFactory.createClient();

    return getObjectUnderTest(connector, client, docInfo);
  }

  /**
   * Creates a LivelinkDocumentList containing documents for the tests.
   *
   * @param client the client to use, instead of getting one from the connector
   * @param docInfo the object IDs (DataID) for the returned documents,
   *     usually one of the values from MockConstants, alternating with
   *     the file sizes (DataSize) for the returned documents
   */
  private DocumentList getObjectUnderTest(Client client, int... docInfo)
      throws RepositoryException {
    LivelinkConnector connector = getConnector();

    return getObjectUnderTest(connector, client, docInfo);
  }

  /** Helper method for two of the other overloads. */
  private DocumentList getObjectUnderTest(LivelinkConnector connector,
      Client client, int... docInfo) throws RepositoryException {
    ContentHandler contentHandler = new FileContentHandler();
    return getObjectUnderTest(connector, client, contentHandler, docInfo);
  }

  /**
   * Creates a LivelinkDocumentList containing documents for the tests.
   *
   * @param contentHandler the content handler to use, instead of
   *     {@code FileContentHandler}
   */
  private DocumentList getObjectUnderTest(ContentHandler contentHandler)
      throws RepositoryException {
    LivelinkConnector connector = getConnector();
    ClientFactory clientFactory = connector.getClientFactory();
    Client client = clientFactory.createClient();

    return getObjectUnderTest(connector, client, contentHandler,
        MockConstants.IO_OBJECT_ID, 0, USER_ID);
  }

  /** Helper method for the other overloads. */
  private DocumentList getObjectUnderTest(LivelinkConnector connector,
      Client client, ContentHandler contentHandler, int... docInfo)
      throws RepositoryException {
    contentHandler.initialize(connector, client);

    final String[] FIELDS = {
      "ModifyDate", "DataID", "OwnerID", "SubType", "MimeType", "DataSize",
      "UserID", "UserData" };
    assertEquals(String.valueOf(docInfo.length), 0, docInfo.length % 3);
    Object[][] values = new Object[docInfo.length / 3][];
    for (int i = 0; i < docInfo.length / 3; i++) {
      int objectId = docInfo[3 * i];
      int dataSize = docInfo[3 * i + 1];
      int userId = docInfo[3 * i + 2];
      values[i] = new Object[] {
        new Date(), objectId, 2000, 144, "text/plain", dataSize, userId, null };
    }
    ClientValue recArray = new MockClientValue(FIELDS, values);

    Field[] fields = new Field[FIELDS.length];
    for (int i = 0; i < fields.length; i++) {
      fields[i] = new Field(FIELDS[i], FIELDS[i]);
    }
    Checkpoint checkpoint = new Checkpoint();

    return new LivelinkDocumentList(connector, client,
        contentHandler, recArray, fields, null, null, checkpoint,
        connector.getUsername());
  }

  public void testContentHandler() throws RepositoryException {
    ContentHandler contentHandler = createMock(ContentHandler.class);
    contentHandler.initialize(isA(LivelinkConnector.class), isA(Client.class));
    replay(contentHandler);
    DocumentList list = getObjectUnderTest(contentHandler);
    verify(contentHandler);
  }

  public void testRefreshableContentHandler() throws RepositoryException {
    RefreshableContentHandler contentHandler =
        createMock(RefreshableContentHandler.class);
    contentHandler.initialize(isA(LivelinkConnector.class), isA(Client.class));
    contentHandler.refresh();
    replay(contentHandler);
    DocumentList list = getObjectUnderTest(contentHandler);
    verify(contentHandler);
  }

  /**
   * Tests a successful document, in this case by asking for an empty
   * document and skipping FetchVersion.
   */
  public void testNextDocument_emptyContent() throws RepositoryException {
    DocumentList list =
        getObjectUnderTest(MockConstants.IO_OBJECT_ID, 0, USER_ID);

    Document doc = list.nextDocument();
    assertNotNull(doc);
    assertNotNull(list.checkpoint());
  }

  /**
   * Tests a document where FetchVersion should throw an I/O exception
   * and be retried.
   */
  public void testNextDocument_exception() throws RepositoryException {
    DocumentList list =
        getObjectUnderTest(MockConstants.IO_OBJECT_ID, 1, USER_ID);

    try {
      Document doc = list.nextDocument();
      fail("Expected a LivelinkIOException");
    } catch (RepositoryDocumentException e) {
      fail("Did not expect a RepositoryDocumentException");
    } catch (LivelinkIOException e) {
      assertNull(list.checkpoint());
    }
  }

  /**
   * Tests a document where FetchVersion should throw an I/O exception
   * but unsupportedFetchVersionTypes is configured to skip it.
   */
  public void testNextDocument_unsupportedFetchVersionTypes()
      throws RepositoryException {
    LivelinkConnector connector =
        getConnector("unsupportedFetchVersionTypes", "144");
    DocumentList list =
        getObjectUnderTest(connector, MockConstants.IO_OBJECT_ID, 1, USER_ID);

    Document doc = list.nextDocument();
    assertNotNull(doc);
    assertNotNull(list.checkpoint());
  }

  /**
   * Tests a document list where the first document is fine and for
   * the second document, FetchVersion should throw an I/O exception.
   * Since we've already processed one document, we just expect
   * nextDocument to return null in this case, unlike the previous
   * case where the LivelinkIOException was rethrown.
   */
  public void testNextDocument_partial() throws RepositoryException {
    DocumentList list = getObjectUnderTest(
        MockConstants.HARMLESS_OBJECT_ID, 0, USER_ID,
        MockConstants.IO_OBJECT_ID, 1, USER_ID);

    Document doc = list.nextDocument();
    assertNotNull(doc);
    String checkpoint = list.checkpoint();
    assertNotNull(checkpoint);

    assertNull(list.nextDocument());
    assertEquals(checkpoint, list.checkpoint());
  }

  /**
   * Tests a document where FetchVersion should throw a
   * RepositoryDocumentException and be skipped.
   */
  public void testNextDocument_skipped() throws RepositoryException {
    // This client throws an exception if GetCurrentUserID is called,
    // since that is used to discriminate between document and
    // transient exceptions, and we are expecting a document
    // exception.
    PingErrorClient client = new PingErrorClient();
    DocumentList list = getObjectUnderTest(client,
        MockConstants.DOCUMENT_OBJECT_ID, 1, USER_ID);

    try {
      Document doc = list.nextDocument();
      fail("Expected a RepositoryDocumentException");
    } catch (RepositoryDocumentException e) {
      assertFalse(client.isThrown());
      assertNotNull(list.checkpoint());
    }
  }

  private static class PingErrorClient extends MockClient {
    private boolean isThrown = false;

    public boolean isThrown() {
      return isThrown;
    }

    @Override
    public int GetCurrentUserID() throws RepositoryException {
      isThrown = true;
      throw new RepositoryException();
    }
  }

  /**
   * Tests a pair of documents where FetchVersion should throw a
   * RepositoryDocumentException on the first and a repository
   * exception with a client that will throw an exception on the
   * GetCurrentUserID discriminator on the second. Previously, this
   * would throw an exception from the batch rather than return null
   * to retry the batch starting with the second document.
   */
  public void testNextDocument_document_fail() throws RepositoryException {
    PingErrorClient client = new PingErrorClient();
    DocumentList list = getObjectUnderTest(client,
        MockConstants.DOCUMENT_OBJECT_ID, 1, USER_ID,
        MockConstants.REPOSITORY_OBJECT_ID, 1, USER_ID);
    testNextDocument_skipped_fail(list, MockConstants.DOCUMENT_OBJECT_ID);
    assertTrue(client.isThrown());
  }

  /**
   * Tests a pair of documents where FetchVersion should throw a
   * RepositoryException on the first and an I/O exception on
   * the second. Previously, this would throw an exception from the
   * batch rather than return null to retry the batch starting with
   * the second document.
   */
  public void testNextDocument_repository_fail() throws RepositoryException {
    DocumentList list = getObjectUnderTest(
        MockConstants.REPOSITORY_OBJECT_ID, 1, USER_ID,
        MockConstants.IO_OBJECT_ID, 1, USER_ID);
    testNextDocument_skipped_fail(list, MockConstants.REPOSITORY_OBJECT_ID);
  }

  /**
   * Expects to get a RepositoryDocumentException on the first
   * document, and null on the second document, with the first
   * document ID being returned in the checkpoint.
   */
  private void testNextDocument_skipped_fail(DocumentList list,
      int checkpointId) throws RepositoryException {
    try {
      Document doc = list.nextDocument();
      fail("Expected a RepositoryDocumentException");
    } catch (RepositoryDocumentException e) {
      assertNotNull(list.checkpoint());
    }

    Document doc = list.nextDocument();
    String checkpoint = list.checkpoint();
    assertTrue(checkpoint, checkpoint.endsWith("," + checkpointId));
  }

  /** Tests that the client must not be null. */
  public void testNullConstructorArgs_client() throws RepositoryException {
    LivelinkConnector connector = getConnector();

    try {
      DocumentList list = new LivelinkDocumentList(connector,
          null, null, null, null, null, null, null, null);
      fail("Expected a NullPointerException");
    } catch (NullPointerException e) {
    }
  }

  /** Tests that the checkpoint must not be null. */
  public void testNullConstructorArgs_checkpoint() throws RepositoryException {
    LivelinkConnector connector = getConnector();
    ClientFactory clientFactory = connector.getClientFactory();
    Client client = clientFactory.createClient();

    DocumentList list = new LivelinkDocumentList(connector,
        client, null, null, null, null, null, null, null);
    try {
      Document next = list.nextDocument();
      fail("Expected a NullPointerException");
    } catch (NullPointerException e) {
    }
  }

  /** Tests that all the other constructs arguments can be null. */
  public void testNullConstructorArgs_others() throws RepositoryException {
    LivelinkConnector connector = getConnector();
    ClientFactory clientFactory = connector.getClientFactory();
    Client client = clientFactory.createClient();
    Checkpoint checkpoint = new Checkpoint();

    DocumentList list = new LivelinkDocumentList(connector,
        client, null, null, null, null, null, checkpoint, null);
    Document next = list.nextDocument();
    assertNull(next);
  }

  /** Tests a null recArray with a non-null publicContentUsername. */
  public void testNullConstructorArgs_publicContentUsername()
      throws RepositoryException {
    LivelinkConnector connector =
        getConnector("publicContentUsername", "anonymous");
    ClientFactory clientFactory = connector.getClientFactory();
    Client client = clientFactory.createClient();
    Checkpoint checkpoint = new Checkpoint();

    DocumentList list = new LivelinkDocumentList(connector,
        client, null, null, null, null, null, checkpoint, null);
    Document next = list.nextDocument();
    assertNull(next);
  }

  private void insertDTreeAcl(int dataID, int rightID, int permissions)
      throws SQLException {
    jdbcFixture.executeUpdate(
        "insert into DTreeACL(DataID, RightID, Permissions) "
            + "values(" + dataID + "," + rightID + "," + permissions + ")");
  }

  private Set<String> getPrincipalsNames(Document doc, String propertyName)
      throws RepositoryException {
    Set<String> names = new HashSet<String>();;
    PrincipalValue prValue;
    Property property = doc.findProperty(propertyName);
    while ((prValue = (PrincipalValue) property.nextValue()) != null) {
      names.add(prValue.getPrincipal().getName());
    }
    return names;
  }

  public void testAcl_usersAndGroups()
      throws RepositoryException, SQLException {
    insertDTreeAcl(21, 1002, Client.PERM_MODIFY);
    insertDTreeAcl(21, 1003, Client.PERM_SEECONTENTS);
    insertDTreeAcl(21, 2002, Client.PERM_SEECONTENTS);
    insertDTreeAcl(21, Client.RIGHT_OWNER, Client.PERM_FULL);
    insertDTreeAcl(21, Client.RIGHT_GROUP, Client.PERM_SEECONTENTS);
    insertDTreeAcl(21, Client.RIGHT_WORLD, Client.PERM_SEE);

    DocumentList list = getObjectUnderTest(21, 0, 1001);
    Document doc = list.nextDocument();

    assertNotNull(doc);
    assertEquals(ImmutableSet.of("user1", "user3"),
        getPrincipalsNames(doc, SpiConstants.PROPNAME_ACLUSERS));
    assertEquals(ImmutableSet.of("group1", "group2"),
        getPrincipalsNames(doc, SpiConstants.PROPNAME_ACLGROUPS));
  }

  public void testAcl_groupsOnly() throws RepositoryException,
      SQLException {
    insertDTreeAcl(22, 1002, Client.PERM_SEE);
    insertDTreeAcl(22, 1003, Client.PERM_MODIFY);
    insertDTreeAcl(22, 2001, Client.PERM_SEECONTENTS);
    insertDTreeAcl(22, 2002, Client.PERM_FULL);

    DocumentList list = getObjectUnderTest(22, 1, 1001);
    Document doc = list.nextDocument();

    assertNotNull(doc);
    assertEquals(ImmutableSet.of(),
        getPrincipalsNames(doc, SpiConstants.PROPNAME_ACLUSERS));
    assertEquals(ImmutableSet.of("group1", "group2"),
        getPrincipalsNames(doc, SpiConstants.PROPNAME_ACLGROUPS));
  }

  public void testAcl_noPublicAccess() throws RepositoryException,
      SQLException {
    insertDTreeAcl(23, 1001, Client.PERM_FULL);
    insertDTreeAcl(23, Client.RIGHT_WORLD, Client.PERM_SEE);

    DocumentList list = getObjectUnderTest(23, 0, 1001);
    Document doc = list.nextDocument();

    assertNotNull(doc);
    assertEquals(ImmutableSet.of("user1"),
        getPrincipalsNames(doc, SpiConstants.PROPNAME_ACLUSERS));
    assertEquals(ImmutableSet.of(),
        getPrincipalsNames(doc, SpiConstants.PROPNAME_ACLGROUPS));
  }

  public void testAcl_onlyPublicAccess() throws RepositoryException,
      SQLException {
    insertDTreeAcl(23, 1001, Client.PERM_MODIFY);
    insertDTreeAcl(23, Client.RIGHT_WORLD, Client.PERM_SEECONTENTS);

    DocumentList list = getObjectUnderTest(23, 0, 1001);
    Document doc = list.nextDocument();

    assertNotNull(doc);
    assertEquals(ImmutableSet.of(),
        getPrincipalsNames(doc, SpiConstants.PROPNAME_ACLUSERS));
    assertEquals(ImmutableSet.of("Public Access"),
        getPrincipalsNames(doc, SpiConstants.PROPNAME_ACLGROUPS));
  }

  public void testAcl_owner() throws RepositoryException, SQLException {
    insertDTreeAcl(24, Client.RIGHT_OWNER, Client.PERM_FULL);
    insertDTreeAcl(24, 2001, Client.PERM_SEE);
    insertDTreeAcl(24, 2002, Client.PERM_MODIFY);

    DocumentList list = getObjectUnderTest(24, 0, 1001);
    Document doc = list.nextDocument();

    assertNotNull(doc);
    assertEquals(ImmutableSet.of("user1"),
        getPrincipalsNames(doc, SpiConstants.PROPNAME_ACLUSERS));
    assertEquals(ImmutableSet.of(),
        getPrincipalsNames(doc, SpiConstants.PROPNAME_ACLGROUPS));
  }

  public void testAcl_userDefaultGroup() throws RepositoryException,
      SQLException {
    insertDTreeAcl(25, Client.RIGHT_GROUP, Client.PERM_SEECONTENTS);

    DocumentList list = getObjectUnderTest(25, 1, 1003);
    Document doc = list.nextDocument();

    assertNotNull(doc);
    assertEquals(ImmutableSet.of(),
        getPrincipalsNames(doc, SpiConstants.PROPNAME_ACLUSERS));
    assertEquals(ImmutableSet.of("group2"),
        getPrincipalsNames(doc, SpiConstants.PROPNAME_ACLGROUPS));
  }

  public void testAcl_noRead() throws RepositoryException, SQLException {
    insertDTreeAcl(26, 1001, Client.PERM_SEE);
    insertDTreeAcl(26, 2001, Client.PERM_MODIFY);
    insertDTreeAcl(26, Client.RIGHT_WORLD, Client.PERM_SEE);

    DocumentList list = getObjectUnderTest(26, 0, 1001);
    Document doc = list.nextDocument();

    assertNotNull(doc);
    assertEquals(ImmutableSet.of(),
        getPrincipalsNames(doc, SpiConstants.PROPNAME_ACLUSERS));
    assertEquals(ImmutableSet.of(),
        getPrincipalsNames(doc, SpiConstants.PROPNAME_ACLGROUPS));
  }

  public void testAcl_noAclEntries() throws RepositoryException, SQLException {
    DocumentList list = getObjectUnderTest(27, 0, 1002);
    Document doc = list.nextDocument();

    assertNotNull(doc);
    assertEquals(ImmutableSet.of(),
        getPrincipalsNames(doc, SpiConstants.PROPNAME_ACLUSERS));
    assertEquals(ImmutableSet.of(),
        getPrincipalsNames(doc, SpiConstants.PROPNAME_ACLGROUPS));
  }

  public void testAcl_emptyUserName() throws RepositoryException, SQLException {
    insertDTreeAcl(28, 1666, Client.PERM_SEECONTENTS);

    DocumentList list = getObjectUnderTest(28, 0, 1001);
    Document doc = list.nextDocument();

    assertNotNull(doc);
    assertEquals(ImmutableSet.of(),
        getPrincipalsNames(doc, SpiConstants.PROPNAME_ACLUSERS));
    assertEquals(ImmutableSet.of(),
        getPrincipalsNames(doc, SpiConstants.PROPNAME_ACLGROUPS));
  }

  public void testAcl_missingGroup() throws RepositoryException, SQLException {
    insertDTreeAcl(29, 2666, Client.PERM_SEECONTENTS);

    DocumentList list = getObjectUnderTest(29, 0, 1002);
    Document doc = list.nextDocument();

    assertNotNull(doc);
    assertEquals(ImmutableSet.of(),
        getPrincipalsNames(doc, SpiConstants.PROPNAME_ACLUSERS));
    assertEquals(ImmutableSet.of(),
        getPrincipalsNames(doc, SpiConstants.PROPNAME_ACLGROUPS));
  }

  private void setUserData(int userId, String userData)
      throws SQLException {
    jdbcFixture.executeUpdate("UPDATE KUAF SET UserData = '" + userData
        + "' where ID = " + userId);
  }

  private Principal getAclPrincipal(Document doc, String prop, String name)
      throws RepositoryException {
    Principal aclPrincipal = null;
    PrincipalValue prValue;
    Property property = doc.findProperty(prop);
    while ((prValue = (PrincipalValue) property.nextValue()) != null) {
      if (prValue.getPrincipal().getName().equalsIgnoreCase(name)) {
        aclPrincipal = prValue.getPrincipal();
        break;
      }
    }
    return aclPrincipal;
  }

  public void testAcl_userNamespace()
      throws RepositoryException, SQLException {
    insertDTreeAcl(31, 1001, Client.PERM_SEECONTENTS);
    insertDTreeAcl(31, 1002, Client.PERM_SEECONTENTS);
    setUserData(1001, "ExternalAuthentication=true");
    setUserData(1002, "ExternalAuthentication=false");

    DocumentList list = getObjectUnderTest(31, 0, 1001);
    Document doc = list.nextDocument();

    assertNotNull(doc);
    assertEquals(
        new Principal(UNKNOWN, GLOBAL_NAMESPACE, "user1",
            EVERYTHING_CASE_SENSITIVE),
        getAclPrincipal(doc, SpiConstants.PROPNAME_ACLUSERS, "user1"));
    assertEquals(
        new Principal(UNKNOWN, LOCAL_NAMESPACE, "user2",
            EVERYTHING_CASE_SENSITIVE),
        getAclPrincipal(doc, SpiConstants.PROPNAME_ACLUSERS, "user2"));
  }

  public void testAcl_GroupNamespace()
      throws RepositoryException, SQLException {
    insertDTreeAcl(32, 2001, Client.PERM_SEECONTENTS);
    insertDTreeAcl(32, 2002, Client.PERM_SEECONTENTS);
    setUserData(2001, "ExternalAuthentication=true");
    setUserData(2002, "ExternalAuthentication=false");

    DocumentList list = getObjectUnderTest(32, 0, 1001);
    Document doc = list.nextDocument();

    assertNotNull(doc);
    assertEquals(
        new Principal(UNKNOWN, GLOBAL_NAMESPACE, "group1",
            EVERYTHING_CASE_SENSITIVE),
        getAclPrincipal(doc, SpiConstants.PROPNAME_ACLGROUPS, "group1"));
    assertEquals(
        new Principal(UNKNOWN, LOCAL_NAMESPACE, "group2",
            EVERYTHING_CASE_SENSITIVE),
        getAclPrincipal(doc, SpiConstants.PROPNAME_ACLGROUPS, "group2"));
  }

  public void testInvalidUserData_UserNamespace() throws RepositoryException,
      SQLException {
    insertDTreeAcl(33, 1001, Client.PERM_SEECONTENTS);
    setUserData(1001, "invaliddata=");

    DocumentList list = getObjectUnderTest(33, 0, 1001);
    Document doc = list.nextDocument();
    assertNotNull(doc);
    assertEquals(
        new Principal(UNKNOWN, LOCAL_NAMESPACE, "user1",
            EVERYTHING_CASE_SENSITIVE),
        getAclPrincipal(doc,
            SpiConstants.PROPNAME_ACLUSERS, "user1"));
  }

  public void testNullUserData_UserNamespace() throws RepositoryException,
      SQLException {
    insertDTreeAcl(34, 1001, Client.PERM_SEECONTENTS);
    setUserData(1001, null);

    DocumentList list = getObjectUnderTest(34, 0, 1001);
    Document doc = list.nextDocument();
    assertNotNull(doc);
    assertEquals(
        new Principal(UNKNOWN, LOCAL_NAMESPACE, "user1",
            EVERYTHING_CASE_SENSITIVE),
        getAclPrincipal(doc, SpiConstants.PROPNAME_ACLUSERS, "user1"));
  }

  public void testPublicAccess_Namespace()
      throws RepositoryException, SQLException {
    insertDTreeAcl(35, Client.RIGHT_WORLD, Client.PERM_SEECONTENTS);

    DocumentList list = getObjectUnderTest(35, 0, 1001);
    Document doc = list.nextDocument();
    assertNotNull(doc);
    assertEquals(
        new Principal(UNKNOWN, LOCAL_NAMESPACE, "Public Access",
            EVERYTHING_CASE_SENSITIVE),
        getAclPrincipal(doc, SpiConstants.PROPNAME_ACLGROUPS, "Public Access"));
  }

  public void testOwner_Namespace()
      throws RepositoryException, SQLException {
    insertDTreeAcl(36, Client.RIGHT_OWNER, Client.PERM_SEECONTENTS);
    setUserData(1001, "ExternalAuthentication=false,ldap=vizdom.com");

    DocumentList list = getObjectUnderTest(36, 0, 1001);
    Document doc = list.nextDocument();
    assertNotNull(doc);
    assertEquals(
        new Principal(UNKNOWN, GLOBAL_NAMESPACE, "user1",
            EVERYTHING_CASE_SENSITIVE),
        getAclPrincipal(doc, SpiConstants.PROPNAME_ACLUSERS, "user1"));
  }

  public void testGroup_Namespace()
      throws RepositoryException, SQLException {
    insertDTreeAcl(37, Client.RIGHT_GROUP, Client.PERM_SEECONTENTS);
    setUserData(1001, "ExternalAuthentication=true");
    setUserData(2001, null);

    DocumentList list = getObjectUnderTest(37, 1, 1001);
    Document doc = list.nextDocument();
    assertNotNull(doc);
    assertEquals(
        new Principal(UNKNOWN, LOCAL_NAMESPACE, "group1",
            EVERYTHING_CASE_SENSITIVE),
        getAclPrincipal(doc, SpiConstants.PROPNAME_ACLGROUPS, "group1"));
  }

  public void testUserdataLdap_Namespace()
      throws RepositoryException, SQLException {
    insertDTreeAcl(38, 1001, Client.PERM_SEECONTENTS);
    setUserData(1001, "LDAP=vizdom");

    DocumentList list = getObjectUnderTest(38, 0, 1001);
    Document doc = list.nextDocument();
    assertNotNull(doc);
    assertEquals(
        new Principal(UNKNOWN, GLOBAL_NAMESPACE, "user1",
            EVERYTHING_CASE_SENSITIVE),
        getAclPrincipal(doc, SpiConstants.PROPNAME_ACLUSERS, "user1"));
  }

  public void testUserdataNtlm_Namespace()
      throws RepositoryException, SQLException {
    insertDTreeAcl(38, 1001, Client.PERM_SEECONTENTS);
    setUserData(1001, "ntlm=");

    DocumentList list = getObjectUnderTest(38, 0, 1001);
    Document doc = list.nextDocument();
    assertNotNull(doc);
    assertEquals(
        new Principal(UNKNOWN, GLOBAL_NAMESPACE, "user1",
            EVERYTHING_CASE_SENSITIVE),
        getAclPrincipal(doc, SpiConstants.PROPNAME_ACLUSERS, "user1"));
  }
}
