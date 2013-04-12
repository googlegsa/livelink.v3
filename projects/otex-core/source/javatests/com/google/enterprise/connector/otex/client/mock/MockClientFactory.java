// Copyright 2007 Google Inc.
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

package com.google.enterprise.connector.otex.client.mock;

import java.lang.ref.WeakReference;
import java.sql.Connection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.enterprise.connector.otex.client.Client;
import com.google.enterprise.connector.otex.client.ClientFactory;

/**
 * A mock factory for the facade interface that encapsulates the Livelink API.
 * This implementation caches the configuration properties in a map that can
 * be retrieved by the tests.
 */
public class MockClientFactory implements ClientFactory {
  private final HashMap<String, Object> values;

  /** The most recent username passed to {@code createClient}. */
  private String username;

  public MockClientFactory() {
    this.values = new HashMap<String, Object>();
  }

  /**
   * Gets the assigned values. The keys in the map are the names of
   * the methods called, and the values are the parameters passed.
   *
   * @return a map of the assigned values in this factory
   */
  public Map<String, Object> getValues() {
    return values;
  }

  /** {@inheritDoc} */
  public void setServer(String value) {
    values.put("setServer", value);
  }

  /** {@inheritDoc} */
  public void setPort(int value) {
    values.put("setPort", new Integer(value));
  }

  /** {@inheritDoc} */
  public void setUsername(String value) {
    values.put("setUsername", value);
  }

  /** {@inheritDoc} */
  public void setPassword(String value) {
    values.put("setPassword", value);
  }

  /** {@inheritDoc} */
  public void setConnection(String value) {
    values.put("setConnection", value);
  }

  /** {@inheritDoc} */
  public void setDomainName(String value) {
    values.put("setDomainName", value);
  }

  /** {@inheritDoc} */
  public void setEncoding(String value) {
    values.put("setEncoding", value);
  }

  /** {@inheritDoc} */
  public void setLivelinkCgi(String value) {
    values.put("setLivelinkCgi", value);
  }

  /** {@inheritDoc} */
  public void setHttps(boolean value) {
    values.put("setHttps", new Boolean(value));
  }

  /** {@inheritDoc} */
  public void setHttpUsername(String value) {
    values.put("setHttpUsername", value);
  }

  /** {@inheritDoc} */
  public void setHttpPassword(String value) {
    values.put("setHttpPassword", value);
  }

  /** {@inheritDoc} */
  public void setVerifyServer(boolean value) {
    values.put("setVerifyServer", new Boolean(value));
  }

  /** {@inheritDoc} */
  public void setCaRootCerts(List<String> value) {
    values.put("setCaRootCerts", value);
  }

  /** {@inheritDoc} */
  public void setEnableNtlm(boolean value) {
    values.put("setEnableNtlm", new Boolean(value));
  }

  /** {@inheritDoc} */
  public void setUseUsernamePasswordWithWebServer(boolean value) {
    values.put("setUseUsernamePasswordWithWebServer", new Boolean(value));
  }

  /** {@inheritDoc} */
  public Client createClient() {
    return new MockClient();
  }

  /** {@inheritDoc} */
  public Client createClient(String username, String password) {
    this.username = username;
    return new MockClient();
  }

  /** Gets the most recent username passed to {@code createClient}. */
  public String getUsername() {
    return username;
  }
}
