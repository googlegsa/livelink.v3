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
import java.util.Enumeration;

import com.google.enterprise.connector.otex.client.ClientValue;

/**
 * A partial mock implementation of a <code>ClientValue</code>. Most of
 * the methods throw an <code>IllegalArgumentException</code>. The
 * <code>size</code>, <code>toString(int,String)</code>,
 * <code>toInteger(String)</code>, and<code>toString(String)</code>
 * methods are implemented.
 */
public final class MockClientValue implements ClientValue {
    private final String[] fieldNames;
    private final Object[][] tableValues;
    private final Object[] assocValues;

    MockClientValue(String[] fieldNames, Object[][] values) {
        if (values.length > 0 && (fieldNames.length != values[0].length))
            throw new IllegalArgumentException();
        this.fieldNames = fieldNames;
        this.tableValues = values;
        this.assocValues = null;
    }

    MockClientValue(String[] fieldNames, Object[] values) {
        if (fieldNames.length != values.length)
            throw new IllegalArgumentException();
        this.fieldNames = fieldNames;
        this.tableValues = null;
        this.assocValues = values;
    }

    private Object getField(Object[] values, String field) {
        for (int i = 0; i < fieldNames.length; i++) {
            if (fieldNames[i].equals(field))
                return values[i];
        }
        throw new IllegalArgumentException(field);
    }

    private Object getValue(int row, String field) {
        if (tableValues == null)
            throw new IllegalArgumentException("ClientValue is not a table.");
        if (row < 0 || row > tableValues.length)
            throw new IllegalArgumentException(String.valueOf(row));
        return getField(tableValues[row], field);
    }
    
    private Object getValue(String field) {
        if (assocValues == null)
            throw new IllegalArgumentException("ClientValue is not an assoc.");
        return getField(assocValues, field);
    }
    
    public int size() {
        if (tableValues != null)
            return tableValues.length;
        else
            return assocValues.length;
    }

    public int type() {
        if (tableValues != null)
            return TABLE;
        else
            return ASSOC;
    }
        
    public Enumeration enumerateNames() {
        return new Enumeration() {
                private int i = 0;
                public boolean hasMoreElements() {
                    return i < fieldNames.length;
                }
                public Object nextElement() {
                    return fieldNames[i++];
                }
            };
    }

    public ClientValue stringToValue() {
        return this;
    }

    public boolean isDefined(int row, String field) {
        throw new IllegalArgumentException();
    }
    
    public ClientValue toValue(int row, String field) {
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
    
    public ClientValue toValue(String field) {
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
        Object v = getValue(field);
        if (v instanceof Integer)
            return ((Integer) v).intValue();
        else
            return Integer.parseInt(v.toString());
    }

    public String toString(String field) {
        return getValue(field).toString();
    }
    
    public boolean isDefined(int index) {
        throw new IllegalArgumentException();
    }
    
    public ClientValue toValue(int index) {
        throw new IllegalArgumentException();
    }
    
    public boolean toBoolean(int index) {
        throw new IllegalArgumentException();
    }

    public Date toDate(int index) {
        throw new IllegalArgumentException();
    }

    public double toDouble(int index) {
        throw new IllegalArgumentException();
    }

    public int toInteger(int index) {
        throw new IllegalArgumentException();
    }

    public String toString(int index) {
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
