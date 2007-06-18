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

package com.google.enterprise.connector.otex.client.lapi;

import java.io.File;
import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.otex.client.Client;
import com.google.enterprise.connector.otex.client.ClientValue;
import com.google.enterprise.connector.otex.client.ClientValueFactory;

import com.opentext.api.LAPI_ATTRIBUTES;
import com.opentext.api.LAPI_DOCUMENTS;
import com.opentext.api.LAPI_USERS;
import com.opentext.api.LLSession;
import com.opentext.api.LLValue;

/**
 * A direct LAPI client implementation.
 */
final class LapiClient implements Client {
    /** The logger for this class. */
    private static final Logger LOGGER =
        Logger.getLogger(LapiClient.class.getName());

    static {
        // Verify that the Client class constants are correct.
        assert TOPICSUBTYPE == LAPI_DOCUMENTS.TOPICSUBTYPE :
            LAPI_DOCUMENTS.TOPICSUBTYPE;
        assert REPLYSUBTYPE == LAPI_DOCUMENTS.REPLYSUBTYPE :
            LAPI_DOCUMENTS.REPLYSUBTYPE;
        assert PROJECTSUBTYPE == LAPI_DOCUMENTS.PROJECTSUBTYPE :
            LAPI_DOCUMENTS.PROJECTSUBTYPE;
        assert TASKSUBTYPE == LAPI_DOCUMENTS.TASKSUBTYPE :
            LAPI_DOCUMENTS.TASKSUBTYPE;
        assert CHANNELSUBTYPE == LAPI_DOCUMENTS.CHANNELSUBTYPE :
            LAPI_DOCUMENTS.CHANNELSUBTYPE;
        assert NEWSSUBTYPE == LAPI_DOCUMENTS.NEWSSUBTYPE :
            LAPI_DOCUMENTS.NEWSSUBTYPE;
        assert POLLSUBTYPE == LAPI_DOCUMENTS.POLLSUBTYPE :
            LAPI_DOCUMENTS.POLLSUBTYPE;
        assert CHARACTER_ENCODING_NONE ==
            LAPI_DOCUMENTS.CHARACTER_ENCODING_NONE :
            LAPI_DOCUMENTS.CHARACTER_ENCODING_NONE;
        assert CHARACTER_ENCODING_UTF8 ==
            LAPI_DOCUMENTS.CHARACTER_ENCODING_UTF8 :
            LAPI_DOCUMENTS.CHARACTER_ENCODING_UTF8;
        assert ATTR_DATAVALUES == LAPI_ATTRIBUTES.ATTR_DATAVALUES :
            LAPI_ATTRIBUTES.ATTR_DATAVALUES;
        assert ATTR_DEFAULTVALUES == LAPI_ATTRIBUTES.ATTR_DEFAULTVALUES :
            LAPI_ATTRIBUTES.ATTR_DEFAULTVALUES;
        assert ATTR_TYPE_BOOL == LAPI_ATTRIBUTES.ATTR_TYPE_BOOL :
            LAPI_ATTRIBUTES.ATTR_TYPE_BOOL;
        assert ATTR_TYPE_DATE == LAPI_ATTRIBUTES.ATTR_TYPE_DATE :
            LAPI_ATTRIBUTES.ATTR_TYPE_DATE;
        assert ATTR_TYPE_DATEPOPUP == LAPI_ATTRIBUTES.ATTR_TYPE_DATEPOPUP :
            LAPI_ATTRIBUTES.ATTR_TYPE_DATEPOPUP;
        assert ATTR_TYPE_REAL == LAPI_ATTRIBUTES.ATTR_TYPE_REAL :
            LAPI_ATTRIBUTES.ATTR_TYPE_REAL;
        assert ATTR_TYPE_REALPOPUP == LAPI_ATTRIBUTES.ATTR_TYPE_REALPOPUP :
            LAPI_ATTRIBUTES.ATTR_TYPE_REALPOPUP;
        assert ATTR_TYPE_INTPOPUP == LAPI_ATTRIBUTES.ATTR_TYPE_INTPOPUP :
            LAPI_ATTRIBUTES.ATTR_TYPE_INTPOPUP;
        assert ATTR_TYPE_SET == LAPI_ATTRIBUTES.ATTR_TYPE_SET :
            LAPI_ATTRIBUTES.ATTR_TYPE_SET;
        assert ATTR_TYPE_STRFIELD == LAPI_ATTRIBUTES.ATTR_TYPE_STRFIELD :
            LAPI_ATTRIBUTES.ATTR_TYPE_STRFIELD;
        assert ATTR_TYPE_STRMULTI == LAPI_ATTRIBUTES.ATTR_TYPE_STRMULTI :
            LAPI_ATTRIBUTES.ATTR_TYPE_STRMULTI;
        assert ATTR_TYPE_STRPOPUP == LAPI_ATTRIBUTES.ATTR_TYPE_STRPOPUP :
            LAPI_ATTRIBUTES.ATTR_TYPE_STRPOPUP;
        assert ATTR_TYPE_USER == LAPI_ATTRIBUTES.ATTR_TYPE_USER :
            LAPI_ATTRIBUTES.ATTR_TYPE_USER;
        assert CATEGORY_TYPE_LIBRARY == LAPI_ATTRIBUTES.CATEGORY_TYPE_LIBRARY :
            LAPI_ATTRIBUTES.CATEGORY_TYPE_LIBRARY;
        assert CATEGORY_TYPE_WORKFLOW ==
            LAPI_ATTRIBUTES.CATEGORY_TYPE_WORKFLOW :
            LAPI_ATTRIBUTES.CATEGORY_TYPE_WORKFLOW;
    }
    
