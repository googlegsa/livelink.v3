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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableList;
import com.google.enterprise.connector.spi.DocumentAcceptor;
import com.google.enterprise.connector.spi.Lister;
import com.google.enterprise.connector.spi.RepositoryException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AbstractListerTest {
  private static final Logger LOGGER =
      Logger.getLogger(AbstractListerTest.class.getName());

  private interface TraceableLister extends Lister {
    List<String> getMethodCalls();
  }

  private static class MockAbstractLister extends AbstractLister
      implements TraceableLister {
    private final List<String> methodCalls = new LinkedList<String>();
    public List<String> getMethodCalls() { return methodCalls; }

    @Override public void init() { methodCalls.add("init"); }
    @Override public void run() { methodCalls.add("run"); waitForShutdown(); }
    @Override public void destroy() { methodCalls.add("destroy"); }

    @Override
    public void setDocumentAcceptor(DocumentAcceptor documentAcceptor) {}
  }

  private static class MockLister implements TraceableLister {
    private final List<String> methodCalls = new LinkedList<String>();
    public List<String> getMethodCalls() { return methodCalls; }

    @Override public void start() { methodCalls.add("start"); }
    @Override public void shutdown() { methodCalls.add("shutdown"); }

    @Override
    public void setDocumentAcceptor(DocumentAcceptor documentAcceptor) {}
  }

  private TraceableLister testLister;
  private List<String> expectedCalls;
  private List<Exception> exceptionList;
  private Thread listerThread;

  @Before
  public void setUp() {
    testLister = new MockAbstractLister();
    exceptionList = new LinkedList<Exception>();
  }

  @After
  public void tearDown() throws RepositoryException, InterruptedException {
    listerThread.join();
    if (!exceptionList.isEmpty()) {
      propagateIfPossible(exceptionList.get(0), RepositoryException.class);
    }
    assertEquals(expectedCalls, testLister.getMethodCalls());
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
  public void testShutdownLister()
      throws RepositoryException, InterruptedException {
    expectedCalls = ImmutableList.of("init", "run", "destroy");

    listerThread = startListerThread(testLister);
    Thread.sleep(100L);

    testLister.shutdown();
  }

  @Test
  public void testInterruptLister()
      throws RepositoryException, InterruptedException {
    expectedCalls = ImmutableList.of("init", "run", "destroy");

    listerThread = startListerThread(testLister);
    Thread.sleep(100L);

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
  private void testRaceCondition()
      throws RepositoryException, InterruptedException {
    // Ready, set, go! The call to shutdown happens first because it's
    // running in the current thread.
    listerThread = startListerThread(testLister);
    testLister.shutdown();
    Thread second = startListerThread(testLister);
    Thread.sleep(100L);
    listerThread.join();

    testLister.shutdown();
    second.join();
  }

  @Test
  public void testFixedRaceCondition() throws Exception {
    expectedCalls =
        ImmutableList.of("init", "run", "destroy", "init", "run", "destroy");
    testRaceCondition();
  }

  @Test
  public void testBrokenRaceCondition() throws Exception {
    testLister = new MockLister();
    expectedCalls = ImmutableList.of("shutdown", "start", "start", "shutdown");
    testRaceCondition();
  }
}
