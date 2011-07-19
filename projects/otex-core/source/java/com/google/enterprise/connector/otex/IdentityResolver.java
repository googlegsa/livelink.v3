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

import com.google.common.base.Strings;
import com.google.enterprise.connector.spi.AuthenticationIdentity;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Maps {@AuthenticationIdentity} values to the usernames used for
 * authentication and authorization.
 *
 * @since 2.8
 */
class IdentityResolver {
  /** The logger for this class. */
  private static final Logger LOGGER =
      Logger.getLogger(IdentityResolver.class.getName());

  /** The {@code domainAndName} advanced configuration property. */
  private final DomainAndName domainAndName;

  /** The {@code windowsDomain} advanced configuration property. */
  private final String windowsDomain;

  /** Creates an instance with no default domain. */
  public IdentityResolver(DomainAndName domainAndName) {
    this(domainAndName, null);
  }

  /** Creates an instance with the given default domain. */
  public IdentityResolver(DomainAndName domainAndName, String windowsDomain) {
    this.domainAndName = domainAndName;
    this.windowsDomain = windowsDomain;
  }

  public String getAuthenticationIdentity(AuthenticationIdentity identity) {
    String username;
    boolean haveDomain;
    String domain = identity.getDomain();
    if (domainAndName != DomainAndName.FALSE
        && domainAndName != DomainAndName.LEGACY
        && !Strings.isNullOrEmpty(domain)) {
      username = domain + "\\" + identity.getUsername();
      haveDomain = true;
    } else {
      username = identity.getUsername();
      int index = username.indexOf("@");
      if (index != -1) {
        if (domainAndName != DomainAndName.FALSE) {
          haveDomain = true;
        } else {
          username = username.substring(0, index);
          haveDomain = false;
        }
      } else {
        haveDomain = false;
      }
    }

    if (!haveDomain && !Strings.isNullOrEmpty(windowsDomain)) {
      username = windowsDomain + '\\' + username;
    }

    if (LOGGER.isLoggable(Level.FINE)
        && !username.equals(identity.getUsername())) {
      LOGGER.fine("AUTHENTICATE AS: " + username);
    }
    return username;
  }

  public String getAuthorizationIdentity(AuthenticationIdentity identity) {
    String username;
    String domain = identity.getDomain();
    if (domainAndName == DomainAndName.TRUE
        && !Strings.isNullOrEmpty(domain)) {
      username = domain + "\\" + identity.getUsername();
    } else {
      username = identity.getUsername();
    }

    // Remove the DNS-style Windows domain, if there is one.
    int index = username.indexOf("@");
    if (index != -1) {
      username = username.substring(0, index);
    }

    if (LOGGER.isLoggable(Level.FINE)
        && !username.equals(identity.getUsername())) {
      LOGGER.fine("AUTHORIZE FOR: " + username);
    }
    return username;
  }
}