    /**
     * The Livelink session. LLSession instances are not thread-safe,
     * so all of the methods that access this session, directly or
     * indirectly, must be synchronized.
     */
    private final LLSession session;
    
    private final LAPI_DOCUMENTS documents;

    private final LAPI_USERS users;

    private final LAPI_ATTRIBUTES attributes;

    /*
     * Constructs a new client using the given session. Initializes
     * and subsidiary objects that are needed.
     *
     * @param session a new Livelink session
     */
    LapiClient(LLSession session) {
        this.session = session;
        this.documents = new LAPI_DOCUMENTS(session);
        this.users = new LAPI_USERS(session);
        this.attributes = new LAPI_ATTRIBUTES(session);
    }

    /** {@inheritDoc} */
    public ClientValueFactory getClientValueFactory()
            throws RepositoryException {
        return new LapiClientValueFactory();
    }

    /** {@inheritDoc} */
    public ClientValue GetServerInfo() throws RepositoryException {
        LLValue value = new LLValue();
        try {
            if (documents.GetServerInfo(value) != 0)
                throw new LapiException(session, LOGGER);
        } catch (RuntimeException e) {
            throw new LapiException(e, LOGGER);
        }
        return new LapiClientValue(value);
    }

    /** {@inheritDoc} */
    public synchronized ClientValue GetCookieInfo()
            throws RepositoryException {
        LLValue cookies = new LLValue();
        try {
            if (users.GetCookieInfo(cookies) != 0)
                throw new LapiException(session, LOGGER);
        } catch (RuntimeException e) {
            throw new LapiException(e, LOGGER);
        }
        return new LapiClientValue(cookies);
    }
    
    /** {@inheritDoc} */
    public synchronized ClientValue GetUserOrGroupByID(int id)
            throws RepositoryException {
        LLValue userInfo = new LLValue();
        try {
            if (users.GetUserOrGroupByID(id, userInfo) != 0) {
                throw new LapiException(session, LOGGER);
            }
        } catch (RuntimeException e) {
            throw new LapiException(e, LOGGER);
        }
        return new LapiClientValue(userInfo);
    }

