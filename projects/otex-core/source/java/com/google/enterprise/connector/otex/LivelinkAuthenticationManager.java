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

    /* The default Windows domain name for authentication. */
    private String windowsDomain;

    /**
     * Default constructor for bean instantiation.
     */
    LivelinkAuthenticationManager() {
        super();
    }

    /** Direct instantiation for the tests. */
    LivelinkAuthenticationManager(ClientFactory clientFactory,
            String windowsDomain) {
        this.clientFactory = clientFactory;
        this.windowsDomain = windowsDomain;
    }
  
    /**
     * Provides the current connector instance to this
     * authentication manager for configuration purposes.
     *
     * @param connector the current LivelinkConnector
     */
    /*
     * This method and the others in this class are synchronized because
     * initialization and use happen in different threads, and we do not
     * control the threads.
     */
    public synchronized void setConnector(Connector connector) {
        LivelinkConnector ll = (LivelinkConnector) connector;
        this.clientFactory = ll.getAuthenticationClientFactory();
        this.windowsDomain = ll.getWindowsDomain();
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
    public synchronized AuthenticationResponse authenticate(
            AuthenticationIdentity identity)
            throws RepositoryLoginException, RepositoryException {
        if (LOGGER.isLoggable(Level.FINE))
            LOGGER.fine("AUTHENTICATE: " + identity.getUsername());

        // FIXME: different message text? or assume this can't
        // happen because we control it in LivelinkConnector?
        if (clientFactory == null) {
            LOGGER.severe("Misconfigured connector; unable to authenticate");
            throw new RepositoryException("Missing Livelink client factory");
        }

        // Check for a domain value.
        String originalUsername = identity.getUsername();
        String username;
        int index = originalUsername.indexOf("@");
        if (index != -1) {
            String user = originalUsername.substring(0, index);
            String domain = originalUsername.substring(index + 1);
            username = domain + '\\' + user;
        } else if (windowsDomain != null && windowsDomain.length() > 0)
            username = windowsDomain + '\\' + originalUsername;
        else {
            username = originalUsername;
        }
        if (LOGGER.isLoggable(Level.FINER)) {
            if (username.indexOf('\\') != -1) {
                LOGGER.finer("AUTHENTICATE AS: " + username);
            }
        }

        try {
            Client client = clientFactory.createClient(
                username, identity.getPassword());

            // Verify connectivity by calling GetServerInfo, just like
            // LivelinkConnector.login does.
            // TODO: We will want to use GetCookieInfo when we need to
            // return the client data. Until then, GetServerInfo is
            // faster.
            ClientValue serverInfo = client.GetServerInfo();
            if (LOGGER.isLoggable(Level.FINE))
              LOGGER.fine("AUTHENTICATED: " + username + ": " +
                serverInfo.hasValue());
            return new AuthenticationResponse(serverInfo.hasValue(), null);
        } catch (RepositoryException e) {
            LOGGER.warning("Authentication failed for " +
                username + "; " + e.getMessage());
            throw e;
        }
    }
}
