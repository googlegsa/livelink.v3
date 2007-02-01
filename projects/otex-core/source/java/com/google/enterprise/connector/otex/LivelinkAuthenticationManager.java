// Copyright (C) 2006-2007 Google Inc.

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

    /** The logger. */
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
            return clientFactory.createClient(username, password).ping(LOGGER);
        } catch (RepositoryException e) {
            LOGGER.warning("Authentication failed for " + username + "; " +
                e.getMessage()); 
            throw e;
        }
    }
}
