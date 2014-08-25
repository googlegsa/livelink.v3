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

import com.google.enterprise.connector.otex.client.Client;
import com.google.enterprise.connector.otex.client.ClientFactory;
import com.google.enterprise.connector.spi.RepositoryException;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;

import junit.framework.TestCase;

public class GroupListerTest extends TestCase {
  private final JdbcFixture jdbcFixture = new JdbcFixture();

  private GroupLister testLister;
  private List<RepositoryException> exceptionList;
  private Thread listerThread;
  private HttpServer httpServer;
  
  @Override
  protected void setUp()
      throws SQLException, IOException, RepositoryException,
      InterruptedException {
    jdbcFixture.setUp();

    httpServer = SimpleHttpServer.getHttpServer();
    httpServer.start();

    testLister = getGroupLister();
    exceptionList = new LinkedList<RepositoryException>();
    listerThread = startListerThread();
    Thread.sleep(500L);
  }

  @Override
  protected void tearDown() throws SQLException, IOException,
      RepositoryException, InterruptedException {
    try {
      listerThread.join();
      if (!exceptionList.isEmpty()) {
        throw exceptionList.get(0);
      }
    } finally {
      httpServer.stop(0);
      jdbcFixture.tearDown();
    }
  }

  private GroupLister getGroupLister() throws RepositoryException {
    LivelinkConnector connector = new LivelinkConnector(
        "com.google.enterprise.connector.otex.client.mock.MockClientFactory");
    connector.setGoogleFeedHost("localhost");
    connector.setGroupFeedSchedule("0 1 * * *");

    ClientFactory clientFactory = connector.getClientFactory();
    Client client = clientFactory.createClient();
    return new GroupLister(connector, new GroupAdaptor(connector, client));
  }

  private Thread startListerThread() {
    Thread t = new Thread() {
      public void run() {
        try {
          testLister.start();
        } catch (RepositoryException e) {
          exceptionList.add(e);
        }
      }
    };
    t.start();
    return t;
  }

  public void testShutdownLister() throws RepositoryException,
      InterruptedException, IOException {
    testLister.shutdown();
  }

  public void testInterruptLister() throws RepositoryException,
      InterruptedException, IOException {
    listerThread.interrupt();
  }
}
