// Copyright (C) 2006-2007 Google Inc.

package com.google.enterprise.connector.otex;

import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Map;

import com.google.enterprise.connector.spi.PropertyMap;
import com.google.enterprise.connector.spi.QueryTraversalManager;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.ResultSet;
import com.google.enterprise.connector.spi.SpiConstants;
import com.google.enterprise.connector.spi.ValueType;
import com.google.enterprise.connector.otex.client.Client;
import com.google.enterprise.connector.otex.client.ClientFactory;
import com.google.enterprise.connector.otex.client.RecArray;

class LivelinkQueryTraversalManager implements QueryTraversalManager {

    private static final Logger LOGGER =
        Logger.getLogger(LivelinkQueryTraversalManager.class.getName());

    /**
     * The primary store for property names that we want to map from
     * the database to the PropertyMap. This is an array of
     * <code>Field</code> objects that include the record array field
     * name, the record array field type, and the the GSA property
     * name. If the property name is <code>null</code>, then the field
     * will not be returned in the property map.
     */
    private static final Field[] FIELDS;

    static {
        // ListNodes requires the DataID and PermID columns to be
        // included here. This implementation requires DataID,
        // OwnerID, ModifyDate, MimeType, and DataSize.
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
            "SubType", ValueType.LONG, "SubType"));

        list.add(new Field(
            "DataSize", ValueType.LONG, null));
        list.add(new Field(
            "PermID", ValueType.LONG, null));
        
        FIELDS = (Field[]) list.toArray(new Field[0]);
    }


    /** The connector contains configuration information. */
    private final LivelinkConnector connector;

    /** The client provides access to the server. */
    private final Client client;

    /** A concrete strategy for retrieving the content from the server. */
    private final ContentHandler contentHandler;
    
    /** The number of results to return in each batch. */
    /* TODO: Configurable default value. */
    private int batchSize = 100;

    /* TODO: Autodetection of the database type plus a config parameter. */
    private boolean isSqlServer = true;


    LivelinkQueryTraversalManager(LivelinkConnector connector,
            ClientFactory clientFactory) throws RepositoryException {
        this.connector = connector;
        client = clientFactory.createClient();

        String contentHandlerClass = connector.getContentHandler();
        try {
            contentHandler = (ContentHandler)
                Class.forName(contentHandlerClass).newInstance();
        } catch (Exception e) {
            throw new LivelinkException(e, LOGGER);
        }
        contentHandler.initialize(connector, client, LOGGER);
    }


    /**
     * Sets the batch size. This implementation limits the actual
     * batch size to 100,000.
     *
     * @param hint the new batch size
     * @throws IllegalArgumentException if the hint is less than zero
     */
    public void setBatchHint(int hint) {
        if (hint < 0)
            throw new IllegalArgumentException();
        else if (hint == 0)
            batchSize = 100; // We could ignore it, but we reset the default.
        else if (hint > 100000) // TODO: Configurable limit.
            batchSize = 100000;
        else
            batchSize = hint;
    }


    /** {@inheritDoc} */
    public ResultSet startTraversal() throws RepositoryException {
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("START @" +
                Integer.toHexString(System.identityHashCode(this)));
        }
        return listNodes(null);
    }


    /**
     * {@inheritDoc}
     * <p>
     * This implementation gets a string of the form "yyyy-MM-dd
     * HH:mm:ss,nnnnnn" where nnnnnn is the object ID of the item
     * represented by the property map.
     *
     * @param pm a property map
     * @return a checkpoint string for the given property map
     */
    public String checkpoint(PropertyMap pm) throws RepositoryException {
        String s = ((RecArrayResultSet.RecArrayPropertyMap) pm).checkpoint();
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("CHECKPOINT: " + s + " @" +
                Integer.toHexString(System.identityHashCode(this)));
        }
        return s;
    }


    /** {@inheritDoc} */
    public ResultSet resumeTraversal(String checkpoint)
            throws RepositoryException {
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("RESUME: " + checkpoint + " @" +
                Integer.toHexString(System.identityHashCode(this)));
        }
        return listNodes(checkpoint);
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
    private ResultSet listNodes(String checkpoint) throws RepositoryException
    {
        RecArray recArray;
        if (isSqlServer)
            recArray = listNodesSqlServer(checkpoint);
        else
            recArray = listNodesOracle(checkpoint);
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("RESULTSET: " + recArray.size() + " rows. @" +
                Integer.toHexString(System.identityHashCode(this)));
        }
        return new RecArrayResultSet(connector, client, contentHandler,
            recArray, FIELDS);
    }

    private static final String ORDER_BY = " order by ModifyDate, DataID";

    private static final String SUBTYPES = "SubType not in " +
        "(137,142,143,148,150,154,161,162,201,203,209,210,211)";

    // TODO: We need to get these volume IDs programmatically.
    private final String ancestors = "DataID not in " +
        "(select DataID from DTreeAncestors where AncestorID in (2001,2313))";

    private final String excluded = SUBTYPES + " and " + ancestors;

    
    private RecArray listNodesSqlServer(String checkpoint)
            throws RepositoryException {
        // TODO: This includes just a sketch of a subtype and volume type
        // restriction. Note that this code only handles SQL Server.
        String query;
        if (checkpoint == null)
            //query = "1=1" + ORDER_BY;
            query = excluded + ORDER_BY;
        else
            //query = getRestriction(checkpoint) + ORDER_BY;
            query = excluded + " and " + getRestriction(checkpoint) + ORDER_BY;
        String view = "WebNodes";

        // FIXME: This code is working around fields with null
        // field names. This turns into "top 100 null, null, ...".
        String[] columns = new String[FIELDS.length];
        columns[0] = "top " + batchSize + " " + FIELDS[0].fieldName;
        for (int i = 1; i < FIELDS.length; i++)
            columns[i] = FIELDS[i].fieldName + "";

        return client.ListNodes(LOGGER, query, view, columns);
    }


    /*
     * XXX: We could use FIRST_ROWS(<batchSize>), but I don't know how
     * important that is on this query.
     */
    private RecArray listNodesOracle(String checkpoint)
            throws RepositoryException {
        String query = "rownum <= " + batchSize;
        StringBuffer buffer = new StringBuffer();
        buffer.append("(select ");

        // FIXME: This code is working around fields with null
        // field names. This turns into "null, null, ...".
        buffer.append(FIELDS[0].fieldName);
        for (int i = 1; i < FIELDS.length; i++) {
            buffer.append(',');
            buffer.append(FIELDS[i].fieldName);
        }

        buffer.append(" from WebNodes ");
        if (checkpoint != null) {
            buffer.append("where ");
            buffer.append(getRestriction(checkpoint));
        }
        buffer.append(ORDER_BY);
        buffer.append(')');
        String view = buffer.toString();
        String[] columns = new String[] { "*" };

        return client.ListNodes(LOGGER, query, view, columns);
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
    private String getRestriction(String checkpoint)
            throws RepositoryException {
        int index = checkpoint.indexOf(',');
        if (index == -1) {
            throw new LivelinkException("Invalid checkpoint " + checkpoint,
                LOGGER);
        } else {
            String ts = isSqlServer ? "" : "TIMESTAMP";
            String modifyDate = checkpoint.substring(0, index);
            String dataId = checkpoint.substring(index + 1);
            return "ModifyDate > " + ts + '\'' + modifyDate + 
                "' or (ModifyDate = " + ts + '\'' + modifyDate +
                "' and DataID > " + dataId + ')';
        }
    }
}
