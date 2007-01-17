// Copyright (C) 2006-2007 Google Inc.

package com.google.enterprise.connector.otex.client.lapi;

import com.google.enterprise.connector.otex.client.Client;
import com.google.enterprise.connector.otex.client.ClientFactory;

import com.opentext.api.LLSession;
import com.opentext.api.LLValue;

/**
 * A direct LAPI client factory implementation.
 */
public final class LapiClientFactory implements ClientFactory
{
    private String hostname = null;
    private int port = 0;
    private String database = null;
    private String username = null;
    private String password = null;
    private LLValue config = null;

    /** {@inheritDoc} */
    public void setHostname(String value) {
        hostname = value;
    }

    /** {@inheritDoc} */
    public void setPort(int value) {
        port = value;
    }

    /** Optional property. */
    public void setDatabase(String value) {
        database = value;
    }

    /** {@inheritDoc} */
    public void setUsername(String value) {
        username = value;
    }

    /** {@inheritDoc} */
    public void setPassword(String value) {
        password = value;
    }

    /** {@inheritDoc} */
    public void setDomainName(String value) {
        setConfig("DomainName", value);
    }

    /** {@inheritDoc} */
    public void setEncoding(String value) {
        setConfig("Encoding", value);
    }

    /**
     * Sets a feature in the config assoc.
     *
     * @param name the feature name
     * @param value the feature value
     */
    /*
     * This method is synchronized to emphasize safety over speed. It
     * is extremely unlikely that this instance will be called from
     * multiple threads, but this is also a very fast, rarely executed
     * operation, so there's no need to cut corners.
     */
    private synchronized void setConfig(String name, String value) {
        if (config == null)
            config = (new LLValue()).setAssocNotSet();

        // XXX: Should we use add or setValue here?
        config.add(name, value);
    }
    
    /** {@inheritDoc} */
    public Client createClient() {
        // Construct a new LLSession and pass that to the client, just
        // to avoid having to pass all of the constructor arguments to
        // LapiClient.
        LLSession session = new LLSession(hostname, port, database, username,
            password, config);
        return new LapiClient(session);
    }

    /** {@inheritDoc} */
    public Client createClient(String providedUsername, 
            String providedPassword) {
        // Construct a new LLSession using the provided username
        // and password instead of the configured ones. This is
        // intended for use by the AuthenticationManager.

        // TODO: support other configurations for directory services, etc. 
        LLSession session = new LLSession(hostname, port, database, 
            providedUsername, providedPassword, config);
        return new LapiClient(session);
    }
}
