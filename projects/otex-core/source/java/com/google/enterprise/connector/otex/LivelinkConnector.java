// Copyright (C) 2006-2007 Google Inc.

package com.google.enterprise.connector.otex;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.enterprise.connector.spi.Connector;
import com.google.enterprise.connector.spi.LoginException;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.Session;
import com.google.enterprise.connector.otex.client.Client;
import com.google.enterprise.connector.otex.client.ClientFactory;

public class LivelinkConnector implements Connector {

    private static final Logger LOGGER =
        Logger.getLogger(LivelinkConnector.class.getName());

    /**
     * The client factory used to configure and instantiate the
     * client facade.
     */
    private final ClientFactory clientFactory;

    /** The display URL prefix for the search results. */
    private String displayUrl;

    /** The <code>ContentHandler</code> implementation class. */
    private String contentHandler =
        "com.google.enterprise.connector.otex.FileContentHandler";
    
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
    /* XXX: Should we throw the relevant checked exceptions instead? */
    LivelinkConnector(String clientFactoryClass) {
        if (LOGGER.isLoggable(Level.CONFIG))
            LOGGER.config("NEW INSTANCE: " + clientFactoryClass);
        try {
            clientFactory = (ClientFactory)
                Class.forName(clientFactoryClass).newInstance();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, e.getMessage(), e);
            throw new RuntimeException(e); // XXX: More specific exception?
        }
    }

    /**
     * Sets the hostname of the Livelink server.
     * 
     * @param hostname the host name to set
     */
    public void setHostname(String hostname) {
        if (LOGGER.isLoggable(Level.CONFIG))
            LOGGER.config("HOSTNAME: " + hostname);
        clientFactory.setHostname(hostname);
    }

    /**
     * Sets the hostname of the Livelink server. This should be the
     * Livelink server port, unless HTTP tunnelling is used, in which
     * case it should be the HTTP port.
     * 
     * @param port the port number to set
     */
    public void setPort(int port) {
        if (LOGGER.isLoggable(Level.CONFIG))
            LOGGER.config("PORT: " + port);
        clientFactory.setPort(port);
    }

    /**
     * Sets the database connection to use. This property is optional.
     * 
     * @param database the database name to set
     */
    public void setDatabase(String database) {
        if (LOGGER.isLoggable(Level.CONFIG))
            LOGGER.config("DATABASE: " + database);
        clientFactory.setDatabase(database);
    }

    /**
     * Sets the Livelink username.
     * 
     * @param username the username to set
     */
    public void setUsername(String username) {
        if (LOGGER.isLoggable(Level.CONFIG))
            LOGGER.config("USERNAME: " + username);
        clientFactory.setUsername(username);
    }

    /**
     * Sets the Livelink password.
     * 
     * @param password the password to set
     */
    public void setPassword(String password) {
        if (LOGGER.isLoggable(Level.CONFIG))
            LOGGER.config("PASSWORD: " + password);
        clientFactory.setPassword(password);
    }

    /**
     * Sets the Livelink domain name. This property is optional.
     * 
     * @param domainName the domain name to set
     */
    public void setDomainName(String domainName) {
        if (LOGGER.isLoggable(Level.CONFIG))
            LOGGER.config("DOMAIN NAME: " + domainName);
        clientFactory.setDomainName(domainName);
    }

    /**
     * Sets the display URL prefix for the search results, e.g.,
     * "http://myhostname/Livelink/livelink.exe".
     * 
     * @param displayUrl the display URL prefix
     */
    public void setDisplayUrl(String displayUrl) {
        if (LOGGER.isLoggable(Level.CONFIG))
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
    
    // TODO: Extra config options (e.g., DS, tunnelling).

    /**
     * Sets the concrete implementation for the
     * <code>ContentHandler</code> interface.
     *
     * @param contentHandler the fully-qualified name of the
     * <code>ContentHandler</code> implementation to use
     */
    public void setContentHandler(String contentHandler) {
        if (LOGGER.isLoggable(Level.CONFIG))
            LOGGER.config("CONTENT HANDLER: " + contentHandler);
        this.contentHandler = contentHandler;
    }
    
    /**
     * Gets the <code>ContentHandler</code> implementation class.
     *
     * @return the fully-qualified name of the <code>ContentHandler</code>
     * implementation to use
     */
    public String getContentHandler() {
        return contentHandler;
    }
    
    /** {@inheritDoc} */
    public Session login() throws LoginException, RepositoryException {
        if (LOGGER.isLoggable(Level.FINE))
            LOGGER.fine("LOGIN");

        // Two birds with one stone. Getting the character encoding
        // from the server verifies connectivity, and we use the
        // result to set the character encoding for future clients.
        Client client = clientFactory.createClient();
        String encoding = client.getEncoding(LOGGER);
        if (encoding != null) {
            if (LOGGER.isLoggable(Level.CONFIG))
                LOGGER.config("ENCODING: " + encoding);
            clientFactory.setEncoding(encoding);
        }
        
        return new LivelinkSession(this, clientFactory);
    }
}
