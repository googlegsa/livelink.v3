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

import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.TimeZone;

import com.google.enterprise.connector.spi.Property;
import com.google.enterprise.connector.spi.PropertyMap;
import com.google.enterprise.connector.spi.PropertyMapList;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.SimpleValue;
import com.google.enterprise.connector.spi.SpiConstants;
import com.google.enterprise.connector.spi.Value;
import com.google.enterprise.connector.spi.ValueType;
import com.google.enterprise.connector.otex.client.Client;
import com.google.enterprise.connector.otex.client.ClientValue;
import com.google.enterprise.connector.otex.client.ClientValueFactory;

class LivelinkAttributes {

    /** The logger for this class. */
    private static final Logger LOGGER =
        Logger.getLogger(LivelinkAttributes.class.getName());

    /** The connector contains configuration information. */
    private final LivelinkConnector connector;

    /** The client provides access to the server. */
    private final Client client;

    /** A concrete strategy for retrieving the content from the server. */
    private final ContentHandler contentHandler;
    
    /** The concrete ClientValue implementation associated with this Client */
    private final ClientValueFactory valueFactory;

    LivelinkAttributes(LivelinkConnector connector, Client client,
                       ContentHandler contentHandler) throws RepositoryException
    {
        this.connector = connector;
        this.client = client;
        this.contentHandler = contentHandler;
        this.valueFactory = client.getClientValueFactory();
    }


