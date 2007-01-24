// Copyright (C) 2006-2007 Google Inc.

package com.google.enterprise.connector.otex.client;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.logging.Logger;

import com.google.enterprise.connector.spi.RepositoryException;

/**
 * A facade interface that encapsulates the Livelink API, as it is
 * used by the connector. The intent is to enable non-LAPI
 * implementations that also don't require a Livelink server for
 * testing and support.
 * <p>
 * All of the methods take a <code>java.util.Logger</code> parameter
 * to use when logging.
 */
/* XXX: Do we want the client implementations to use their own loggers? */
public interface Client
{
    /**
     * Gets the server character encoding, or null if an encoding does
     * not need to be set on the session.
     *
     * @param logger a logger instance to use
     * @throws RepositoryException if an error occurs
     */
    String getEncoding(Logger logger) throws RepositoryException;

    /**
     * Gets the value of the LLCookie for the logged in user.
     *
     * @param logger a logger instance to use
     * @throws RepositoryException if an error occurs or if the
     * LLCookie is missing
     */
    String getLLCookie(Logger logger) throws RepositoryException;

    /**
     * Wraps the <code>LAPI_DOCUMENTS.ListNodes</code> method.
     * Not all of the arguments of that method are exposed here.
     * <p>
     * The LAPI <code>ListNodes</code> implementation requires the
     * DataID and PermID columns to be included in the selected
     * columns.
     *
     * @param logger a logger instance to use
     * @param query a SQL condition, used in the WHERE clause
     * @param view a SQL table expression, used in the FROM clause
     * @param columns a SQL select list, used in the SELECT clause
     * @throws RepositoryException if an error occurs
     */
    RecArray ListNodes(Logger logger, String query, String view,
        String[] columns) throws RepositoryException;

    /**
     * Wraps the <code>LAPI_DOCUMENTS.FetchVersion</code> method.
     * All of the arguments of that method are exposed here.
     *
     * @param logger a logger instance to use
     * @param volumeId the volume ID of the object to fetch
     * @param objectId the object ID of the object to fetch
     * @param versionNumber the version number to fetch
     * @param path the path of the file to write the contents to
     * @throws RepositoryException if an error occurs
     */
    void FetchVersion(Logger logger, int volumeId, int objectId,
        int versionNumber, File path) throws RepositoryException;

    /**
     * Wraps the <code>LAPI_DOCUMENTS.FetchVersion</code> method.
     * All of the arguments of that method are exposed here.
     *
     * @param logger a logger instance to use
     * @param volumeId the volume ID of the object to fetch
     * @param objectId the object ID of the object to fetch
     * @param versionNumber the version number to fetch
     * @param out the stream to write the contents to
     * @throws RepositoryException if an error occurs
     */
    void FetchVersion(Logger logger, int volumeId, int objectId,
        int versionNumber, OutputStream out) throws RepositoryException;

    /**
     * Wraps the <code>LLSession.ImpersonateUser</code>
     * method. The initial session must have been created with a
     * user who has Livelink system administration privileges in
     * order for impersonation to work.
     *
     * @param logger a logger instance to use
     * @param username the username
     * @throws RepositoryException if an error occurs
     */
    void ImpersonateUser(Logger logger, String username)
        throws RepositoryException;

    /**
     * Verifies that the current session can call a method which
     * accesses the repository. This method may be used to ensure
     * that the current user can access the repository, or that
     * the repository is available.
     *
     * @param logger a logger instance to use
     * @return true if the client can access the repository; false otherwise
     * @throws RepositoryException if an error occurs
     */
    boolean ping(Logger logger) throws RepositoryException; 
}
