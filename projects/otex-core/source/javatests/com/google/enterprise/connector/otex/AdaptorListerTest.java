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
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.isA;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.enterprise.adaptor.Adaptor;
import com.google.enterprise.adaptor.AdaptorContext;
import com.google.enterprise.adaptor.Config;
import com.google.enterprise.adaptor.DocIdPusher;
import com.google.enterprise.connector.spi.Lister;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.TraversalSchedule;
import com.google.enterprise.connector.spi.TraversalScheduleAware;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AdaptorListerTest {
  private static final Logger LOGGER =
      Logger.getLogger(AdaptorListerTest.class.getName());

  private static final int DASHBOARD_PORT =
        Integer.parseInt(System.getProperty("tests.dashboardPort"));

  private static final String[] adaptorArgs = {
    "-Dgsa.version=7.2.0-90",
    "-Dgsa.hostname=localhost",
    "-Dserver.hostname=localhost",
    "-Dserver.port=0",
    "-Dserver.dashboardPort=" + DASHBOARD_PORT,
  };

  private Adaptor adaptor;
  private AdaptorLister testLister;
  private List<Exception> exceptionList;
  private Thread listerThread;

  @Before
  public void setUp() throws Exception {
    adaptor = getMockAdaptor();
    testLister = new AdaptorLister(adaptor, adaptorArgs);
    exceptionList = new LinkedList<Exception>();
  }

  /**
   * Gets a mock adaptor that expects all methods except getDocContent
   * to be called at least once.
   */
  private Adaptor getMockAdaptor() throws Exception {
    Adaptor adaptor = createMock(Adaptor.class);
    adaptor.initConfig(isA(Config.class));
    expectLastCall().atLeastOnce();
    adaptor.init(isA(AdaptorContext.class));
    expectLastCall().atLeastOnce();
    adaptor.getDocIds(isA(DocIdPusher.class));
    expectLastCall().atLeastOnce();
    adaptor.destroy();
    expectLastCall().atLeastOnce();
    replay(adaptor);
    return adaptor;
  }

  @After
  public void tearDown() throws RepositoryException, InterruptedException {
    if (listerThread != null) {
      listerThread.join();
    }
    if (!exceptionList.isEmpty()) {
      propagateIfPossible(exceptionList.get(0), RepositoryException.class);
    }
    verify(adaptor);
  }

  private Thread startListerThread(final Lister testLister) {
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
  public void testNotScheduleAware() {
    adaptor = createMock(Adaptor.class);
    replay(adaptor);
    testLister = new AdaptorLister(adaptor, adaptorArgs);

    testLister.setTraversalSchedule(createMock(TraversalSchedule.class));
  }

  private interface ScheduleAwareAdaptor
      extends Adaptor, TraversalScheduleAware {
  }

  @Test
  public void testScheduleAware() throws Exception {
    adaptor = createMock(ScheduleAwareAdaptor.class);
    ((TraversalScheduleAware) adaptor).setTraversalSchedule(
        isA(TraversalSchedule.class));
    expectLastCall().atLeastOnce();
    replay(adaptor);
    testLister = new AdaptorLister(adaptor, adaptorArgs);

    testLister.setTraversalSchedule(createMock(TraversalSchedule.class));
  }

  @Test
  public void testShutdownLister()
      throws RepositoryException, InterruptedException {
    listerThread = startListerThread(testLister);
    Thread.sleep(500L);

    testLister.shutdown();
  }

  @Test
  public void testInterruptLister()
      throws RepositoryException, InterruptedException {
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
    // Ready, set, go! The call to shutdown happens first because it's
    // running in the current thread.
    listerThread = startListerThread(testLister);
    testLister.shutdown();
    Thread second = startListerThread(testLister);
    listerThread.join();

    // We need more time for the HttpServer to startup.
    Thread.sleep(1000L);

    URL dashboardUrl = new URL("http", "localhost", DASHBOARD_PORT, "/");
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
