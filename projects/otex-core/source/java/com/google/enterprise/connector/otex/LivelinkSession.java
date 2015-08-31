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

import com.google.enterprise.connector.otex.client.ClientFactory;
import com.google.enterprise.connector.spi.AuthenticationManager;
import com.google.enterprise.connector.spi.AuthorizationManager;
import com.google.enterprise.connector.spi.Lister;
import com.google.enterprise.connector.spi.ListerAware;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.Session;
import com.google.enterprise.connector.spi.TraversalManager;

class LivelinkSession implements Session, ListerAware {
    /** The connector instance. */
    private final LivelinkConnector connector;

    /** The client factory for traversal and authorization clients. */
    private final ClientFactory clientFactory;

    /** The list of authentication managers. */
    private final AuthenticationManager authenticationManager;

    /** The authorization manager. */
    private final AuthorizationManager authorizationManager;

    /** The Lister. */
    private Lister lister;

    /**
     *
     * @param connector a connector instance
     * @param clientFactory a client factory
     * @param authenticationManager the configured AuthenticationManager
     * @param authorizationManager the configured AuthorizationManager
     * @throws RepositoryException not thrown
     */
    public LivelinkSession(LivelinkConnector connector,
            ClientFactory clientFactory,
            AuthenticationManager authenticationManager,
            AuthorizationManager authorizationManager)
            throws RepositoryException {
        this.connector = connector;
        this.clientFactory = clientFactory;
        this.authenticationManager = authenticationManager;
        this.authorizationManager = authorizationManager;
        if (connector.getPushAcls()) {
          GroupAdaptor groupAdaptor =
              new GroupAdaptor(connector, clientFactory);
          String[] groupAdaptorArgs = {
              "-Dgsa.hostname=" + connector.getGoogleFeedHost(),
              "-Dserver.hostname=localhost",
              "-Dserver.port=0",
              "-Dserver.dashboardPort=0",
              "-Dfeed.name=" + connector.getGoogleConnectorName(),
              "-Dadaptor.fullListingSchedule="
              + connector.getGroupFeedSchedule(),
            };
          this.lister = new AdaptorLister(groupAdaptor, groupAdaptorArgs);
        } else {
          this.lister = null;
        }
    }

  /**
   * Gets a TraversalManager to implement query-based traversal
   *
   * @return a TraversalManager
   * @throws RepositoryException
   */
  @Override
  public TraversalManager getTraversalManager() throws RepositoryException {
    return new TraversalManagerWrapper(connector, clientFactory);
  }

    /**
     * Gets an AuthenticationManager to implement per-user authentication.
     *
     * @return an AuthenticationManager
     * @throws RepositoryException
     */
    @Override
    public AuthenticationManager getAuthenticationManager() {
        // XXX: Should we ever return null, indicating that
        // authentication is handled by the GSA?
        return authenticationManager;
    }

    /**
     * Gets an AuthorizationManager to implement per-user authorization.
     *
     * @return an AuthorizationManager
     * @throws RepositoryException
     */
    @Override
    public AuthorizationManager getAuthorizationManager()
            throws RepositoryException {
        return authorizationManager;
    }

    @Override
    public Lister getLister() throws RepositoryException {
      return lister;
    }

    /**
     * Returns the ClientFactory for test use.
     */
    ClientFactory getFactory() {
        return clientFactory;
    }
}
