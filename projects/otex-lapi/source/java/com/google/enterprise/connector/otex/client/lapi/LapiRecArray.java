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

    LapiRecArray(LLValue value) {
        if (value.type() != LLValue.LL_TABLE)
            throw new IllegalArgumentException();
        this.value = value;
    }
    
    /** {@inheritDoc} */
    public int size() {
        return value.size();
    }

    public boolean toBoolean(int row, String field) {
        return value.toBoolean(row, field);
    }

    public Date toDate(int row, String field) {
        return value.toDate(row, field);
    }

    public double toDouble(int row, String field) {
        return value.toDouble(row, field);
    }

    public int toInteger(int row, String field) {
        return value.toInteger(row, field);
    }

    public String toString(int row, String field) {
        return value.toString(row, field);
    }
}
