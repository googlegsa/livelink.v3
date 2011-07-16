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

import java.util.logging.Level;
import java.util.logging.Logger;

class IdentityResolver {
  /** The logger for this class. */
  private static final Logger LOGGER =
      Logger.getLogger(IdentityResolver.class.getName());

  /** The {@code windowsDomain} advanced configuration property. */
  private final String windowsDomain;

  /** Creates an instance with no default domain. */
  public IdentityResolver() {
    this(null);
  }

  /** Creates an instance with the given default domain. */
  public IdentityResolver(String windowsDomain) {
    this.windowsDomain = windowsDomain;
  }

  public String getAuthenticationIdentity(AuthenticationIdentity identity) {
    // Check for a domain value.
    String originalUsername = identity.getUsername();
    String username;
    int index = originalUsername.indexOf("@");
    if (index != -1) {
      String user = originalUsername.substring(0, index);
      String domain = originalUsername.substring(index + 1);
      username = domain + '\\' + user;
    } else if (windowsDomain != null && windowsDomain.length() > 0) {
      username = windowsDomain + '\\' + originalUsername;
    } else {
      username = originalUsername;
    }

    if (LOGGER.isLoggable(Level.FINER) && username.indexOf('\\') != -1) {
      LOGGER.finer("AUTHENTICATE AS: " + username);
    }
    return username;
  }

  public String getAuthorizationIdentity(AuthenticationIdentity identity) {
    String username = identity.getUsername();

    // Remove the DNS-style Windows domain, if there is one.
    int index = username.indexOf("@");
    if (index != -1) {
      username = username.substring(0, index);
    }

    if (LOGGER.isLoggable(Level.FINE) && index != -1) {
      LOGGER.fine("AUTHORIZE FOR: " + username);
    }
    return username;
  }
}
