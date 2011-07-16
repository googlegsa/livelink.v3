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

import com.google.enterprise.connector.otex.client.mock.MockClientFactory;
import com.google.enterprise.connector.spi.AuthenticationIdentity;
import com.google.enterprise.connector.spi.AuthenticationManager;
import com.google.enterprise.connector.spi.AuthenticationResponse;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.SimpleAuthenticationIdentity;

import junit.framework.TestCase;

/** Tests the construction of the username for authentication. */
public class LivelinkAuthenticationManagerTest extends TestCase {
  private void test(String windowsDomain,
      SimpleAuthenticationIdentity identity, String username, boolean valid)
      throws RepositoryException {
    test(new MockClientFactory(), windowsDomain, identity, username, valid);
  }

  private void test(MockClientFactory clientFactory, String windowsDomain,
      SimpleAuthenticationIdentity identity, String username, boolean valid)
      throws RepositoryException {
    // Get the object under test.
    AuthenticationManager lam =
        new LivelinkAuthenticationManager(clientFactory, windowsDomain);

    // Perform the action.
    AuthenticationResponse response = lam.authenticate(identity);

    // Check the results.
    assertEquals(username, clientFactory.getUsername());
    assertEquals(valid, response.isValid());
  }

  public void testNullClientFactory() throws RepositoryException {
    try {
      test(null, null,
          new SimpleAuthenticationIdentity("fred"),
          "xgzzg", false);
      fail();
    } catch (RepositoryException e) {
      if (e.getMessage().indexOf("client factory") == -1) {
        throw e;
      }
    }
  }

  public void testPlain() throws RepositoryException {
    test(null,
        new SimpleAuthenticationIdentity("fred"),
        "fred", true);
    test("",
        new SimpleAuthenticationIdentity("fred"),
        "fred", true);
  }

  public void testNetbiosDomain() throws RepositoryException {
    test(null,
        new SimpleAuthenticationIdentity("fred", null, "burrow"),
        "fred", true);
    test("",
        new SimpleAuthenticationIdentity("fred", null, "burrow"),
        "fred", true);
  }

  public void testDnsDomain() throws RepositoryException {
    test(null,
        new SimpleAuthenticationIdentity("fred@hogwarts.edu"),
        "hogwarts.edu\\fred", true);
    test("",
        new SimpleAuthenticationIdentity("fred@hogwarts.edu"),
        "hogwarts.edu\\fred", true);
  }

  public void testWindowsDomainPlain() throws RepositoryException {
    test("hogsmeade",
        new SimpleAuthenticationIdentity("fred"),
        "hogsmeade\\fred", true);
  }

  public void testWindowsDomainNetbiosDomain() throws RepositoryException {
    test("hogsmeade",
        new SimpleAuthenticationIdentity("fred", null, "burrow"),
        "hogsmeade\\fred", true);
  }

  public void testWindowsDomainDnsDomain() throws RepositoryException {
    test("hogsmeade",
        new SimpleAuthenticationIdentity("fred@hogwarts.edu"),
        "hogwarts.edu\\fred", true);
  }
}
