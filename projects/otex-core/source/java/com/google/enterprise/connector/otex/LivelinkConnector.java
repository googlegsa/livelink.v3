// Copyright (C) 2006-2007 Google Inc.

package com.google.enterprise.connector.otex;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.enterprise.connector.spi.Connector;
import com.google.enterprise.connector.spi.LoginException;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.Session;
import com.google.enterprise.connector.otex.client.ClientFactory;

public class LivelinkConnector implements Connector {

    private static final Logger LOGGER =
        Logger.getLogger(LivelinkConnector.class.getName());

    private final ClientFactory clientFactory;

    /** The display URL prefix for the search results. */
    private String displayUrl;

    /**
     * Constructs a connector instance for a specific Livelink
     * repository, using the default client factory class.
     */
    LivelinkConnector() {
        this("com.google.enterprise.connector.otex.client.lapi." +
            "LapiClientFactory");
    }

    /**
     * Constructs a connector instance for a specific Livelink
     * repository, using the specified client factory class.
     */
    LivelinkConnector(String clientFactoryClass) {
        LOGGER.config("NEW INSTANCE");
        try {
            clientFactory = (ClientFactory)
                Class.forName(clientFactoryClass).newInstance();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, e.getMessage(), e);
            throw new RuntimeException(e); // XXX: More specific exception?
        }
    }

    /**
     * @param hostname the host name to set
     */
    public void setHostname(String hostname) {
        LOGGER.config("HOSTNAME: " + hostname);
        clientFactory.setHostname(hostname);
    }

    /**
     * @param port the port number to set
     */
    public void setPort(int port) {
        LOGGER.config("PORT: " + port);
        clientFactory.setPort(port);
    }

    /**
     * @param database the database name to set
     */
    public void setDatabase(String database) {
        LOGGER.config("DATABASE: " + database);
        clientFactory.setDatabase(database);
    }

    /**
     * @param username the username to set
     */
    public void setUsername(String username) {
        LOGGER.config("USERNAME: " + username);
        clientFactory.setUsername(username);
    }

    /**
     * @param password the password to set
     */
    public void setPassword(String password) {
        LOGGER.config("PASSWORD: " + password);
        clientFactory.setPassword(password);
    }

    /**
     * @param domainName the domain name to set
     */
    public void setDomainName(String domainName) {
        LOGGER.config("DOMAIN NAME: " + domainName);
        clientFactory.setDomainName(domainName);
    }

    /**
     * Gets the display URL prefix for the search results, e.g.,
     * "http://myhostname/Livelink/livelink.exe".
     * 
     * @param displayUrl the display URL prefix
     */
    public void setDisplayUrl(String displayUrl) {
        LOGGER.config("DISPLAY URL: " + displayUrl);
        this.displayUrl = displayUrl;
    }
    
    /**
     * Gets the display URL prefix for the search results.
     *
     * @return the display URL prefix
     */
    public String getDisplayUrl() {
        return displayUrl;
    }
    
    // TODO: Extra config options (DS, tunnelling, domains),
    // character encodings, volume ID.
    
    public Session login() throws LoginException, RepositoryException {
        LOGGER.fine("LOGIN");

        // TODO: Make sure that the client can actually connect to Livelink.
        // This test can just use a local client, e.g.,
        // Client client = clientFactory.createClient();
        // client.GetServerInfo(logger, ...);
        
        return new LivelinkSession(this, clientFactory);
    }
}
