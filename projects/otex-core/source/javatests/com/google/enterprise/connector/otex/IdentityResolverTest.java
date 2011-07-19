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
  private void test(DomainAndName domainAndName, String windowsDomain,
      AuthenticationIdentity identity,
      String expectedAuthN, String expectedAuthZ) {
    IdentityResolver resolver =
        new IdentityResolver(domainAndName, windowsDomain);
    Object authN = resolver.getAuthenticationIdentity(identity);
    assertEquals("authN identity", expectedAuthN, authN);
    Object authZ = resolver.getAuthorizationIdentity(identity);
    assertEquals("authZ identity", expectedAuthZ, authZ);
  }

  /**
   * Helper method to shrink the text in the tests and hide the
   * password parameter, which we do not need.
   */
  private AuthenticationIdentity getIdentity(String username, String domain) {
    return new SimpleAuthenticationIdentity(username, null, domain);
  }

  public void testNodan() {
    test(DomainAndName.FALSE, null,
        getIdentity("fred", null),
        "fred", "fred");
    test(DomainAndName.FALSE, "",
        getIdentity("fred", null),
        "fred", "fred");
    test(DomainAndName.FALSE, null,
        getIdentity("fred", ""),
        "fred", "fred");
    test(DomainAndName.FALSE, "",
        getIdentity("fred", ""),
        "fred", "fred");
  }

  public void testNodanDns() {
    test(DomainAndName.FALSE, null,
        getIdentity("fred@hogwarts.edu", null),
        "fred", "fred");
    test(DomainAndName.FALSE, "",
        getIdentity("fred@hogwarts.edu", null),
        "fred", "fred");
    test(DomainAndName.FALSE, null,
        getIdentity("fred@hogwarts.edu", ""),
        "fred", "fred");
    test(DomainAndName.FALSE, "",
        getIdentity("fred@hogwarts.edu", ""),
        "fred", "fred");
  }

  public void testNodanNetbios() {
    test(DomainAndName.FALSE, null,
        getIdentity("fred", "burrow"),
        "fred", "fred");
    test(DomainAndName.FALSE, "",
        getIdentity("fred", "burrow"),
        "fred", "fred");
  }

  public void testNodanDnsNetbios() {
    test(DomainAndName.FALSE, null,
        getIdentity("fred@hogwarts.edu", "burrow"),
        "fred", "fred");
    test(DomainAndName.FALSE, "",
        getIdentity("fred@hogwarts.edu", "burrow"),
        "fred", "fred");
  }

  public void testNodanWindows() {
    test(DomainAndName.FALSE, "hogsmeade",
        getIdentity("fred", null),
        "hogsmeade\\fred", "fred");
    test(DomainAndName.FALSE, "hogsmeade",
        getIdentity("fred", ""),
        "hogsmeade\\fred", "fred");
  }

  public void testNodanWindowsDns() {
    test(DomainAndName.FALSE, "hogsmeade",
        getIdentity("fred@hogwarts.edu", null),
        "hogsmeade\\fred", "fred");
    test(DomainAndName.FALSE, "hogsmeade",
        getIdentity("fred@hogwarts.edu", ""),
        "hogsmeade\\fred", "fred");
  }

  public void testNodanWindowsNetbios() {
    test(DomainAndName.FALSE, "hogsmeade",
        getIdentity("fred", "burrow"),
        "hogsmeade\\fred", "fred");
  }

  public void testNodanWindowsDnsNetbios() {
    test(DomainAndName.FALSE, "hogsmeade",
        getIdentity("fred@hogwarts.edu", "burrow"),
        "hogsmeade\\fred", "fred");
  }

  public void testLegacy() {
    test(DomainAndName.LEGACY, null,
        getIdentity("fred", null),
        "fred", "fred");
    test(DomainAndName.LEGACY, "",
        getIdentity("fred", null),
        "fred", "fred");
    test(DomainAndName.LEGACY, null,
        getIdentity("fred", ""),
        "fred", "fred");
    test(DomainAndName.LEGACY, "",
        getIdentity("fred", ""),
        "fred", "fred");
  }

  public void testLegacyDns() {
    test(DomainAndName.LEGACY, null,
        getIdentity("fred@hogwarts.edu", null),
        "fred@hogwarts.edu", "fred");
    test(DomainAndName.LEGACY, "",
        getIdentity("fred@hogwarts.edu", null),
        "fred@hogwarts.edu", "fred");
    test(DomainAndName.LEGACY, null,
        getIdentity("fred@hogwarts.edu", ""),
        "fred@hogwarts.edu", "fred");
    test(DomainAndName.LEGACY, "",
        getIdentity("fred@hogwarts.edu", ""),
        "fred@hogwarts.edu", "fred");
  }

  public void testLegacyNetbios() {
    test(DomainAndName.LEGACY, null,
        getIdentity("fred", "burrow"),
        "fred", "fred");
    test(DomainAndName.LEGACY, "",
        getIdentity("fred", "burrow"),
        "fred", "fred");
  }

  public void testLegacyDnsNetbios() {
    test(DomainAndName.LEGACY, null,
        getIdentity("fred@hogwarts.edu", "burrow"),
        "fred@hogwarts.edu", "fred");
    test(DomainAndName.LEGACY, "",
        getIdentity("fred@hogwarts.edu", "burrow"),
        "fred@hogwarts.edu", "fred");
  }

  public void testLegacyWindows() {
    test(DomainAndName.LEGACY, "hogsmeade",
        getIdentity("fred", null),
        "hogsmeade\\fred", "fred");
    test(DomainAndName.LEGACY, "hogsmeade",
        getIdentity("fred", ""),
        "hogsmeade\\fred", "fred");
  }

  public void testLegacyWindowsDns() {
    test(DomainAndName.LEGACY, "hogsmeade",
        getIdentity("fred@hogwarts.edu", null),
        "fred@hogwarts.edu", "fred");
    test(DomainAndName.LEGACY, "hogsmeade",
        getIdentity("fred@hogwarts.edu", ""),
        "fred@hogwarts.edu", "fred");
  }

  public void testLegacyWindowsNetbios() {
    test(DomainAndName.LEGACY, "hogsmeade",
        getIdentity("fred", "burrow"),
        "hogsmeade\\fred", "fred");
  }

  public void testLegacyWindowsDnsNetbios() {
    test(DomainAndName.LEGACY, "hogsmeade",
        getIdentity("fred@hogwarts.edu", "burrow"),
        "fred@hogwarts.edu", "fred");
  }

  public void testAuthn() {
    test(DomainAndName.AUTHENTICATION, null,
        getIdentity("fred", null),
        "fred", "fred");
    test(DomainAndName.AUTHENTICATION, "",
        getIdentity("fred", null),
        "fred", "fred");
    test(DomainAndName.AUTHENTICATION, null,
        getIdentity("fred", ""),
        "fred", "fred");
    test(DomainAndName.AUTHENTICATION, "",
        getIdentity("fred", ""),
        "fred", "fred");
  }

  public void testAuthnDns() {
    test(DomainAndName.AUTHENTICATION, null,
        getIdentity("fred@hogwarts.edu", null),
        "fred@hogwarts.edu", "fred");
    test(DomainAndName.AUTHENTICATION, "",
        getIdentity("fred@hogwarts.edu", null),
        "fred@hogwarts.edu", "fred");
    test(DomainAndName.AUTHENTICATION, null,
        getIdentity("fred@hogwarts.edu", ""),
        "fred@hogwarts.edu", "fred");
    test(DomainAndName.AUTHENTICATION, "",
        getIdentity("fred@hogwarts.edu", ""),
        "fred@hogwarts.edu", "fred");
  }

  public void testAuthnNetbios() {
    test(DomainAndName.AUTHENTICATION, null,
        getIdentity("fred", "burrow"),
        "burrow\\fred", "fred");
    test(DomainAndName.AUTHENTICATION, "",
        getIdentity("fred", "burrow"),
        "burrow\\fred", "fred");
  }

  public void testAuthnDnsNetbios() {
    test(DomainAndName.AUTHENTICATION, null,
        getIdentity("fred@hogwarts.edu", "burrow"),
        "burrow\\fred@hogwarts.edu", "fred");
    test(DomainAndName.AUTHENTICATION, "",
        getIdentity("fred@hogwarts.edu", "burrow"),
        "burrow\\fred@hogwarts.edu", "fred");
  }

  public void testAuthnWindows() {
    test(DomainAndName.AUTHENTICATION, "hogsmeade",
        getIdentity("fred", null),
        "hogsmeade\\fred", "fred");
    test(DomainAndName.AUTHENTICATION, "hogsmeade",
        getIdentity("fred", ""),
        "hogsmeade\\fred", "fred");
  }

  public void testAuthnWindowsDns() {
    test(DomainAndName.AUTHENTICATION, "hogsmeade",
        getIdentity("fred@hogwarts.edu", null),
        "fred@hogwarts.edu", "fred");
    test(DomainAndName.AUTHENTICATION, "hogsmeade",
        getIdentity("fred@hogwarts.edu", ""),
        "fred@hogwarts.edu", "fred");
  }

  public void testAuthnWindowsNetbios() {
    test(DomainAndName.AUTHENTICATION, "hogsmeade",
        getIdentity("fred", "burrow"),
        "burrow\\fred", "fred");
  }

  public void testAuthnWindowsDnsNetbios() {
    test(DomainAndName.AUTHENTICATION, "hogsmeade",
        getIdentity("fred@hogwarts.edu", "burrow"),
        "burrow\\fred@hogwarts.edu", "fred");
  }

  public void testDan() {
    test(DomainAndName.TRUE, null,
        getIdentity("fred", null),
        "fred", "fred");
    test(DomainAndName.TRUE, "",
        getIdentity("fred", null),
        "fred", "fred");
    test(DomainAndName.TRUE, null,
        getIdentity("fred", ""),
        "fred", "fred");
    test(DomainAndName.TRUE, "",
        getIdentity("fred", ""),
        "fred", "fred");
  }

  public void testDanDns() {
    test(DomainAndName.TRUE, null,
        getIdentity("fred@hogwarts.edu", null),
        "fred@hogwarts.edu", "fred");
    test(DomainAndName.TRUE, "",
        getIdentity("fred@hogwarts.edu", null),
        "fred@hogwarts.edu", "fred");
    test(DomainAndName.TRUE, null,
        getIdentity("fred@hogwarts.edu", ""),
        "fred@hogwarts.edu", "fred");
    test(DomainAndName.TRUE, "",
        getIdentity("fred@hogwarts.edu", ""),
        "fred@hogwarts.edu", "fred");
  }

  public void testDanNetbios() {
    test(DomainAndName.TRUE, null,
        getIdentity("fred", "burrow"),
        "burrow\\fred", "burrow\\fred");
    test(DomainAndName.TRUE, "",
        getIdentity("fred", "burrow"),
        "burrow\\fred", "burrow\\fred");
  }

  public void testDanDnsNetbios() {
    test(DomainAndName.TRUE, null,
        getIdentity("fred@hogwarts.edu", "burrow"),
        "burrow\\fred@hogwarts.edu", "burrow\\fred");
    test(DomainAndName.TRUE, "",
        getIdentity("fred@hogwarts.edu", "burrow"),
        "burrow\\fred@hogwarts.edu", "burrow\\fred");
  }

  public void testDanWindows() {
    test(DomainAndName.TRUE, "hogsmeade",
        getIdentity("fred", null),
        "hogsmeade\\fred", "fred");
    test(DomainAndName.TRUE, "hogsmeade",
        getIdentity("fred", ""),
        "hogsmeade\\fred", "fred");
  }

  public void testDanWindowsDns() {
    test(DomainAndName.TRUE, "hogsmeade",
        getIdentity("fred@hogwarts.edu", null),
        "fred@hogwarts.edu", "fred");
    test(DomainAndName.TRUE, "hogsmeade",
        getIdentity("fred@hogwarts.edu", ""),
        "fred@hogwarts.edu", "fred");
  }

  public void testDanWindowsNetbios() {
    test(DomainAndName.TRUE, "hogsmeade",
        getIdentity("fred", "burrow"),
        "burrow\\fred", "burrow\\fred");
  }

  public void testDanWindowsDnsNetbios() {
    test(DomainAndName.TRUE, "hogsmeade",
        getIdentity("fred@hogwarts.edu", "burrow"),
        "burrow\\fred@hogwarts.edu", "burrow\\fred");
  }
}
