// Copyright (C) 2006-2007 Google Inc.

package com.google.enterprise.connector.otex.client.mock;

import java.util.Date;

import com.google.enterprise.connector.otex.client.RecArray;

/**
 * A mock implementation of an empty <code>RecArray</code>. The size
 * is always zero, and the getter methods always throw an
 * <code>IllegalArgumentException</code>.
 */
public final class MockRecArray implements RecArray {
    MockRecArray() {
    }
    
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
