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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.enterprise.connector.otex.client.Client;
import com.google.enterprise.connector.otex.client.ClientValue;
import com.google.enterprise.connector.spi.RepositoryException;

import java.util.Enumeration;
import java.util.logging.Level;
import java.util.logging.Logger;

class IdentityUtils {
  private static final Logger LOGGER =
      Logger.getLogger(IdentityUtils.class.getName());

  /** From the Users, Groups, and Domains API Overview page. */
  @VisibleForTesting
  static final int LOGIN_MASK = Client.PRIV_LOGIN
      | Client.PRIV_UAPI_SESSION | Client.PRIV_DAPI_SESSION
      | Client.PRIV_WAPI_SESSION;

  private final LivelinkConnector connector;
  private final Client client;
  private final IdentityResolver identityResolver;

  IdentityUtils(LivelinkConnector connector, Client client) {
    this.connector = connector;
    this.client = client;
    this.identityResolver = new IdentityResolver(
        connector.getDomainAndName(), connector.getWindowsDomain());
  }

  public ClientValue getUserOrGroupById(int userId)
      throws RepositoryException {
    ClientValue info = client.GetUserOrGroupByIDNoThrow(userId);
    if (info == null || !info.hasValue() || isDisabled(userId, info)) {
      return null;
    } else {
      return info;
    }
  }

  public boolean isDisabled(ClientValue info) throws RepositoryException {
    return isDisabled(info.toInteger("ID"), info);
  }

  private boolean isDisabled(int userId, ClientValue info)
      throws RepositoryException {
    boolean isDisabled = info.toInteger("Deleted") == 1
        || (info.toInteger("Type") == Client.USER
            && (info.toInteger("UserPrivileges") & LOGIN_MASK) != LOGIN_MASK);
    if (isDisabled) {
      LOGGER.log(Level.FINEST,
          "SKIPPING DISABLED USER OR GROUP ID: {0,number,#}, {1}",
          new Object[] { userId, info.toString("Name") });
    }
    return isDisabled;
  }

  public interface PrincipalFactory<T> {
    T createUser(String name, String namespace);
    T createGroup(String name, String namespace);
  }

  public <T> T getPrincipal(ClientValue userInfo, PrincipalFactory<T> factory)
      throws RepositoryException {
      int userId = userInfo.toInteger("ID");
      String name = userInfo.toString("Name");
      if (Strings.isNullOrEmpty(name)) {
        LOGGER.log(Level.FINE,
            "Ignoring ID {0}: empty name", userId);
        return null;
      }
      int type = userInfo.toInteger("Type");
      ClientValue userData = userInfo.toValue("UserData");

      String namespace;
      try {
        if (isExternal(userData)) {
          name = identityResolver.getPrincipalIdentity(name);
          namespace = connector.getGoogleGlobalNamespace();
        } else {
          namespace = connector.getGoogleLocalNamespace();
        }
      } catch (IllegalArgumentException e) {
        LOGGER.log(Level.FINER,
            "Unable to get principal for {0} from {1}: {2}",
            new Object[] {name, userData.getDiagnosticString(), e});
        return null;
      }

      T principal;
      switch (type) {
        case Client.USER:
          principal = factory.createUser(name, namespace);
          break;
        case Client.GROUP:
          principal = factory.createGroup(name, namespace);
          break;
        default:
          LOGGER.log(Level.FINE,
              "Ignoring ID {0}: unknown principal type: {1,number,#}",
              new Object[] { userId, type });
          principal = null;
      }
      LOGGER.log(Level.FINEST, "PRINCIPAL: "
          + "UserID {0,number,#}, UserData {1}: {2}",
          new Object[] { userId,
            userData == null ? null : userData.getDiagnosticString(),
            principal });
      return principal;
  }

  private boolean isExternal(ClientValue userData)
      throws RepositoryException {
    if (userData != null && userData.hasValue()) {
      Enumeration<String> fieldNames = userData.enumerateNames();
      while (fieldNames.hasMoreElements()) {
        String name = fieldNames.nextElement();
        if (name.equals("ExternalAuthentication")) {
          if (userData.toBoolean("ExternalAuthentication")) {
            return true;
          }
        } else if (name.equalsIgnoreCase("ldap")
            || name.equalsIgnoreCase("ntlm")) {
          return true;
        }
      }
    }
    return false;
  }
}