    /** {@inheritDoc} */
    public synchronized ClientValue ListNodes(String query, String view,
            String[] columns) throws RepositoryException {
        LLValue recArray = new LLValue();
        try {
            LLValue args = (new LLValue()).setList();
            LLValue columnsList = (new LLValue()).setList();
            for (int i = 0; i < columns.length; i++)
                columnsList.add(columns[i]);
            if (documents.ListNodes(query, args, view, columnsList, 
                    LAPI_DOCUMENTS.PERM_SEECONTENTS, LLValue.LL_FALSE,
                    recArray) != 0) {
                throw new LapiException(session, LOGGER);
            }
        } catch (RuntimeException e) {
            throw new LapiException(e, LOGGER);
        }
        return new LapiClientValue(recArray);
    }

    /** {@inheritDoc} */
    public synchronized ClientValue GetObjectInfo(int volumeId, int objectId)
            throws RepositoryException {
        LLValue objectInfo = new LLValue();
        try {
            if (documents.GetObjectInfo(volumeId, objectId, objectInfo) != 0) {
                throw new LapiException(session, LOGGER);
            }
        } catch (RuntimeException e) {
            throw new LapiException(e, LOGGER);
        }
        return new LapiClientValue(objectInfo);
    }
    
    /** {@inheritDoc} */
    public synchronized ClientValue GetObjectAttributesEx(
            ClientValue objectIdAssoc, ClientValue categoryIdAssoc)
            throws RepositoryException {
        LLValue categoryVersion = new LLValue();
        try {
            LLValue objIDa = ((LapiClientValue) objectIdAssoc).getLLValue();
            LLValue catIDa = ((LapiClientValue) categoryIdAssoc).getLLValue();
            if (documents.GetObjectAttributesEx(objIDa, catIDa,
                    categoryVersion) != 0) {
                throw new LapiException(session, LOGGER);
            }
        } catch (RuntimeException e) {
            throw new LapiException(e, LOGGER);
        }
        return new LapiClientValue(categoryVersion);
    }
    
    
    /** {@inheritDoc} */
    public synchronized ClientValue AttrListNames(ClientValue categoryVersion,
            ClientValue attributeSetPath) throws RepositoryException {
        LLValue attrNames = new LLValue();
        try {
            LLValue catVersion =
                ((LapiClientValue) categoryVersion).getLLValue();
            LLValue attrPath = (attributeSetPath == null) ? null :
                ((LapiClientValue) attributeSetPath).getLLValue();
            if (attributes.AttrListNames(catVersion, attrPath,
                    attrNames) != 0) {
                throw new LapiException(session, LOGGER); 
            }
        } catch (RuntimeException e) {
            throw new LapiException(e, LOGGER);
        }
        return new LapiClientValue(attrNames);
    }
    
    
    /** {@inheritDoc} */
    public synchronized ClientValue AttrGetInfo(ClientValue categoryVersion,
            String attributeName, ClientValue attributeSetPath)
            throws RepositoryException {
        LLValue info = new LLValue();
        try {
            LLValue catVersion =
                ((LapiClientValue) categoryVersion).getLLValue();
            LLValue attrPath = (attributeSetPath == null) ? null :
                ((LapiClientValue) attributeSetPath).getLLValue();
            if (attributes.AttrGetInfo(catVersion, attributeName, attrPath,
                    info) != 0) {
                throw new LapiException(session, LOGGER);
            }
        } catch (RuntimeException e) {
            throw new LapiException(e, LOGGER);
        }
        return new LapiClientValue(info);
    }
    
    
    /** {@inheritDoc} */
    public synchronized ClientValue AttrGetValues(ClientValue categoryVersion,
            String attributeName, ClientValue attributeSetPath)
            throws RepositoryException {
        LLValue attrValues = new LLValue();
        try {
            LLValue catVersion =
                ((LapiClientValue) categoryVersion).getLLValue();
            LLValue attrPath = (attributeSetPath == null) ? null :
                ((LapiClientValue) attributeSetPath).getLLValue();
            if (attributes.AttrGetValues(catVersion, attributeName,
                    LAPI_ATTRIBUTES.ATTR_DATAVALUES, attrPath,
                    attrValues) != 0) {
                throw new LapiException(session, LOGGER);
            }
        } catch (RuntimeException e) {
            throw new LapiException(e, LOGGER);
        }
        return new LapiClientValue(attrValues);
    }


