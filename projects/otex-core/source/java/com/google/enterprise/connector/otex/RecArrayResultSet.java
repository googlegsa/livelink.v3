// Copyright (C) 2006-2007 Google Inc.

package com.google.enterprise.connector.otex;

import java.io.InputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.TimeZone;

import com.google.enterprise.connector.spi.Property;
import com.google.enterprise.connector.spi.PropertyMap;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.ResultSet;
import com.google.enterprise.connector.spi.SimpleValue;
import com.google.enterprise.connector.spi.SpiConstants;
import com.google.enterprise.connector.spi.Value;
import com.google.enterprise.connector.spi.ValueType;
import com.google.enterprise.connector.otex.client.Client;
import com.google.enterprise.connector.otex.client.RecArray;

/**
 * This result set implementation could be trivial, but is not. Since
 * a <code>ResultSet</code> in this implementation is a wrapper on a
 * recarray, we need the recarray and other things in the
 * <code>ResultSet</code>, <code>PropertyMap</code>,
 * <code>Property</code>, and <code>Value</code> implementations,
 * along with the associated <code>Iterator</code> implementations. So
 * we use inner classes to share the necessary objects from this
 * outermost class.
 */
class RecArrayResultSet implements ResultSet {
    private static final Logger LOGGER =
        Logger.getLogger(LivelinkQueryTraversalManager.class.getName());

    private final LivelinkConnector connector;

    private final Client client;

    private final RecArray recArray;

    /**
     * A map from the property name to the Field descriptor. It is
     * important that this implementation use a
     * <code>LinkedHashMap</code> in order to keep the fields in
     * insertion order.
     */
    private final LinkedHashMap fieldsMap;

    /** A GMT calendar for converting timestamps to UTC. */
    private final Calendar gmtCalendar =
        Calendar.getInstance(TimeZone.getTimeZone("GMT+0"));

    /** The ISO 8601 date format returned in property values. */
    private final SimpleDateFormat iso8601 =
        new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

    /** The ISO SQL date format used in database queries. */
    private final SimpleDateFormat sql =
        new SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ss");
    
    RecArrayResultSet(LivelinkConnector connector, Client client,
            RecArray recArray, Field[] fields) {
        this.connector = connector;
        this.client = client;
        this.recArray = recArray;

        fieldsMap = new LinkedHashMap(fields.length * 2);
        for (int i = 0; i < fields.length; i++) {
            if (fields[i].propertyName != null)
                fieldsMap.put(fields[i].propertyName, fields[i]);
        }

        iso8601.setCalendar(gmtCalendar);
    }

