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

package com.google.enterprise.connector.otex.client.mock;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.logging.Logger;

import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.otex.LivelinkException;
import com.google.enterprise.connector.otex.client.Client;
import com.google.enterprise.connector.otex.client.ClientValue;

/**
 * A mock client implementation.
 */
final class MockClient implements Client {
    /** The logger for this class. */
    private static final Logger LOGGER =
        Logger.getLogger(MockClient.class.getName());

    MockClient() {
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation returns an assoc containing a
     * CharacterEncoding of CHARACTER_ENCODING_NONE and a
     * ServerVersion of "9.5.0".
     */
    public ClientValue GetServerInfo() {
        return new MockClientValue(
            new String[] { "CharacterEncoding", "ServerVersion" },
            new Object[] { new Integer(CHARACTER_ENCODING_NONE), "9.5.0" });
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation returns an empty string.
     */
    public String getLLCookie() throws RepositoryException {
        return "";
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation returns an empty <code>ClientValue</code>.
     */
    public ClientValue ListNodes(String query, String view, String[] columns) {
        LOGGER.fine("Entering MockClient.ListNodes");

        Object[][] values;
        if (query.startsWith("SubType in (") && view.equals("DTree")) {
            // This is the excluded volume types query.

            // One of the tests asks for invalid volume types, namely
            // 2001 and 4104. That query needs to return no values.
            // Other than that, any non-empty results are OK.
            // TODO: Get the actual list of requested types and use >
            // 2000 to check for invalid types.
            if (query.indexOf("2001,4104") != -1)
                values = new Object[0][0];
            else {
                values = new Object[][] {
                    new Object[] { new Integer(9999), new Integer(0) } };
            }
        } else
            values = new Object[0][0];
        return new MockClientValue(new String[] { "DataID", "PermID" },
            values);
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation returns an empty assoc.
     */
    public ClientValue GetObjectInfo(int volumeId, int objectId) {
        return new MockClientValue(new String[0], new Object[0]);
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation does nothing.
     */
    public void FetchVersion(int volumeId, int objectId, int versionNumber,
            File path) {
        LOGGER.fine("Entering MockClient.FetchVersion");
        // TODO: Make sure that the file exists and is empty.
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation does nothing but close the output stream.
     */
    public void FetchVersion(int volumeId, int objectId, int versionNumber,
            OutputStream out) throws RepositoryException {
        LOGGER.fine("Entering MockClient.FetchVersion");
        try {
            out.close();
        } catch (IOException e) {
            throw new LivelinkException(e, LOGGER);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation has no effect.
     */
    public synchronized void ImpersonateUser(String username)
            throws RepositoryException {
        LOGGER.fine("Entering MockClient.ImpersonateUser");
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation always returns true.
     */
    public synchronized boolean ping() {
        LOGGER.fine("Entering MockClient.ping");
        return true;
    }
}
