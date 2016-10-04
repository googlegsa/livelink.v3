// Copyright 2016 Google Inc. All Rights Reserved.
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

import static com.google.enterprise.connector.otex.IdentityUtils.LOGIN_MASK;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.enterprise.connector.otex.client.Client;
import com.google.enterprise.connector.otex.client.ClientValue;
import com.google.enterprise.connector.otex.client.mock.MockClient;
import com.google.enterprise.connector.otex.client.mock.MockClientValue;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class IdentityUtilsTest extends JdbcFixture {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private static final String GLOBAL_NAMESPACE = "globalNS";
  private static final String LOCAL_NAMESPACE = "localNS";

  private LivelinkConnector connector;
  private Client client;
  private IdentityUtils out;

  @Before
  public void setUpObjectUnderTest() throws Exception {
    LivelinkConnector connector = new LivelinkConnector(
        "com.google.enterprise.connector.otex.client.mock.MockClientFactory");
    connector.setGoogleGlobalNamespace(GLOBAL_NAMESPACE);
    connector.setGoogleLocalNamespace(LOCAL_NAMESPACE);
    client = connector.getClientFactory().createClient();
    out = new IdentityUtils(connector, client);
  }

  private Client getMockClient(final ClientValue userInfo) {
    return new MockClient() {
      @Override
      public ClientValue GetUserOrGroupByIDNoThrow(int id) {
        return userInfo;
      }
    };
  }

  @Test
  public void testGetUserOrGroupById_null() throws Exception {
    addUser(1001, "user1");
    out = new IdentityUtils(connector, getMockClient(null));
    assertNull(out.getUserOrGroupById(1001));
  }

  @Test
  public void testGetUserOrGroupById_undefined() throws Exception {
    addUser(1001, "user1");
    out = new IdentityUtils(connector, getMockClient(new MockClientValue()));
    assertNull(out.getUserOrGroupById(1001));
  }

  @Test
  public void testGetUserOrGroupById_deleted() throws Exception {
    addUser(1001, "user1");
    deleteUser(1001);
    assertNull(out.getUserOrGroupById(1001));
  }

  @Test
  public void testGetUserOrGroupById_disabled() throws Exception {
    addUser(1001, "user1", 0);
    assertNull(out.getUserOrGroupById(1001));
  }

  @Test
  public void testGetUserOrGroupById_user() throws Exception {
    addUser(1001, "user1", LOGIN_MASK);
    assertEquals(1001, out.getUserOrGroupById(1001).toInteger("ID"));
  }

  @Test
  public void testGetUserOrGroupById_group() throws Exception {
    addGroup(2001, "group1");
    assertEquals(2001, out.getUserOrGroupById(2001).toInteger("ID"));
  }

  @Test
  public void testIsDisabled_deleted() throws Exception {
    addUser(1001, "user1", LOGIN_MASK);
    deleteUser(1001);
    assertTrue(out.isDisabled(client.GetUserOrGroupByIDNoThrow(1001)));
  }

  @Test
  public void testIsDisabled_disabled() throws Exception {
    addUser(1001, "user1", 0);
    assertTrue(out.isDisabled(client.GetUserOrGroupByIDNoThrow(1001)));
  }

  @Test
  public void testIsDisabled_user() throws Exception {
    addUser(1001, "user1", LOGIN_MASK);
    assertFalse(out.isDisabled(client.GetUserOrGroupByIDNoThrow(1001)));
  }

  @Test
  public void testIsDisabled_group() throws Exception {
    addGroup(2001, "group1");
    assertFalse(out.isDisabled(client.GetUserOrGroupByIDNoThrow(2001)));
  }

  @Test
  public void testGetPrincipal_emptyName() throws Exception {
    addUser(1001, "");
    ClientValue user1 = client.GetUserOrGroupByIDNoThrow(1001);

    assertNull(out.getPrincipal(user1, null));
  }

  /** Verifies that the emptyName test is not using the factory. */
  @Test
  public void testGetPrincipal_npe() throws Exception {
    addUser(1001, "user1");
    ClientValue user1 = client.GetUserOrGroupByIDNoThrow(1001);

    thrown.expect(NullPointerException.class);
    out.getPrincipal(user1, null);
  }

  private void testGetNamespace(String userData, String expectedNamespace)
      throws Exception {
    addUser(1001, "user1");
    setUserData(1001, userData);
    ClientValue user1 = client.GetUserOrGroupByIDNoThrow(1001);

    assertEquals(expectedNamespace,
        out.getPrincipal(user1, new NamespaceFactory()));
  }

  private static class NamespaceFactory
      implements IdentityUtils.PrincipalFactory<String> {
    @Override
    public String createUser(String name, String namespace) {
      return namespace;
    }

    @Override
    public String createGroup(String name, String namespace) {
      return namespace;
    }
  }

  @Test
  public void testGetNamespace_undefined() throws Exception {
    testGetNamespace(null, LOCAL_NAMESPACE);
  }

  @Test
  public void testGetNamespace_nonAssoc() throws Exception {
    testGetNamespace("something", null);
  }

  @Test
  public void testGetNamespace_other() throws Exception {
    testGetNamespace("something=this", LOCAL_NAMESPACE);
  }

  @Test
  public void testGetNamespace_ExternalAuthenticationTrue() throws Exception {
    testGetNamespace("ExternalAuthentication=true", GLOBAL_NAMESPACE);
  }

  @Test
  public void testGetNamespace_ExternalAuthenticationFalse() throws Exception {
    testGetNamespace("ExternalAuthentication=false", LOCAL_NAMESPACE);
  }

  @Test
  public void testGetNamespace_ldap() throws Exception {
    testGetNamespace("LDAP=example,something=that", GLOBAL_NAMESPACE);
  }

  @Test
  public void testGetNamespace_ambiguous() throws Exception {
    testGetNamespace("ExternalAuthentication=false,ldap=example.com",
        GLOBAL_NAMESPACE);
  }

  @Test
  public void testGetNamespace_ntlm() throws Exception {
    testGetNamespace("something=that,ntlm=", GLOBAL_NAMESPACE);
  }
}
