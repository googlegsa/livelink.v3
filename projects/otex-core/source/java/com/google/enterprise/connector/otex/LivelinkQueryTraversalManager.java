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
import java.util.logging.Logger;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.TimeZone;

import com.google.enterprise.connector.spi.Property;
import com.google.enterprise.connector.spi.PropertyMap;
import com.google.enterprise.connector.spi.QueryTraversalManager;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.ResultSet;
import com.google.enterprise.connector.spi.SimpleProperty;
import com.google.enterprise.connector.spi.SimplePropertyMap;
import com.google.enterprise.connector.spi.SimpleResultSet;
import com.google.enterprise.connector.spi.SimpleValue;
import com.google.enterprise.connector.spi.SpiConstants;
import com.google.enterprise.connector.spi.Value;
import com.google.enterprise.connector.spi.ValueType;
import com.google.enterprise.connector.otex.client.Client;
import com.google.enterprise.connector.otex.client.ClientFactory;
import com.google.enterprise.connector.otex.client.RecArray;

class LivelinkQueryTraversalManager implements QueryTraversalManager {

    /* TODO: Autodetection of the database type plus a config parameter. */
    private static boolean isSqlServer = true;

    private static final Logger LOGGER =
        Logger.getLogger(LivelinkQueryTraversalManager.class.getName());

    private final LivelinkConnector connector;

    private final Client client;
    
    private int batchSize = 100;

    LivelinkQueryTraversalManager(LivelinkConnector connector,
                                  ClientFactory clientFactory) {
        this.connector = connector;
        client = clientFactory.createClient();
    }


    /**
     * Will be called only once by Connector Manager
     */
    public ResultSet startTraversal() throws RepositoryException {
        LOGGER.fine("START @" + System.identityHashCode(this));
        return listNodes(null);
    }


    public void setBatchHint(int hint) {
        batchSize = hint;
    }


    /**
     * Gets a string of the form "yyyy-MM-dd HH:mm:ss,nnnnnn" where
     * nnnnnn is the object ID of the item represented by the property
     * map.
     *
     * @param pm a property map
     * @return a checkpoint string for the given property map
     */
    public String checkpoint(PropertyMap pm) throws RepositoryException {
        String s = ((RecArrayPropertyMap) pm).checkpoint();
        LOGGER.fine("CHECKPOINT: " + s + " @" + System.identityHashCode(this));
        return s;
    }


    public ResultSet resumeTraversal(String checkpoint)
        throws RepositoryException {
        LOGGER.fine("RESUME: " + checkpoint + " @" +
            System.identityHashCode(this));
        return listNodes(checkpoint);
    }


    /**
     * The primary store for property names that we want to map from
     * the database to the PropertyMap. This is an array of
     * <code>Field</code> objects that include the record array field
     * name, the record array field type, and the the GSA property
     * name. If the property name is <code>null</code>, then the field
     * will not be returned in the property map.
     */
    private static final Field[] fields;

    /**
     * A map from the property name to the Field descriptor. We could
     * use a LinkedHashMap here and not need to keep the fields array
     * separately, although we would lose direct indexing.
     */
    private static final Map fieldsMap;

    /**
     * Describes the properties returned to the caller and the fields
     * in Livelink needed to implement them. A null
     * <code>fieldName</code> indicates a property that is in not in
     * the recarray selected from the database. These properties are
     * implemented separately. A null <code>propertyName</code>
     * indicates a field that is required from the database but which
     * is not returned as a property to the caller.
     */
    private static final class Field {
        public final String fieldName;
        public final ValueType fieldType;
        public final String propertyName;

        public Field(String fieldName, ValueType fieldType,
                     String propertyName) {
            this.fieldName = fieldName;
            this.fieldType = fieldType;
            this.propertyName = propertyName;
        }
    }
    
    static {
        // ListNodes requires the DataID and PermID columns to be
        // included here. This class requires DataID, OwnerID,
        // ModifyDate, and MimeType.
        ArrayList list = new ArrayList();

        list.add(new Field(
            null, ValueType.STRING, SpiConstants.PROPNAME_CONTENT));
        list.add(new Field(
            null, ValueType.STRING, SpiConstants.PROPNAME_DISPLAYURL));
        list.add(new Field(
            null, ValueType.BOOLEAN, SpiConstants.PROPNAME_ISPUBLIC));

        list.add(new Field(
            "DataID", ValueType.LONG, SpiConstants.PROPNAME_DOCID));
        list.add(new Field(
            "ModifyDate", ValueType.DATE, SpiConstants.PROPNAME_LASTMODIFY));
        list.add(new Field(
            "MimeType", ValueType.STRING, SpiConstants.PROPNAME_MIMETYPE));

        list.add(new Field(
            "Name", ValueType.STRING, "Name"));
        list.add(new Field(
            "OwnerID", ValueType.LONG, "OwnerID"));
        list.add(new Field(
            "PermID", ValueType.LONG, null));
        list.add(new Field(
            "SubType", ValueType.LONG, "SubType"));

        fields = (Field[]) list.toArray(new Field[0]);

        fieldsMap = new LinkedHashMap(fields.length * 2);
        for (int i = 0; i < fields.length; i++) {
            if (fields[i].propertyName != null)
                fieldsMap.put(fields[i].propertyName, fields[i]);
        }
    }