    /** {@inheritDoc} */
    public synchronized ClientValue ListObjectCategoryIDs(
            ClientValue objectIdAssoc) throws RepositoryException {
        LLValue categoryIds = new LLValue();
        try {
            LLValue objIDa = ((LapiClientValue) objectIdAssoc).getLLValue();
            if (documents.ListObjectCategoryIDs(objIDa, categoryIds) != 0)
                throw new LapiException(session, LOGGER);
        } catch (RuntimeException e) {
            throw new LapiException(e, LOGGER);
        }
        return new LapiClientValue(categoryIds);
    }
    

    /** {@inheritDoc} */
    public synchronized void FetchVersion(int volumeId, int objectId,
            int versionNumber, File path) throws RepositoryException {
        try {
            if (documents.FetchVersion(volumeId, objectId, versionNumber,
                    path.getPath()) != 0) {
                throw new LapiException(session, LOGGER);
            }
        } catch (RuntimeException e) {
            throw new LapiException(e, LOGGER);
        }
    }

    /** {@inheritDoc} */
    public synchronized void FetchVersion(int volumeId, int objectId,
            int versionNumber, OutputStream out) throws RepositoryException {
        try {
            if (documents.FetchVersion(volumeId, objectId,
                    versionNumber, out) != 0) { 
                throw new LapiException(session, LOGGER);
            }
        } catch (RuntimeException e) {
            throw new LapiException(e, LOGGER);
        }
    }


    /**
     * {@inheritDoc}
     */
    public synchronized void ImpersonateUser(String username) {
        session.ImpersonateUser(username);
    }


    /**
     * {@inheritDoc}
     *
     * <p>This implementation uses AccessEnterpriseWSEx.</p>
     */
    /* User access to the Personal Workspace can be disabled, so
     * try the Enterprise Workspace.  Passing null for the domain
     * parameter should cause the domain to default to the domain
     * of the logged-in user. We might want to use the
     * GetCookieInfo method instead.
     */
    public synchronized boolean ping() throws RepositoryException {
        LLValue info = new LLValue();
        try {
            // TODO: ensure that the LapiClient handles domains.
            if (documents.AccessEnterpriseWSEx(null, info) != 0)
                throw new LapiException(session, LOGGER); 
        } catch (RuntimeException e) {
            throw new LapiException(e, LOGGER); 
        }
        return true;
    }

    /**
     * Gets the category attribute values for the indicated
     * object. Each attribute name is mapped to a linked list of
     * values for each occurrence of that attribute. Attributes
     * with the same name in different categories will have their
     * values merged into a single list. Attribute sets are
     * supported, but nested attribute sets are not. User
     * attributes are resolved into the user or group name.
     *
     * @param objId the object id of the Livelink item for which
     * to retrieve category information
     * @return a map of attribute names to lists of values
     * @throws RepositoryException if an error occurs
     */
    public Map getCategoryAttributes(int objId) throws RepositoryException {
        HashMap data = new HashMap(); 
        try {

            // List the categories. LAPI requires us to use this
            // Assoc containing the id instead of just passing in
            // the id. The Assoc may have two other values, Type,
            // which specifies the kind of object being looked up
            // (there's only one legal value currently) and
            // Version, which specifies which version of the
            // object to use (the default is the current
            // version).
            LLValue objectIdAssoc = new LLValue().setAssoc();
            objectIdAssoc.add("ID", objId); 
            LLValue categoryIds = new LLValue(); 
            if (documents.ListObjectCategoryIDs(objectIdAssoc,
                    categoryIds) != 0) {
                throw new LapiException(session, LOGGER);
            }

            // Loop over the categories.
            int numCategories = categoryIds.size();
            for (int i = 0; i < numCategories; i++) {
                LLValue categoryId = categoryIds.toValue(i);
                // Make sure we know what type of categoryId
                // object we have. There are also Workflow
                // category attributes which can't be read here.
                int categoryType = categoryId.toInteger("Type");
                if (LAPI_ATTRIBUTES.CATEGORY_TYPE_LIBRARY != categoryType) {
                    LOGGER.finer("Unknown category implementation type " +
                        categoryType + "; skipping"); 
                    continue;
                }
                //System.out.println(categoryId.toString("DisplayName")); 

                LLValue categoryVersion = new LLValue();
                if (documents.GetObjectAttributesEx(objectIdAssoc,
                        categoryId, categoryVersion) != 0) {
                    throw new LapiException(session, LOGGER);
                }
                LLValue attributeNames = new LLValue();
                if (attributes.AttrListNames(categoryVersion, null, 
                        attributeNames) != 0) {
                    throw new LapiException(session, LOGGER);
                }
                
                // Loop over the attributes for this category.
                int numAttributes = attributeNames.size();
                for (int j = 0; j < numAttributes; j++) {
                    String attributeName = attributeNames.toString(j);
                    LLValue attributeInfo = new LLValue();
                    if (attributes.AttrGetInfo(categoryVersion, attributeName,
                            null, attributeInfo) != 0) {
                        throw new LapiException(session, LOGGER);
                    }
                    int attributeType = attributeInfo.toInteger("Type");
                    if (LAPI_ATTRIBUTES.ATTR_TYPE_SET == attributeType) {
                        getAttributeSetValues(data, categoryVersion, 
                            attributeName); 
                    }
                    else {
                        getAttributeValue(data, categoryVersion,
                            attributeName, attributeType, null, attributeInfo);
                    }
                }
            }
        } catch (RuntimeException e) {
            throw new LapiException(e, LOGGER); 
        }
        return data; 
    }

