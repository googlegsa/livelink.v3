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

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.isA;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

import com.google.enterprise.connector.otex.client.Client;
import com.google.enterprise.connector.otex.client.ClientFactory;
import com.google.enterprise.connector.otex.client.ClientValue;
import com.google.enterprise.connector.otex.client.mock.MockClient;
import com.google.enterprise.connector.otex.client.mock.MockClientValue;
import com.google.enterprise.connector.otex.client.mock.MockConstants;
import com.google.enterprise.connector.spi.Document;
import com.google.enterprise.connector.spi.DocumentList;
import com.google.enterprise.connector.spi.RepositoryDocumentException;
import com.google.enterprise.connector.spi.RepositoryException;

import junit.framework.TestCase;

import java.sql.SQLException;
import java.util.Collections;
import java.util.Date;

public class LivelinkDocumentListTest extends TestCase {
  private final JdbcFixture jdbcFixture = new JdbcFixture();

  protected void setUp() throws SQLException {
    jdbcFixture.setUp();
  }

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
        MockConstants.IO_OBJECT_ID, 0);
  }

  /** Helper method for the other overloads. */
  private DocumentList getObjectUnderTest(LivelinkConnector connector,
      Client client, ContentHandler contentHandler, int... docInfo)
      throws RepositoryException {
    contentHandler.initialize(connector, client);

    final String[] FIELDS = {
      "ModifyDate", "DataID", "OwnerID", "SubType", "MimeType", "DataSize" };
    assertEquals(String.valueOf(docInfo.length), 0, docInfo.length % 2);
    Object[][] values = new Object[docInfo.length / 2][];
    for (int i = 0; i < docInfo.length / 2; i++) {
      int objectId = docInfo[2 * i];
      int dataSize = docInfo[2 * i + 1];
      values[i] = new Object[] {
        new Date(), objectId, 2000, 144, "text/plain", dataSize };
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
    DocumentList list = getObjectUnderTest(MockConstants.IO_OBJECT_ID, 0);

    Document doc = list.nextDocument();
    assertNotNull(doc);
    assertNotNull(list.checkpoint());
  }

  /**
   * Tests a document where FetchVersion should throw an I/O exception
   * and be retried.
   */
  public void testNextDocument_exception() throws RepositoryException {
    DocumentList list = getObjectUnderTest(MockConstants.IO_OBJECT_ID, 1);

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
        getObjectUnderTest(connector, MockConstants.IO_OBJECT_ID, 1);

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
    DocumentList list = getObjectUnderTest(MockConstants.HARMLESS_OBJECT_ID, 0,
        MockConstants.IO_OBJECT_ID, 1);

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
        MockConstants.DOCUMENT_OBJECT_ID, 1);

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
        MockConstants.DOCUMENT_OBJECT_ID, 1,
        MockConstants.REPOSITORY_OBJECT_ID, 1);
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
        MockConstants.REPOSITORY_OBJECT_ID, 1,
        MockConstants.IO_OBJECT_ID, 1);
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
}
