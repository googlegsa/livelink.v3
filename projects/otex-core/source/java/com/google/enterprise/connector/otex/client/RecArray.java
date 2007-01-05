// Copyright (C) 2006-2007 Google Inc.

package com.google.enterprise.connector.otex.client;

import java.util.Date;

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

    boolean toBoolean(int row, String field);
    Date toDate(int row, String field);
    double toDouble(int row, String field);
    int toInteger(int row, String field);
    String toString(int row, String field);
}
