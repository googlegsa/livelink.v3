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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.TimeZone;

import com.google.enterprise.connector.spi.AuthorizationManager;
import com.google.enterprise.connector.spi.Document;
import com.google.enterprise.connector.spi.DocumentList;
import com.google.enterprise.connector.spi.Property;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.SpiConstants;
import com.google.enterprise.connector.spi.TraversalContext;
import com.google.enterprise.connector.spi.Value;
import com.google.enterprise.connector.otex.client.Client;
import com.google.enterprise.connector.otex.client.ClientFactory;
import com.google.enterprise.connector.otex.client.ClientValue;
import com.google.enterprise.connector.otex.client.ClientValueFactory;

/**
 * This result set implementation could be trivial, but is not. Since
 * a <code>DocumentList</code> in this implementation is a wrapper on a
 * recarray, we need the recarray and other things in the
 * <code>DocumentList</code>, <code>Document</code>,
 * <code>Property</code>, and <code>Value</code> implementations,
 * along with the associated <code>Iterator</code> implementations. So
 * we use inner classes to share the necessary objects from this
 * outermost class.
 */
class LivelinkDocumentList implements DocumentList {
    /** The logger for this class. */
    private static final Logger LOGGER =
        Logger.getLogger(LivelinkDocumentList.class.getName());

    /** An immutable string value of "text/html". */
    private static final Value VALUE_TEXT_HTML =
        Value.getStringValue("text/html");

    /** An immutable false value. */
    private static final Value VALUE_FALSE =
        Value.getBooleanValue(false);

    /** An immutable true value. */
    private static final Value VALUE_TRUE =
        Value.getBooleanValue(true);

    /** The connector contains configuration information. */
    private final LivelinkConnector connector;

    /** The client provides access to the server. */
    private final Client client;

    /** A concrete strategy for retrieving the content from the server. */
    private final ContentHandler contentHandler;

    /** The concrete ClientValue implementation associated with this Client. */
    private final ClientValueFactory valueFactory;

    /** The table of Livelink data, one row per doc, one column per field. */
    private final ClientValue recArray;

    /** The recarray fields. */
    private final Field[] fields;

    /** The TraversalContext from TraversalContextAware Interface */
    private final TraversalContext traversalContext;

    /** If the traversal client user is the public content user,
     *  then all documents it sees will be publicly available.
     */
    private boolean isPublicContentUser = false;

    /** This subset of documents are authorized as public content. */
    private HashSet publicContentDocs = null;

    /** This is the checkpoint string for the current DocumentList */
    private String chkpoint;

    /** This is the DocumentList Iterator */
    private Iterator docIterator;

    LivelinkDocumentList(LivelinkConnector connector, Client client,
            ContentHandler contentHandler, ClientValue recArray,
            Field[] fields, TraversalContext traversalContext,
            String checkpoint) throws RepositoryException {

        this.connector = connector;
        this.client = client;
        this.valueFactory = client.getClientValueFactory();
        this.contentHandler = contentHandler;
        this.recArray = recArray;
        this.fields = fields;
        this.traversalContext = traversalContext;
        this.chkpoint = checkpoint;

        // Subset the docIds in the recArray into Public and Private Docs.
        findPublicContent();

        // Prime the DocumentList.nextDocument() iterator
        docIterator = iterator();
    }

    /**
     * {@inheritDoc}
     */
    public Document nextDocument() throws RepositoryException {
        return (docIterator.hasNext()) ? (Document) docIterator.next() : null;
    }
        
    /**
     * {@inheritDoc}
     */
    public String checkpoint() throws RepositoryException {
        return chkpoint;
    }

    /**
     * Generate a checkpoint string from a date and an object Id.
     *
     * @param date the date at which to start
     * @param objectId the object id at which to start
     * @return the checkpoint string.
     */
    public void setCheckpoint(Date date, int objectId)
    {
        chkpoint = LivelinkDateFormat.getInstance().toSqlString(date) +
            ',' + objectId;
    }


