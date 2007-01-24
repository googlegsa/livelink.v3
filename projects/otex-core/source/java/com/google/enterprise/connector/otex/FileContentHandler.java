// Copyright (C) 2006-2007 Google Inc.

package com.google.enterprise.connector.otex;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.otex.client.Client;

/**
 * This content handler implementation uses <code>FetchVersion</code>
 * with a temporary file path and returns a
 * <code>FileInputStream</code> subclass that deletes the the
 * temporary file when the stream is closed.
 * 
 * @see ContentHandler
 */
class FileContentHandler implements ContentHandler {
    /** The connector contains configuration information. */
    private LivelinkConnector connector;

    /** The client provides access to the server. */
    private Client client;

    FileContentHandler() {
    }

    /** {@inheritDoc} */
    public void initialize(LivelinkConnector connector, Client client,
            Logger logger) throws RepositoryException {
        this.connector = connector;
        this.client = client;
    }
    
    /** {@inheritDoc} */
    public InputStream getInputStream(final Logger logger,
            int volumeId, int objectId, int versionNumber, int size)
            throws RepositoryException {
        try {
            // TODO: Does the new delete before throwing clear up debris?
            // TODO: Use connector working dir here?
            final File temporaryFile =
                File.createTempFile("gsa-otex-", null);
            if (logger.isLoggable(Level.FINER))
                logger.finer("TEMP FILE: " + temporaryFile);
            try {
                client.FetchVersion(logger, volumeId, objectId,
                    versionNumber, temporaryFile);
            } catch (RepositoryException e) {
                try {
                    if (logger.isLoggable(Level.FINER))
                        logger.finer("DELETE: " + temporaryFile);
                    temporaryFile.delete();
                } finally {
                    throw e;
                }
            }
            return new FileInputStream(temporaryFile) {
                    public void close() throws IOException {
                        try {
                            super.close();
                        } finally {
                            if (logger.isLoggable(Level.FINER))
                                logger.finer("DELETE: " + temporaryFile);
                            temporaryFile.delete();
                        }
                    }
                };
        } catch (IOException e) {
            throw new LivelinkException(e, logger);
        }
    }
}
