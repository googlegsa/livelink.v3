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

import com.google.common.base.Strings;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

/**
 * This class starts and stops an HTTP server on localhost, which just serves
 * the GSA version string for Adaptor. This is used for tests.
 */
public class SimpleHttpServer {
  public static HttpServer getHttpServer() throws IOException {
    int httpPort;
    String port = System.getProperty("tests.httpServerPort");
    if (Strings.isNullOrEmpty(port)) {
      httpPort = 80;
    } else {
      httpPort = Integer.parseInt(port);
    }

    HttpServer server;
    server = HttpServer.create(new InetSocketAddress(httpPort), 0);
    server.createContext("/sw_version.txt", new SimpleHandler());
    server.setExecutor(null);

    return server;
  }

  private static class SimpleHandler implements HttpHandler {
    public void handle(HttpExchange httpExchange) throws IOException {
      String version = "7.2.0-74";
      httpExchange.sendResponseHeaders(200, version.length());
      OutputStream os = httpExchange.getResponseBody();
      os.write(version.getBytes());
      os.close();
    }
  }
}
