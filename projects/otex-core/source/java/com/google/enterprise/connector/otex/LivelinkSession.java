// Copyright (C) 2006-2007 Google Inc.

package com.google.enterprise.connector.otex;

import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.enterprise.connector.spi.AuthenticationManager;
import com.google.enterprise.connector.spi.AuthorizationManager;
import com.google.enterprise.connector.spi.QueryTraversalManager;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.Session;
import com.google.enterprise.connector.otex.client.ClientFactory;

class LivelinkSession
        implements Session {

    /** The logger. */
    private static final Logger LOGGER =
        Logger.getLogger(LivelinkSession.class.getName());

    /** The connector instance. */
    private final LivelinkConnector connector;

    /** The client factory for traversal and authorization clients. */
    private final ClientFactory clientFactory;

    /** The authentication factory for authentication clients; may be null. */ 
    private final ClientFactory authenticationClientFactory;

    /**
     *
     * @param connector a connector instance
     * @param clientFactory a client factory
     * @param authenticationClientFactory a client factory
     * configured for use in authentication
     * @throws RepositoryException not thrown
     */
    public LivelinkSession(LivelinkConnector connector,
            ClientFactory clientFactory, 
            ClientFactory authenticationClientFactory) throws RepositoryException {
        this.connector = connector;
        this.clientFactory = clientFactory;
        this.authenticationClientFactory = authenticationClientFactory;
    }

    /**
     * Gets a QueryTraversalManager to implement query-based traversal
     * 
     * @return a QueryTraversalManager
     * @throws RepositoryException
     */
    public QueryTraversalManager getQueryTraversalManager()
        throws RepositoryException
    {
        return new LivelinkQueryTraversalManager(connector, clientFactory);
    }
  
    /**
     * Gets an AuthenticationManager to implement per-user authentication.
     * 
     * @return an AuthenticationManager
     * @throws RepositoryException
     */
    public AuthenticationManager getAuthenticationManager() {
        // XXX: Should we ever return null, indicating that
        // authentication is handled by the GSA?
        if (authenticationClientFactory != null) {
            return new LivelinkAuthenticationManager(
                authenticationClientFactory);
        }
        return new LivelinkAuthenticationManager(clientFactory); 
    }

    /**
     * Gets an AuthorizationManager to implement per-user authorization.
     * 
     * @return an AuthorizationManager
     * @throws RepositoryException
     */
    public AuthorizationManager getAuthorizationManager() {
        return new LivelinkAuthorizationManager(clientFactory);
    }
}
