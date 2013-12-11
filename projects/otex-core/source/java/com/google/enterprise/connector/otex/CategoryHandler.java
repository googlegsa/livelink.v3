// Copyright 2007 Google Inc.
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

import com.google.common.annotations.VisibleForTesting;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.otex.client.Client;
import com.google.enterprise.connector.otex.client.ClientValue;
import com.google.enterprise.connector.otex.client.ClientValueFactory;

import java.util.HashSet;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Collects the category attribute values for an object. Each
 * attribute name is mapped to a list of values for each occurrence of
 * that attribute. Attributes with the same name in different
 * categories will have their values merged into a single list.
 * Attribute sets are supported, but nested attribute sets are not.
 * User attributes are resolved into the user or group name.
 *
 */
class CategoryHandler {
  /** The logger for this class. */
  private static final Logger LOGGER =
      Logger.getLogger(CategoryHandler.class.getName());

  /** The connector contains configuration information. */
  private final LivelinkConnector connector;

  /** The client provides access to the server. */
  private final Client client;

  /** The concrete ClientValue implementation associated with this Client. */
  private final ClientValueFactory valueFactory;

  /** The set of categories to include. */
  private HashSet<Object> includedCategories;

  /** The set of categories to exclude. */
  private HashSet<Object> excludedCategories;

  /** Whether to even bother with Category attributes? */
  private final boolean doCategories;

  /** Index only category attributes that are marked searchable? */
  private final boolean includeSearchable;

  /** Include category names as properties? */
  private final boolean includeCategoryNames;

  /**
   * Cache of category object IDs that have a category version with an
   * AttrInfo that does not have a "Search" field. We cannot check
   * whether this attributes are searchable, so we assume that they
   * should be indexed.
   */
  @VisibleForTesting
  HashSet<Integer> searchableCache = null;

  /**
   * Constructs a category handler. This object is specific a
   * Connector instance, but not to a Document or DocumentList.
   */
  CategoryHandler(LivelinkConnector connector, Client client)
      throws RepositoryException { 
    this.connector = connector;
    this.client = client;
    this.valueFactory = client.getClientValueFactory();

    // Fetch the set of categories to include and exclude.
    this.includedCategories = connector.getIncludedCategories();
    this.excludedCategories = connector.getExcludedCategories();

    // Should we index any Category attributes at all?
    this.doCategories = !(includedCategories.contains("none") ||
        excludedCategories.contains("all"));

    // Set qualifiers on the included category attributes.
    this.includeSearchable = includedCategories.contains("searchable");
    this.includeCategoryNames = includedCategories.contains("name");

    // If we index all Categories, don't bother searching the set,
    // as it will only slow us down.
    if (includedCategories.contains("all"))
      includedCategories = null;

    // If we exclude no Categories, don't bother searching the set,
    // as it will only slow us down.
    if (excludedCategories.contains("none"))
      excludedCategories = null;
  }

  /**
   * Gets the category attribute values for the indicated
   * object.
   *
   * @param objectId the object ID
   * @param nameHandler a handler that maps user IDs to user names
   * @param props the collection of all document properties to add the
   * category attribute values to
   * @throws RepositoryException if an error occurs
   */
  public void collectCategoryAttributes(int objectId,
      UserNameHandler nameHandler, LivelinkDocument props)
      throws RepositoryException {
    if (doCategories == false)
      return;

    // List the categories. LAPI requires us to use this
    // Assoc containing the id instead of just passing in
    // the id. The Assoc may have two other values, Type,
    // which specifies the kind of object being looked up
    // (there's only one legal value currently) and
    // Version, which specifies which version of the
    // object to use (the default is the current
    // version).
    ClientValue objIdAssoc = valueFactory.createAssoc();
    objIdAssoc.add("ID", objectId);
    ClientValue categoryIds = client.ListObjectCategoryIDs(objIdAssoc);

    // Loop over the categories.
    int numCategories = categoryIds.size();
    for (int i = 0; i < numCategories; i++) {
      ClientValue categoryId = categoryIds.toValue(i);

      // If this Category is not in the included list, or it is
      // explicitly mentioned in the excluded list, then skip it.
      Integer id = new Integer(categoryId.toInteger("ID"));
      if (((includedCategories != null) &&
              !includedCategories.contains(id)) ||
          ((excludedCategories != null) &&
              excludedCategories.contains(id)))
        continue;

      // Make sure we know what type of categoryId
      // object we have. There are also Workflow
      // category attributes which can't be read here.
      int categoryType = categoryId.toInteger("Type");
      if (Client.CATEGORY_TYPE_LIBRARY != categoryType) {
        if (LOGGER.isLoggable(Level.FINER)) {
          LOGGER.finer("Unknown category implementation type " +
              categoryType + "; skipping");
        }
        continue;
      }

      if (includeCategoryNames) {
        // XXX: This is the only property name that is
        // hard-coded here like this. I think that's OK,
        // because the recarray fields are just hard-coded
        // someplace else. "Category" is an ObjectInfo
        // attribute name that is unused in Livelink 9.0 or
        // later.
        ClientValue name = categoryId.toValue("DisplayName");
        if (name.hasValue())
          props.addProperty("Category", name);
      }

      ClientValue categoryVersion =
          client.GetObjectAttributesEx(objIdAssoc, categoryId);
      ClientValue attrNames =
          client.AttrListNames(categoryVersion, null);

      // Loop over the attributes for this category.
      int numAttributes = attrNames.size();
      for (int j = 0; j < numAttributes; j++) {
        String attrName = attrNames.toString(j);
        ClientValue attrInfo =
            client.AttrGetInfo(categoryVersion, attrName, null);
        int attrType = attrInfo.toInteger("Type");
        if (Client.ATTR_TYPE_SET == attrType) {
          getAttributeSetValues(nameHandler, props, id, categoryVersion,
              attrName);
        } else {
          getAttributeValue(nameHandler, props, id, categoryVersion,
              attrName, attrType, null, attrInfo);
        }
      }
    }
  }

