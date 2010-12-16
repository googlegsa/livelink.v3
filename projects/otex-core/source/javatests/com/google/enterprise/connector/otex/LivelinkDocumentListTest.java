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

import com.google.enterprise.connector.otex.client.Client;
import com.google.enterprise.connector.otex.client.ClientFactory;
import com.google.enterprise.connector.otex.client.ClientValue;
import com.google.enterprise.connector.otex.client.mock.MockClientValue;
import com.google.enterprise.connector.otex.client.mock.MockConstants;
import com.google.enterprise.connector.spi.Document;
import com.google.enterprise.connector.spi.DocumentList;
import com.google.enterprise.connector.spi.RepositoryDocumentException;
import com.google.enterprise.connector.spi.RepositoryException;

import junit.framework.TestCase;

import java.util.Collections;
import java.util.Date;

public class LivelinkDocumentListTest extends TestCase {
  /**
   * Creates a connector instance.
   *
   * @param publicContentUsername an optional parameter, may be null
   * @return a new LivelinkConnector instance
   */
  private LivelinkConnector getConnector(String publicContentUsername)
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

    if (publicContentUsername != null) {
      connector.setPublicContentUsername(publicContentUsername);
    }
    
    connector.login();
    return connector;
  }

  /**
   * Creates a LivelinkDocumentList containing one document for the tests.
   *
   * @param objectId the object ID (DataID) for the returned document,
   *     usually one of the values from MockConstants
   * @param dataSize the file size (DataSize) for the returned document
   */
  private DocumentList getObjectUnderTest(int objectId, int... dataSize)
      throws RepositoryException {
    LivelinkConnector connector = getConnector(null);

    ClientFactory clientFactory = connector.getClientFactory();
    Client client = clientFactory.createClient();

    ContentHandler contentHandler = new FileContentHandler();
    contentHandler.initialize(connector, client);

    final String[] FIELDS = {
      "ModifyDate", "DataID", "OwnerID", "Subtype", "MimeType", "DataSize" };
    Object[][] values = new Object[dataSize.length][];
    for (int i = 0; i < dataSize.length; i++) {
      values[i] = new Object[] {
        new Date(), objectId, 2000, 144, "text/plain", dataSize[i] };
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
   * Tests a document list where the first document is fine and for
   * the second document, FetchVersion should throw an I/O exception.
   * Since we've already processed one document, we just expect
   * nextDocument to return null in this case, unlike the previous
   * case where the LivelinkIOException was rethrown.
   */
  public void testNextDocument_partial() throws RepositoryException {
    DocumentList list = getObjectUnderTest(MockConstants.IO_OBJECT_ID, 0, 1);

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
    DocumentList list = getObjectUnderTest(MockConstants.DOCUMENT_OBJECT_ID, 1);

    try {
      Document doc = list.nextDocument();
      fail("Expected a RepositoryDocumentException");
    } catch (RepositoryDocumentException e) {
      assertNotNull(list.checkpoint());
    }
  }

  /** Tests that the client must not be null. */
  public void testNullConstructorArgs_client() throws RepositoryException {
    LivelinkConnector connector = getConnector(null);

    try {
      DocumentList list = new LivelinkDocumentList(connector,
          null, null, null, null, null, null, null, null);
      fail("Expected a NullPointerException");
    } catch (NullPointerException e) {
    }
  }

  /** Tests that the checkpoint must not be null. */
  public void testNullConstructorArgs_checkpoint() throws RepositoryException {
    LivelinkConnector connector = getConnector(null);
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
    LivelinkConnector connector = getConnector(null);
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
    LivelinkConnector connector = getConnector("anonymous");
    ClientFactory clientFactory = connector.getClientFactory();
    Client client = clientFactory.createClient();
    Checkpoint checkpoint = new Checkpoint();

    DocumentList list = new LivelinkDocumentList(connector,
        client, null, null, null, null, null, checkpoint, null);
    Document next = list.nextDocument();
    assertNull(next);
  }
}
