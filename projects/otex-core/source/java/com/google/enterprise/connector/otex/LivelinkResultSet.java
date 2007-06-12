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

    /** The client provides access to the server. */
    private final Client client;

    /** A concrete strategy for retrieving the content from the server. */
    private final ContentHandler contentHandler;
    
    private final ClientValue recArray;

    /** The recarray fields. */
    private final Field[] fields;

    /** A GMT calendar for converting timestamps to UTC. */
    private final Calendar gmtCalendar =
        Calendar.getInstance(TimeZone.getTimeZone("GMT+0"));

    /** The ISO 8601 date format returned in property values. */
    private final SimpleDateFormat iso8601 =
        new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

    /** The ISO SQL date format used in database queries. */
    private final SimpleDateFormat sql =
        new SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ss");

    LivelinkResultSet(LivelinkConnector connector, Client client,
            ContentHandler contentHandler, ClientValue recArray,
            Field[] fields) throws RepositoryException {
        this.connector = connector;
        this.client = client;
        this.contentHandler = contentHandler;
        this.recArray = recArray;
        this.fields = fields;

        iso8601.setCalendar(gmtCalendar);
    }

    /**
     * Livelink only stores timestamps to the nearest second, but LAPI
     * constructs a Date object that includes milliseconds, which are
     * taken from the current time. So we need to avoid using the
     * milliseconds in the parameter.
     *
     * @param value a Livelink date
     * @return an ISO 8601 formatted string representation,
     *     using the pattern "yyyy-MM-dd'T'HH:mm:ss'Z'"
     * @see toSqlString
     */
    /*
     * This method is synchronized to emphasize safety over speed.
     * <code>Calendar</code> and <code>SimpleDateFormat</code> objects
     * are not thread-safe. We've put these calendar and date format
     * objects in instance fields to balance object creation, not
     * frequent at just once per result set, and synchronization,
     * which is fast in the absence of contention. It is extremely
     * unlikely that <code>PropertyMapList</code> instances or their
     * children will be called from multiple threads, but there's no
     * need to cut corners here.
     * 
     * TODO: LAPI converts the database local time to UTC using the
     * default Java time zone, so if the database time zone is different
     * from the Java time zone, we need to adjust the given Date
     * accordingly. In order to account for Daylight Savings, we
     * probably need to subtract the offsets for the date under both
     * time zones and apply the resulting adjustment (in milliseconds)
     * to the Date object.
     */
    private synchronized String toIso8601String(Date value) {
        return iso8601.format(value);
    }

    /**
     * Converts a local time date to an ISO SQL local time string.
     *
     * @param value a timestamp where local time is database local time
     * @return an ISO SQL string using the pattern
     * "yyyy-MM-dd' 'HH:mm:ss"
     * @see #toIso8601String
     */
    private synchronized String toSqlString(Date value) {
        return sql.format(value);
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
        private int row;
        private final int size;

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
                    return new LivelinkPropertyMap(row++);
                } catch (RepositoryException e) {
                    throw new RuntimeException(e);
                }
            } else
                throw new NoSuchElementException();
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
    }


    /**
     * {@inheritDoc}
     * <p>
     * This implementation accumlated properties from the the
     * underlying recarray, along with other properties derived from
     * the recarray.
     */
    class LivelinkPropertyMap implements PropertyMap {
        /** The row of the recarray this property map is based on. */
        private final int row;

        /*
         * I'm using a LinkedHashMap just because it's got a more
         * predictable ordering when I'm looking at test output.
         */
        private final Map properties = new LinkedHashMap(fields.length * 2);

        LivelinkPropertyMap(int row) throws RepositoryException {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("PROPERTY MAP FOR ID = " +
                    recArray.toInteger(row, "DataID"));
            }

            this.row = row;

            collectRecArrayProperties();
            collectDerivedProperties();
        }

        /**
         * Adds a property to the property map. If the property
         * already exists in the map, the given value is added to the
         * list of values in the property.
         *
         * @param name a property name
         * @param value a property value
         */
        private void addProperty(String name, Value value) {
            Object values = properties.get(name);
            if (values == null) {
                LinkedList firstValues = new LinkedList();
                firstValues.add(value);
                properties.put(name, firstValues);
            } else
                ((LinkedList) values).add(value);
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
                if (fields[i].propertyName != null) {
                    ClientValue value =
                        recArray.toValue(row, fields[i].fieldName);
                    if (value.isDefined()) {
                        addProperty(fields[i].propertyName,
                            new LivelinkValue(fields[i].fieldType, value));
                    }
                }
            }
        }
        
        /** Collects additional properties derived from the recarray. */
        private void collectDerivedProperties() throws RepositoryException {
            addProperty(SpiConstants.PROPNAME_ISPUBLIC, VALUE_FALSE);

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

                int objectId = recArray.toInteger(row, "DataID");
                int volumeId = recArray.toInteger(row, "OwnerID");

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
                addProperty(SpiConstants.PROPNAME_CONTENT, contentValue);
            } else {
                // XXX: What about objects that have files associated
                // with them but also have data in ExtendedData? We
                // should be extracting the metadata from ExtendedData
                // but not assembling the HTML content in that case.
                collectExtendedDataProperties(subType);
            }

            // DISPLAYURL
            int objectId = recArray.toInteger(row, "DataID");
            int volumeId = recArray.toInteger(row, "OwnerID");
            String url = connector.getDisplayUrl(subType, objectId, volumeId);
            addProperty(SpiConstants.PROPNAME_DISPLAYURL,
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
                String name = recArray.toString(row, "Name");
                int objectId = recArray.toInteger(row, "DataID");
                int volumeId = recArray.toInteger(row, "OwnerID");
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
                addProperty(SpiConstants.PROPNAME_MIMETYPE, VALUE_TEXT_HTML);
            }
            addProperty(SpiConstants.PROPNAME_CONTENT, contentValue);
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

                ValueType type;
                switch (value.type()) {
                case ClientValue.BOOLEAN: type = ValueType.BOOLEAN; break;
                case ClientValue.DATE: type = ValueType.DATE; break;
                case ClientValue.DOUBLE: type = ValueType.DOUBLE; break;
                case ClientValue.INTEGER: type = ValueType.LONG; break;
                case ClientValue.STRING: type = ValueType.STRING; break;
                default:
                    throw new AssertionError("This can't happen.");
                }
                addProperty(name, new SimpleValue(type, value.toString2()));
                break;

            default:
                LOGGER.finest("Ignoring an unimplemented type.");
                break;
            }
        }
        
        public Iterator getProperties() {
            return new LivelinkPropertyMapIterator(properties);
        }

        public Property getProperty(String name) {
            Object values = properties.get(name);
            if (values == null)
                return null;
            else
                return new LivelinkProperty(name, (LinkedList) values);
        }

        /**
         * Gets a string of the form "yyyy-MM-dd HH:mm:ss,nnnnnn" where
         * nnnnnn is the object ID of the item represented by this property
         * map.
         *
         * @return a checkpoint string for this property map
         * @throws RepositoryException if an error occurs retrieving
         * the field data
         */
        public String checkpoint() throws RepositoryException {
            return toSqlString(recArray.toDate(row, "ModifyDate")) +
                ','  + recArray.toString(row, "DataID");
        }
    }


    /**
     * Iterates over a <code>PropertyMap</code>, returning each
     * <code>Property</code> it contains.
     */
    private static class LivelinkPropertyMapIterator implements Iterator {
        private final Iterator properties;

        LivelinkPropertyMapIterator(Map properties) {
            this.properties = properties.entrySet().iterator();
        }

        public boolean hasNext() {
            return properties.hasNext();
        }

        public Object next() {
            Map.Entry property = (Map.Entry) properties.next();
            return new LivelinkProperty((String) property.getKey(),
                (LinkedList) property.getValue());
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
    }


    /**
     * {@inheritDoc}
     * <p>
     * This implementation represents a property that always has one
     * or more values.
     */
    /*
     * We could just use SimpleProperty instead, and move the logging
     * to LivelinkPropertyMapIterator.
     */
    private static class LivelinkProperty implements Property {
        private final String name;
        private final LinkedList values;

        LivelinkProperty(String name, LinkedList values) {
            this.name = name;
            this.values = values;

            if (LOGGER.isLoggable(Level.FINEST))
                LOGGER.finest("PROPERTY: " + name + " = " + values);
        }

        public String getName() {
            return name;
        }

        public Value getValue() throws RepositoryException {
            return (Value) values.getFirst();
        }

        public Iterator getValues() throws RepositoryException {
            return values.iterator();
        }
    }


    /**
     * {@inheritDoc}
     * <p>
     * This implementation represents an atomic <code>ClientValue</code>.
     */
    /*
     * TODO: Value conversion across types. We're relying on
     * the behavior of the LLInstance subclasses here.
     */
    private class LivelinkValue implements Value {
        private final ValueType type;
        private final ClientValue clientValue;

        LivelinkValue(ValueType type, ClientValue clientValue) {
            this.clientValue = clientValue;
            this.type = type;
        }

        public boolean getBoolean() throws RepositoryException {
            return clientValue.toBoolean();
        }

        public Calendar getDate() throws RepositoryException {
            Calendar c = Calendar.getInstance();
            c.setTime(clientValue.toDate());
            return c;
        }

        public double getDouble() throws RepositoryException {
            return clientValue.toDouble();
        }

        public long getLong() throws RepositoryException {
            return clientValue.toInteger();
        }

        public InputStream getStream() throws RepositoryException {
            try {
                return new ByteArrayInputStream(getString().getBytes("UTF8"));
            } catch (UnsupportedEncodingException e) {
                // This can't happen.
                RuntimeException re = new IllegalArgumentException();
                re.initCause(e);
                throw re;
            }
        }

        public String getString() throws RepositoryException {
            if (type == ValueType.DATE)
                return toIso8601String(clientValue.toDate());
            else
                return clientValue.toString2();
        }

        public ValueType getType() {
            return type;
        }

        /**
         * {@inheritDoc}
         * <p>
         * This implementation returns the string value, or if an
         * exception is thrown obtaining the value, it returns the
         * string value of the exception. This is intended for
         * debugging and logging.
         */
        public String toString() {
            try {
                return getString();
            } catch (RepositoryException e) {
                return e.toString();
            }
        }
    }
}
