// Copyright (C) 2006-2007 Google Inc.

package com.google.enterprise.connector.otex.client;

import java.util.Date;

import com.google.enterprise.connector.spi.RepositoryException;

/**
 * A table-like interface for the database results.
 */
public interface RecArray {
    /**
     * Gets the number of records in this recarray.
     *
     * @return the number of records
     */
    int size();

    /**
     * Gets whether the named field from the given row has a defined value.
     *
     * @param row a zero-based row index
     * @param field a field name
     * @return <code>true</code> if the value is defined, or
     * <code>false</code> if the value is <code>Undefined</code>.
     */
    boolean isDefined(int row, String field) throws RepositoryException;
    
    /**
     * Gets the named field from the given row as a boolean value.
     *
     * @param row a zero-based row index
     * @param field a field name
     * @return a boolean field value
     */
    boolean toBoolean(int row, String field) throws RepositoryException;

    /**
     * Gets the named field from the given row as a
     * <code>java.util.Date</code> value.
     *
     * @param row a zero-based row index
     * @param field a field name
     * @return a <code>Date</code> field value
     */
    Date toDate(int row, String field) throws RepositoryException;

    /**
     * Gets the named field from the given row as a double value.
     *
     * @param row a zero-based row index
     * @param field a field name
     * @return a double field value
     */
    double toDouble(int row, String field) throws RepositoryException;

    /**
     * Gets the named field from the given row as an integer value.
     *
     * @param row a zero-based row index
     * @param field a field name
     * @return an integer field value
     */
    int toInteger(int row, String field) throws RepositoryException;

    /**
     * Gets the named field from the given row as a string value.
     *
     * @param row a zero-based row index
     * @param field a field name
     * @return a string field value
     */
    String toString(int row, String field) throws RepositoryException;
}