    /**
     * If we have a Public Content User specified, some of the
     * documents in the repository may be available to the public.
     * If the current user *is* the public content user, then all
     * documents in the recArray are by definition available to the
     * public.  However if the current user is not the public user,
     * we must subset the documents into those that are public and
     * those that are not.
     *
     * NOTE: This gets access to the AuthorizationManager via the
     * Connector's ClientFactory.  It should really get it from the
     * Session, but at this point we don't know what session we
     * belong to.
     */
    private void findPublicContent() throws RepositoryException {
        String pcuser = connector.getPublicContentUsername();
        if ((pcuser != null) && (pcuser.length() > 0)) {
            isPublicContentUser = connector.getUsername().equals(pcuser);
            if (! isPublicContentUser) {
                // Get the subset of the DocIds that have public access.
                ClientFactory clientFactory = connector.getClientFactory();
                LivelinkAuthorizationManager authz;
                authz = new LivelinkAuthorizationManager(clientFactory);
                publicContentDocs = new HashSet();
                authz.addAuthorizedDocids(new DocIdIterator(), pcuser,
                                          publicContentDocs);

                // We only care if there actually are public docs in this batch.
                if (publicContentDocs.isEmpty())
                    publicContentDocs = null;
            }
        }
    }


    /**
     * This iterates over the DocIDs in the recArray.
     */
    private class DocIdIterator implements Iterator {
        /** The current row of the recarray. */
        private int row;

        /** The size of the recarray. */
        private final int size;

        public DocIdIterator() {
            this.row = 0;
            this.size = recArray.size();
        }

        public boolean hasNext() {
            return (row < size);
        }

        public Object next() {
            if (row < size) {
                try {
                    return recArray.toString(row++, "DataID");
                } catch (RepositoryException e) {
                    LOGGER.warning("LivelinkDocumentList.DocIdIterator.next()" +
                                   "caught exception - " + e.getMessage());
                }
            }
            return null;
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
    }



    /** {@inheritDoc} */
    public Iterator iterator() {
        return new LivelinkDocumentListIterator();
    }


    /**
     * Iterates over a <code>DocumentList</code>, returning each
     * <code>Document</code> it contains.
     */
    private class LivelinkDocumentListIterator implements Iterator {
        /** The current row of the recarray. */
        private int row;

        /** The size of the recarray. */
        private final int size;

        /** The object ID of the current row. */
        private int objectId;

        /** The volume ID of the current row. */
        private int volumeId;

        /** The Document Properties associated with the current row. */
        private LivelinkDocument props;

        LivelinkDocumentListIterator() {
            this.row = 0;
            this.size = recArray.size();
        }

        public boolean hasNext() {
            return (row < size);
        }

        public Object next() {
            if (row < size) {

                try {
                    objectId = recArray.toInteger(row, "DataID");
                    volumeId = recArray.toInteger(row, "OwnerID");
                    props = new LivelinkDocument(objectId, fields.length*2);

                    /* Establish the checkpoint string for this row.
                     * NOTE: This assumes that there is not more than one active
                     * iterator on the docList.  In the SPI world that is true.
                     * however the tests iterate over the docList several times.
                     */
                    setCheckpoint(recArray.toDate(row, "ModifyDate"), objectId);

                    /* collect the various properties for this row */
                    collectRecArrayProperties();
                    collectDerivedProperties();
                    collectCategoryAttributes();

                    row++;
                    return props;
                } catch (RepositoryException e) {
                    throw new RuntimeException(e);
                }
            } else
                throw new NoSuchElementException();
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }


        /** Collects the recarray-based properties. */
        /*
         * TODO: Undefined values will not be added to the property
         * map. We may want some other value, possibly different
         * default values for each column (e.g., MimeType =
         * "application/octet-stream").
         */
        private void collectRecArrayProperties() throws RepositoryException {
            for (int i = 0; i < fields.length; i++) {
                if (fields[i].propertyNames.length > 0) {
                    ClientValue value =
                        recArray.toValue(row, fields[i].fieldName);
                    if (value.isDefined()) {
                        // FIXME: This is a hack. See Field for how
                        // this is set. When more user IDs might be
                        // returned, we need to fix this.
                        if (fields[i].isUserId) {
                            ClientValue userInfo =
                                client.GetUserOrGroupByID(value.toInteger());
                            ClientValue userName = userInfo.toValue("Name");
                            if (userName.isDefined())
                                props.addProperty(fields[i], userName);
                            else if (LOGGER.isLoggable(Level.WARNING)) {
                                LOGGER.warning(
                                    "No username found for user ID " +
                                    value.toInteger());
                            }
                        } else
                            props.addProperty(fields[i], value);

                    }
                }
            }
        }