    /**
     * Gets the values for attributes contained in an attribute set.
     *
     * @param data the object in which to store the values
     * @param categoryVersion the category being read
     * @param attributeName the name of the attribute set
     * @throws RepositoryException if an error occurs
     */
    private void getAttributeSetValues(Map data, LLValue categoryVersion,
            String attributeName) throws RepositoryException {
        // The "path" indicates the set attribute name to look
        // inside of in other methods like AttrListNames.
        LLValue attributeSetPath = new LLValue().setList(); 
        attributeSetPath.add(attributeName); 

        // Get a list of the names of the attributes in the
        // set. Look up and store the types to avoid repeating
        // the type lookup when there's more than one instance of
        // the attribute set.
        LLValue attributeSetNames = new LLValue(); 
        if (attributes.AttrListNames(categoryVersion, attributeSetPath,
                attributeSetNames) != 0) {
            throw new LapiException(session, LOGGER); 
        }
        LLValue[] attributeInfo = new LLValue[attributeSetNames.size()];
        for (int i = 0; i < attributeSetNames.size(); i++) {
            String name = attributeSetNames.toString(i); 
            LLValue info = new LLValue();
            if (attributes.AttrGetInfo(categoryVersion, name, 
                    attributeSetPath, info) != 0) {
                throw new LapiException(session, LOGGER);
            }
            attributeInfo[i] = info;
        }

        // List the values for the set attribute itself. There
        // may be multiple instances of the set.
        LLValue setValues = new LLValue();
        if (attributes.AttrGetValues(categoryVersion,
                attributeName, LAPI_ATTRIBUTES.ATTR_DATAVALUES,
                null, setValues) != 0) {
            throw new LapiException(session, LOGGER);
        }
        // Update the path to hold index of the set instance.
        attributeSetPath.setSize(2); 
        int numSets = setValues.size();
        for (int i = 0; i < numSets; i++) {
            attributeSetPath.setInteger(1, i); 
            // For each instance (row) of the attribute set, loop
            // over the attribute names.
            for (int j = 0; j < attributeSetNames.size(); j++) {
                int type = attributeInfo[j].toInteger("Type");
                if (LAPI_ATTRIBUTES.ATTR_TYPE_SET == type) {
                    LOGGER.finer("Nested attributes sets are not supported.");
                    continue;
                }
                //System.out.println("      " + attributeSetNames.toString(j));
                getAttributeValue(data, categoryVersion, 
                    attributeSetNames.toString(j), type,
                    attributeSetPath, attributeInfo[j]); 
            }
        }
    }

