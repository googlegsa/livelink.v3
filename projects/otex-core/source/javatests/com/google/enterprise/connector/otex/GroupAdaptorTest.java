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

import static com.google.enterprise.connector.otex.IdentityUtils.LOGIN_MASK;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableSet;
import com.google.enterprise.adaptor.GroupPrincipal;
import com.google.enterprise.adaptor.Principal;
import com.google.enterprise.adaptor.UserPrincipal;
import com.google.enterprise.connector.otex.client.Client;
import com.google.enterprise.connector.otex.client.ClientFactory;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.TraversalSchedule;

import org.junit.Test;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class GroupAdaptorTest extends JdbcFixture {
  private static final String GLOBAL_NAMESPACE = "globalNS";
  private static final String LOCAL_NAMESPACE = "localNS";

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
    return new GroupAdaptor(connector, clientFactory);
  }

  private Map<GroupPrincipal, ? extends Collection<Principal>> getGroupInfo(
      GroupAdaptor llAdaptor) throws IOException, InterruptedException {
    FakeDocIdPusher fakePusher = new FakeDocIdPusher();
    llAdaptor.getDocIds(fakePusher);
    return fakePusher.getGroupDefinitions();
  }

  public void assertGroupsEquals(Set<GroupPrincipal> expectedGroups,
      Set<GroupPrincipal> groupSet) throws RepositoryException {
    ImmutableSet<GroupPrincipal> expectedSet =
        ImmutableSet.<GroupPrincipal>builder()
        .addAll(expectedGroups)
        .add(new GroupPrincipal(Client.SYSADMIN_GROUP, LOCAL_NAMESPACE))
        .add(new GroupPrincipal(Client.PUBLIC_ACCESS_GROUP, LOCAL_NAMESPACE))
        .build();
    assertEquals(expectedSet, groupSet);
  }

  @Test
  public void testEmptyGroup()
      throws RepositoryException, SQLException, IOException,
      InterruptedException {
    addGroup(2001, "group1");

    Map<GroupPrincipal, ? extends Collection<Principal>> groups =
        getGroupInfo(getGroupsAdaptor());

    Set<GroupPrincipal> groupSet = groups.keySet();
    Set<GroupPrincipal> expectedGroupSet =
        ImmutableSet.of(new GroupPrincipal("group1", LOCAL_NAMESPACE));
    assertGroupsEquals(expectedGroupSet, groupSet);

    Collection<Principal> users = groups.get(groupSet.iterator().next());
    assertTrue(users.isEmpty());
  }

  private void testOneGroup(GroupAdaptor out)
      throws RepositoryException, SQLException, IOException,
      InterruptedException {
    addUser(1001, "user1");
    addUser(1002, "user2");
    addGroup(2001, "group1");
    addGroupMembers(2001, 1001, 1002);

    Map<GroupPrincipal, ? extends Collection<Principal>> groups =
        getGroupInfo(out);

    Set<GroupPrincipal> groupSet = groups.keySet();
    Set<GroupPrincipal> expectedGroupSet =
        ImmutableSet.of(new GroupPrincipal("group1", LOCAL_NAMESPACE));
    assertGroupsEquals(expectedGroupSet, groupSet);

    Set<UserPrincipal> expectedUserSet =
        ImmutableSet.of(
            new UserPrincipal("user1", LOCAL_NAMESPACE),
            new UserPrincipal("user2", LOCAL_NAMESPACE));
    Collection<Principal> users = groups.get(groupSet.iterator().next());
    Set<Principal> userSet = new HashSet<Principal>(users);
    assertEquals(expectedUserSet, userSet);
  }

  @Test
  public void testOneGroup() throws Exception {
    GroupAdaptor out = getGroupsAdaptor();
    testOneGroup(out);
  }

  @Test
  public void testOneGroup_enabled() throws Exception {
    GroupAdaptor out = getGroupsAdaptor();
    setScheduleDisabled(out, false);
    testOneGroup(out);
  }

  @Test
  public void testOneGroup_disabled() throws Exception {
    addUser(1001, "user1");
    addUser(1002, "user2");
    addGroup(2001, "group1");
    addGroupMembers(2001, 1001, 1002);

    GroupAdaptor out = getGroupsAdaptor();
    setScheduleDisabled(out, true);

    assertEquals(null, getGroupInfo(out));
  }

  private void setScheduleDisabled(GroupAdaptor adaptor, boolean isDisabled) {
    TraversalSchedule schedule = createMock(TraversalSchedule.class);
    expect(schedule.isDisabled()).andReturn(isDisabled).atLeastOnce();
    replay(schedule);
    adaptor.setTraversalSchedule(schedule);
  }

  @Test
  public void testGroupNameWithSpace()
      throws RepositoryException, SQLException, IOException,
      InterruptedException {
    addUser(1001, "user1");
    addUser(1002, "user2");
    addGroup(2001, "  group1");
    addGroupMembers(2001, 1001, 1002);

    Map<GroupPrincipal, ? extends Collection<Principal>> groups =
        getGroupInfo(getGroupsAdaptor());

    Set<GroupPrincipal> groupSet = groups.keySet();
    GroupPrincipal expectedGroup =
        new GroupPrincipal("group1", LOCAL_NAMESPACE);

    Set<GroupPrincipal> expectedGroupSet =
        ImmutableSet.of(expectedGroup);
    assertGroupsEquals(expectedGroupSet, groupSet);

    Set<UserPrincipal> expectedUserSet =
        ImmutableSet.of(
            new UserPrincipal("user1", LOCAL_NAMESPACE),
            new UserPrincipal("user2", LOCAL_NAMESPACE));
    Collection<Principal> users = groups.get(expectedGroup);
    Set<Principal> userSet = new HashSet<Principal>(users);
    assertEquals(expectedUserSet, userSet);
  }

  @Test
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

    Set<GroupPrincipal> groupSet = groups.keySet();
    GroupPrincipal expectedGroup1 =
        new GroupPrincipal("group1", LOCAL_NAMESPACE);
    GroupPrincipal expectedGroup2 =
        new GroupPrincipal("group2", LOCAL_NAMESPACE);
    Set<GroupPrincipal> expectedGroupSet =
        ImmutableSet.of(expectedGroup1, expectedGroup2);
    assertGroupsEquals(expectedGroupSet, groupSet);

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

  @Test
  public void testDisabledUsersAndGroups() throws Exception {
    addUser(1001, "user1",
        Client.PRIV_PERM_WORLD | Client.PRIV_PERM_BYPASS | LOGIN_MASK);
    addUser(1002, "user2", Client.PRIV_PERM_WORLD);
    addUser(1003, "user3");
    addGroup(2001, "group1");
    addGroupMembers(2001, 1001, 1002, 1003);
    deleteUser(1001);

    addUser(1004, "user4", Client.PRIV_PERM_WORLD | LOGIN_MASK);
    addGroup(2002, "group2");
    addGroupMembers(2002, 1003, 1004);
    deleteGroup(2002);

    Map<GroupPrincipal, ? extends Collection<Principal>> groups =
        getGroupInfo(getGroupsAdaptor());

    GroupPrincipal expectedGroup =
        new GroupPrincipal("group1", LOCAL_NAMESPACE);
    GroupPrincipal sysAdminGroup =
        new GroupPrincipal(Client.SYSADMIN_GROUP, LOCAL_NAMESPACE);
    GroupPrincipal pubAccessGroup =
        new GroupPrincipal(Client.PUBLIC_ACCESS_GROUP, LOCAL_NAMESPACE);
    assertEquals(ImmutableSet.of(expectedGroup, sysAdminGroup, pubAccessGroup),
        groups.keySet());

    assertEquals(
        ImmutableSet.of(new UserPrincipal("user3", LOCAL_NAMESPACE)),
        new HashSet<Principal>(groups.get(expectedGroup)));
    assertEquals(
        ImmutableSet.of(
            new UserPrincipal("Admin", LOCAL_NAMESPACE)),
        new HashSet<Principal>(groups.get(sysAdminGroup)));
    assertEquals(
        ImmutableSet.of(new UserPrincipal("user4", LOCAL_NAMESPACE)),
        new HashSet<Principal>(groups.get(pubAccessGroup)));
  }

  @Test
  public void testExternalUser() throws RepositoryException,
      SQLException, IOException, InterruptedException {
    addUser(1001, "user1");
    addUser(1002, "user2");
    setUserData(1002, "ExternalAuthentication=true");
    addGroup(2001, "group1");
    addGroupMembers(2001, 1001, 1002);

    Map<GroupPrincipal, ? extends Collection<Principal>> groups =
        getGroupInfo(getGroupsAdaptor());

    Set<GroupPrincipal> groupSet = groups.keySet();
    Set<GroupPrincipal> expectedGroupSet =
        ImmutableSet.of(new GroupPrincipal("group1", LOCAL_NAMESPACE));
    assertGroupsEquals(expectedGroupSet, groupSet);

    Set<UserPrincipal> expectedUserSet =
        ImmutableSet.of(
            new UserPrincipal("user1", LOCAL_NAMESPACE),
            new UserPrincipal("user2", GLOBAL_NAMESPACE));
    Collection<Principal> users = groups.get(groupSet.iterator().next());
    Set<Principal> userSet = new HashSet<Principal>(users);
    assertEquals(expectedUserSet, userSet);
  }

  @Test
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

    Set<GroupPrincipal> groupSet = groups.keySet();
    GroupPrincipal expectedGroup1 =
        new GroupPrincipal("group1", LOCAL_NAMESPACE);
    GroupPrincipal expectedGroup2 =
        new GroupPrincipal("group2", LOCAL_NAMESPACE);
    GroupPrincipal expectedGroup3 =
        new GroupPrincipal("group3", GLOBAL_NAMESPACE);
    Set<GroupPrincipal> expectedGroupSet =
        ImmutableSet.of(expectedGroup1, expectedGroup2, expectedGroup3);
    assertGroupsEquals(expectedGroupSet, groupSet);

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

  private void testGroupsForUserPrivileges(int privileges, String... groupNames)
      throws RepositoryException, SQLException, IOException,
      InterruptedException {
    addUser(1001, "user1", privileges | LOGIN_MASK);
    Set<String> groupNamesSet = ImmutableSet.copyOf(groupNames);

    Map<GroupPrincipal, ? extends Collection<Principal>> groups =
        getGroupInfo(getGroupsAdaptor());

    Set<GroupPrincipal> groupSet = groups.keySet();
    for (GroupPrincipal group : groupSet) {
      Collection<Principal> members = groups.get(group);
      assertEquals(group.getName() + " - " + members.toString(),
          groupNamesSet.contains(group.getName()),
          members.contains(new UserPrincipal("user1", LOCAL_NAMESPACE)));
    }
  }

  @Test
  public void testSysAdminPrivileges() throws SQLException, IOException,
      InterruptedException, RepositoryException {
    testGroupsForUserPrivileges(Client.PRIV_PERM_BYPASS, Client.SYSADMIN_GROUP);
  }

  @Test
  public void testPublicAccessPrivileges() throws SQLException, IOException,
      InterruptedException, RepositoryException {
    testGroupsForUserPrivileges(Client.PRIV_PERM_WORLD,
        Client.PUBLIC_ACCESS_GROUP);
  }

  @Test
  public void testSysAdminAndPublicAccessPrivileges() throws SQLException,
      IOException, InterruptedException, RepositoryException {
    testGroupsForUserPrivileges(
        (Client.PRIV_PERM_BYPASS | Client.PRIV_PERM_WORLD),
        Client.PUBLIC_ACCESS_GROUP, Client.SYSADMIN_GROUP);
  }

  @Test
  public void testNoPublAccessPrivileges() throws SQLException, IOException,
      InterruptedException, RepositoryException {
    testGroupsForUserPrivileges((~0 & ~Client.PRIV_PERM_WORLD),
        Client.SYSADMIN_GROUP);
  }

  @Test
  public void testNoSysAdminPrivileges() throws SQLException, IOException,
      InterruptedException, RepositoryException {
    testGroupsForUserPrivileges((~0 & ~Client.PRIV_PERM_BYPASS),
        Client.PUBLIC_ACCESS_GROUP);
  }

  @Test
  public void testNoSysAdminOrPublAccessPrivileges() throws SQLException,
      IOException, InterruptedException, RepositoryException {
    testGroupsForUserPrivileges(~0 & ~Client.PRIV_PERM_BYPASS
        & ~Client.PRIV_PERM_WORLD);
  }

  @Test
  public void testFullPrivileges() throws SQLException, IOException,
      InterruptedException, RepositoryException {
    testGroupsForUserPrivileges(~0, Client.PUBLIC_ACCESS_GROUP,
        Client.SYSADMIN_GROUP);
  }

  @Test
  public void testNoPrivileges() throws SQLException, IOException,
      InterruptedException, RepositoryException {
    testGroupsForUserPrivileges(0);
  }

  @Test
  public void testForSysAdminPublicAccessGroups() throws IOException,
      InterruptedException, RepositoryException {
    Map<GroupPrincipal, ? extends Collection<Principal>> groups =
        getGroupInfo(getGroupsAdaptor());

    Set<GroupPrincipal> groupSet = groups.keySet();
    GroupPrincipal pubAccessGroup =
        new GroupPrincipal(Client.PUBLIC_ACCESS_GROUP, LOCAL_NAMESPACE);
    GroupPrincipal sysAdminGroup =
        new GroupPrincipal(Client.SYSADMIN_GROUP, LOCAL_NAMESPACE);
    assertEquals(groupSet, ImmutableSet.of(sysAdminGroup, pubAccessGroup));

    Collection<Principal> publAccessMembers = groups.get(pubAccessGroup);
    assertTrue(publAccessMembers.isEmpty());
    Collection<Principal> sysAdminMembers = groups.get(sysAdminGroup);
    assertEquals(ImmutableSet.of(new UserPrincipal("Admin", LOCAL_NAMESPACE)),
        ImmutableSet.copyOf(sysAdminMembers));
  }
}