    LivelinkAttributes(LivelinkConnector connector, Client client)
        throws RepositoryException
    {
        this.connector = connector;
        this.client = client;
        this.contentHandler = null;	// [??? - bmj] need this?
        this.valueFactory = client.getClientValueFactory();
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

        // List the categories. LAPI requires us to use this
        // Assoc containing the id instead of just passing in
        // the id. The Assoc may have two other values, Type,
        // which specifies the kind of object being looked up
        // (there's only one legal value currently) and
        // Version, which specifies which version of the
        // object to use (the default is the current
        // version).
        ClientValue objectIdAssoc = valueFactory.createAssoc();
        objectIdAssoc.add("ID", objId); 
        
        ClientValue categoryIds = client.ListObjectCategoryIDs(objectIdAssoc);
        
        // Loop over the categories.
        int numCategories = categoryIds.size();
        for (int i = 0; i < numCategories; i++) {
            ClientValue categoryId = categoryIds.toValue(i);
            // Make sure we know what type of categoryId
            // object we have. There are also Workflow
            // category attributes which can't be read here.
            int categoryType = categoryId.toInteger("Type");
            if (Client.CATEGORY_TYPE_LIBRARY != categoryType) {
                LOGGER.finer("Unknown category implementation type " +
                             categoryType + "; skipping"); 
                continue;
            }
            //System.out.println(categoryId.toString("DisplayName")); 
            
            ClientValue categoryVersion;
            categoryVersion = client.GetObjectAttributesEx(objectIdAssoc, categoryId);
            ClientValue attributeNames = client.AttrListNames(categoryVersion, null);
            
            // Loop over the attributes for this category.
            int numAttributes = attributeNames.size();
            for (int j = 0; j < numAttributes; j++) {
                String attributeName = attributeNames.toString(j);
                ClientValue attributeInfo = client.AttrGetInfo(categoryVersion,
                                                               attributeName, null);
                int attributeType = attributeInfo.toInteger("Type");
                if (Client.ATTR_TYPE_SET == attributeType) {
                    getAttributeSetValues(data, categoryVersion, 
                                          attributeName); 
                }
                else {
                    getAttributeValue(data, categoryVersion,
                                      attributeName, attributeType,
                                      null, attributeInfo);
                }
            }
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
    private void getAttributeSetValues(Map data,
                                       ClientValue categoryVersion,
                                       String attributeName)
        throws RepositoryException {

        // The "path" indicates the set attribute name to look
        // inside of in other methods like AttrListNames.
        ClientValue attributeSetPath = valueFactory.createList();
        attributeSetPath.add(attributeName); 

        // Get a list of the names of the attributes in the
        // set. Look up and store the types to avoid repeating
        // the type lookup when there's more than one instance of
        // the attribute set.
        ClientValue attributeSetNames = client.AttrListNames(categoryVersion,
                                                             attributeSetPath);

        ClientValue[] attributeInfo = new ClientValue[attributeSetNames.size()];
        for (int i = 0; i < attributeSetNames.size(); i++) {
            String name = attributeSetNames.toString(i); 
            attributeInfo[i] = client.AttrGetInfo(categoryVersion,
                                                  name, attributeSetPath);
        }

        // List the values for the set attribute itself. There
        // may be multiple instances of the set.
        ClientValue setValues = client.AttrGetValues(categoryVersion,
                                                     attributeName, null);

        // Update the path to hold index of the set instance.
        attributeSetPath.setSize(2); 
        int numSets = setValues.size();
        for (int i = 0; i < numSets; i++) {
            attributeSetPath.setInteger(1, i); 
            // For each instance (row) of the attribute set, loop
            // over the attribute names.
            for (int j = 0; j < attributeSetNames.size(); j++) {
                int type = attributeInfo[j].toInteger("Type");
                if (Client.ATTR_TYPE_SET == type) {
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
    private void getAttributeValue(Map data, ClientValue categoryVersion,
            String attributeName, int attributeType,
            ClientValue attributeSetPath, ClientValue attributeInfo)
            throws RepositoryException {

        if (Client.ATTR_TYPE_SET == attributeType)
            throw new IllegalArgumentException("attributeType = SET"); 
        // Skip attributes marked as not searchable.
        if (!attributeInfo.toBoolean("Search"))
            return;

        //System.out.println("getAttributeValue: attributeName = " + attributeName);

        ClientValue attributeValues = client.AttrGetValues(categoryVersion,
                                                           attributeName,
                                                           attributeSetPath);
        
        // Even a simple attribute type can have multiple values
        // (displayed as rows in the Livelink UI).
        int numValues = attributeValues.size();
        if (numValues == 0)
            return;

        // System.out.println("getAttributeValue: numValues = " + numValues);

        LinkedList valueList = (LinkedList) data.get(attributeName);
        if (valueList == null) {
            valueList = new LinkedList(); 
            data.put(attributeName, valueList);
        }
        for (int k = 0; k < numValues; k++) {
            ClientValue value = attributeValues.toValue(k);
            // Avoid errors if the attribute hasn't been set.
            if (!value.hasValue())
                continue;
            // System.out.println("getAttributeValue: k = " + k + " ; value = " + value.toString2());
            if (Client.ATTR_TYPE_USER == attributeType) {
                int userId = value.toInteger();
                ClientValue userInfo = client.GetUserOrGroupByID(userId);
                valueList.add(userInfo.toValue("Name")); 
            } else {
                // TODO: what value object should we put here? A
                // CategoryValue, like the LivelinkValue?
                valueList.add(value);
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
            ClientValue attributeData = client.ListNodes(
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
                case Client.ATTR_TYPE_BOOL:
                    // The API methods don't return a value when
                    // a checkbox is unset, so here we check for
                    // "0" and don't add a value.
                    if (attributeData.isDefined(i, "ValInt")) {
                        int flag = attributeData.toInteger(i, "ValInt");
                        if (flag != 0)
                            valueList.add(attributeData.toString(i, "ValInt"));
                    }
                    break;
                case Client.ATTR_TYPE_DATE:
                case Client.ATTR_TYPE_DATEPOPUP:
                    if (attributeData.isDefined(i, "ValDate"))
                        valueList.add(attributeData.toString(i, "ValDate")); 
                    break;
                case Client.ATTR_TYPE_INT:
                case Client.ATTR_TYPE_INTPOPUP:
                    if (attributeData.isDefined(i, "ValInt"))
                        valueList.add(attributeData.toString(i, "ValInt")); 
                    break;
                case Client.ATTR_TYPE_STRFIELD:
                case Client.ATTR_TYPE_STRPOPUP:
                    if (attributeData.isDefined(i, "ValStr"))
                        valueList.add(attributeData.toString(i, "ValStr")); 
                    break;
                case Client.ATTR_TYPE_STRMULTI:
                    if (attributeData.isDefined(i, "ValLong"))
                        valueList.add(attributeData.toString(i, "ValLong")); 
                    break;
                case Client.ATTR_TYPE_USER:
                    if (attributeData.isDefined(i, "ValInt"))
                    {
                        int userId = attributeData.toInteger(i, "ValInt");
                        ClientValue userInfo = client.GetUserOrGroupByID(userId);
                        valueList.add(userInfo.toString("Name"));
                    }
                    break;
                case Client.ATTR_TYPE_REAL:
                case Client.ATTR_TYPE_REALPOPUP:
                    if (attributeData.isDefined(i, "ValReal"))
                        valueList.add(attributeData.toString(i, "ValReal")); 
                    break;
                case Client.ATTR_TYPE_SET:
                default:
                    break;
                }
                if (valueList.size() == 0)
                    results.remove(attributeName); 
            }
        } catch (RuntimeException e) {
            throw new RepositoryException(e); 
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
        ClientValue namesRecArray = client.ListNodes(query, "CatRegionMap", columns); 
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

}