        /** Collects additional properties derived from the recarray. */
        private void collectDerivedProperties() throws RepositoryException {

            // Flag the document as publicly accessable (or not).
            boolean isPublic = (isPublicContentUser) ? true :
                               ((publicContentDocs == null) ? false :
                                publicContentDocs.contains(
                                                  Integer.toString(objectId)));

            props.addProperty(SpiConstants.PROPNAME_ISPUBLIC,
                              ((isPublic)? VALUE_TRUE : VALUE_FALSE));

            int subType = recArray.toInteger(row, "SubType");

            // Fetch the content.  Returns null if no content.
            Value contentValue = collectContentProperty(subType);

            // FIXME: Until the GSA pays attention to the Name property...
            // The default name of a document for the GSA is the quite
            // uninformative URL.  Since we know the name of this item,
            // even though it has no content, push a Title tag so that
            // the GSA can at least display a meaningful name.
            if (contentValue == null) {
                String name = recArray.toString(row, "Name");
                props.addProperty(SpiConstants.PROPNAME_MIMETYPE,
                    VALUE_TEXT_HTML);
                props.addProperty(SpiConstants.PROPNAME_CONTENT,
                    Value.getStringValue("<title>" + name + "</title>\n"));
            }

            // Add the ExtendedData as MetaData properties.
            collectExtendedDataProperties(subType);

            // DISPLAYURL
            String url = connector.getDisplayUrl(subType, objectId, volumeId);
            props.addProperty(SpiConstants.PROPNAME_DISPLAYURL,
                              Value.getStringValue(url));
        }

        /**
         * Collect the content property for the item.
         * If the item does not (or must not) have content, then no
         * content property is generated.  If the item's content is
         * not acceptable according to the TraversalContext, then no
         * content is generated.  The content property's value is
         * inserted into the property map, but is also returned.
         *
         * @param subType the subtype of the item
         * @returns content Value object, null if no content
         */
        private Value collectContentProperty(int subType)
                throws RepositoryException {
            if (LOGGER.isLoggable(Level.FINER))
                LOGGER.finer("CONTENT WITH SUBTYPE = " + subType);

            // DataSize is the only non-nullable column from
            // DVersData that appears in the WebNodes view,
            // but there are cases (such as categories) where
            // there are rows in DVersData but FetchVersion
            // fails. So we're guessing here that if MimeType
            // is non-null then there should be a blob.
            ClientValue mimeType = recArray.toValue(row, "MimeType");
            if (!mimeType.isDefined())
                return null;

            // XXX: This value might be wrong. There are
            // data size callbacks which can change this
            // value. For example, the value returned by
            // GetObjectInfo may be different than the
            // value retrieved from the database.
            int size = recArray.toInteger(row, "DataSize");
            if (size <= 0)
                return null;

            // The TraversalContext Interface provides additional
            // screening based upon content size and mimetype.
            if (traversalContext != null) {
                // Is the content too large?
                if (((long) size) > traversalContext.maxDocumentSize())
                    return null;

                // Is this MimeType supported?
                String mt = mimeType.toString();
                if (traversalContext.mimeTypeSupportLevel(mt) <= 0)
                    return null;
            }

            // If we pass the gauntlet, create a content stream property
            // and add it to the property map.
            InputStream is =
                contentHandler.getInputStream(volumeId, objectId, 0, size);
            Value contentValue = Value.getBinaryValue(is);
            props.addProperty(SpiConstants.PROPNAME_CONTENT, contentValue);
            return contentValue;
        }

