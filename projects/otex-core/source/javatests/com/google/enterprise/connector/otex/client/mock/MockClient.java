// Copyright (C) 2007 Google Inc.

package com.google.enterprise.connector.otex.client.mock;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.logging.Logger;

import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.otex.LivelinkException;
import com.google.enterprise.connector.otex.client.Client;
import com.google.enterprise.connector.otex.client.RecArray;

/**
 * A mock client implementation.
 */
final class MockClient implements Client
{
    MockClient() {
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation returns <code>null</code>.
     */
    public String getEncoding(Logger logger) {
        return null;
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation returns an empty string.
     */
    public String getLLCookie(Logger logger) throws RepositoryException {
        return "";
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation returns an empty <code>RecArray</code>.
     */
    public RecArray ListNodes(Logger logger, String query,
            String view, String[] columns) {
        logger.fine("Entering MockClient.ListNodes");

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
        return new MockRecArray(new String[] { "DataID", "PermID" }, values);
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation returns an empty recarray.
     */
    /* TODO: This should return an empty assoc. */
    public RecArray GetObjectInfo(final Logger logger, int volumeId,
        int objectId)
    {
        return new MockRecArray(new String[0], new Object[0][0]);
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation does nothing.
     */
    public void FetchVersion(final Logger logger,
            int volumeId, int objectId, int versionNumber, File path) {
        logger.fine("Entering MockClient.FetchVersion");
        // TODO: Make sure that the file exists and is empty.
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation does nothing but close the output stream.
     */
    public void FetchVersion(final Logger logger, int volumeId,
            int objectId, int versionNumber, OutputStream out)
            throws RepositoryException {
        logger.fine("Entering MockClient.FetchVersion");
        try {
            out.close();
        } catch (IOException e) {
            throw new LivelinkException(e, logger);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation has no effect.
     */
    public synchronized void ImpersonateUser(final Logger logger, 
        String username) throws RepositoryException {
        logger.fine("Entering MockClient.ImpersonateUser");
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation always returns true.
     */
    public synchronized boolean ping(final Logger logger) {
        logger.fine("Entering MockClient.ping");
        return true;
    }
}
