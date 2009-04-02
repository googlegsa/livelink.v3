// Copyright (C) 2007-2008 Google Inc.
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

import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.enterprise.connector.spi.AuthenticationIdentity;
import com.google.enterprise.connector.spi.AuthenticationManager;
import com.google.enterprise.connector.spi.AuthenticationResponse;
import com.google.enterprise.connector.spi.Connector;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.RepositoryLoginException;
import com.google.enterprise.connector.otex.client.Client;
import com.google.enterprise.connector.otex.client.ClientFactory;
import com.google.enterprise.connector.otex.client.ClientValue;

/**
 * Implements an AuthenticationManager for the Livelink connector.
 */
class LivelinkAuthenticationManager
        implements AuthenticationManager, ConnectorAware  {

    /** The logger for this class. */
    private static final Logger LOGGER =
        Logger.getLogger(LivelinkAuthenticationManager.class.getName());

    /** Client factory for obtaining client instances. */
    private ClientFactory clientFactory;


    /**
     * Default constructor for bean instantiation.
     */
    LivelinkAuthenticationManager() {
        super();
    }

    /**
     * Provides the current connector instance to this
     * authentication manager for configuration purposes.
     *
     * @param connector the current LivelinkConnector
     */
    public void setConnector(Connector connector) {
        this.clientFactory =
            ((LivelinkConnector) connector).getAuthenticationClientFactory();
    }


    /**
     * Authenticates the given user for access to the back-end
     * Livelink.
     *
     * @param username the username to check
     * @param password the user's password
     * @returns true if the user can be authenticated
     * @throws RepositoryLoginException not currently thrown
     * @throws RepositoryException if an exception occurred during
     * authentication
     */
    public AuthenticationResponse authenticate(AuthenticationIdentity identity)
            throws RepositoryLoginException, RepositoryException {
        if (LOGGER.isLoggable(Level.FINE))
            LOGGER.fine("AUTHENTICATE: " + identity.getUsername());

        // FIXME: different message text? or assume this can't
        // happen because we control it in LivelinkConnector?
        if (clientFactory == null) {
            LOGGER.severe("Misconfigured connector; unable to authenticate");
            throw new RepositoryException("Missing Livelink client factory");
        }

        try {
            // XXX: Pass the identity to the ClientFactory?
            Client client = clientFactory.createClient(
                identity.getUsername(), identity.getPassword());

            // Verify connectivity by calling GetServerInfo, just like
            // LivelinkConnector.login does.
            // TODO: We will want to use GetCookieInfo when we need to
            // return the client data. Until then, GetServerInfo is
            // faster.
            ClientValue serverInfo = client.GetServerInfo();
            return new AuthenticationResponse(serverInfo.hasValue(), null);
        } catch (RepositoryException e) {
            LOGGER.warning("Authentication failed for " +
                identity.getUsername() + "; " + e.getMessage());
            throw e;
        }
    }
}
