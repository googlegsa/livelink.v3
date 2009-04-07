// Copyright (C) 2007-2009 Google Inc.
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
import java.util.Date;
import java.util.logging.Logger;

import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.otex.LivelinkException;
import com.google.enterprise.connector.otex.client.Client;
import com.google.enterprise.connector.otex.client.ClientValue;
import com.google.enterprise.connector.otex.client.ClientValueFactory;

/**
 * A mock client implementation.
 */
final class MockClient implements Client {
    /** The logger for this class. */
    private static final Logger LOGGER =
        Logger.getLogger(MockClient.class.getName());

    MockClient() {
    }

    /** {@inheritDoc} */
    public ClientValueFactory getClientValueFactory()
            throws RepositoryException {
        return new MockClientValueFactory();
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
     * This implementation returns 1000, the user ID of Admin.
     */
    public int GetCurrentUserID() throws RepositoryException {
        return 1000;
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation returns an empty list.
     */
    public ClientValue GetCookieInfo() throws RepositoryException {
        return new MockClientValue(new Object[0]);
    }

    /** {@inheritDoc} */
    public ClientValue GetUserOrGroupByIDNoThrow(int id)
            throws RepositoryException {
        return new MockClientValue(
            new String[] { "Name" }, new String[] { "Admin" });
    }

    /** {@inheritDoc} */
    public ClientValue GetUserInfo(String username)
            throws RepositoryException {
        int privileges;
        if (username.equals("Admin"))
            privileges = PRIV_PERM_BYPASS;
        else
            privileges = 0;
        return new MockClientValue(
            new String[] { "UserPrivileges" },
            new Integer[] { new Integer(privileges) });
    }

    /** {@inheritDoc} */
    public ClientValue AccessEnterpriseWS() throws RepositoryException {
        return new MockClientValue(new String[] { "ID", "VolumeID" },
            new Integer[] { new Integer(2000), new Integer(-2000) });
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation returns an empty <code>ClientValue</code>.
     */
    public ClientValue ListNodes(String query, String view, String[] columns) {
        LOGGER.fine("Entering MockClient.ListNodes");

        String[] fields;
        Object[][] values;
        if (query.startsWith("SubType in (") && view.equals("DTree")) {
            // This is the excluded volume types query.

            fields = new String[] { "DataID", "PermID" };

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
        } else if (view.equals("DTreeAncestors")) {
            // This is the check for an empty DTreeAncestors table.
            // The returned value doesn't matter.
            fields = new String[] { "DataID" };
            values = new Object[][] {
                new Object[] { new Integer(4104) } };
        } else if (columns.length == 1 &&
                columns[0].endsWith("minModifyDate")) {
            // This is the check for missing entries in the
            // DTreeAncestors table, and the optimized startDate. The
            // returned value doesn't matter.
            fields = new String[] { "minModifyDate" };
            values = new Object[][] {
                new Object[] { new Date() } };
        } else {
            fields = new String[0];
            values = new Object[0][0];
        }
        return new MockClientValue(fields, values);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Version of ListNodes() that does not throw exceptions.
     * Since the MockClient version of ListNodes already doesn't
     * throw exceptions, this doesn't do anything other than that.
     */
    public ClientValue ListNodesNoThrow(String query, String view,
            String[] columns) {
        return ListNodes(query, view, columns);
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation returns an empty assoc.
     */
    public ClientValue GetObjectInfo(int volumeId, int objectId) {
        return new MockClientValue(new String[0], new Object[0]);
    }

    /** {@inheritDoc} */
    public ClientValue GetObjectAttributesEx(ClientValue objectIdAssoc,
            ClientValue categoryIdAssoc) throws RepositoryException {
        try {
            return new MockClientValue(new String[0], new Object[0]);
        } catch (RuntimeException e) {
            throw new LivelinkException(e, LOGGER);
        }
    }
    
    /** {@inheritDoc} */
    public ClientValue AttrListNames(ClientValue categoryVersion,
            ClientValue attributeSetPath) throws RepositoryException {
        try {
            return new MockClientValue(new Object[0]);
        } catch (RuntimeException e) {
            throw new LivelinkException(e, LOGGER);
        }
    }
    
    /** {@inheritDoc} */
    public ClientValue AttrGetInfo(ClientValue categoryVersion,
            String attributeName, ClientValue attributeSetPath)
            throws RepositoryException {
        try {
            return new MockClientValue(new String[0], new Object[0]);
        } catch (RuntimeException e) {
            throw new LivelinkException(e, LOGGER);
        }
    }
    
    /** {@inheritDoc} */
    public ClientValue AttrGetValues(ClientValue categoryVersion,
            String attributeName, ClientValue attributeSetPath)
            throws RepositoryException {
        try {
            return new MockClientValue(new Object[0]);
        } catch (RuntimeException e) {
            throw new LivelinkException(e, LOGGER);
        }
    }

    /** {@inheritDoc} */
    public ClientValue ListObjectCategoryIDs(ClientValue objectIdAssoc)
            throws RepositoryException {
        try {
            return new MockClientValue(new Object[0]);
        } catch (RuntimeException e) {
            throw new LivelinkException(e, LOGGER);
        }
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

    /** {@inheritDoc} */
    public ClientValue GetVersionInfo(int volumeId, int objectId, int versionNumber)
            throws RepositoryException {
        LOGGER.fine("Entering MockClient.GetVersionInfo");
        return null; // FIXME; stub implementation to get it to compile
    }


    /**
     * {@inheritDoc}
     * <p>
     * This implementation has no effect.
     */
    public void ImpersonateUser(String username) throws RepositoryException {
        LOGGER.fine("Entering MockClient.ImpersonateUser");
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation has no effect.
     */
    public void ImpersonateUserEx(String username, String domain)
        throws RepositoryException {
        LOGGER.fine("Entering MockClient.ImpersonateUserEx");
    }
}
