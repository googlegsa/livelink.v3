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

package com.google.enterprise.connector.otex.client;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;

import com.google.enterprise.connector.spi.RepositoryException;

/**
 * A facade interface that encapsulates the Livelink API, as it is
 * used by the connector. The intent is to enable non-LAPI
 * implementations that also don't require a Livelink server for
 * testing and support.
 */
public interface Client
{
    /** Topic object. */
    int TOPICSUBTYPE = 130;

    /** Reply object. */
    int REPLYSUBTYPE = 134;
    
    /** Task object. */
    int TASKSUBTYPE = 206;

    /** No character encoding. */
    int CHARACTER_ENCODING_NONE = 0;

    /** UTF-8 character encoding. */
    int CHARACTER_ENCODING_UTF8 = 1;
    
    /**
     * Gets information about the Livelink server.
     *
     * @throws RepositoryException if an error occurs
     */
    ClientValue GetServerInfo() throws RepositoryException;

    /**
     * Gets the value of the LLCookie for the logged in user.
     *
     * @throws RepositoryException if an error occurs or if the
     * LLCookie is missing
     */
    String getLLCookie() throws RepositoryException;

    /**
     * Wraps the <code>LAPI_DOCUMENTS.ListNodes</code> method.
     * Not all of the arguments of that method are exposed here.
     * <p>
     * The LAPI <code>ListNodes</code> implementation requires the
     * DataID and PermID columns to be included in the selected
     * columns.
     *
     * @param query a SQL condition, used in the WHERE clause
     * @param view a SQL table expression, used in the FROM clause
     * @param columns a SQL select list, used in the SELECT clause
     * @throws RepositoryException if an error occurs
     */
    ClientValue ListNodes(String query, String view, String[] columns)
        throws RepositoryException;

    /**
     * Wraps the <code>LAPI_DOCUMENTS.GetObjectInfo</code> method.
     * 
     * @param volumeId the volume ID of the object
     * @param objectId the object ID of the object
     */
    ClientValue GetObjectInfo(int volumeId, int objectId)
        throws RepositoryException;

    /**
     * Wraps the <code>LAPI_DOCUMENTS.FetchVersion</code> method.
     * All of the arguments of that method are exposed here.
     *
     * @param volumeId the volume ID of the object to fetch
     * @param objectId the object ID of the object to fetch
     * @param versionNumber the version number to fetch
     * @param path the path of the file to write the contents to
     * @throws RepositoryException if an error occurs
     */
    void FetchVersion(int volumeId, int objectId, int versionNumber,
        File path) throws RepositoryException;

    /**
     * Wraps the <code>LAPI_DOCUMENTS.FetchVersion</code> method.
     * All of the arguments of that method are exposed here.
     *
     * @param volumeId the volume ID of the object to fetch
     * @param objectId the object ID of the object to fetch
     * @param versionNumber the version number to fetch
     * @param out the stream to write the contents to
     * @throws RepositoryException if an error occurs
     */
    void FetchVersion(int volumeId, int objectId, int versionNumber,
        OutputStream out) throws RepositoryException;

    /**
     * Wraps the <code>LLSession.ImpersonateUser</code>
     * method. The initial session must have been created with a
     * user who has Livelink system administration privileges in
     * order for impersonation to work.
     *
     * @param username the username
     * @throws RepositoryException if an error occurs
     */
    void ImpersonateUser(String username) throws RepositoryException;

    /**
     * Verifies that the current session can call a method which
     * accesses the repository. This method may be used to ensure
     * that the current user can access the repository, or that
     * the repository is available.
     *
     * @return true if the client can access the repository; false otherwise
     * @throws RepositoryException if an error occurs
     */
    boolean ping() throws RepositoryException; 
}
