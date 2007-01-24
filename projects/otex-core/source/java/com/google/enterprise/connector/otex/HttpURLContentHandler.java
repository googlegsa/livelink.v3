// Copyright (C) 2007 Google Inc.

package com.google.enterprise.connector.otex;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.net.URLConnection;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.otex.client.Client;

/**
 * This content handler implementation uses a Livelink download URL.
 * It depends on the LAPI client, so it can't be used in deployments
 * with the mock client.
 * 
 * @see ContentHandler
 */
class HttpURLContentHandler implements ContentHandler {
    /** The connector contains configuration information. */
    private LivelinkConnector connector;

    /** The client provides access to the server. */
    private Client client;

    /** The user's LLCookie value. */
    private String llCookie;
    
    HttpURLContentHandler() {
    }

    /** {@inheritDoc} */
    public void initialize(LivelinkConnector connector, Client client,
            Logger logger) throws RepositoryException {
        this.connector = connector;
        this.client = client;

        llCookie = client.getLLCookie(logger);
        if (logger.isLoggable(Level.FINE))
            logger.fine("LLCOOKIE: " + llCookie);
    }
    
    /** {@inheritDoc} */
    public InputStream getInputStream(final Logger logger,
            int volumeId, int objectId, int versionNumber, int size)
            throws RepositoryException {
        try {
            String downloadUrlBase = connector.getDisplayUrl() +
                "?func=ll&objAction=download&objId=";
            URL downloadUrl = new URL(downloadUrlBase + objectId);
            URLConnection download = downloadUrl.openConnection();
            download.addRequestProperty("Cookie", "LLCookie=" + llCookie);

            // XXX: Does the BufferedInputStream help at all?
            return new BufferedInputStream(download.getInputStream(), 32768);
        } catch (IOException e) {
            throw new LivelinkException(e, logger);
        }
    }
}
