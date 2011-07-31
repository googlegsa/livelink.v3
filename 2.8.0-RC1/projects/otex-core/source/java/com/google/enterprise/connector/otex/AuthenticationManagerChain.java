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

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.google.enterprise.connector.spi.AuthenticationIdentity;
import com.google.enterprise.connector.spi.AuthenticationManager;
import com.google.enterprise.connector.spi.AuthenticationResponse;
import com.google.enterprise.connector.spi.Connector;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.RepositoryLoginException;

/**
 * Wraps a list of AuthenticationManager instances and tries each
 * in turn to implement AuthenticationManager.authenticate.
 */
class AuthenticationManagerChain
        implements AuthenticationManager, ConnectorAware {
    /** The logger for this class. */
    private static final Logger LOGGER =
        Logger.getLogger(AuthenticationManagerChain.class.getName());

    /** The list of authentication managers. */
    private List authenticationManagers;

    /**
     * Constructor.
     */
    AuthenticationManagerChain() {
        super();
    }

    /**
     * Sets the list of authentication managers to try.
     *
     * @param authenticationManagers the list of authentication
     * managers; may not be null or empty
     */
    public void setAuthenticationManagers(List authenticationManagers) {
        if (authenticationManagers == null ||
                authenticationManagers.size() == 0) {
            throw new IllegalArgumentException();
        }
        this.authenticationManagers = authenticationManagers;
    }

    /** {@inheritDoc} */
    /* Must be called with a fully-initialized connector. Must be
     * called after the bean initialization has called
     * setAuthenticationManagers.
     */
    public void setConnector(Connector connector) throws RepositoryException {
        for (int i = 0; i < authenticationManagers.size(); i++) {
            Object o = authenticationManagers.get(i);
            if (o instanceof ConnectorAware)
                ((ConnectorAware) o).setConnector(connector);
        }
    }

    /** {@inheritDoc} */
    public AuthenticationResponse authenticate(AuthenticationIdentity identity)
            throws RepositoryLoginException, RepositoryException {
        if (LOGGER.isLoggable(Level.FINE))
            LOGGER.fine("AUTHENTICATE: " + identity.getUsername());

        AuthenticationResponse response = null;
        for (int i = 0; i < authenticationManagers.size(); i++) {
            try {
                AuthenticationManager a = (AuthenticationManager)
                    authenticationManagers.get(i);
                if (LOGGER.isLoggable(Level.FINER))
                    LOGGER.finer("Trying authentication manager " + a);
                response = a.authenticate(identity);
                if (response.isValid())
                    break;
            }
            catch (RepositoryException e) {
                LOGGER.warning("Authentication failed for " +
                    identity.getUsername() + "; " + e.getMessage());
                response = new AuthenticationResponse(false, null);
            }
        }
        return response;
    }
}
