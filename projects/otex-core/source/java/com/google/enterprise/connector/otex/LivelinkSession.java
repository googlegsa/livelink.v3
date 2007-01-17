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
import com.google.enterprise.connector.spi.ResultSet;
import com.google.enterprise.connector.spi.Session;
import com.google.enterprise.connector.spi.SimpleProperty;
import com.google.enterprise.connector.spi.SimplePropertyMap;
import com.google.enterprise.connector.spi.SimpleResultSet;
import com.google.enterprise.connector.spi.SpiConstants;
import com.google.enterprise.connector.otex.client.ClientFactory;

class LivelinkSession
        implements Session {

    private static final Logger LOGGER =
        Logger.getLogger(LivelinkSession.class.getName());

    private final LivelinkConnector connector;

    private final ClientFactory clientFactory;
    
    public LivelinkSession(LivelinkConnector connector,
            ClientFactory clientFactory) throws RepositoryException {
        this.connector = connector;
        this.clientFactory = clientFactory;
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
