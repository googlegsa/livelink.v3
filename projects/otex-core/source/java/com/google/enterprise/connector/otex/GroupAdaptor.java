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

import com.google.enterprise.adaptor.AbstractAdaptor;
import com.google.enterprise.adaptor.DocIdPusher;
import com.google.enterprise.adaptor.GroupPrincipal;
import com.google.enterprise.adaptor.Principal;
import com.google.enterprise.adaptor.Request;
import com.google.enterprise.adaptor.Response;
import com.google.enterprise.adaptor.UserPrincipal;

import com.google.enterprise.connector.logging.NDC;
import com.google.enterprise.connector.otex.client.Client;
import com.google.enterprise.connector.otex.client.ClientValue;
import com.google.enterprise.connector.spi.RepositoryException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementation of {@link AbstractAdaptor} that feeds Livelink groups
 * information from the repository.
 */
class GroupAdaptor extends AbstractAdaptor {
  private static final Logger LOGGER =
      Logger.getLogger(GroupAdaptor.class.getName());

  /** The connector contains configuration information. */
  private final LivelinkConnector connector;

  /** The client provides access to the server. */
  private final Client client;

  private final IdentityUtils identityUtils;

  GroupAdaptor(LivelinkConnector connector, Client client) {
    this.connector = connector;
    this.client = client;
    this.identityUtils = new IdentityUtils(connector);
  }

  private List<Principal> getMemberPrincipalList(ClientValue groupMembers)
      throws RepositoryException {
    List<Principal> memberPrincipals = new ArrayList<Principal>();

    for (int i = 0; i < groupMembers.size(); i++) {
      String memberName = groupMembers.toString(i, "Name");
      int memberType = groupMembers.toInteger(i, "Type");
      LOGGER.log(Level.FINER, "Member name: {0} ; Member Type: {1}",
          new Object[] {memberName, memberType});
      ClientValue memberUserData = groupMembers.toValue(i, "UserData");
      String memberNamespace = identityUtils.getNamespace(memberUserData);
      if (memberType == Client.USER) {
        memberPrincipals.add(new UserPrincipal(memberName, memberNamespace));
      } else if (memberType == Client.GROUP) {
        memberPrincipals.add(new GroupPrincipal(memberName, memberNamespace));
      }
    }

    return memberPrincipals;
  }

  private Map<GroupPrincipal, List<Principal>> getLivelinkGroups()
      throws RepositoryException {
    Map<GroupPrincipal, List<Principal>> groups =
        new LinkedHashMap<GroupPrincipal, List<Principal>>();
    getStandardGroups(groups);
    getSystemAdminAndPublicGroups(groups);
    return groups;
  }

  private void getStandardGroups(Map<GroupPrincipal, List<Principal>> groups)
      throws RepositoryException {
    ClientValue groupsValue = client.ListGroups();
    for (int i = 0; i < groupsValue.size(); i++) {
      String groupName = groupsValue.toString(i, "Name");
      LOGGER.log(Level.FINER, "Fetching group members for group name: {0}",
          groupName);
      ClientValue groupUserData = groupsValue.toValue(i, "UserData");
      String groupNamespace = identityUtils.getNamespace(groupUserData);
      GroupPrincipal groupPrincipal =
          new GroupPrincipal(groupName, groupNamespace);

      ClientValue groupMembers = client.ListMembers(groupName);
      List<Principal> memberPrincipals = getMemberPrincipalList(groupMembers);
      groups.put(groupPrincipal, memberPrincipals);
      LOGGER.log(Level.FINER, "Group principal: {0} ; Member principals: {1}",
          new Object[] {groupPrincipal, memberPrincipals});
    }
  }

  private void getSystemAdminAndPublicGroups(
      Map<GroupPrincipal, List<Principal>> groups)
          throws RepositoryException {
    List<Principal> sysAdminMembers = new ArrayList<Principal>();
    List<Principal> publicAccessMembers = new ArrayList<Principal>();

    ClientValue usersValue = client.ListUsers();
    for (int i = 0; i < usersValue.size(); i++) {
      String userName = usersValue.toString(i, "Name");
      LOGGER.log(Level.FINER, "Fetching groups for {0}", userName);
      ClientValue userData = usersValue.toValue(i, "UserData");
      String userNamespace = identityUtils.getNamespace(userData);

      ClientValue usersInfo = client.GetUserInfo(userName);
      int privs = usersInfo.toInteger("UserPrivileges");

      if ((privs & Client.PRIV_PERM_BYPASS) == Client.PRIV_PERM_BYPASS) {
        LOGGER.log(Level.FINER, "Admin Privileges for user {0}: {1}",
            new Object[] {userName, privs});
        sysAdminMembers.add(new UserPrincipal(userName, userNamespace));
      }

      if ((privs & Client.PRIV_PERM_WORLD) == Client.PRIV_PERM_WORLD) {
        LOGGER.log(Level.FINER, "Public Access Privileges for user {0}: {1}",
            new Object[] {userName, privs});
        publicAccessMembers.add(new UserPrincipal(userName, userNamespace));
      }
    }

    GroupPrincipal sysAdminGroupPrincipal =
        new GroupPrincipal(Client.SYSADMIN_GROUP,
            connector.getGoogleLocalNamespace());
    groups.put(sysAdminGroupPrincipal, sysAdminMembers);

    GroupPrincipal publicAccessGroupPrincipal =
        new GroupPrincipal(Client.PUBLIC_ACCESS_GROUP,
            connector.getGoogleLocalNamespace());
    groups.put(publicAccessGroupPrincipal, publicAccessMembers);
  }

  /**
   * No documents to serve for this adaptor.
   */
  @Override
  public void getDocContent(Request req, Response resp) throws IOException,
      InterruptedException {
    resp.respondNotFound();
  }

  /**
   * Pushes all groups and their member definitions.
   */
  @Override
  public void getDocIds(DocIdPusher docPusher)
      throws IOException, InterruptedException {
    NDC.push("GroupFeed " + connector.getGoogleConnectorName());
    try {
      docPusher.pushGroupDefinitions(getLivelinkGroups(), false);
    } catch (RepositoryException e) {
      throw new IOException("Error in feeding groups ", e);
    } finally {
      NDC.remove();
    }
  }
}
