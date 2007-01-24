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
        logger.info("Entering MockClient.ListNodes");
        return new MockRecArray();
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation does nothing.
     */
    public void FetchVersion(final Logger logger,
            int volumeId, int objectId, int versionNumber, File path) {
        logger.info("Entering MockClient.FetchVersion");
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
        logger.info("Entering MockClient.FetchVersion");
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
        logger.info("Entering MockClient.ImpersonateUser");
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation always returns true.
     */
    public synchronized boolean ping(final Logger logger) {
        logger.info("Entering MockClient.ping");
        return true;
    }
}
