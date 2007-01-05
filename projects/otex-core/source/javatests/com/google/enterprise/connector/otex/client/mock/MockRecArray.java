// Copyright (C) 2006-2007 Google Inc.

package com.google.enterprise.connector.otex.client.mock;

import java.util.Date;

import com.google.enterprise.connector.otex.client.RecArray;

/**
 * A wrapper implementation on an <code>LLValue</code> containing a recarray.
 */
public final class MockRecArray implements RecArray {
    MockRecArray() {
    }
    
    /** {@inheritDoc} */
    public int size() {
        return 0;
    }

    public boolean toBoolean(int row, String field) {
        throw new IllegalArgumentException();
    }

    public Date toDate(int row, String field) {
        throw new IllegalArgumentException();
    }

    public double toDouble(int row, String field) {
        throw new IllegalArgumentException();
    }

    public int toInteger(int row, String field) {
        throw new IllegalArgumentException();
    }

    public String toString(int row, String field) {
        throw new IllegalArgumentException();
    }
}
