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
        implements Session, AuthenticationManager, AuthorizationManager {

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
  
    public AuthenticationManager getAuthenticationManager() {
        return this;
    }

    public AuthorizationManager getAuthorizationManager() {
        return this;
    }

    public boolean authenticate(String username, String password) {
        if (LOGGER.isLoggable(Level.FINE))
            LOGGER.fine("AUTHENTICATE: " + username);
        return true;
    }

    public ResultSet authorizeDocids(List docids, String username)
        throws RepositoryException
    {
        if (LOGGER.isLoggable(Level.FINE))
            LOGGER.fine("AUTHORIZE DOCIDS");
        SimpleResultSet rs = new SimpleResultSet();
        for (Iterator i = docids.iterator(); i.hasNext(); ) {
            SimplePropertyMap pm = new SimplePropertyMap();
            pm.putProperty(
                new SimpleProperty(SpiConstants.PROPNAME_DOCID,
                    (String) i.next()));
            pm.putProperty(
                new SimpleProperty(SpiConstants.PROPNAME_AUTH_VIEWPERMIT,
                    true));
            rs.add(pm);
        }
        return rs;
    }

    public ResultSet authorizeTokens(List docids, String username) {
        // TODO: Throw an exception, because if we don't return a
        // SECURITYTOKEN property this method should never be called.
        if (LOGGER.isLoggable(Level.FINE))
            LOGGER.fine("AUTHORIZE TOKENS");
        return new SimpleResultSet();
    }
}
