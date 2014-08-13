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

import com.google.enterprise.connector.otex.client.ClientValue;
import com.google.enterprise.connector.spi.RepositoryException;

import java.util.Enumeration;

class IdentityUtils {

  private LivelinkConnector connector;

  IdentityUtils(LivelinkConnector connector) {
    this.connector = connector;
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

  public String getNamespace(ClientValue userData) throws RepositoryException {
    String namespace;
    if (isExternal(userData)) {
      namespace = connector.getGoogleGlobalNamespace();
    } else {
      namespace = connector.getGoogleLocalNamespace();
    }
    return namespace;
  }
}
