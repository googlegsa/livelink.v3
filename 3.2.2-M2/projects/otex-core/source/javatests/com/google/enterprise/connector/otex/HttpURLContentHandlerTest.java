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

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import junit.framework.TestCase;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;

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

  public void testConnectTimeout() {
    assertEquals(0, out.getConnectTimeout());
    int expected = 60;
    out.setConnectTimeout(expected);

    assertEquals(expected * 1000, out.getConnectTimeout());
  }

  // TODO(jlacey): Add a live test of the connect timeout. Maybe we
  // can use non-blocking I/O and not call select?

  public void testReadTimeout() {
    assertEquals(0, out.getReadTimeout());
    int expected = 60;
    out.setReadTimeout(expected);

    assertEquals(expected * 1000, out.getReadTimeout());
  }

  /** In practice, this timeout exception will be caught in the connector. */
  /* TODO(jlacey): Verify that the timeout exception skips the document. */
  public void testReadTimeoutInHeaders()
      throws RepositoryException, IOException {
    // Sleep 2 seconds and timeout after 1 second.
    out.setReadTimeout(1);
    HttpServer server = createSleepyServer(2, SleepyLocation.HEADERS);
    server.start();

    try {
      out.getInputStream(0, 0, 0, 0);
      fail("Expected a SocketTimeoutException");
    } catch (LivelinkException expected) {
      assertNotNull(expected.toString(), expected.getCause());
      assertTrue(expected.getCause().toString(),
          expected.getCause() instanceof SocketTimeoutException);
    } finally {
      server.stop(0);
    }
  }

  /**
   * In practice, this timeout exception will be caught in the
   * connector manager.
   */
  /* TODO(jlacey): Verify that the timeout exception skips the document. */
  public void testReadTimeoutInBody() throws RepositoryException, IOException {
    // Sleep 2 seconds and timeout after 1 second.
    out.setReadTimeout(1);
    HttpServer server = createSleepyServer(2, SleepyLocation.BODY);
    server.start();

    InputStream in = out.getInputStream(0, 0, 0, 0);
    try {
      in.read();
      fail("Expected a SocketTimeoutException");
    } catch (SocketTimeoutException expected) {
    } finally {
      server.stop(0);
    }
  }

  public enum SleepyLocation { HEADERS, BODY };

  /**
   * Creates a sleepy HttpServer and configures the object under test to
   * talk to it.
   */
  private HttpServer createSleepyServer(int sleepSeconds, SleepyLocation where)
      throws RepositoryException, IOException {
    // TODO(jlacey): Get an unused port.
    String host = "localhost";
    int port = 58080;
    String path = "/";

    out.setUrlBase("http://" + host + ":" + port);
    out.setUrlPath(path);
    out.initialize(connector, client);

    HttpServer server = HttpServer.create(new InetSocketAddress(host, port), 0);
    server.createContext(path, new SleepyHandler(sleepSeconds, where));
    return server;
  }

  static class SleepyHandler implements HttpHandler {
    private final int sleepSeconds;
    private final SleepyLocation where;

    public SleepyHandler(int sleepSeconds, SleepyLocation where) {
      this.sleepSeconds = sleepSeconds;
      this.where = where;
    }

    public void handle(HttpExchange exchange) throws IOException {
      if (where == SleepyLocation.HEADERS) {
        sleep();
      }
      byte[] response = "hello, world".getBytes();
      exchange.sendResponseHeaders(200, response.length);
      OutputStream body = exchange.getResponseBody();

      if (where == SleepyLocation.BODY) {
        sleep();
      }
      body.write(response);
      body.close();
    }

    private void sleep() {
      try {
        Thread.sleep(sleepSeconds * 1000);
      } catch (InterruptedException e) {
        // TODO(jlacey): Rethrow as an InterruptedIOException?
      }
    }
  }
}
