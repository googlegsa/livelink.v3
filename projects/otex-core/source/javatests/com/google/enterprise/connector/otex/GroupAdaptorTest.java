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

import com.google.common.collect.ImmutableSet;
import com.google.enterprise.adaptor.GroupPrincipal;
import com.google.enterprise.adaptor.Principal;
import com.google.enterprise.adaptor.UserPrincipal;
import com.google.enterprise.connector.otex.client.Client;
import com.google.enterprise.connector.otex.client.ClientFactory;
import com.google.enterprise.connector.spi.RepositoryException;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import junit.framework.TestCase;

public class GroupAdaptorTest extends TestCase {

  private static final String GLOBAL_NAMESPACE = "globalNS";
  private static final String LOCAL_NAMESPACE = "localNS";

  private final JdbcFixture jdbcFixture = new JdbcFixture();

  @Override
  protected void setUp() throws SQLException {
    jdbcFixture.setUp();
  }

  @Override
  protected void tearDown() throws SQLException {
    jdbcFixture.tearDown();
  }

  private LivelinkConnector getConnector()
      throws RepositoryException {
    LivelinkConnector connector = new LivelinkConnector(
        "com.google.enterprise.connector.otex.client.mock.MockClientFactory");
    connector.setGoogleGlobalNamespace(GLOBAL_NAMESPACE);
    connector.setGoogleLocalNamespace(LOCAL_NAMESPACE);
    return connector;
  }

  private GroupAdaptor getGroupsAdaptor()
      throws RepositoryException {
    LivelinkConnector connector = getConnector();
    ClientFactory clientFactory = connector.getClientFactory();
    Client client = clientFactory.createClient();
    return new GroupAdaptor(connector, client);
  }

  private void addUser(int userId, String name) throws SQLException {
    jdbcFixture.executeUpdate("insert into KUAF(ID, Name, Type, UserData)"
        + " values(" + userId + ",'" + name + "' ,0 , NULL)");
  }

  private void addGroup(int userId, String name) throws SQLException {
    jdbcFixture.executeUpdate("insert into KUAF(ID, Name, Type, UserData)"
        + " values(" + userId + ",'" + name + "' ,1 , NULL)");
  }

  private void addGroupMembers(int groupId, int... userIds)
      throws SQLException {
    for (int i = 0; i < userIds.length; i++) {
      jdbcFixture.executeUpdate("insert into KUAFChildren(ID, ChildID)"
          + " values(" + groupId + ", " + userIds[i] + ")");
    }
  }

  private void setUserData(int userId, String userData) throws SQLException {
    jdbcFixture.executeUpdate("UPDATE KUAF SET UserData = '" + userData
        + "' where ID = " + userId);
  }

  private Map<GroupPrincipal, ? extends Collection<Principal>> getGroupInfo(
      GroupAdaptor llAdaptor) throws IOException, InterruptedException {
    FakeDocIdPusher fakePusher = new FakeDocIdPusher();
    llAdaptor.getDocIds(fakePusher);
    return fakePusher.getGroupDefinitions();
  }

  public void testEmptyGroup()
      throws RepositoryException, SQLException, IOException,
      InterruptedException {
    addGroup(2001, "group1");

    Map<GroupPrincipal, ? extends Collection<Principal>> groups =
        getGroupInfo(getGroupsAdaptor());

    assertEquals(1, groups.size());

    Set<GroupPrincipal> groupSet = groups.keySet();
    Set<GroupPrincipal> expectedGroupSet =
        ImmutableSet.of(new GroupPrincipal("group1", LOCAL_NAMESPACE));
    assertEquals(expectedGroupSet, groupSet);

    Collection<Principal> users = groups.get(groupSet.iterator().next());
    assertTrue(users.isEmpty());
  }

  public void testOneGroup()
      throws RepositoryException, SQLException, IOException,
      InterruptedException {
    addUser(1001, "user1");
    addUser(1002, "user2");
    addGroup(2001, "group1");
    addGroupMembers(2001, 1001, 1002);

    Map<GroupPrincipal, ? extends Collection<Principal>> groups =
        getGroupInfo(getGroupsAdaptor());

    assertEquals(1, groups.size());

    Set<GroupPrincipal> groupSet = groups.keySet();
    Set<GroupPrincipal> expectedGroupSet =
        ImmutableSet.of(new GroupPrincipal("group1", LOCAL_NAMESPACE));
    assertEquals(expectedGroupSet, groupSet);

    Set<UserPrincipal> expectedUserSet =
        ImmutableSet.of(
            new UserPrincipal("user1", LOCAL_NAMESPACE),
            new UserPrincipal("user2", LOCAL_NAMESPACE));
    Collection<Principal> users = groups.get(groupSet.iterator().next());
    Set<Principal> userSet = new HashSet<Principal>(users);
    assertEquals(expectedUserSet, userSet);
  }

