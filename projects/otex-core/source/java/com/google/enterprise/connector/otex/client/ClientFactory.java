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

    /** Optional property. */
    void setLivelinkCgi(String value); 

    /** Optional property. */
    void setUseHttps(boolean value); 

    /** Optional property. */
    void setHttpUsername(String value);

    /** Optional property. */
    void setHttpPassword(String value);

    /** Optional property. */
    void setVerifyServer(boolean value);

    /** Optional property. */
    void setCaRootCerts(java.util.List value);

    /** Optional property. */
    void setEnableNtlm(boolean value);

    /** Optional property. */
    void setUseUsernamePasswordWithWebServer(boolean value);

    /**
     * Gets a new client instance.
     *
     * @return a new client instance
     */
    Client createClient();

    /**
     * Gets a new client instance using the provided username,
     * which may be different than the username passed to {@link
     * #setUsername}. This method should avoid using any
     * configured usernames from either {@link #setUsername} or
     * {@link #setHttpUsername} and only use the values provided
     * in the parameters.
     *
     * @param username a username
     * @param password a password
     * @return a new client instance
     */
    Client createClient(String username, String password);  
}
