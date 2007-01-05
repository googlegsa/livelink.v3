// Copyright (C) 2007 Google Inc.

package com.google.enterprise.connector.otex.client.mock;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.logging.Logger;

import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.otex.client.Client;
import com.google.enterprise.connector.otex.client.RecArray;

/**
 * A mock client implementation.
 */
final class MockClient implements Client
{
    MockClient() {
    }

    public synchronized RecArray ListNodes(Logger logger, String query,
            String view, String[] columns) throws RepositoryException {
        logger.info("Entering MockClient.ListNodes");
        return new MockRecArray();
    }

    public synchronized InputStream FetchVersion(final Logger logger,
            int volumeId, int objectId, int versionNumber)
            throws RepositoryException {
        logger.info("Entering MockClient.FetchVersion");
        return new ByteArrayInputStream(new byte[0]);
    }
}
