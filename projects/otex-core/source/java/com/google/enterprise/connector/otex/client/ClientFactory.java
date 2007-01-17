// Copyright (C) 2006-2007 Google Inc.

package com.google.enterprise.connector.otex.client;

/**
 * A factory for the facade interface that encapsulates the Livelink API.
 *
 * @see Client
 */
public interface ClientFactory {
    /** Required property. */
    void setHostname(String value);

    /** Required property. */
    void setPort(int value);

    /** Required property. */
    void setUsername(String value);

    /** Required property. */
    void setPassword(String value);

    /** Optional property. */
    void setDatabase(String value);

    /** Optional property. */
    void setDomainName(String value);

    /** Required property. */
    void setEncoding(String value);

    /**
     * Gets a new client instance.
     *
     * @return a new client instance
     */
    Client createClient();

    /**
     * Gets a new client instance using the provided username,
     * which may be different than the username passed to {@link
     * #setUsername}.
     *
     * @param username a username
     * @param password a password
     * @return a new client instance
     */
    Client createClient(String username, String password);  
}
