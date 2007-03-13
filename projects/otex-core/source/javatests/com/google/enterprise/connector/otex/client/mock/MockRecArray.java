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

package com.google.enterprise.connector.otex.client.mock;

import java.util.Date;

import com.google.enterprise.connector.otex.client.RecArray;

/**
 * A mock implementation of an empty <code>RecArray</code>. The size
 * is always zero, and the getter methods always throw an
 * <code>IllegalArgumentException</code>.
 */
public final class MockRecArray implements RecArray {
    private final String[] fieldNames;
    private final Object[][] values;

    MockRecArray(String[] fieldNames, Object[][] values) {
        if (values.length > 0 && (fieldNames.length != values[0].length))
            throw new IllegalArgumentException();
        this.fieldNames = fieldNames;
        this.values = values;
    }

    private Object getValue(int row, String field) {
        if (row < 0 || row > values.length)
            throw new IllegalArgumentException(String.valueOf(row));
        for (int i = 0; i < fieldNames.length; i++) {
            if (fieldNames[i].equals(field))
                return values[row][i];
        }
        throw new IllegalArgumentException(field);
    }
    
    public int size() {
        return values.length;
    }

    public boolean isDefined(int row, String field) {
        throw new IllegalArgumentException();
    }
    
    public RecArray toValue(int row, String field) {
        throw new IllegalArgumentException();
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
        return getValue(row, field).toString();
    }
    
    public boolean isDefined(String field) {
        throw new IllegalArgumentException();
    }
    
    public RecArray toValue(String field) {
        throw new IllegalArgumentException();
    }
    
    public boolean toBoolean(String field) {
        throw new IllegalArgumentException();
    }

    public Date toDate(String field) {
        throw new IllegalArgumentException();
    }

    public double toDouble(String field) {
        throw new IllegalArgumentException();
    }

    public int toInteger(String field) {
        throw new IllegalArgumentException();
    }

    public String toString(String field) {
        throw new IllegalArgumentException();
    }
    
    public boolean isDefined() {
        throw new IllegalArgumentException();
    }
    
    public boolean toBoolean() {
        throw new IllegalArgumentException();
    }

    public Date toDate() {
        throw new IllegalArgumentException();
    }

    public double toDouble() {
        throw new IllegalArgumentException();
    }

    public int toInteger() {
        throw new IllegalArgumentException();
    }

    public String toString2() {
        throw new IllegalArgumentException();
    }
}