    /**
     * This method uses <code>LAPI_DOCUMENTS.ListNodes</code>, which
     * is an undocumented LAPI method. This method essentially
     * executes a query of the form
     *
     * <pre>
     *     SELECT <em>columns</em> FROM <em>view</em> WHERE <em>query</em>
     * </pre>
     *
     * where <em>columns</em>, <em>view</em>, and <em>query</em> are
     * three of the parameters to <code>ListNodes</code>.
     *
     * <p>
     * We want to execute queries of the following form
     *
     * <pre>
     *     SELECT <em>columns</em> FROM WebNodes WHERE
     *     <em>after_last_checkpoint</em> ORDER BY ModifyDate, DataID
     * </pre>
     *
     * and only read the first <code>batchSize</code> rows. The ORDER
     * BY clause is passed to the <code>ListNodes</code> method as
     * part of the WHERE clause. The <em>after_last_checkpoint</em>
     * condition is empty for <code>startTraversal</code>, or
     *
     * <pre>
     *     ModifyDate > <em>X</em> or 
     *         (ModifyDate = <em>X</em> and DataID > <em>Y</em>)
     * </pre>
     *
     * for <code>resumeTraversal</code> where <em>X</em> is the
     * ModifyDate of the checkpoint and <em>Y</em> is the DataID of
     * the checkpoint.
     *
     * <p>
     * The details are little messy because there is not a single
     * syntax supported by both Oracle and SQL Server for limiting the
     * number of rows returned. For Oracle, we use ROWNUM, and for SQL
     * Server, we use TOP, which requires SQL Server 7.0. The
     * <code>ROW_NUMBER()</code> function is supported by Oracle and
     * also by SQL Server 2005, but that's too limiting, and it's
     * likely to be much slower than TOP or ROWNUM. The standard SQL
     * <code>SET ROWCOUNT</em> statement is supported by SQL Server
     * 6.5, but not by Oracle, and I don't know of a way to execute it
     * from LAPI. A final complication is that ROWNUM limits the rows
     * before the ORDER BY clause is applied, so a subquery is needed
     * to do the ordering before the limit is applied.
     *
     * @param checkpoint a checkpoint string, or <code>null</code> if
     * a new traversal should be started
     * @return a batch of results starting at the checkpoint, if there
     * is one, or the beginning of the traversal order, otherwise
     */
    /*
     * 1. I like the StringBuffer for performance, but for some softer
     * software, where we could put the queries into a properties
     * file, we could use MessageFormat:
     * 
     * MessageFormat subquery = new MessageFormat(
     *     "(select {0} from WebNodes {1,choice,0#|1#where {2}}{3})");
     * Integer choice;
     * String where;
     * if (checkpoint == null) {
     *     choice = new Integer(0);
     *     where = null;
     * } else {
     *     choice = new Integer(1);
     *     where = getRestriction(checkpoint);
     * }
     * view = subquery.format(new Object[] {
     *     getSelectList(), choice, where, getOrderBy() });
     * 
     * 2. We could use FIRST_ROWS(<batchSize>), but I don't know how
     * important that is on this query.
     */
    private ResultSet listNodes(String checkpoint) throws RepositoryException
    {
        String query;
        String view;
        String[] columns;

        // TODO: This is just a sketch of a subtype and volume type
        // restriction. Note that this code only handles SQL Server.
        String subtypes = "SubType not in (137,142,143,148,150,154,161,162,201,203,209,210,211)";
        String ancestors = "DataID not in (select DataID from DTreeAncestors where AncestorID in (2001,2313))";
        String excluded = subtypes + " and " + ancestors;

        if (isSqlServer) {
            if (checkpoint == null)
                //query = "1=1" + getOrderBy();
                query = excluded + getOrderBy();
            else
                //query = getRestriction(checkpoint) + getOrderBy();
                query = excluded + " and " + getRestriction(checkpoint) + getOrderBy();
            view = "WebNodes";

            // FIXME: This code is working around fields with null
            // field names. This turns into "top 100 null, null, ...".
            columns = new String[fields.length];
            columns[0] = "top " + batchSize + " " + fields[0].fieldName;
            for (int i = 1; i < fields.length; i++)
                columns[i] = fields[i].fieldName + "";
        } else {
            query = "rownum <= " + batchSize;
            StringBuffer buffer = new StringBuffer();
            buffer.append("(select ");
            buffer.append(getSelectList());
            buffer.append(" from WebNodes ");
            if (checkpoint != null) {
                buffer.append("where ");
                buffer.append(getRestriction(checkpoint));
            }
            buffer.append(getOrderBy());
            buffer.append(')');
            view = buffer.toString();
            columns = new String[] { "*" };
        }

        final RecArray recArray =
            client.ListNodes(LOGGER, query, view, columns);
        LOGGER.fine("RESULTSET: " + recArray.size() + " rows. @" +
            System.identityHashCode(this));
        
        return new ResultSet() { public Iterator iterator() {
            return new RecArrayResultSetIterator(connector, client, recArray); } };
    }