  /**
   * Gets the values for attributes contained in an attribute set.
   *
   * @param nameHandler a handler that maps user IDs to user names
   * @param props the collection of all document properties to add the
   * @param id the category object ID for use as a cache index
   * @param categoryVersion the category being read
   * @param attributeName the name of the attribute set
   * @throws RepositoryException if an error occurs
   */
  private void getAttributeSetValues(UserNameHandler nameHandler,
      LivelinkDocument props, Integer id, ClientValue categoryVersion,
      String attrName) throws RepositoryException {
    // The "path" indicates the set attribute name to look
    // inside of in other methods like AttrListNames.
    ClientValue attrSetPath = valueFactory.createList();
    attrSetPath.add(attrName);

    // Get a list of the names of the attributes in the
    // set. Look up and store the types to avoid repeating
    // the type lookup when there's more than one instance of
    // the attribute set.
    ClientValue attrSetNames =
        client.AttrListNames(categoryVersion, attrSetPath);

    ClientValue[] attrInfo = new ClientValue[attrSetNames.size()];
    for (int i = 0; i < attrSetNames.size(); i++) {
      String name = attrSetNames.toString(i);
      attrInfo[i] = client.AttrGetInfo(categoryVersion, name, attrSetPath);
    }

    // List the values for the set attribute itself. There
    // may be multiple instances of the set.
    ClientValue setValues =
        client.AttrGetValues(categoryVersion, attrName, null);

    // Update the path to hold index of the set instance.
    attrSetPath.setSize(2);
    int numSets = setValues.size();
    for (int i = 0; i < numSets; i++) {
      attrSetPath.setInteger(1, i);
      // For each instance (row) of the attribute set, loop
      // over the attribute names.
      for (int j = 0; j < attrSetNames.size(); j++) {
        int type = attrInfo[j].toInteger("Type");
        if (Client.ATTR_TYPE_SET == type) {
          LOGGER.finer("Nested attributes sets are not supported.");
          continue;
        }
        //System.out.println("      " + attrSetNames.toString(j));
        getAttributeValue(nameHandler, props, id, categoryVersion,
            attrSetNames.toString(j), type, attrSetPath, attrInfo[j]);
      }
    }
  }

  /**
   * Gets the values for an attribute.
   *
   * @param nameHandler a handler that maps user IDs to user names
   * @param props the collection of all document properties to add the
   * @param id the category object ID for use as a cache index
   * @param categoryVersion the category version in which the
   *     values are stored
   * @param attributeName the name of the attribute whose
   *     values are being read
   * @param attributeType the type of the attribute data; may
   *     not be "SET"
   * @param attributeSetPath if the attribute is contained
   *     within an attribute set, this is a list containing the set
   *     name and set instance index; otherwise, this should be
   *     null
   * throws RepositoryException if an error occurs
   */
  @VisibleForTesting
  void getAttributeValue(UserNameHandler nameHandler,
      LivelinkDocument props, Integer id, ClientValue categoryVersion,
      String attrName, int attrType, ClientValue attrSetPath,
      ClientValue attrInfo) throws RepositoryException {
    if (Client.ATTR_TYPE_SET == attrType)
      throw new IllegalArgumentException("attrType = SET");

    // Skip attributes that are marked as not searchable.
    if (includeSearchable) {
      if (searchableCache != null && searchableCache.contains(id)) {
        if (LOGGER.isLoggable(Level.FINEST)) {
          LOGGER.finest("Including " + attrName + " from category ID " + id);
        }
      } else {
        try {
          if (!attrInfo.toBoolean("Search")) {
            return;
          }
        } catch (RepositoryException e) {
          // Categories created under old versions of Livelink do not
          // have a Search attribute. Cache the category ID and log an
          // explanation.
          if (searchableCache == null)
            searchableCache = new HashSet<Integer>();
          searchableCache.add(id);

          if (LOGGER.isLoggable(Level.WARNING)) {
            LOGGER.warning("Marking category ID " + id +
                " as searchable after " + e.getMessage());
          }
        }
      }
    }

    //System.out.println("getAttributeValue: attrName = " + attrName);

    ClientValue attrValues =
        client.AttrGetValues(categoryVersion, attrName, attrSetPath);

    // Even a simple attribute type can have multiple values
    // (displayed as rows in the Livelink UI).
    int numValues = attrValues.size();
    if (numValues == 0)
      return;

    // System.out.println("getAttributeValue: numValues = " +
    // numValues);

    for (int k = 0; k < numValues; k++) {
      ClientValue value = attrValues.toValue(k);
      // Avoid errors if the attribute hasn't been set.
      if (!value.hasValue())
        continue;
      // System.out.println("getAttributeValue: k = " + k +
      // " ; value = " + value.toString2());
      if (Client.ATTR_TYPE_USER == attrType)
        nameHandler.addUserByName(attrName, value, props);
      else
        props.addProperty(attrName, value);
    }
  }
}
