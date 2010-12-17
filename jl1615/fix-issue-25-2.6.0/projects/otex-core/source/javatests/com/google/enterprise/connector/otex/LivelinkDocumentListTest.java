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
import com.google.enterprise.connector.spi.Document;
import com.google.enterprise.connector.spi.DocumentList;
import com.google.enterprise.connector.spi.RepositoryException;

import junit.framework.TestCase;

import java.util.Collections;

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
