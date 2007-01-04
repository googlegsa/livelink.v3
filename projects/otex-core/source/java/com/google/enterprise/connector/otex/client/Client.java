// Copyright (C) 2006-2007 Google Inc.

package com.google.enterprise.connector.otex.client;

import java.io.InputStream;
import java.util.logging.Logger;

import com.google.enterprise.connector.spi.RepositoryException;

// TODO: Eliminate this.
import com.opentext.api.LLValue;

/**
 * A facade interface that encapsulates the Livelink API, as it is
 * used by the connector. The intent is to enable non-LAPI
 * implementations that also don't require a Livelink server for
 * testing and support.
 */
public interface Client
{
    LLValue ListNodes(Logger logger, String query, String view,
        LLValue columns) throws RepositoryException;

    InputStream FetchVersion(Logger logger, int volumeId, int objectId,
        int versionNumber) throws RepositoryException;
}

