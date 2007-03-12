// Copyright (C) 2006-2007 Google Inc.

package com.google.enterprise.connector.otex.client.lapi;

import java.io.File;
import java.io.OutputStream;
import java.util.logging.Logger;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.otex.client.Client;
import com.google.enterprise.connector.otex.client.RecArray;

import com.opentext.api.LAPI_ATTRIBUTES;
import com.opentext.api.LAPI_DOCUMENTS;
import com.opentext.api.LAPI_USERS;
import com.opentext.api.LLSession;
import com.opentext.api.LLValue;

/**
 * A direct LAPI client implementation.
 */
final class LapiClient implements Client
{
    static {
        // Verify that the Client class constants are correct.
        assert TOPICSUBTYPE == LAPI_DOCUMENTS.TOPICSUBTYPE : TOPICSUBTYPE;
        assert REPLYSUBTYPE == LAPI_DOCUMENTS.REPLYSUBTYPE : REPLYSUBTYPE;
        assert TASKSUBTYPE == LAPI_DOCUMENTS.TASKSUBTYPE : TASKSUBTYPE;
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

    /**
     * {@inheritDoc} 
     * <p>
     * This implementation calls GetServerInfo to retrieve the server
     * encoding.
     */
    /* FIXME: Livelink 9.2 does not return the CharacterEncoding field. */
    public String getEncoding(Logger logger) throws RepositoryException {
        try {
            LLValue value = (new LLValue()).setAssocNotSet();
            if (documents.GetServerInfo(value) != 0)
                throw new LapiException(session, logger);
            int serverEncoding = value.toInteger("CharacterEncoding");
            if (serverEncoding == LAPI_DOCUMENTS.CHARACTER_ENCODING_UTF8)
                return "UTF-8";
            else
                return null;
        } catch (RuntimeException e) {
            throw new LapiException(e, logger);
        }
    }

    /** {@inheritDoc} */
    public synchronized String getLLCookie(Logger logger)
            throws RepositoryException {
        LLValue cookies = (new LLValue()).setList();
        try {
            if (users.GetCookieInfo(cookies) != 0)
                throw new LapiException(session, logger);
        } catch (RuntimeException e) {
            throw new LapiException(e, logger);
        }
        for (int i = 0; i < cookies.size(); i++) {
            LLValue cookie = cookies.toValue(i);
            if ("LLCookie".equalsIgnoreCase(cookie.toString("Name"))) {
                return cookie.toString("Value");
            }
        }
        // XXX: Or return null and have the caller do this?
        throw new RepositoryException("Missing LLCookie");
    }
    
    /** {@inheritDoc} */
    public synchronized RecArray ListNodes(Logger logger, String query,
            String view, String[] columns) throws RepositoryException {
        LLValue recArray = (new LLValue()).setTable();
        try {
            LLValue args = (new LLValue()).setList();
            LLValue columnsList = (new LLValue()).setList();
            for (int i = 0; i < columns.length; i++)
                columnsList.add(columns[i]);
            if (documents.ListNodes(query, args, view, columnsList, 
                    LAPI_DOCUMENTS.PERM_SEECONTENTS, LLValue.LL_FALSE,
                    recArray) != 0) {
                throw new LapiException(session, logger);
            }
        } catch (RuntimeException e) {
            throw new LapiException(e, logger);
        }
        return new LapiRecArray(logger, recArray);
    }

    /** {@inheritDoc} */
    public synchronized RecArray GetObjectInfo(final Logger logger,
            int volumeId, int objectId) throws RepositoryException {
        try {
            LLValue objectInfo = new LLValue();
            if (documents.GetObjectInfo(volumeId, objectId, objectInfo) != 0) {
                throw new LapiException(session, logger);
            }
            return new LapiRecArray(logger, objectInfo);
        } catch (RuntimeException e) {
            throw new LapiException(e, logger);
        }
    }
    
    /** {@inheritDoc} */
    public synchronized void FetchVersion(final Logger logger,
            int volumeId, int objectId, int versionNumber, File path)
            throws RepositoryException {
        try {
            if (documents.FetchVersion(volumeId, objectId, versionNumber,
                    path.getPath()) != 0) {
                throw new LapiException(session, logger);
            }
        } catch (RuntimeException e) {
            throw new LapiException(e, logger);
        }
    }

    /** {@inheritDoc} */
    public synchronized void FetchVersion(final Logger logger,
            int volumeId, int objectId, int versionNumber, OutputStream out)
            throws RepositoryException {
        try {
            if (documents.FetchVersion(volumeId, objectId,
                    versionNumber, out) != 0) { 
                throw new LapiException(session, logger);
            }
        } catch (RuntimeException e) {
            throw new LapiException(e, logger);
        }
    }


    /**
     * {@inheritDoc}
     */
    public synchronized void ImpersonateUser(final Logger logger, 
            String username) {
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
    public synchronized boolean ping(final Logger logger)
            throws RepositoryException {
        LLValue info = new LLValue().setAssocNotSet();
        try {
            // TODO: ensure that the LapiClient handles domains.
            if (documents.AccessEnterpriseWSEx(null, info) != 0)
                throw new LapiException(session, logger); 
        } catch (RuntimeException e) {
            throw new LapiException(e, logger); 
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
     * @param logger the logger
     * @param objId the object id of the Livelink item for which
     * to retrieve category information
     * @return a map of attribute names to lists of values
     * @throws RepositoryException if an error occurs
     */
    public Map getCategoryAttributes(Logger logger, int objId)
             throws RepositoryException {

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
            LLValue categoryIds = new LLValue().setList(); 
            if (documents.ListObjectCategoryIDs(objectIdAssoc,
                    categoryIds) != 0) {
                throw new LapiException(session, logger);
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
                    logger.finer("Unknown category implementation type " +
                        categoryType + "; skipping"); 
                    continue;
                }
                //System.out.println(categoryId.toString("DisplayName")); 

                LLValue categoryVersion = new LLValue().setAssocNotSet();
                if (documents.GetObjectAttributesEx(objectIdAssoc,
                        categoryId, categoryVersion) != 0) {
                    throw new LapiException(session, logger);
                }
                LLValue attributeNames = new LLValue().setList();
                if (attributes.AttrListNames(categoryVersion, null, 
                        attributeNames) != 0) {
                    throw new LapiException(session, logger);
                }
                
                // Loop over the attributes for this category.
                int numAttributes = attributeNames.size();
                for (int j = 0; j < numAttributes; j++) {
                    String attributeName = attributeNames.toString(j);
                    LLValue attributeInfo = new LLValue().setAssocNotSet();
                    if (attributes.AttrGetInfo(categoryVersion, attributeName,
                            null, attributeInfo) != 0) {
                        throw new LapiException(session, logger);
                    }
                    int attributeType = attributeInfo.toInteger("Type");
                    if (LAPI_ATTRIBUTES.ATTR_TYPE_SET == attributeType) {
                        getAttributeSetValues(logger, data, categoryVersion, 
                            attributeName); 
                    }
                    else {
                        getAttributeValue(logger, data, categoryVersion,
                            attributeName, attributeType, null, attributeInfo);
                    }
                }
            }
        } catch (RuntimeException e) {
            throw new LapiException(e, logger); 
        }
        return data; 
    }

    /**
     * Gets the values for attributes contained in an attribute set.
     *
     * @param logger the logger
     * @param data the object in which to store the values
     * @param categoryVersion the category being read
     * @param attributeName the name of the attribute set
     * @throws RepositoryException if an error occurs
     */
    private void getAttributeSetValues(Logger logger, Map data,
            LLValue categoryVersion, String attributeName)
            throws RepositoryException {
        
        // The "path" indicates the set attribute name to look
        // inside of in other methods like AttrListNames.
        LLValue attributeSetPath = new LLValue().setList(); 
        attributeSetPath.add(attributeName); 

        // Get a list of the names of the attributes in the
        // set. Look up and store the types to avoid repeating
        // the type lookup when there's more than one instance of
        // the attribute set.
        LLValue attributeSetNames = new LLValue().setList(); 
        if (attributes.AttrListNames(categoryVersion, attributeSetPath,
                attributeSetNames) != 0) {
            throw new LapiException(session, logger); 
        }
        LLValue[] attributeInfo = new LLValue[attributeSetNames.size()];
        for (int i = 0; i < attributeSetNames.size(); i++) {
            String name = attributeSetNames.toString(i); 
            LLValue info = new LLValue().setAssocNotSet();
            if (attributes.AttrGetInfo(categoryVersion, name, 
                    attributeSetPath, info) != 0) {
                throw new LapiException(session, logger);
            }
            attributeInfo[i] = info;
        }

        // List the values for the set attribute itself. There
        // may be multiple instances of the set.
        LLValue setValues = new LLValue().setList();
        if (attributes.AttrGetValues(categoryVersion,
                attributeName, LAPI_ATTRIBUTES.ATTR_DATAVALUES,
                null, setValues) != 0) {
            throw new LapiException(session, logger);
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
                    logger.finer("Nested attributes sets are not supported.");
                    continue;
                }
                //System.out.println("      " + attributeSetNames.toString(j));
                getAttributeValue(logger, data, categoryVersion, 
                    attributeSetNames.toString(j), type,
                    attributeSetPath, attributeInfo[j]); 
            }
        }
    }

