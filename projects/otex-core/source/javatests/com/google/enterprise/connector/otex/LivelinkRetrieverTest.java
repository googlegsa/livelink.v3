// Copyright 2011 Google Inc.
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

import com.google.enterprise.connector.otex.client.mock.MockConstants;

import com.google.enterprise.connector.spi.Document;
import com.google.enterprise.connector.spi.RepositoryDocumentException;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.Session;

import junit.framework.TestCase;

import java.io.InputStream;

/**
 * Tests the LivelinkRetriever.
 */
// TODO: Figure out MockClient; extract MockLivelinkDocument from
// MockLivelinkDocumentList.

public class LivelinkRetrieverTest extends TestCase {
  private LivelinkConnector conn;
  private LivelinkSession sess;
  private LivelinkRetriever retriever;

  public void setUp() throws RepositoryException {
    conn = LivelinkConnectorFactory.getConnector("connector.");
    conn.setFeedType("CONTENTURL");
    sess = (LivelinkSession) conn.login();
    retriever = (LivelinkRetriever) sess.getRetriever();
    assertNotNull(retriever);
  }

  public void testInvalidDocidGetContent() throws Exception {
    try {
      retriever.getContent("invalidDocid");
      fail("Expected RepositoryDocumentException");
    } catch (RepositoryDocumentException expected) {
      assertTrue(expected.getMessage().startsWith("Invalid docid:"));
    }
  }

  public void testDocumentNotFoundGetContent() throws Exception {
    try {
      retriever.getContent(Integer.toString(MockConstants.DOCUMENT_OBJECT_ID));
      fail("Expected RepositoryDocumentException");
    } catch (RepositoryDocumentException expected) {
      // TODO: verify it is document not found.
    }
  }

  public void testDocumentNotFoundGetMetaData() throws Exception {
    try {
      retriever.getMetaData(Integer.toString(MockConstants.DOCUMENT_OBJECT_ID));
      fail("Expected RepositoryDocumentException");
    } catch (RepositoryDocumentException expected) {
      // TODO: verify it is document not found.
    }
  }

  public void testGetContent() throws Exception {
    InputStream in = retriever.getContent("9999");
    assertNotNull(in);
  }

  public void testGetMetaData() throws Exception {
    Document doc = retriever.getMetaData("9999");
    assertNotNull(doc);
  }
}