    /**
     * Gets the values for an attribute.
     *
     * @param data the object to hold the values
     * @param categoryVersion the category version in which the
     * values are stored 
     * @param attributeName the name of the attribute whose
     * values are being read
     * @param attributeType the type of the attribute data; may not be "SET"
     * @param attributeSetPath if the attribute is contained
     * within an attribute set, this is a list containing the set
     * name and set instance index; otherwise, this should be
     * null
     * throws RepositoryException if an error occurs
     */
    private void getAttributeValue(Map data, LLValue categoryVersion,
            String attributeName, int attributeType,
            LLValue attributeSetPath, LLValue attributeInfo)
            throws RepositoryException {

        if (LAPI_ATTRIBUTES.ATTR_TYPE_SET == attributeType)
            throw new IllegalArgumentException("attributeType = SET"); 
        // Skip attributes marked as not searchable.
        if (!attributeInfo.toBoolean("Search"))
            return;

        LLValue attributeValues = new LLValue();
        if (attributes.AttrGetValues(categoryVersion,
                attributeName, LAPI_ATTRIBUTES.ATTR_DATAVALUES,
                attributeSetPath, attributeValues) != 0) {
            throw new LapiException(session, LOGGER);
        }
        // Even a simple attribute type can have multiple values
        // (displayed as rows in the Livelink UI).
        int numValues = attributeValues.size();
        if (numValues == 0)
            return;

        LinkedList valueList = (LinkedList) data.get(attributeName);
        if (valueList == null) {
            valueList = new LinkedList(); 
            data.put(attributeName, valueList);
        }
        for (int k = 0; k < numValues; k++) {
            // Avoid errors if the attribute hasn't been set.
            int dataType = attributeValues.toValue(k).type(); 
            if (LLValue.LL_UNDEFINED == dataType ||
                    LLValue.LL_ERROR == dataType ||
                    LLValue.LL_NOTSET == dataType) {
                continue;
            }
            if (LAPI_ATTRIBUTES.ATTR_TYPE_USER == attributeType) {
                LLValue userInfo = new LLValue();
                if (users.GetUserOrGroupByID(
                        attributeValues.toInteger(k),
                        userInfo) != 0) {
                    throw new LapiException(session, LOGGER);
                }
                valueList.add(userInfo.toString("Name")); 
            } else {
                // TODO: what value object should we put here? A
                // CategoryValue, like the LivelinkValue?
                valueList.add(attributeValues.toValue(k).toString());
            }
        }
        // Don't return empty attribute value lists.
        if (valueList.size() == 0)
            data.remove(attributeName); 
    }

