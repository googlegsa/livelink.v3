// Copyright 2007 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.enterprise.connector.otex;

import com.google.enterprise.connector.spi.AuthenticationManager;
import com.google.enterprise.connector.spi.AuthorizationManager;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.Session;
import com.google.enterprise.connector.spi.TraversalManager;
import com.google.enterprise.connector.otex.client.ClientFactory;

class LivelinkSession implements Session {
    /** The connector instance. */
    private final LivelinkConnector connector;

    /** The client factory for traversal and authorization clients. */
    private final ClientFactory clientFactory;

    /** The list of authentication managers. */
    private final AuthenticationManager authenticationManager;

    /** The authorization manager. */
    private final AuthorizationManager authorizationManager;

    /**
     *
     * @param connector a connector instance
     * @param clientFactory a client factory
     * @param authenticationManager the configured AuthenticationManager
     * @throws RepositoryException not thrown
     */
    public LivelinkSession(LivelinkConnector connector,
            ClientFactory clientFactory,
            AuthenticationManager authenticationManager,
            AuthorizationManager authorizationManager)
            throws RepositoryException {
        this.connector = connector;
        this.clientFactory = clientFactory;
        this.authenticationManager = authenticationManager;
        this.authorizationManager = authorizationManager;
    }

    /**
     * Gets a TraversalManager to implement query-based traversal
     *
     * @return a TraversalManager
     * @throws RepositoryException
     */
    public TraversalManager getTraversalManager() throws RepositoryException {
        return new LivelinkTraversalManager(connector, clientFactory);
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
        return authenticationManager;
    }

    /**
     * Gets an AuthorizationManager to implement per-user authorization.
     *
     * @return an AuthorizationManager
     * @throws RepositoryException
     */
    public AuthorizationManager getAuthorizationManager()
        throws RepositoryException
    {
        return authorizationManager;
    }

    /**
     * Returns the ClientFactory for test use.
     */
    ClientFactory getFactory() {
        return clientFactory;
    }
}
