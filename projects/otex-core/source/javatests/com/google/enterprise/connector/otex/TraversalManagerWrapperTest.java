// Copyright 2015 Google Inc. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.enterprise.connector.otex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.google.enterprise.connector.otex.client.mock.MockClientFactory;
import com.google.enterprise.connector.spi.DocumentList;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.SimpleTraversalContext;
import com.google.enterprise.connector.util.testing.Logging;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.sql.SQLException;
import java.util.ArrayList;

public class TraversalManagerWrapperTest {
  private final JdbcFixture jdbcFixture = new JdbcFixture();

  @Before
  public void setUp() throws SQLException {
    jdbcFixture.setUp();
  }

  @After
  public void tearDown() throws SQLException {
    jdbcFixture.tearDown();
  }

  private LivelinkConnector connector;

  @Before
  public void createConnector() {
    connector = new LivelinkConnector(new MockClientFactory());
    connector.setContentHandler(new ByteArrayContentHandler());
    connector.setIncludedSelectExpressions(null);
    connector.setUsername("Admin");
  }

  /**
   * This is a smoke test to make sure that no NullPointerExceptions
   * are thrown, but we also check the logging to verify when new
   * delegates are created.
   */
  private void smokeTest() throws RepositoryException {
    String expectedMessage =
        "CREATING A NEW TRAVERSAL MANAGER; HTTP TUNNELING: {0}";
    ArrayList<String> logMessages = new ArrayList<String>();
    Logging.captureLogMessages(TraversalManagerWrapper.class,
        expectedMessage, logMessages);

    TraversalManagerWrapper out =
        new TraversalManagerWrapper(connector, connector.getClientFactory());
    assertEquals(logMessages.toString(),
        (connector.getUseHttpTunneling()) ? 0 : 1, logMessages.size());

    out.setTraversalContext(new SimpleTraversalContext());
    out.setBatchHint(200);
    DocumentList list = out.startTraversal();
    assertNull(list);
    list = out.resumeTraversal(null);
    assertNull(list);
    assertEquals(logMessages.toString(),
        (connector.getUseHttpTunneling()) ? 2 : 1, logMessages.size());
  }

  /** Tests a direct connection with no traversal user. */
  @Test
  public void testDirect() throws RepositoryException {
    smokeTest();
  }

  /** Tests a direct connection with a traversal user. */
  @Test
  public void testDirect_traversalUser() throws RepositoryException {
    connector.setTraversalUsername("alice");
    smokeTest();
  }

  /** Tests an HTTP tunneling connection with a matching traversal user. */
  @Test
  public void testHttpTunneling() throws RepositoryException {
    connector.setUseHttpTunneling(true);
    connector.setTraversalUsername("Admin");
    smokeTest();
  }
}
