// Copyright 2014 Google Inc. All Rights Reserved.
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

import static com.google.common.base.Throwables.propagateIfPossible;
import static java.net.HttpURLConnection.HTTP_SEE_OTHER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableMap;
import com.google.enterprise.connector.otex.client.Client;
import com.google.enterprise.connector.otex.client.ClientFactory;
import com.google.enterprise.connector.spi.RepositoryException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GroupListerTest {
  private static final Logger LOGGER =
      Logger.getLogger(GroupLister.class.getName());

  private final JdbcFixture jdbcFixture = new JdbcFixture();

  private List<Exception> exceptionList;
  private Thread listerThread;
  private LivelinkConnector connector;
  private GroupAdaptor adaptor;

  @Before
  public void setUp()
      throws SQLException, IOException, RepositoryException,
      InterruptedException {
    jdbcFixture.setUp();

    connector = new LivelinkConnector(
        "com.google.enterprise.connector.otex.client.mock.MockClientFactory");
    connector.setGoogleFeedHost("localhost");
    connector.setGroupFeedSchedule("0 1 * * *");

    ClientFactory clientFactory = connector.getClientFactory();
    Client client = clientFactory.createClient();
    adaptor = new GroupAdaptor(connector, client);

    exceptionList = new LinkedList<Exception>();
  }

  @After
  public void tearDown() throws SQLException, IOException,
      RepositoryException, InterruptedException {
    try {
      listerThread.join();
      if (!exceptionList.isEmpty()) {
        propagateIfPossible(exceptionList.get(0), RepositoryException.class);
      }
    } finally {
      jdbcFixture.tearDown();
    }
  }

  private Thread startListerThread(final GroupLister testLister) {
    Thread t = new Thread() {
      public void run() {
        try {
          testLister.start();
        } catch (RepositoryException e) {
          LOGGER.log(Level.SEVERE, "Caught exception starting lister", e);
          exceptionList.add(e);
        } catch (RuntimeException e) {
          LOGGER.log(Level.SEVERE, "Caught exception starting lister", e);
          exceptionList.add(e);
        }
      }
    };
    t.start();
    return t;
  }

  @Test
  public void testShutdownLister() throws RepositoryException,
      InterruptedException, IOException {
    GroupLister testLister = new GroupLister(connector, adaptor,
        ImmutableMap.of("gsa.version", "7.2.0-90"));
    listerThread = startListerThread(testLister);
    Thread.sleep(500L);

    testLister.shutdown();
  }

  @Test
  public void testInterruptLister() throws RepositoryException,
      InterruptedException, IOException {
    GroupLister testLister = new GroupLister(connector, adaptor,
        ImmutableMap.of("gsa.version", "7.2.0-90"));
    listerThread = startListerThread(testLister);
    Thread.sleep(500L);

    try {
      listerThread.interrupt();
      assertTrue(listerThread.isInterrupted());
      assertEquals(Thread.State.WAITING, listerThread.getState());
    } finally {
      testLister.shutdown();
    }
  }

  /**
   * When this test fails, it is very likely to hang. Check test.log
   * for the root cause.
   */
  @Test
  public void testRaceCondition() throws Exception {
    String dashboardPort = System.getProperty("tests.dashboardPort");
    GroupLister testLister = new GroupLister(connector, adaptor,
        ImmutableMap.of("gsa.version", "7.2.0-90",
            "server.dashboardPort", dashboardPort));

    // Ready, set, go! The call to shutdown happens first because it's
    // running in the current thread.
    listerThread = startListerThread(testLister);
    testLister.shutdown();
    Thread second = startListerThread(testLister);
    listerThread.join();

    // We need more time for the HttpServer to startup.
    Thread.sleep(1000L);

    URL dashboardUrl =
        new URL("http", "localhost", Integer.parseInt(dashboardPort), "/");
    HttpURLConnection dashboard =
        (HttpURLConnection) dashboardUrl.openConnection();
    dashboard.setInstanceFollowRedirects(false);
    assertEquals(HTTP_SEE_OTHER, dashboard.getResponseCode());
    assertEquals(dashboardUrl + "dashboard",
        dashboard.getHeaderField("Location"));

    testLister.shutdown();
    second.join();

    // This is the crucial test, making sure that the dashboard HTTP
    // server is shutdown.
    dashboard = (HttpURLConnection) dashboardUrl.openConnection();
    dashboard.setInstanceFollowRedirects(false);
    try {
      dashboard.connect();
      fail("Expected a ConnectException but got HTTP "
          + dashboard.getResponseCode());
    } catch (ConnectException expected) {
    }
  }
}