    /**
     * Gets the values for an attribute.
     *
     * @param logger the logger
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
    private void getAttributeValue(Logger logger, Map data, 
            LLValue categoryVersion, String attributeName, int attributeType,
            LLValue attributeSetPath, LLValue attributeInfo)
            throws RepositoryException {

        if (LAPI_ATTRIBUTES.ATTR_TYPE_SET == attributeType)
            throw new IllegalArgumentException("attributeType = SET"); 
        // Skip attributes marked as not searchable.
        if (!attributeInfo.toBoolean("Search"))
            return;

        LLValue attributeValues = new LLValue().setList();
        if (attributes.AttrGetValues(categoryVersion,
                attributeName, LAPI_ATTRIBUTES.ATTR_DATAVALUES,
                attributeSetPath, attributeValues) != 0) {
            throw new LapiException(session, logger);
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
                LLValue userInfo = new LLValue().setRecord();
                if (users.GetUserOrGroupByID(
                        attributeValues.toInteger(k),
                        userInfo) != 0) {
                    throw new LapiException(session,
                        logger);
                }
                valueList.add(userInfo.toString("Name")); 
            } else {
                // TODO: what value object should we put here? A
                // CategoryValue, like the RecArrayValue?
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
     * @param logger the logger
     * @param objId the object id of the Livelink item for which
     * to retrieve category information
     * @return a map of attribute names to lists of values
     * @throws RepositoryException if an error occurs
     */
    /* Alternate implementation of category attribute fetching
     * which uses the underlying Livelink database tables.
     */
    public Map getCategoryAttributes2(Logger logger, int objId)
             throws RepositoryException {

        HashMap results = new HashMap(); 
        try {
            String[] columns = { "ID", "DefID", "AttrId", "AttrType", 
                                 "EntryNum", "ValInt", "ValReal", 
                                 "ValDate", "ValStr", "ValLong" }; 
            RecArray attributeData = ListNodes(logger, 
                "ID=" + objId + " order by DefId,AttrId,EntryNum",
                "LLAttrData", columns); 
            if (attributeData.size() == 0) {
                logger.finest("No category data for " + objId); 
                return results;
            }
            Map attributeNames = getAttributeNames2(logger, objId);
            for (int i = 0; i < attributeData.size(); i++) {
                String categoryId = attributeData.toString(i, "DefId");
                String attributeId = attributeData.toString(i, "AttrId");
                String attributeName = getAttributeName2(
                    attributeNames, categoryId, attributeId); 
                if (attributeName == null) {
                    logger.warning("No attribute name for cat/attr: " + 
                        categoryId + "/" + attributeId);
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
                        LLValue userInfo = new LLValue().setRecord();
                        int userId = attributeData.toInteger(i, "ValInt");
                        if (users.GetUserOrGroupByID(userId, userInfo) != 0) {
                            throw new LapiException(session, logger);
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
            throw new LapiException(e, logger); 
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
     * @param logger the logger
     * @param objId the object id
     * @return an attribute name map
     * @throws RepositoryException if an error occurs
     */
    /* The CatRegionMap table has a field called "RegionName"
     * which is of the form "Attr_" + categoryId + "_" +
     * attributeIndex. This method creates a map from keys of the
     * form categoryId + "_" + attributeIndex to the attribute names.
     */
    private Map getAttributeNames2(Logger logger, int objId)
            throws RepositoryException {
        String[] columns = { "CatId", "AttrName", "RegionName" }; 
        String query = "CatId in (select distinct DefId from LLAttrData " +
            "where ID = " + objId + ")";
        RecArray namesRecArray = ListNodes(logger, query, "CatRegionMap",
            columns); 
        HashMap attributeNames = new HashMap(); 
        for (int i = 0; i < namesRecArray.size(); i++) {
            String categoryId = namesRecArray.toString(i, "CatId");
            String region = namesRecArray.toString(i, "RegionName");
            int u = region.indexOf("_");
            if (u != -1) {
                String key = region.substring(u + 1);
                if (!key.matches("\\d+\\_\\d+")) {
                    logger.warning("Unknown RegionName format: " + region);
                    continue;
                }
                if (namesRecArray.isDefined(i, "AttrName")) {
                    attributeNames.put(key, namesRecArray.toString(i,
                                           "AttrName"));
                }
                else
                    logger.warning("No attribute name for " + region); 
            }
            else
                logger.warning("Unknown RegionName format: " + region);
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