    /** {@inheritDoc} */
    public Iterator iterator() {
        return new RecArrayResultSetIterator();
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
     * unlikely that <code>ResultSet</code> instances or their
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
    private synchronized String toIso8601String(Date value)
    {
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


    /**
     * Iterates over a <code>ResultSet</code>, returning each
     * <code>PropertyMap</code> it contains.
     */
    private class RecArrayResultSetIterator implements Iterator {
        private int row;
        private final int size;

        RecArrayResultSetIterator()
        {
            this.row = 0;
            this.size = recArray.size();
        }

        public boolean hasNext() {
            return row < size;
        }

        public Object next() {
            if (row < size)
                return new RecArrayPropertyMap(row++);
            else
                throw new NoSuchElementException();
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
    }


    /**
     * {@inheritDoc}
     * <p>
     * This implementation simply represents a specific row of the
     * underlying recarray.
     */
    class RecArrayPropertyMap implements PropertyMap {
        private final int row;
        
        RecArrayPropertyMap(int row) {
            this.row = row;
        }

        public Iterator getProperties() {
            return new RecArrayPropertyMapIterator(row);
        }

        public Property getProperty(String name) {
            Object field = fieldsMap.get(name);
            if (field == null)
                return null;
            else
                return new RecArrayProperty(row, (Field) field);
        }

        /**
         * Gets a string of the form "yyyy-MM-dd HH:mm:ss,nnnnnn" where
         * nnnnnn is the object ID of the item represented by this property
         * map.
         *
         * @return a checkpoint string for this property map
         */
        public String checkpoint() {
            return toSqlString(recArray.toDate(row, "ModifyDate")) +
                ','  + recArray.toString(row, "DataID");
        }
    }


    /**
     * Iterates over a <code>PropertyMap</code>, returning each
     * <code>Property</code> it contains.
     */
    private class RecArrayPropertyMapIterator implements Iterator {
        private final int row;
        private Iterator columns;

        RecArrayPropertyMapIterator(int row) {
            this.row = row;
            this.columns = fieldsMap.values().iterator();
        }

        public boolean hasNext() {
            return columns.hasNext();
        }

        public Object next() {
            return new RecArrayProperty(row, (Field) columns.next());
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
    }


    /**
     * {@inheritDoc}
     * <p>
     * This implementation represents a specific property from a
     * specific row of the underlying recarray. In some cases the
     * property value is simply a field in the recarray, but it others
     * it is obtained from the Livelink server using information in
     * the recarray, most notably the object and volume IDs.
     */
    private class RecArrayProperty implements Property {
        private final int row;
        private final Field column;

        RecArrayProperty(int row, Field column) {
            this.row = row;
            this.column = column;
        }

        public String getName() {
            return column.propertyName;
        }

        public Value getValue() throws RepositoryException {
            if (column.fieldName == null) {
                // This column isn't in the recarray, so we need to do
                // something else to get the value.

                // TODO: Should we cache this? Use a hashed looked
                // instead of sequential string comparisons?
                if (column.propertyName.equals(SpiConstants.PROPNAME_CONTENT)) {
                    // FIXME: Check for IsDefined instead?
                    String mimeType = recArray.toString(row, "MimeType");
                    if ("?".equals(mimeType)) {
                        // TODO: This is a workaround for a bug where
                        // the QueryTraverser requires a content or
                        // contenturl property.
                        return new SimpleValue(ValueType.STRING, "");
                    } else {
                        // FIXME: I think that compound documents are
                        // returning with a MIME type but, obviously,
                        // no versions.
                        int subType = recArray.toInteger(row, "SubType");
                        if (LOGGER.isLoggable(Level.FINER))
                            LOGGER.finer("CONTENT WITH SUBTYPE = " + subType);
                        int objectId = recArray.toInteger(row, "DataID");
                        int volumeId = recArray.toInteger(row, "OwnerID");
                        return new InputStreamValue(
                            client.FetchVersion(LOGGER, volumeId,
                                objectId, 0));
                    }
                } else if (column.propertyName.equals(
                               SpiConstants.PROPNAME_DISPLAYURL)) {
                    String displayUrl = connector.getDisplayUrl() +
                        "?func=ll&objAction=open&objId=";
                    int objectId = recArray.toInteger(row, "DataID");
                    return new SimpleValue(ValueType.STRING, displayUrl
                        + objectId);
                } else if (column.propertyName.equals(
                               SpiConstants.PROPNAME_ISPUBLIC)) {
                    // FIXME: SimpleValue is immutable, so we only
                    // need one (or maybe two) of these, not a new one
                    // for each PropertyMap.
                    return new SimpleValue(ValueType.BOOLEAN, "false");
                } else {
                    throw new LivelinkException(
                        "FIXME: Unimplemented property \"" +
                        column.propertyName + '"', LOGGER);
                }
            } else
                return new RecArrayValue(recArray, row, column);
        }

        public Iterator getValues() throws RepositoryException {
            // TODO: Real implementation, please.
            ArrayList values = new ArrayList(1);
            values.add(getValue());
            return values.iterator();
        }
    }


    /**
     * {@inheritDoc}
     * <p>
     * This implementation represents a specific field from a
     * specific row of the underlying recarray.
     */
    /*
     * TODO: Value conversion across types. We're relying on
     * the behavior of the LLInstance subclasses here, and we're not
     * catching the exceptions they throw.
     */
    private class RecArrayValue implements Value {
        private final RecArray recArray;
        private final int row;
        private final Field column;

        RecArrayValue(RecArray recArray, int row, Field column) {
            this.recArray = recArray;
            this.row = row;
            this.column = column;
            if (LOGGER.isLoggable(Level.FINEST)) {
                LOGGER.finest("VALUE: " + column.propertyName + " = " +
                    getString());
            }
        }

        public boolean getBoolean() {
            return recArray.toBoolean(row, column.fieldName);
        }

        public Calendar getDate() {
            Calendar c = Calendar.getInstance();
            c.setTime(recArray.toDate(row, column.fieldName));
            return c;
        }

        public double getDouble() {
            return recArray.toDouble(row, column.fieldName);
        }

        public long getLong() {
            return recArray.toInteger(row, column.fieldName);
        }

        /*
         * XXX: Should we read the string and return a stream based on that?
         */
        public InputStream getStream() {
            return null;
        }

        public String getString() {
            if (column.fieldType == ValueType.DATE)
                return toIso8601String(recArray.toDate(row, column.fieldName));
            else
                return recArray.toString(row, column.fieldName);
        }

        public ValueType getType() {
            return column.fieldType;
        }
    }
}
