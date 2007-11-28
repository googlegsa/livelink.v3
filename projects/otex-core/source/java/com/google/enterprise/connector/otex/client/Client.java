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
    
    /** Project object. */
    int PROJECTSUBTYPE = 202;
    
    /** Task object. */
    int TASKSUBTYPE = 206;

    /** Channel object. */
    int CHANNELSUBTYPE = 207;
    
    /** News object. */
    int NEWSSUBTYPE = 208;
    
    /** Poll object. */
    int POLLSUBTYPE = 218;
    
    /** ObjectInfo.Catalog.DISPLAYTYPE_HIDDEN */
    int DISPLAYTYPE_HIDDEN = 2;
       
    /** No character encoding. */
    int CHARACTER_ENCODING_NONE = 0;

    /** UTF-8 character encoding. */
    int CHARACTER_ENCODING_UTF8 = 1;
    
    /** Constants corresponding to LAPI_ATTRIBUTES.* */
    int ATTR_DATAVALUES = 0;
    int ATTR_DEFAULTVALUES = 1;
    int ATTR_TYPE_BOOL = 5;
    int ATTR_TYPE_DATE = -7;
    int ATTR_TYPE_DATEPOPUP = 13;
    int ATTR_TYPE_INT = 2;
    int ATTR_TYPE_REAL = -4;
    int ATTR_TYPE_REALPOPUP = 20;
    int ATTR_TYPE_INTPOPUP = 12;
    int ATTR_TYPE_SET = -18;
    int ATTR_TYPE_STRFIELD = -1;
    int ATTR_TYPE_STRMULTI = 11;
    int ATTR_TYPE_STRPOPUP = 10;
    int ATTR_TYPE_USER = 14;
    int CATEGORY_TYPE_LIBRARY = 0;
    int CATEGORY_TYPE_WORKFLOW = 2;


    /**
     * Get a Factory for the ClientValue concrete implementation used
     * by this client.
     */
    ClientValueFactory getClientValueFactory() throws RepositoryException;

    /**
     * Gets information about the Livelink server.
     *
     * @throws RepositoryException if an error occurs
     */
    ClientValue GetServerInfo() throws RepositoryException;

    /**
     * Gets the value of the cookies for the logged in user.
     *
     * @throws RepositoryException if an error occurs
     */
    ClientValue GetCookieInfo() throws RepositoryException;

    /**
     * Wraps the <code>LAPI_USERS.GetUserOrGroupByID</code> method.
     * All of the arguments of that method are exposed here.
     * 
     * @param id the ID of a user or group.
     * @returns a Record containing information about a user or group
     * corresponding to the specified ID. The Type attribute of the
     * returned record identifies whether the returned information is
     * for a user or a group.
     * @throws RepositoryException if an error occurs
     */
    ClientValue GetUserOrGroupByID(int id) throws RepositoryException;

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
     * Wraps the <code>LAPI_DOCUMENTS.ListNodes</code> method.
     * Not all of the arguments of that method are exposed here.
     * <p>
     * The LAPI <code>ListNodes</code> implementation requires the
     * DataID and PermID columns to be included in the selected
     * columns.
     * <p>
     * This wrapper on <code>ListNodes</code> does not throw a
     * <code>RepositoryExeception</code> on SQL query failure.  
     * Rather it returns a <code>null</code> value instead.
     * [It may still throw a RepositoryException if a LAPI
     * Runtime error occurs.]
     *
     * @param query a SQL condition, used in the WHERE clause
     * @param view a SQL table expression, used in the FROM clause
     * @param columns a SQL select list, used in the SELECT clause
     * @throws RepositoryException if an error occurs
     */
    ClientValue ListNodesNoThrow(String query, String view, String[] columns)
        throws RepositoryException;

    /**
     * Wraps the <code>LAPI_DOCUMENTS.GetObjectInfo</code> method.
     * All of the arguments of that method are exposed here.
     * 
     * @param volumeId the volume ID of the object
     * @param objectId the object ID of the object
     * @throws RepositoryException if an error occurs
     */
    ClientValue GetObjectInfo(int volumeId, int objectId)
        throws RepositoryException;

    /**
     * Wraps the <code>LAPI_DOCUMENTS.GetObjectAttributesEx</code> method.
     * All of the arguments of that method are exposed here.
     *
     * @param objectIdAssoc an Assoc Value that designates the object
     * whose list of Category Attribute values are being retrieved.
     * @param categoryIdAssoc an Assoc Value that designates the
     * Category version on the node object that will be retrieved.
     * @returns an Assoc Value that contains the retrieved Category version.
     * @throws RepositoryException if an error occurs
     */
    ClientValue GetObjectAttributesEx(ClientValue objectIdAssoc,
        ClientValue categoryIdAssoc) throws RepositoryException;

    /**
     * Wraps the <code>LAPI_ATTRIBUTES.AttrListNames</code> method.
     * All of the arguments of that method are exposed here.
     *
     * @param categoryVersion an Assoc Value that designates the
     * Category version (returned from
     * <code>GetObjectAttributesEx</code>).
     * @param attributeSetPath a List Value - only used when the
     * attribute specified by the attrName parameter is a member of a
     * parent Set attribute. If this parameter is not of type List, is
     * null, or is empty, it is ignored.
     * @returns a List Value that contains display names for an
     * attribute that is a direct child of the category or of a Set
     * attribute within the category.
     * @throws RepositoryException if an error occurs
     */
    ClientValue AttrListNames(ClientValue categoryVersion,
        ClientValue attributeSetPath) throws RepositoryException;

    /**
     * Wraps the <code>LAPI_ATTRIBUTES.AttrGetInfo</code> method.
     * All of the arguments of that method are exposed here.
     *
     * @param categoryVersion an Assoc Value that designates the
     * Category version (returned from
     * <code>GetObjectAttributesEx</code>).
     * @param attributeName the display name of the attribute for
     * which information is to be retrieved.
     * @param attributeSetPath a List Value - only used when the
     * attribute specified by the attributeName parameter is a member
     * of a parent Set attribute. If this parameter is not of type
     * List, is null, or is empty, it is ignored.
     * @returns a List Value that contains display names for an
     * attribute that is a direct child of the category or of a Set
     * attribute within the category.
     * @throws RepositoryException if an error occurs
     */
    ClientValue AttrGetInfo(ClientValue categoryVersion,
        String attributeName, ClientValue attributeSetPath)
        throws RepositoryException;

    /**
     * Wraps the <code>LAPI_ATTRIBUTES.AttrGetValues</code> method.
     * Not all of the arguments of that method are exposed here.
     *
     * @param categoryVersion an Assoc Value that designates the
     * Category version (returned from
     * <code>GetObjectAttributesEx</code>).
     * @param attributeName the display name of the attribute for
     * which information is to be retrieved.
     * @param attributeSetPath a List Value - only used when the
     * attribute specified by the attributeName parameter is a member
     * of a parent Set attribute. If this parameter is not of type
     * List, is null, or is empty, it is ignored.
     * @returns a List Value that holds the updated data or default
     * attribute values.
     * @throws RepositoryException if an error occurs
     */
    ClientValue AttrGetValues(ClientValue categoryVersion,
        String attributeName, ClientValue attributeSetPath)
        throws RepositoryException;

    /**
     * Wraps the <code>LAPI_DOCUMENTS.ListObjectCategoryIDs</code>
     * method.  All of the arguments of that method are exposed here.
     *
     * @param objectIdAssoc an Assoc Value that designates the object
     * whose list of Category Attribute values are being retrieved.
     * @returns a List Value that contains the list of category
     * identifiers for the categories assigned to the object.
     * @throws RepositoryException if an error occurs
     */
    ClientValue ListObjectCategoryIDs(ClientValue objectIdAssoc)
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
     * Wraps the <code>LAPI_DOCUMENTS.getVersionInfo</code> method.
     * 
     * @param volumeId the volume ID of the object
     * @param objectId the object ID of the object
     * @param versionNumber the version number to get info on
     * @returns a ClientValue Assoc object containing the VersionInfo
     * @throws RepositoryException if an error occurs
     */
    ClientValue GetVersionInfo(int volumeId, int objectId, int versionNumber)
        throws RepositoryException;

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
     * Wraps the (undocumented) <code>LLSession.ImpersonateUserEx</code>
     * method. The initial session must have been created with a
     * user who has Livelink system administration privileges in
     * order for impersonation to work.
     *
     * @param username the username
     * @param domain the domainname (may be null or empty)
     * @throws RepositoryException if an error occurs
     */
    void ImpersonateUserEx(String username, String domain)
        throws RepositoryException;
}
