// Copyright 2007 Google Inc.
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

import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.otex.client.Client;
import com.google.enterprise.connector.otex.client.ClientValue;

class UserNameHandler {
  /** The logger for this class. */
  private static final Logger LOGGER =
      Logger.getLogger(UserNameHandler.class.getName());

  /** The client provides access to the server. */
  private final Client client;

  /** This is a small cache of UserID and GroupID name resolutions. */
  private final HashMap<Integer, ClientValue> userNameCache =
      new HashMap<Integer, ClientValue>(200);

  UserNameHandler(Client client) throws RepositoryException {
    this.client = client;
  }

  /**
   * Add a UserID or GroupID property value as the name of the user or
   * group, rather than the integral ID.
   *
   * @param propertyName  the property key to use when adding the value
   * to the map.
   * @param idValue ClientValue containing  the UserID or GroupID to
   * resolve to a name.
   */
  public void addUserByName(String propertyName, ClientValue idValue,
      LivelinkDocument props) throws RepositoryException {
    // If the UserID or GroupID is 0, then ignore it.
    // For reason why, see ObjectInfo Reserved and ReservedBy fields.
    int id = idValue.toInteger();
    if (id == 0)
      return;

    // Check the userName cache (if we recently looked up this user).
    ClientValue userName = userNameCache.get(new Integer(id));
    if (userName == null) {
      // User is not in the cache, get the name from the server.
      ClientValue userInfo = client.GetUserOrGroupByIDNoThrow(id);
      if (userInfo != null)
        userName = userInfo.toValue("Name");
      if (userName == null || !userName.isDefined()) {
        if (LOGGER.isLoggable(Level.WARNING)) {
          LOGGER.warning("No user or group name found for ID " + id);
        }
        return;
      }

      // If the cache was full, flush it. Not sophisticated MRU, but
      // good enough for our needs.
      if (userNameCache.size() > 100)
        userNameCache.clear();

      // Cache this userId to userName mapping for later reference.
      userNameCache.put(new Integer(id), userName);
    }

    // Finally, add the userName property to the map.
    props.addProperty(propertyName, userName);
  }
}
