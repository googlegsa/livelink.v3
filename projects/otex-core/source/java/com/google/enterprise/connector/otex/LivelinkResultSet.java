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

/**
 * This result set implementation could be trivial, but is not. Since
 * a <code>PropertyMapList</code> in this implementation is a wrapper on a
 * recarray, we need the recarray and other things in the
 * <code>PropertyMapList</code>, <code>PropertyMap</code>,
 * <code>Property</code>, and <code>Value</code> implementations,
 * along with the associated <code>Iterator</code> implementations. So
 * we use inner classes to share the necessary objects from this
 * outermost class.
 */
class LivelinkResultSet implements PropertyMapList {
    /** The logger for this class. */
    private static final Logger LOGGER =
        Logger.getLogger(LivelinkResultSet.class.getName());

    /** An immutable empty string value. */
    private static final Value VALUE_EMPTY =
        new SimpleValue(ValueType.STRING, "");

    /** An immutable string value of "text/html". */
    private static final Value VALUE_TEXT_HTML =
        new SimpleValue(ValueType.STRING, "text/html");
    
    /** An immutable false value. */
    private static final Value VALUE_FALSE =
        new SimpleValue(ValueType.BOOLEAN, "false");
        
    /** The connector contains configuration information. */
    private final LivelinkConnector connector;

    /** This povides a mechanism to extract document Category Attributes */
    private final LivelinkAttributes cattr;

    /** The client provides access to the server. */
    private final Client client;

    /** A concrete strategy for retrieving the content from the server. */
    private final ContentHandler contentHandler;
    
    private final ClientValue recArray;

    /** The recarray fields. */
    private final Field[] fields;

    LivelinkResultSet(LivelinkConnector connector, Client client,
            ContentHandler contentHandler, ClientValue recArray,
            Field[] fields) throws RepositoryException {
        this.connector = connector;
        this.client = client;
        this.contentHandler = contentHandler;
        this.recArray = recArray;
        this.fields = fields;
        this.cattr = new LivelinkAttributes(connector, client, contentHandler);
    }


    /** {@inheritDoc} */
    public Iterator iterator() {
        return new LivelinkResultSetIterator();
    }
    

    /**
     * Iterates over a <code>PropertyMapList</code>, returning each
     * <code>PropertyMap</code> it contains.
     */
    private class LivelinkResultSetIterator implements Iterator {
        /** The current row of the recarray. */
        private int row;
        private final int size;

        /** The ObjectID and VolumeID of the current repository object */
        private int objectId;
        private int volumeId;

        /** The PropertyMap associated with the current row */
        private LivelinkPropertyMap props;

        /** Iterator Interface */

        LivelinkResultSetIterator() {
            this.row = 0;
            this.size = recArray.size();
        }

        public boolean hasNext() {
            return row < size;
        }

