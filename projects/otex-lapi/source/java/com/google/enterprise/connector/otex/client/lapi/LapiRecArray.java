// Copyright (C) 2006-2007 Google Inc.

package com.google.enterprise.connector.otex.client.lapi;

import java.util.Date;

import com.opentext.api.LLValue;

import com.google.enterprise.connector.otex.client.RecArray;

/**
 * A wrapper implementation on an <code>LLValue</code> containing a recarray.
 */
public final class LapiRecArray implements RecArray {
    private final LLValue value;

    /**
     * Wraps an <code>LLValue</code> containing a recarray.
     *
     * @param value the recarray
     * @throws IllegalArgumentException if the value is not a recarray
     */
    LapiRecArray(LLValue value) {
        if (value.type() != LLValue.LL_TABLE)
            throw new IllegalArgumentException();
        this.value = value;
    }
    
    /** {@inheritDoc} */
    public int size() {
        return value.size();
    }

    /** {@inheritDoc} */
    public boolean toBoolean(int row, String field) {
        return value.toBoolean(row, field);
    }

    /** {@inheritDoc} */
    public Date toDate(int row, String field) {
        return value.toDate(row, field);
    }

    /** {@inheritDoc} */
    public double toDouble(int row, String field) {
        return value.toDouble(row, field);
    }

    /** {@inheritDoc} */
    public int toInteger(int row, String field) {
        return value.toInteger(row, field);
    }

    /** {@inheritDoc} */
    public String toString(int row, String field) {
        return value.toString(row, field);
    }
}