        /**
         * Collect properties from the ExtendedData assoc.
         *
         * @param subType the subtype of the item
         */
        private void collectExtendedDataProperties(int subType)
                throws RepositoryException {
            // TODO: In the most general case, we might want some
            // entries for the content, other entries just for
            // metadata, and yet other entries not at all.
            String[] fields = connector.getExtendedDataKeys(subType);
            if (fields == null)
                return;

            // TODO: When we implement configurable metadata, we
            // may need to call GetObjectInfo elsewhere, so we
            // should only do that once, and only if needed.
            ClientValue objectInfo = client.GetObjectInfo(volumeId, objectId);
            ClientValue extendedData = objectInfo.toValue("ExtendedData");
            if (extendedData == null || !extendedData.hasValue())
                return;

            // Decompose the ExtendedData into its atomic values,
            // and add them as properties.
            for (int i = 0; i < fields.length; i++) {
                ClientValue value = extendedData.toValue(fields[i]);
                if (value != null && value.hasValue()) {
                    // XXX: For polls, the Questions field is a
                    // stringified list of assoc. We're only handling
                    // this one case, rather than handling stringified
                    // values generally.
                    if (subType == Client.POLLSUBTYPE &&
                            fields[i].equals("Questions")) {
                        value = value.stringToValue();
                    }

                    collectValueProperties(fields[i], value);
                }
            }
        }

        /**
         * Collects properties in an LLValue.
         *
         * @param name the property name
         * @param value the property value
         * @see #collectExtendedDataProperties
         */
        private void collectValueProperties(String name, ClientValue value)
                throws RepositoryException {
            if (LOGGER.isLoggable(Level.FINEST)) {
                LOGGER.finest("Type: " + value.type() + "; value: " +
                    value.toString2());
            }

            switch (value.type()) {
            case ClientValue.LIST:
                for (int i = 0; i < value.size(); i++)
                    collectValueProperties(name, value.toValue(i));
                break;

            case ClientValue.ASSOC:
                Enumeration keys = value.enumerateNames();
                while (keys.hasMoreElements()) {
                    String key = (String) keys.nextElement();
                    collectValueProperties(key, value.toValue(key));
                }
                break;

            case ClientValue.BOOLEAN:
            case ClientValue.DATE:
            case ClientValue.DOUBLE:
            case ClientValue.INTEGER:
            case ClientValue.STRING:
                props.addProperty(name, value);
                break;

            default:
                LOGGER.finest("Ignoring an unimplemented type.");
                break;
            }
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
         * @throws RepositoryException if an error occurs
         */
        private void collectCategoryAttributes() throws RepositoryException {
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
                categoryVersion =
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
                        getAttributeSetValues(categoryVersion, attrName);
                    }
                    else {
                        getAttributeValue(categoryVersion, attrName,
                            attrType, null, attrInfo);
                    }
                }
            }
        }

        /**
         * Gets the values for attributes contained in an attribute set.
         *
         * @param categoryVersion the category being read
         * @param attributeName the name of the attribute set
         * @throws RepositoryException if an error occurs
         */
        private void getAttributeSetValues(ClientValue categoryVersion,
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
                attrInfo[i] =
                    client.AttrGetInfo(categoryVersion, name, attrSetPath);
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
                        LOGGER.finer(
                            "Nested attributes sets are not supported.");
                        continue;
                    }
                    //System.out.println("      " + attrSetNames.toString(j));
                    getAttributeValue(categoryVersion,
                        attrSetNames.toString(j), type, attrSetPath,
                        attrInfo[j]);
                }
            }
        }

        /**
         * Gets the values for an attribute.
         *
         * @param categoryVersion the category version in which the
         * values are stored
         * @param attributeName the name of the attribute whose
         * values are being read
         * @param attributeType the type of the attribute data; may
         * not be "SET"
         * @param attributeSetPath if the attribute is contained
         * within an attribute set, this is a list containing the set
         * name and set instance index; otherwise, this should be
         * null
         * throws RepositoryException if an error occurs
         */
        private void getAttributeValue(ClientValue categoryVersion,
                String attrName, int attrType, ClientValue attrSetPath,
                ClientValue attrInfo) throws RepositoryException {
            if (Client.ATTR_TYPE_SET == attrType)
                throw new IllegalArgumentException("attrType = SET");

            // Skip attributes marked as not searchable.
            if (!attrInfo.toBoolean("Search"))
                return;

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
                if (Client.ATTR_TYPE_USER == attrType) {
                    int userId = value.toInteger();
                    ClientValue userInfo = client.GetUserOrGroupByID(userId);
                    props.addProperty(attrName, userInfo.toValue("Name"));
                } else {
                    props.addProperty(attrName, value);
                }
            }
        }
    }
}
