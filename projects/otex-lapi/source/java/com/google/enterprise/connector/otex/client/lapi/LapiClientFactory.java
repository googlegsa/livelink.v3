// Copyright (C) 2006-2007 Google Inc.

package com.google.enterprise.connector.otex.client.lapi;

import java.util.logging.Logger;

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
    private String displayUrl = null;
    private LLValue config = null;

    /** Required property. */
    public void setHostname(String value) {
        this.hostname = value;
    }

    /** Required property. */
    public void setPort(int value) {
        this.port = value;
    }

    /** Optional property. */
    public void setDatabase(String value) {
        this.database = value;
    }

    /** Required property. */
    public void setUsername(String value) {
        this.username = value;
    }

    /** Required property. */
    public void setPassword(String value) {
        this.password = value;
    }

    /** Optional property. */
    public void setDomainName(String value) {
        if (config == null)
            config = (new LLValue()).setAssocNotSet();

        // XXX: Should we use add or setValue here?
        config.add("DomainName", value);
    }

    /**
     * Gets an initialized client.
     *
     * @return a client
     */
    public Client createClient() {
        LLSession session = new LLSession(hostname, port, database, username,
            password, config);
        return new LapiClient(session);
    }
}
