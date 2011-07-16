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

import com.google.enterprise.connector.spi.AuthenticationIdentity;
import com.google.enterprise.connector.spi.SimpleAuthenticationIdentity;

import junit.framework.TestCase;

/** Tests the mapping of the username for authentication and authorization. */
public class IdentityResolverTest extends TestCase {
  /**
   * Tests the usernames for authentication and authorization.
   *
   * @param windowsDomain the {@code windowsDomain} configuration property
   * @param identity the identity under test
   * @param expectedAuthN the expected authentication username
   * @param expectedAuthZ the expected authorization username
   */   
  private void test(String windowsDomain,
      AuthenticationIdentity identity,
      String expectedAuthN, String expectedAuthZ) {
    IdentityResolver resolver = new IdentityResolver(windowsDomain);
    String authN = resolver.getAuthenticationIdentity(identity);
    assertEquals(expectedAuthN, authN);
    String authZ = resolver.getAuthorizationIdentity(identity);
    assertEquals(expectedAuthZ, authZ);
  }

  public void testNone() {
    test(null,
        new SimpleAuthenticationIdentity("fred"),
        "fred", "fred");
    test("",
        new SimpleAuthenticationIdentity("fred"),
        "fred", "fred");
  }

  public void testDns() {
    test(null,
        new SimpleAuthenticationIdentity("fred@hogwarts.edu"),
        "hogwarts.edu\\fred", "fred");
    test("",
        new SimpleAuthenticationIdentity("fred@hogwarts.edu"),
        "hogwarts.edu\\fred", "fred");
  }

  public void testNetbios() {
    test(null,
        new SimpleAuthenticationIdentity("fred", null, "burrow"),
        "fred", "fred");
    test("",
        new SimpleAuthenticationIdentity("fred", null, "burrow"),
        "fred", "fred");
  }

  public void testDnsNetbios() {
    test(null,
        new SimpleAuthenticationIdentity("fred@hogwarts.edu", null, "burrow"),
        "hogwarts.edu\\fred", "fred");
    test("",
        new SimpleAuthenticationIdentity("fred@hogwarts.edu", null, "burrow"),
        "hogwarts.edu\\fred", "fred");
  }

  public void testWindows() {
    test("hogsmeade",
        new SimpleAuthenticationIdentity("fred"),
        "hogsmeade\\fred", "fred");
  }

  public void testWindowsDns() {
    test("hogsmeade",
        new SimpleAuthenticationIdentity("fred@hogwarts.edu"),
        "hogwarts.edu\\fred", "fred");
  }

  public void testWindowsNetbios() {
    test("hogsmeade",
        new SimpleAuthenticationIdentity("fred", null, "burrow"),
        "hogsmeade\\fred", "fred");
  }

  public void testWindowsDnsNetbios() {
    test("hogsmeade",
        new SimpleAuthenticationIdentity("fred@hogwarts.edu", null, "burrow"),
        "hogwarts.edu\\fred", "fred");
  }
}
