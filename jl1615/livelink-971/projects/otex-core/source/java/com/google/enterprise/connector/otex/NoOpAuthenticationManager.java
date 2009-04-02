// Copyright (C) 2007 Google Inc.
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

import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.enterprise.connector.spi.AuthenticationIdentity;
import com.google.enterprise.connector.spi.AuthenticationManager;
import com.google.enterprise.connector.spi.AuthenticationResponse;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.RepositoryLoginException;

/**
 * Implements an no-op AuthenticationManager that authenticates every
 * request. Optionally, a shared password can be set that is checked
 * against every request.
 */
class NoOpAuthenticationManager implements AuthenticationManager {
    /** The logger for this class. */
    private static final Logger LOGGER =
        Logger.getLogger(NoOpAuthenticationManager.class.getName());

    /**
     * The shared password to check for, or <code>null</code> if
     * any password should be accepted.
     */
    private String sharedPassword;;

    /** No-args constructor for bean instantiation. */
    public NoOpAuthenticationManager() {
    }

    /**
     * Sets the shared password to check for. If this property is not
     * set or is set to <code>null</code>, any password will be
     * accepted as valid.
     *
     * @param sharedPassword the password to check for
     */
    public void setSharedPassword(String sharedPassword) {
        if (LOGGER.isLoggable(Level.CONFIG))
            LOGGER.config("SHARED PASSWORD: ...");
        this.sharedPassword = sharedPassword;
    }
    
    /** {@inheritDoc} */
    public AuthenticationResponse authenticate(AuthenticationIdentity identity)
            throws RepositoryLoginException, RepositoryException {
        boolean isValid = sharedPassword == null ||
            sharedPassword.equals(identity.getPassword());
        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.finest("AUTHENTICATE " + identity.getUsername() +
                ": " + isValid);
        }
        return new AuthenticationResponse(isValid, null);
    }
}
