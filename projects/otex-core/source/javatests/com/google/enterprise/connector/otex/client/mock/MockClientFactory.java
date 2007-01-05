// Copyright (C) 2007 Google Inc.

package com.google.enterprise.connector.otex.client.mock;

import com.google.enterprise.connector.otex.client.Client;
import com.google.enterprise.connector.otex.client.ClientFactory;

/**
 * A mock factory for the facade interface that encapsulates the Livelink API.
 */
public class MockClientFactory implements ClientFactory {
    /** {@inheritDoc} */
    public void setHostname(String value) {
    }

    /** {@inheritDoc} */
    public void setPort(int value) {
    }

    /** {@inheritDoc} */
    public void setUsername(String value) {
    }

    /** {@inheritDoc} */
    public void setPassword(String value) {
    }

    /** {@inheritDoc} */
    public void setDatabase(String value) {
    }

    /** {@inheritDoc} */
    public void setDomainName(String value) {
    }

    /**
     * Gets a new client instance.
     *
     * @return a new mutex instance
     */
    public Client createClient() {
        return new MockClient();
    }
}