    /**
     * Gets the category attribute values for the indicated
     * object. Each attribute name is mapped to a linked list of
     * values for each occurrence of that attribute. Attributes
     * with the same name in different categories will have their
     * values merged into a single list. Attribute sets are
     * supported, but nested attribute sets are not. User
     * attributes are resolved into the user or group name.
     *
     * @param objId the object id of the Livelink item for which
     * to retrieve category information
     * @return a map of attribute names to lists of values
     * @throws RepositoryException if an error occurs
     */
    /* Alternate implementation of category attribute fetching
     * which uses the underlying Livelink database tables.
     */
    public Map getCategoryAttributes2(int objId) throws RepositoryException {
        HashMap results = new HashMap(); 
        try {
            String[] columns = { "ID", "DefID", "AttrId", "AttrType", 
                                 "EntryNum", "ValInt", "ValReal", 
                                 "ValDate", "ValStr", "ValLong" }; 
            ClientValue attributeData = ListNodes(
                "ID=" + objId + " order by DefId,AttrId,EntryNum",
                "LLAttrData", columns); 
            if (attributeData.size() == 0) {
                LOGGER.finest("No category data for " + objId); 
                return results;
            }
            Map attributeNames = getAttributeNames2(objId);
            for (int i = 0; i < attributeData.size(); i++) {
                String categoryId = attributeData.toString(i, "DefId");
                String attributeId = attributeData.toString(i, "AttrId");
                String attributeName = getAttributeName2(
                    attributeNames, categoryId, attributeId); 
                if (attributeName == null) {
                    if (LOGGER.isLoggable(Level.WARNING)) {
                        LOGGER.warning("No attribute name for cat/attr: " + 
                            categoryId + "/" + attributeId);
                    }
                    continue;
                }
                LinkedList valueList = getValueList2(results, attributeName); 
                int attributeType = attributeData.toInteger(i, "AttrType");

                // TODO: What type of object should be stored for
                // the data? We don't have access to the original
                // LLValue.
                switch (attributeType)
                {
                case LAPI_ATTRIBUTES.ATTR_TYPE_BOOL:
                    // The API methods don't return a value when
                    // a checkbox is unset, so here we check for
                    // "0" and don't add a value.
                    if (attributeData.isDefined(i, "ValInt")) {
                        int flag = attributeData.toInteger(i, "ValInt");
                        if (flag != 0)
                            valueList.add(attributeData.toString(i, "ValInt"));
                    }
                    break;
                case LAPI_ATTRIBUTES.ATTR_TYPE_DATE:
                case LAPI_ATTRIBUTES.ATTR_TYPE_DATEPOPUP:
                    if (attributeData.isDefined(i, "ValDate"))
                        valueList.add(attributeData.toString(i, "ValDate")); 
                    break;
                case LAPI_ATTRIBUTES.ATTR_TYPE_INT:
                case LAPI_ATTRIBUTES.ATTR_TYPE_INTPOPUP:
                    if (attributeData.isDefined(i, "ValInt"))
                        valueList.add(attributeData.toString(i, "ValInt")); 
                    break;
                case LAPI_ATTRIBUTES.ATTR_TYPE_STRFIELD:
                case LAPI_ATTRIBUTES.ATTR_TYPE_STRPOPUP:
                    if (attributeData.isDefined(i, "ValStr"))
                        valueList.add(attributeData.toString(i, "ValStr")); 
                    break;
                case LAPI_ATTRIBUTES.ATTR_TYPE_STRMULTI:
                    if (attributeData.isDefined(i, "ValLong"))
                        valueList.add(attributeData.toString(i, "ValLong")); 
                    break;
                case LAPI_ATTRIBUTES.ATTR_TYPE_USER:
                    if (attributeData.isDefined(i, "ValInt"))
                    {
                        LLValue userInfo = new LLValue();
                        int userId = attributeData.toInteger(i, "ValInt");
                        if (users.GetUserOrGroupByID(userId, userInfo) != 0) {
                            throw new LapiException(session, LOGGER);
                        }
                        valueList.add(userInfo.toString("Name"));
                    }
                    break;
                case LAPI_ATTRIBUTES.ATTR_TYPE_REAL:
                case LAPI_ATTRIBUTES.ATTR_TYPE_REALPOPUP:
                    if (attributeData.isDefined(i, "ValReal"))
                        valueList.add(attributeData.toString(i, "ValReal")); 
                    break;
                case LAPI_ATTRIBUTES.ATTR_TYPE_SET:
                default:
                    break;
                }
                if (valueList.size() == 0)
                    results.remove(attributeName); 
            }
        } catch (RuntimeException e) {
            throw new LapiException(e, LOGGER); 
        }
        return results; 
    }

    /**
     * Looks up the attribute name in the map built by getAttributeNames2.
     *
     * @param attributeNames the map of names
     * @param categoryId the category id
     * @param attributeId the attribute id
     * @return the attribute name, or null
     */
    private String getAttributeName2(Map attributeNames, String categoryId, 
            String attributeId) {
        String key = categoryId + "_" + attributeId; 
        return (String) attributeNames.get(key);
    }


    /**
     * Returns the list of values into which values for the given
     * attribute should be placed. Adds the list to the set of
     * results if it isn't present.
     *
     * @param results the attribute data results
     * @param attributeName the attribute name
     * @return a list for values
     */
    private LinkedList getValueList2(Map results, String attributeName)
    {
        LinkedList value = (LinkedList) results.get(attributeName);
        if (value == null)
        {
            value = new LinkedList();
            results.put(attributeName, value);
        }
        return value;
    }

