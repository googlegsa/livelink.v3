// Copyright (C) 2007 Google Inc.
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
import java.util.logging.LogRecord;

import com.google.enterprise.connector.spi.AuthenticationManager;
import com.google.enterprise.connector.spi.LoginException;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.otex.client.ClientFactory;

/**
 * Implements an AuthenticationManager for the Livelink connector.
 */
class LivelinkAuthenticationManager implements AuthenticationManager {

    /** The logger for this class. */
    private static final Logger LOGGER =
        Logger.getLogger(LivelinkAuthenticationManager.class.getName());

    /** Client factory for obtaining client instances. */
    private final ClientFactory clientFactory;


    /**
     * Constructor - caches client factory.
     */
    LivelinkAuthenticationManager(ClientFactory clientFactory) {
        super();
        this.clientFactory = clientFactory;
    }


    /**
     * Authenticates the given user for access to the back-end
     * Livelink.
     *
     * @param username the username to check
     * @param password the user's password
     * @returns true if the user can be authenticated
     * @throws LoginException not currently thrown
     * @throws RepositoryException if an exception occured during
     * authentication
     */
    public boolean authenticate(String username, String password)
            throws LoginException, RepositoryException {
        if (LOGGER.isLoggable(Level.FINE))
            LOGGER.fine("AUTHENTICATE: " + username);
        try {
            return clientFactory.createClient(username, password).ping();
        } catch (RepositoryException e) {
            LOGGER.warning("Authentication failed for " + username + "; " +
                e.getMessage()); 
            throw e;
        }
    }
}