    /**
     * Gets a SQL condition representing the given checkpoint.
     *
     * @param checkpoint
     * @return a SQL condition returning items following the checkpoint
     */
    /*
     * The TIMESTAMP literal, part of the SQL standard, was first
     * supported by Oracle 9i, and not at all by SQL Server.
     */
    private static String getRestriction(String checkpoint) {
        int index = checkpoint.indexOf(',');
        if (index == -1) {
            LOGGER.warning("Invalid checkpoint " + checkpoint +
                "; restarting traversal.");
            return "1=1";
        } else {
            String ts = isSqlServer ? "" : "TIMESTAMP";
            String modifyDate = checkpoint.substring(0, index);
            String dataId = checkpoint.substring(index + 1);
            return "ModifyDate > " + ts + '\'' + modifyDate + 
                "' or (ModifyDate = " + ts + '\'' + modifyDate +
                "' and DataID > " + dataId + ')';
        }
    }

    private static String getSelectList() {
        StringBuffer buffer = new StringBuffer();
        buffer.append(fields[0].fieldName);
        for (int i = 1; i < fields.length; i++) {
            buffer.append(',');
            buffer.append(fields[i].fieldName);
        }
        return buffer.toString();
    }
    
    private static String getOrderBy() {
        return " order by ModifyDate, DataID";
    }

    
    private static class RecArrayResultSetIterator implements Iterator {
        private final RecArray recArray;
        private int row;
        private final int size;
        private final LivelinkConnector connector;
        private final Client client;
        RecArrayResultSetIterator(LivelinkConnector connector, Client client,
            RecArray recArray)
        {
            this.recArray = recArray;
            this.row = 0;
            this.size = recArray.size();
            this.client = client;
            this.connector = connector;
        }
        public boolean hasNext() {
            return row < size;
        }
        public Object next() {
            if (row < size)
                return new RecArrayPropertyMap(connector, client, recArray, row++);
            else
                throw new NoSuchElementException();
        }
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }


    private static class RecArrayPropertyMap implements PropertyMap {
        private final RecArray recArray;
        private final int row;
        private final LivelinkConnector connector;
        private final Client client;
        RecArrayPropertyMap(LivelinkConnector connector, Client client,
            RecArray recArray, int row) {
            this.recArray = recArray;
            this.row = row;
            this.client = client;
            this.connector = connector;
        }
        public Iterator getProperties() {
            return new RecArrayPropertyMapIterator(connector, client, recArray, row);
        }
        public Property getProperty(String name) {
            Object field = fieldsMap.get(name);
            if (field == null)
                return null;
            else {
                return new RecArrayProperty(connector, client, recArray, row,
                    (Field) field);
            }
        }

        /**
         * Gets a string of the form "yyyy-MM-dd HH:mm:ss,nnnnnn" where
         * nnnnnn is the object ID of the item represented by this property
         * map.
         *
         * @return a checkpoint string for this property map
         */
        private String checkpoint() {
            return toSqlString(recArray.toDate(row, "ModifyDate")) +
                ','  + recArray.toString(row, "DataID");
        }
    }


    private static class RecArrayPropertyMapIterator implements Iterator {
        private final RecArray recArray;
        private final int row;
        private Iterator columns;
        private final LivelinkConnector connector;
        private final Client client;
        RecArrayPropertyMapIterator(LivelinkConnector connector, Client client,
            RecArray recArray, int row) {
            this.recArray = recArray;
            this.row = row;
            this.columns = fieldsMap.values().iterator();
            this.client = client;
            this.connector = connector;
        }
        public boolean hasNext() {
            return columns.hasNext();
        }
        public Object next() {
            return new RecArrayProperty(connector, client, recArray, row,
                (Field) columns.next());
        }
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }


    private static class RecArrayProperty implements Property {
        private static final Logger LOGGER =
            Logger.getLogger(LivelinkQueryTraversalManager.class.getName());

        private final RecArray recArray;
        private final int row;
        private final Field column;
        private final LivelinkConnector connector;
        private final Client client;
        RecArrayProperty(LivelinkConnector connector, Client client,
            RecArray recArray, int row, Field column) {
            this.recArray = recArray;
            this.row = row;
            this.column = column;
            this.client = client;
            this.connector = connector;
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
                        LOGGER.fine("CONTENT WITH SUBTYPE = " + subType);
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


    /*
     * TODO: Value conversion across types. We're relying on
     * the behavior of the LLInstance subclasses here, and we're not
     * catching the exceptions they throw.
     */
    private static class RecArrayValue implements Value {
        private static final Logger LOGGER =
            Logger.getLogger(LivelinkQueryTraversalManager.class.getName());
        private final RecArray recArray;
        private final int row;
        private final Field column;
        RecArrayValue(RecArray recArray, int row, Field column) {
            this.recArray = recArray;
            this.row = row;
            this.column = column;
            if (column.propertyName.equals(SpiConstants.PROPNAME_DOCID) ||
                column.propertyName.equals(SpiConstants.PROPNAME_LASTMODIFY)) {
                LOGGER.fine("COLUMN: " + column.propertyName + " = " +
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
        public InputStream getStream() {
            return null;
        }
        public String getString() {
            if (column.fieldType == ValueType.DATE)
                return toIso8601String(recArray.toDate(row, column.fieldName));
            else
                return recArray.toString(row, column.fieldName);
        }
        public ValueType getType() throws RepositoryException {
            return column.fieldType;
        }
    }
    
    
    /*
     * TODO: Value conversion across types.
     */
    private static class InputStreamValue implements Value {
        private static final Logger LOGGER =
            Logger.getLogger(LivelinkQueryTraversalManager.class.getName());
        private final InputStream in;
        InputStreamValue(InputStream in) {
            this.in = in;
        }
        public boolean getBoolean() {
            throw new IllegalArgumentException();
        }
        public Calendar getDate() {
            throw new IllegalArgumentException();
        }
        public double getDouble() {
            throw new IllegalArgumentException();
        }
        public long getLong() {
            throw new IllegalArgumentException();
        }
        public InputStream getStream() {
            return in;
        }
        public String getString() throws RepositoryException {
            byte[] buffer = new byte[32];
            try {
                int count = in.read(buffer);
                return new String(buffer, 0, count);
            } catch (IOException e) {
                throw new LivelinkException(e, LOGGER);
            }
        }
        public ValueType getType() throws RepositoryException {
            return ValueType.BINARY;
        }
    }


    /*
     * TODO: DateFormats are not synchronized, and presumably neither
     * are their associated calendars. So we need to create an
     * instance per thread, or synchronize access, or perhaps create
     * per ResultSet instances which are then synchronized. The
     * benefit of the last is that concurrent access to a ResultSet is
     * unlikely, and single-thread synchronization is relatively fast.
     */
    private static final Calendar GMT_CALENDAR =
        Calendar.getInstance(TimeZone.getTimeZone("GMT+0"));

    /**
     * Livelink only stores timestamps to the nearest second, but LAPI
     * constructs a Date object that includes milliseconds, which are
     * taken from the current time. So we need to avoid using the
     * milliseconds in the parameter.
     *
     * @param value a Livelink date
     * @return an ISO 8601 formatted string representation,
     *     using the pattern "yyyy-MM-dd'T'HH:mm:ss'Z'"
     */
    /*
     * TODO: LAPI converts the database local time to UTC using the
     * default Java time zone, so if the database time zone is different
     * from the Java time zone, we need to adjust the given Date
     * accordingly. In order to account for Daylight Savings, we
     * probably need to subtract the offsets for the date under both
     * time zones and apply the resulting adjustment (in milliseconds)
     * to the Date object.
     */
    private static String toIso8601String(Date value)
    {
        SimpleDateFormat iso8601 =
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        iso8601.setCalendar(GMT_CALENDAR);
        return iso8601.format(value);
    }

    /**
     * Converts a local time date to an ISO SQL local time string.
     *
     * @param value a timestamp where local time is database local time
     * @return an ISO SQL string using the pattern
     * "yyyy-MM-dd' 'HH:mm:ss"
     */
    private static String toSqlString(Date value) {
        SimpleDateFormat sql = new SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ss");
        return sql.format(value);
    }
}