    /**
     * Builds a map which maps category and attribute ids to
     * attribute names. The keys are of the form
     * "categoryId_attributeId".
     *
     * @param objId the object id
     * @return an attribute name map
     * @throws RepositoryException if an error occurs
     */
    /* The CatRegionMap table has a field called "RegionName"
     * which is of the form "Attr_" + categoryId + "_" +
     * attributeIndex. This method creates a map from keys of the
     * form categoryId + "_" + attributeIndex to the attribute names.
     */
    private Map getAttributeNames2(int objId) throws RepositoryException {
        String[] columns = { "CatId", "AttrName", "RegionName" }; 
        String query = "CatId in (select distinct DefId from LLAttrData " +
            "where ID = " + objId + ")";
        ClientValue namesRecArray = ListNodes(query, "CatRegionMap", columns); 
        HashMap attributeNames = new HashMap(); 
        for (int i = 0; i < namesRecArray.size(); i++) {
            String categoryId = namesRecArray.toString(i, "CatId");
            String region = namesRecArray.toString(i, "RegionName");
            int u = region.indexOf("_");
            if (u != -1) {
                String key = region.substring(u + 1);
                if (!key.matches("\\d+\\_\\d+")) {
                    LOGGER.warning("Unknown RegionName format: " + region);
                    continue;
                }
                if (namesRecArray.isDefined(i, "AttrName")) {
                    attributeNames.put(key, namesRecArray.toString(i,
                                           "AttrName"));
                }
                else
                    LOGGER.warning("No attribute name for " + region); 
            }
            else
                LOGGER.warning("Unknown RegionName format: " + region);
        }
        return attributeNames;
    }

    /**
     * Utility to return a printable name for an LLValue data type.
     *
     * @param type the type
     * @return the type name
     */
    private String llvalueTypeName(int type) {
        switch(type) {
        case LLValue.LL_ASSOC: return "ASSOC"; 
        case LLValue.LL_BOOLEAN: return "BOOLEAN"; 
        case LLValue.LL_DATE: return "DATE"; 
        case LLValue.LL_DOUBLE: return "DOUBLE"; 
        case LLValue.LL_ERROR: return "ERROR"; 
        case LLValue.LL_INTEGER: return "INTEGER"; 
        case LLValue.LL_LIST: return "LIST"; 
        case LLValue.LL_NOTSET: return "NOTSET"; 
        case LLValue.LL_RECORD: return "RECORD"; 
        case LLValue.LL_STRING: return "STRING"; 
        case LLValue.LL_TABLE: return "TABLE"; 
        case LLValue.LL_UNDEFINED: return "UNDEFINED"; 
        }
        return "UNKNOWN";
    }


    /**
     * Utility to return a printable name for an attribute type.
     *
     * @param type the type
     * @return the type name
     */
    private String attributeTypeName(int type) {
        switch (type) {
        case LAPI_ATTRIBUTES.ATTR_TYPE_BOOL: return "BOOL"; 
        case LAPI_ATTRIBUTES.ATTR_TYPE_DATE: return "DATE";
        case LAPI_ATTRIBUTES.ATTR_TYPE_DATEPOPUP: return "DATEPOPUP";
        case LAPI_ATTRIBUTES.ATTR_TYPE_INT: return "INT";
        case LAPI_ATTRIBUTES.ATTR_TYPE_INTPOPUP: return "INTPOPUP";
        case LAPI_ATTRIBUTES.ATTR_TYPE_STRFIELD: return "STRFIELD";
        case LAPI_ATTRIBUTES.ATTR_TYPE_STRMULTI: return "STRMULTI";
        case LAPI_ATTRIBUTES.ATTR_TYPE_STRPOPUP: return "STRPOPUP";
        case LAPI_ATTRIBUTES.ATTR_TYPE_USER: return "USER";
        case LAPI_ATTRIBUTES.ATTR_TYPE_REAL: return "REAL";
        case LAPI_ATTRIBUTES.ATTR_TYPE_REALPOPUP: return "REALPOPUP";
        default: return "OTHER TYPE"; 
        }
    }
}