        public Object next() {
            if (row < size) {
                try {
                    objectId = recArray.toInteger(row, "DataID");
                    volumeId = recArray.toInteger(row, "OwnerID");
                    props = new LivelinkPropertyMap(objectId, fields.length*2);

                    /* establish the checkpoint string for this row */
                    Date date = recArray.toDate(row, "ModifyDate");
                    String cp = LivelinkDateFormat.getInstance().toSqlString(date) +
                        ','  + objectId;
                    props.setCheckpoint(cp);

                    /* collect the various properties for this row */
                    collectRecArrayProperties();
                    collectDerivedProperties();
                    //                    collectCategoryAttributes();

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


       /** Collect properties for the current Iterator item */
        
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
            props.addProperty(SpiConstants.PROPNAME_ISPUBLIC, VALUE_FALSE);

            int subType = recArray.toInteger(row, "SubType");

            // CONTENT
            if (LOGGER.isLoggable(Level.FINER))
                LOGGER.finer("CONTENT WITH SUBTYPE = " + subType);

            // DataSize is the only non-nullable column from
            // DVersData that appears in the WebNodes view,
            // but there are cases (such as categories) where
            // there are rows in DVersData but FetchVersion
            // fails. So we're guessing here that if MimeType
            // is non-null then there should be a blob.
            if (recArray.isDefined(row, "MimeType")) {
                // FIXME: I think that compound documents are
                // returning with a MIME type but, obviously,
                // no versions.

                // XXX: This value might be wrong. There are
                // data size callbacks which can change this
                // value. For example, the value returned by
                // GetObjectInfo may be different than the
                // value retrieved from the database.
                int size = recArray.toInteger(row, "DataSize");

                // FIXME: Better error handling. Either
                // uninstalling the Doorways module or calling
                // this with undefined MimeTypes throws errors
                // that we might want to handle more
                // gracefully (e.g., maybe the underlying error
                // is "no content").
                Value contentValue = new InputStreamValue(
                        contentHandler.getInputStream(volumeId, objectId, 0,
                        size));
                props.addProperty(SpiConstants.PROPNAME_CONTENT, contentValue);
            } else {
                // TODO: What about objects that have files associated
                // with them but also have data in ExtendedData? We
                // should be extracting the metadata from ExtendedData
                // but not assembling the HTML content in that case.
                collectExtendedDataProperties(subType);
            }

            // DISPLAYURL
            String url = connector.getDisplayUrl(subType, objectId, volumeId);
            props.addProperty(SpiConstants.PROPNAME_DISPLAYURL,
                              new SimpleValue(ValueType.STRING, url));
        }


        /**
         * Collect properties from the ExtendedData assoc. These
         * properties are also assembled, together with the object
         * name, in an HTML content property.
         *
         * @param subType the subtype of the item
         */
        private void collectExtendedDataProperties(int subType)
                throws RepositoryException {
            // TODO: In the most general case, we might want some
            // entries for the content, other entries just for
            // metadata, and yet other entries not at all.
            String[] fields = connector.getExtendedDataKeys(subType);
            Value contentValue;
            if (fields == null) {
                // TODO: This is a workaround for Connector Manager
                // Issue 21, where the QueryTraverser requires a
                // content or contenturl property.
                contentValue = VALUE_EMPTY;
            } else {
                // TODO: We need to handle undefined fields here.
                // During one test run, I saw an undefined
                // ExtendedData value, but I haven't been able to
                // reproduce that.
                // TODO: When we implemented configurable metadata, we
                // may need to call GetObjectInfo elsewhere, so we
                // should only do that once, and only if needed.
                String name = recArray.toString(row, "Name");
                ClientValue objectInfo =
                    client.GetObjectInfo(volumeId, objectId);
                ClientValue extendedData = objectInfo.toValue("ExtendedData");

                StringBuffer buffer = new StringBuffer();
                buffer.append("<title>");
                buffer.append(name);
                buffer.append("</title>\n");
                for (int i = 0; i < fields.length; i++) {
                    ClientValue value = extendedData.toValue(fields[i]);

                    // XXX: For polls, the Questions field is a
                    // stringified list of assoc. We're only handling
                    // this one case, rather than handling stringified
                    // values generally.
                    if (subType == Client.POLLSUBTYPE &&
                            fields[i].equals("Questions")) {
                        value = value.stringToValue();
                    }

                    // FIXME: GSA Feed Data Source Log:swift95
                    // 
                    // ProcessNode: Content attribute not properly
                    // specified, skipping record with URL
                    // googleconnector://swift95.localhost/doc?docid=31352
                    //
                    // Caused by issue 30, empty metadata values, in
                    // this case the Comments field.
                    collectValueContent(fields[i], value, "<div>",
                        "</div>\n", buffer);
                }
                contentValue =
                    new SimpleValue(ValueType.STRING, buffer.toString());
                props.addProperty(SpiConstants.PROPNAME_MIMETYPE,
                                  VALUE_TEXT_HTML);
            }
            props.addProperty(SpiConstants.PROPNAME_CONTENT, contentValue);
        }

        /**
         * Collects properties in an LLValue. These properties are
         * also assembled, together with the object name, in an HTML
         * content property.
         *
         * @param name the property name
         * @param value the property value
         * @param openTag the HTML open tag to wrap the value in
         * @param closeTag the HTML close tag to wrap the value in
         * @param buffer the buffer to assemble the HTML content in
         * @see #collectExtendedDataProperties
         */
        private void collectValueContent(String name, ClientValue value,
                String openTag, String closeTag, StringBuffer buffer)
                throws RepositoryException {
            if (LOGGER.isLoggable(Level.FINEST)) {
                LOGGER.finest("Type: " + value.type() + "; value: " +
                    value.toString2());

            }
            
            switch (value.type()) {
            case ClientValue.LIST:
                buffer.append(openTag);
                buffer.append("<ul>");
                for (int i = 0; i < value.size(); i++) {
                    collectValueContent(name, value.toValue(i), "<li>",
                        "</li>\n", buffer);
                }
                buffer.append("</ul>\n");
                buffer.append(closeTag);
                break;

            case ClientValue.ASSOC:
                buffer.append(openTag);
                buffer.append("<ul>");
                Enumeration keys = value.enumerateNames();
                while (keys.hasMoreElements()) {
                    String key = (String) keys.nextElement();
                    collectValueContent(key, value.toValue(key), "<li>",
                        "</li>\n", buffer);
                }
                buffer.append("</ul>\n");
                buffer.append(closeTag);
                break;

            case ClientValue.BOOLEAN:
            case ClientValue.DATE:
            case ClientValue.DOUBLE:
            case ClientValue.INTEGER:
            case ClientValue.STRING:
                buffer.append(openTag);
                buffer.append("<p>");
                buffer.append(value.toString2());
                buffer.append("</p>\n");
                buffer.append(closeTag);

                props.addProperty(name, new LivelinkValue(value));
                break;

            default:
                LOGGER.finest("Ignoring an unimplemented type.");
                break;
            }
        }
        
        /**
         * Collect Category Attributes from the CategoryAttributes Map.
         */
        private void collectCategoryAttributes() throws RepositoryException {
            Map attrmap = cattr.getCategoryAttributes(objectId);
            if (attrmap.isEmpty())
                return;

            // Iterate over the category attributes, adding them to the property map
            Iterator i = attrmap.entrySet().iterator(); 
            Map.Entry attr;
            while ((attr = (Map.Entry) i.next()) != null) {

                System.out.println("CategoryAttribute: " + attr.toString());

                String attrName = attr.getKey().toString();

                ClientValue attrValue = (ClientValue) attr.getValue();

                props.addProperty(attrName, new LivelinkValue(attrValue));

                attr = null;
            }
        }
    }
}
