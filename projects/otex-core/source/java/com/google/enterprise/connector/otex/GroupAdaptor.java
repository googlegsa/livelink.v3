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
import com.google.enterprise.connector.otex.client.ClientFactory;
import com.google.enterprise.connector.otex.client.ClientValue;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.TraversalSchedule;
import com.google.enterprise.connector.spi.TraversalScheduleAware;

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
class GroupAdaptor extends AbstractAdaptor implements TraversalScheduleAware{
  private static final Logger LOGGER =
      Logger.getLogger(GroupAdaptor.class.getName());

  /** The connector contains configuration information. */
  private final LivelinkConnector connector;

  /**
   * The client factory used to get a new client for each getDocIds
   * call. Don't bother with two strategies here, as getDocIds is not
   * called as often as resumeTraversal.
   *
   * @see TraversalManagerWrapper
   */
  private final ClientFactory clientFactory;

  /** Whether the traversal, and therefore group feeding, is disabled. */
  private volatile boolean isDisabled = false;

  GroupAdaptor(LivelinkConnector connector, ClientFactory clientFactory) {
    this.connector = connector;
    this.clientFactory = clientFactory;
  }

  /**
   * This implementation checks whether traversal is disabled, but
   * otherwise group feeding follows its own schedule.
   */
  @Override
  public void setTraversalSchedule(TraversalSchedule schedule) {
    LOGGER.log(Level.CONFIG, "{0} GROUP FEEDING",
        (schedule.isDisabled()) ? "DISABLE" : "ENABLE");
    this.isDisabled = schedule.isDisabled();
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
      if (isDisabled) {
        LOGGER.fine("GROUP FEEDING IS DISABLED");
        return;
      }
      docPusher.pushGroupDefinitions(
          new Traverser(connector, clientFactory).getGroups(), false);
    } catch (RepositoryException e) {
      throw new IOException("Error in feeding groups ", e);
    } finally {
      NDC.remove();
    }
  }

  private static class Traverser {
    private final LivelinkConnector connector;
    private final Client client;
    private final IdentityUtils identityUtils;
    private final GroupsPrincipalFactory principalFactory;

    public Traverser(LivelinkConnector connector, ClientFactory clientFactory) {
      this.connector = connector;
      this.client = clientFactory.createClient();
      this.identityUtils = new IdentityUtils(connector, client);
      this.principalFactory = new GroupsPrincipalFactory();
    }

    public Map<GroupPrincipal, List<Principal>> getGroups()
        throws RepositoryException {
      Map<GroupPrincipal, List<Principal>> groups =
          new LinkedHashMap<GroupPrincipal, List<Principal>>();
      getStandardGroups(groups);
      getSystemAdminAndPublicGroups(groups);
      return groups;
    }

    private static class GroupsPrincipalFactory
        implements IdentityUtils.PrincipalFactory<Principal> {
      @Override
      public Principal createUser(String name, String namespace) {
        return new UserPrincipal(name, namespace);
      }

      @Override
      public Principal createGroup(String name, String namespace) {
        return new GroupPrincipal(name, namespace);
      }
    }

    private void getStandardGroups(Map<GroupPrincipal, List<Principal>> groups)
        throws RepositoryException {
      ClientValue groupsValue = client.ListGroups();
      for (int i = 0; i < groupsValue.size(); i++) {
        ClientValue groupInfo = groupsValue.toValue(i);
        String groupName = groupInfo.toString("Name");
        LOGGER.log(Level.FINEST, "Fetching group members for group name: {0}",
            groupName);
        GroupPrincipal groupPrincipal = (GroupPrincipal)
            identityUtils.getPrincipal(groupInfo, principalFactory);
        if (groupPrincipal != null) {
          ClientValue groupMembers = client.ListMembers(groupName);
          List<Principal> memberPrincipals =
              getMemberPrincipalList(groupMembers);
          groups.put(groupPrincipal, memberPrincipals);
          LOGGER.log(Level.FINER,
              "Group principal: {0}; Member principals: {1}",
              new Object[] {groupPrincipal, memberPrincipals});
        }
      }
    }

    private List<Principal> getMemberPrincipalList(ClientValue groupMembers)
        throws RepositoryException {
      List<Principal> memberPrincipals = new ArrayList<Principal>();

      for (int i = 0; i < groupMembers.size(); i++) {
        ClientValue memberInfo = groupMembers.toValue(i);
        String memberName = memberInfo.toString("Name");
        int memberType = memberInfo.toInteger("Type");
        LOGGER.log(Level.FINEST, "Member name: {0}; Member Type: {1}",
            new Object[] {memberName, memberType});
        if (identityUtils.isDisabled(memberInfo)) {
          continue;
        }
        Principal memberPrincipal =
            identityUtils.getPrincipal(memberInfo, principalFactory);
        if (memberPrincipal != null) {
          memberPrincipals.add(memberPrincipal);
        }
      }

      return memberPrincipals;
    }

    private void getSystemAdminAndPublicGroups(
        Map<GroupPrincipal, List<Principal>> groups)
        throws RepositoryException {
      List<Principal> sysAdminMembers = new ArrayList<Principal>();
      List<Principal> publicAccessMembers = new ArrayList<Principal>();

      ClientValue usersValue = client.ListUsers();
      for (int i = 0; i < usersValue.size(); i++) {
        ClientValue userInfo = usersValue.toValue(i);
        String userName = userInfo.toString("Name");
        LOGGER.log(Level.FINEST, "Fetching privileges for {0}", userName);
        if (identityUtils.isDisabled(userInfo)) {
          continue;
        }
        Principal userPrincipal =
            identityUtils.getPrincipal(userInfo, principalFactory);
        if (userPrincipal != null) {
          int privs = userInfo.toInteger("UserPrivileges");

          if ((privs & Client.PRIV_PERM_BYPASS) == Client.PRIV_PERM_BYPASS) {
            LOGGER.log(Level.FINEST, "Admin Privileges for user {0}: {1}",
                new Object[] {userName, privs});
            sysAdminMembers.add(userPrincipal);
          }

          if ((privs & Client.PRIV_PERM_WORLD) == Client.PRIV_PERM_WORLD) {
            LOGGER.log(Level.FINEST,
                "Public Access Privileges for user {0}: {1}",
                new Object[] {userName, privs});
            publicAccessMembers.add(userPrincipal);
          }
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
  }
}
