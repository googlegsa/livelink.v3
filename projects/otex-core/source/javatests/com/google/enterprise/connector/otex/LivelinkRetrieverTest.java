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
import java.sql.SQLException;

/**
 * Tests the LivelinkRetriever.
 */
// TODO: Figure out MockClient; extract MockLivelinkDocument from
// MockLivelinkDocumentList.
public class LivelinkRetrieverTest extends TestCase {
  private final JdbcFixture jdbcFixture = new JdbcFixture();

  private LivelinkConnector conn;
  private LivelinkSession sess;
  private LivelinkRetriever retriever;

  protected void setUp() throws SQLException, RepositoryException {
    jdbcFixture.setUp();
    jdbcFixture.executeUpdate(
        "insert into WebNodes(DataID, SubType, MimeType, DataSize) "
        + "values(9999, 144, 'text/xml', 100)");

    conn = LivelinkConnectorFactory.getConnector("connector.");
    conn.setFeedType("CONTENTURL");
    sess = (LivelinkSession) conn.login();
    retriever = (LivelinkRetriever) sess.getRetriever();
    assertNotNull(retriever);
  }

  protected void tearDown() throws SQLException {
    jdbcFixture.tearDown();
  }

  private void assertMessage(Exception e, String message) {
    assertTrue(e.getMessage(), e.getMessage().startsWith(message));
  }

  public void testInvalidDocidGetContent() throws Exception {
    try {
      retriever.getContent("invalidDocid");
      fail("Expected RepositoryDocumentException");
    } catch (RepositoryDocumentException expected) {
      assertMessage(expected, "Invalid docid:");
    }
  }

  public void testDocumentNotFoundGetContent() throws Exception {
    try {
      retriever.getContent("404");
      fail("Expected RepositoryDocumentException");
    } catch (RepositoryDocumentException expected) {
      assertMessage(expected, "Not found:");
    }
  }

  public void testDocumentNotFoundGetMetaData() throws Exception {
    try {
      retriever.getMetaData("404");
      fail("Expected RepositoryDocumentException");
    } catch (RepositoryDocumentException expected) {
      assertMessage(expected, "Not found:");
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