  public void testMultipleGroups()
      throws RepositoryException, SQLException, IOException,
      InterruptedException {
    addUser(1001, "user1");
    addUser(1002, "user2");
    addGroup(2001, "group1");
    addGroupMembers(2001, 1001, 1002);

    addUser(1001, "user1");
    addUser(1003, "user3");
    addUser(1004, "user4");
    addGroup(2002, "group2");
    addGroupMembers(2002, 1001, 1003, 1004);

    Map<GroupPrincipal, ? extends Collection<Principal>> groups =
        getGroupInfo(getGroupsAdaptor());

    assertEquals(2, groups.size());

    Set<GroupPrincipal> groupSet = groups.keySet();
    GroupPrincipal expectedGroup1 =
        new GroupPrincipal("group1", LOCAL_NAMESPACE);
    GroupPrincipal expectedGroup2 =
        new GroupPrincipal("group2", LOCAL_NAMESPACE);
    Set<GroupPrincipal> expectedGroupSet =
        ImmutableSet.of(expectedGroup1, expectedGroup2);
    assertEquals(expectedGroupSet, groupSet);

    Set<UserPrincipal> expectedUserSet1 =
        ImmutableSet.of(
            new UserPrincipal("user1", LOCAL_NAMESPACE),
            new UserPrincipal("user2", LOCAL_NAMESPACE));
    Collection<Principal> users1 = groups.get(expectedGroup1);
    Set<Principal> userSet1 = new HashSet<Principal>(users1);
    assertEquals(expectedUserSet1, userSet1);

    Set<UserPrincipal> expectedUserSet2 =
        ImmutableSet.of(
            new UserPrincipal("user1", LOCAL_NAMESPACE),
            new UserPrincipal("user3", LOCAL_NAMESPACE),
            new UserPrincipal("user4", LOCAL_NAMESPACE));
    Collection<Principal> users2 = groups.get(expectedGroup2);
    Set<Principal> userSet2 = new HashSet<Principal>(users2);
    assertEquals(expectedUserSet2, userSet2);
  }

  public void testExternalUser() throws RepositoryException,
      SQLException, IOException, InterruptedException {
    addUser(1001, "user1");
    addUser(1002, "user2");
    setUserData(1002, "ExternalAuthentication=true");
    addGroup(2001, "group1");
    addGroupMembers(2001, 1001, 1002);

    Map<GroupPrincipal, ? extends Collection<Principal>> groups =
        getGroupInfo(getGroupsAdaptor());

    assertEquals(1, groups.size());

    Set<GroupPrincipal> groupSet = groups.keySet();
    Set<GroupPrincipal> expectedGroupSet =
        ImmutableSet.of(new GroupPrincipal("group1", LOCAL_NAMESPACE));
    assertEquals(expectedGroupSet, groupSet);

    Set<UserPrincipal> expectedUserSet =
        ImmutableSet.of(
            new UserPrincipal("user1", LOCAL_NAMESPACE),
            new UserPrincipal("user2", GLOBAL_NAMESPACE));
    Collection<Principal> users = groups.get(groupSet.iterator().next());
    Set<Principal> userSet = new HashSet<Principal>(users);
    assertEquals(expectedUserSet, userSet);
  }

  public void testNestedGroup()
      throws RepositoryException, SQLException, IOException,
      InterruptedException {
    addUser(1001, "user1");
    addUser(1002, "user2");
    addUser(1003, "user3");
    addGroup(2001, "group1");
    addGroup(2002, "group2");
    addGroup(2003, "group3");
    setUserData(2003, "ExternalAuthentication=true");
    addGroupMembers(2001, 1001, 1002);
    addGroupMembers(2002, 1001, 1003, 2001, 2003);

    Map<GroupPrincipal, ? extends Collection<Principal>> groups =
        getGroupInfo(getGroupsAdaptor());

    assertEquals(3, groups.size());

    Set<GroupPrincipal> groupSet = groups.keySet();
    GroupPrincipal expectedGroup1 =
        new GroupPrincipal("group1", LOCAL_NAMESPACE);
    GroupPrincipal expectedGroup2 =
        new GroupPrincipal("group2", LOCAL_NAMESPACE);
    GroupPrincipal expectedGroup3 =
        new GroupPrincipal("group3", GLOBAL_NAMESPACE);
    Set<GroupPrincipal> expectedGroupSet =
        ImmutableSet.of(expectedGroup1, expectedGroup2, expectedGroup3);
    assertEquals(expectedGroupSet, groupSet);

    Set<UserPrincipal> expectedUserSet1 =
        ImmutableSet.of(
            new UserPrincipal("user1", LOCAL_NAMESPACE),
            new UserPrincipal("user2", LOCAL_NAMESPACE));
    Collection<Principal> users1 = groups.get(expectedGroup1);
    Set<Principal> userSet1 = new HashSet<Principal>(users1);
    assertEquals(expectedUserSet1, userSet1);

    Set<Principal> expectedUserSet2 = ImmutableSet.of(
        new UserPrincipal("user1", LOCAL_NAMESPACE),
        new UserPrincipal("user3", LOCAL_NAMESPACE),
        new GroupPrincipal("group1", LOCAL_NAMESPACE),
        new GroupPrincipal("group3", GLOBAL_NAMESPACE));
    Collection<Principal> users2 = groups.get(expectedGroup2);
    Set<Principal> userSet2 = new HashSet<Principal>(users2);
    assertEquals(expectedUserSet2, userSet2);

    Collection<Principal> users3 = groups.get(expectedGroup3);
    assertTrue(users3.isEmpty());
  }
}
