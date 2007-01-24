// Copyright (C) 2007 Google Inc.

package com.google.enterprise.connector.otex;

import java.io.InputStream;
import java.util.logging.Logger;

import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.otex.client.Client;

/**
 * Retrieves the item content as an <code>InputStream</code>.
 * <p>
 * There are four implementations of this interface:
 * <dl>
 * <dt><code>ByteArrayContentHandler</code>
 * <dd> The fastest implementation, but not a very scalable one
 * because it maintains the entire content in a byte array.
 * 
 * <dt><code>FileContentHandler</code>
 * <dd> Stores the content in a temporary file, but performs very
 * badly for files larger than 10 MB.
 * 
 * <dt><code>HttpURLContentHandler</code>
 * <dd> Retrieves the content directly from the Livelink server using
 * an <code>HttpURLConnection</code>. Performance is inexplicably
 * disappointing, in line with but consistently 1-1/2 to 3 times slower
 * than the others.
 * 
 * <dt><code>PipedContentHandler</code>
 * <dd> Uses a pipe to match the in-memory performance characteristics
 * of the byte array handler without the scalability issues. A
 * consistently good performance with small memory consumption.
 * </dl>
 *
 * The default is <code>FileContentHandler</code>.
 *
 * @see LivelinkConnector#setContentHandler
 */
/*
 * FIXME: PipedContentHandler has deadlocks, ByteArrayContentHandler
 * isn't a general-purpose candidate, and Connector Manager issue 4
 * prohibits the use of HttpURLContentHandler. So the default is
 * FileContentHandler, which always works without consuming inordinate
 * resources. If the pipes can't be fixed but issue #4 can be, then a
 * blend of byte arrays and HTTP is probably the best solution.
 */
interface ContentHandler {
    /**
     * Initializes the content handler. This method is guaranteed to
     * be called exactly once on each instance.
     * 
     */
    void initialize(LivelinkConnector connector, Client client, Logger logger)
        throws RepositoryException;
    
    /**
     * Gets the contents.
     *
     * @param logger a logger instance to use
     * @param volumeId the volume ID of the object to fetch
     * @param objectId the object ID of the object to fetch
     * @param versionNumber the version number to fetch
     * @param size the size of the contents, as a hint to the implementation
     */
    InputStream getInputStream(Logger logger, int volumeId, int objectId,
        int versionNumber, int size) throws RepositoryException;
}
