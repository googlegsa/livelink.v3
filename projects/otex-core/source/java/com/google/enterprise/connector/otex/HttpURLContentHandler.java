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

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.net.URLConnection;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.otex.client.Client;
import com.google.enterprise.connector.otex.client.ClientValue;

/**
 * This content handler implementation uses a Livelink download URL.
 * It depends on the LAPI client, so it can't be used in deployments
 * with the mock client.
 * 
 * @see ContentHandler
 */
class HttpURLContentHandler implements ContentHandler {
    /** The logger for this class. */
    private static final Logger LOGGER =
        Logger.getLogger(HttpURLContentHandler.class.getName());

    /** The connector contains configuration information. */
    private LivelinkConnector connector;

    /** The client provides access to the server. */
    private Client client;

    /** The user's LLCookie value. */
    private String llCookie;
    
    HttpURLContentHandler() {
    }

    /** {@inheritDoc} */
    public void initialize(LivelinkConnector connector, Client client)
            throws RepositoryException {
        this.connector = connector;
        this.client = client;

        llCookie = getLLCookie();
        if (LOGGER.isLoggable(Level.FINE))
            LOGGER.fine("LLCOOKIE: " + llCookie);
    }

    /**
     * Gets the LLCookie value for this session.
     *
     * @return the cookie value
     * @throws RepositoryException if the cookie is not set
     */
    private String getLLCookie() throws RepositoryException {
        ClientValue cookies = client.GetCookieInfo();
        for (int i = 0; i < cookies.size(); i++) {
            ClientValue cookie = cookies.toValue(i);
            if ("LLCookie".equalsIgnoreCase(cookie.toString("Name"))) {
                return cookie.toString("Value");
            }
        }
        throw new RepositoryException("Missing LLCookie");
    }
    
    /** {@inheritDoc} */
    public InputStream getInputStream(int volumeId, int objectId,
            int versionNumber, int size) throws RepositoryException {
        try {
            String downloadUrlBase = connector.getDisplayUrl() +
                "?func=ll&objAction=download&objId=";
            URL downloadUrl = new URL(downloadUrlBase + objectId);
            URLConnection download = downloadUrl.openConnection();
            download.addRequestProperty("Cookie", "LLCookie=" + llCookie);

            // XXX: Does the BufferedInputStream help at all?
            return new BufferedInputStream(download.getInputStream(), 32768);
        } catch (IOException e) {
            throw new LivelinkException(e, LOGGER);
        }
    }
}
