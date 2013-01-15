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

import com.google.enterprise.connector.otex.client.Client;
import com.google.enterprise.connector.otex.client.ClientFactory;
import com.google.enterprise.connector.spi.RepositoryException;

import junit.framework.TestCase;

public class HttpURLContentHandlerTest extends TestCase {
  private LivelinkConnector connector;
  private Client client;
  private HttpURLContentHandler out;

  public void setUp() throws RepositoryException {
    connector = LivelinkConnectorFactory.getConnector("connector.");
    ClientFactory clientFactory = connector.getClientFactory();
    client = clientFactory.createClient();
    out = new HttpURLContentHandler();
  }

  public void testDefaultParameters() throws RepositoryException {
    out.initialize(connector, client);

    assertEquals(connector.getDisplayUrl(), out.getUrlBase());
    assertEquals(HttpURLContentHandler.DEFAULT_URL_PATH, out.getUrlPath());
  }

  public void testUrlBase() throws RepositoryException {
    String expected = "http://example.com/";
    assertFalse(expected.equals(connector.getDisplayUrl()));
    out.setUrlBase(expected);
    out.initialize(connector, client);

    assertEquals(expected, out.getUrlBase());
  }

  public void testUrlPath() throws RepositoryException {
    String expected = "/something";
    assertFalse(expected.equals(HttpURLContentHandler.DEFAULT_URL_PATH));
    out.setUrlPath(expected);
    out.initialize(connector, client);

    assertEquals(expected, out.getUrlPath());
  }
}
