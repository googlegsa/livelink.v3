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

package com.google.enterprise.connector.otex.client.lapi;

import java.io.ByteArrayInputStream;
import java.io.PushbackInputStream;
import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.Enumeration;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.opentext.api.LLIllegalOperationException;
import com.opentext.api.LLInputStream;
import com.opentext.api.LLValue;

import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.otex.client.ClientValue;

/**
 * A wrapper implementation on an <code>LLValue</code>.
 */
/*
 * TODO: Retrieving values by index rather than by string is about ten
 * times faster. Even building a map and doing a map lookup before
 * each call is five times faster.
 */
public final class LapiClientValue implements ClientValue {
    /** The logger for this class. */
    private static final Logger LOGGER =
        Logger.getLogger(LapiClientValue.class.getName());


    static {
        // Verify that the ClientValue class constants are correct.
        assert ASSOC == LLValue.LL_ASSOC : LLValue.LL_ASSOC;
        assert BOOLEAN == LLValue.LL_BOOLEAN : LLValue.LL_BOOLEAN;
        assert DATE == LLValue.LL_DATE : LLValue.LL_DATE;
        assert DOUBLE == LLValue.LL_DOUBLE : LLValue.LL_DOUBLE;
        assert ERROR == LLValue.LL_ERROR : LLValue.LL_ERROR;
        assert INTEGER == LLValue.LL_INTEGER : LLValue.LL_INTEGER;
        assert LIST == LLValue.LL_LIST : LLValue.LL_LIST;
        assert NOTSET == LLValue.LL_NOTSET : LLValue.LL_NOTSET;
        assert RECORD == LLValue.LL_RECORD : LLValue.LL_RECORD;
        assert STRING == LLValue.LL_STRING : LLValue.LL_STRING;
        assert TABLE == LLValue.LL_TABLE : LLValue.LL_TABLE;
        assert UNDEFINED == LLValue.LL_UNDEFINED : LLValue.LL_UNDEFINED;
    }
    

    /** The Actual LAPI Value object encapsulated by this ClientValue */
    private final LLValue value;

    
    /**
     * Wraps an <code>LLValue</code>.
     *
     * @param value the value to wrap
     */
    LapiClientValue(LLValue value) {
        this.value = value;
    }
    

    /**
     * Quick way to get the LLValue from a ClientValue.
     * Best used when passing an LLValue back and forth
     * accross the Client API.
     */
    LLValue getLLValue() {
        return this.value;
    }

    /** {@inheritDoc} */
    public int size() {
        return value.size();
    }

    /** {@inheritDoc} */
    public void setSize(int size) throws RepositoryException {
        try {
            value.setSize(size);
        } catch (LLIllegalOperationException e) {
            throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new LapiException(e, LOGGER);
        }
    }


    /** {@inheritDoc} */
    public void setInteger(int index, int val) throws RepositoryException {
        try {
            value.setInteger(index, val);
        } catch (LLIllegalOperationException e) {
            throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new LapiException(e, LOGGER);
        }
    }

    /** {@inheritDoc} */
    public int type() {
        return value.type();
    }
    
    /** {@inheritDoc} */
    public Enumeration enumerateNames() {
        final Enumeration names = value.enumerateNames();
        return new Enumeration() {
                public boolean hasMoreElements() {
                    return names.hasMoreElements();
                }
                public Object nextElement() {
                    return names.nextElement().toString();
                }
            };
    }

    /** {@inheritDoc} */
    public boolean isDefined(int row, String field)
            throws RepositoryException {
        try {
            return value.toValue(row, field).isDefined();
        } catch (LLIllegalOperationException e) {
            throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new LapiException(e, LOGGER);
        }
    }

    /** {@inheritDoc} */
    public boolean hasValue() {
        try {
            if (this.value == null)
                return false;
            int dataType = this.value.type();
            if (LLValue.LL_UNDEFINED == dataType ||
                LLValue.LL_ERROR == dataType ||
                LLValue.LL_NOTSET == dataType)
                return false;
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
    

    /** {@inheritDoc} */
    public ClientValue stringToValue() throws RepositoryException {
        String s = value.toString();
        try {
            LLValue sv = LLValue.crack(
                new LLInputStream(
                    new PushbackInputStream(
                        new ByteArrayInputStream(s.getBytes("UTF-8"))),
                    "UTF-8"));
            if (LOGGER.isLoggable(Level.FINEST))
                LOGGER.finest("Converted: " + s + " to: " + sv);
            return new LapiClientValue(sv);
        } catch (UnsupportedEncodingException e) {
            // This can't happen.
            RuntimeException re = new IllegalArgumentException();
            re.initCause(e);
            throw re;
        } catch (RuntimeException e) {
            throw new LapiException(e, LOGGER);
        }
    }
    
    /** {@inheritDoc} */
    public ClientValue toValue(int row, String field)
            throws RepositoryException {
        try {
            return new LapiClientValue(value.toValue(row, field));
        } catch (LLIllegalOperationException e) {
            throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new LapiException(e, LOGGER);
        }
    }

    /** {@inheritDoc} */
    public boolean toBoolean(int row, String field)
            throws RepositoryException {
        try {
            return value.toBoolean(row, field);
        } catch (LLIllegalOperationException e) {
            throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new LapiException(e, LOGGER);
        }
    }

    /** {@inheritDoc} */
    public Date toDate(int row, String field) throws RepositoryException {
        try {
            return value.toDate(row, field);
        } catch (LLIllegalOperationException e) {
            throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new LapiException(e, LOGGER);
        }
    }

    /** {@inheritDoc} */
    public double toDouble(int row, String field) throws RepositoryException {
        try {
            return value.toDouble(row, field);
        } catch (LLIllegalOperationException e) {
            throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new LapiException(e, LOGGER);
        }
    }

    /** {@inheritDoc} */
    public int toInteger(int row, String field) throws RepositoryException {
        try {
            return value.toInteger(row, field);
        } catch (LLIllegalOperationException e) {
            throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new LapiException(e, LOGGER);
        }
    }

    /** {@inheritDoc} */
    public String toString(int row, String field) throws RepositoryException {
        try {
            return value.toString(row, field);
        } catch (LLIllegalOperationException e) {
            throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new LapiException(e, LOGGER);
        }
    }

    /** {@inheritDoc} */
    public boolean isDefined(String field)
            throws RepositoryException {
        try {
            return value.toValue(field).isDefined();
        } catch (LLIllegalOperationException e) {
            throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new LapiException(e, LOGGER);
        }
    }

    /** {@inheritDoc} */
    public ClientValue toValue(String field) throws RepositoryException {
        try {
            return new LapiClientValue(value.toValue(field));
        } catch (LLIllegalOperationException e) {
            throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new LapiException(e, LOGGER);
        }
    }

    /** {@inheritDoc} */
    public boolean toBoolean(String field) throws RepositoryException {
        try {
            return value.toBoolean(field);
        } catch (LLIllegalOperationException e) {
            throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new LapiException(e, LOGGER);
        }
    }

    /** {@inheritDoc} */
    public Date toDate(String field) throws RepositoryException {
        try {
            return value.toDate(field);
        } catch (LLIllegalOperationException e) {
            throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new LapiException(e, LOGGER);
        }
    }

    /** {@inheritDoc} */
    public double toDouble(String field) throws RepositoryException {
        try {
            return value.toDouble(field);
        } catch (LLIllegalOperationException e) {
            throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new LapiException(e, LOGGER);
        }
    }

    /** {@inheritDoc} */
    public int toInteger(String field) throws RepositoryException {
        try {
            return value.toInteger(field);
        } catch (LLIllegalOperationException e) {
            throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new LapiException(e, LOGGER);
        }
    }

    /** {@inheritDoc} */
    public String toString(String field) throws RepositoryException {
        try {
            return value.toString(field);
        } catch (LLIllegalOperationException e) {
            throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new LapiException(e, LOGGER);
        }
    }

    /** {@inheritDoc} */
    public boolean isDefined(int index) throws RepositoryException {
        try {
            return value.toValue(index).isDefined();
        } catch (LLIllegalOperationException e) {
            throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new LapiException(e, LOGGER);
        }
    }

    /** {@inheritDoc} */
    public ClientValue toValue(int index) throws RepositoryException {
        try {
            return new LapiClientValue(value.toValue(index));
        } catch (LLIllegalOperationException e) {
            throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new LapiException(e, LOGGER);
        }
    }

    /** {@inheritDoc} */
    public boolean toBoolean(int index) throws RepositoryException {
        try {
            return value.toBoolean(index);
        } catch (LLIllegalOperationException e) {
            throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new LapiException(e, LOGGER);
        }
    }

    /** {@inheritDoc} */
    public Date toDate(int index) throws RepositoryException {
        try {
            return value.toDate(index);
        } catch (LLIllegalOperationException e) {
            throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new LapiException(e, LOGGER);
        }
    }

    /** {@inheritDoc} */
    public double toDouble(int index) throws RepositoryException {
        try {
            return value.toDouble(index);
        } catch (LLIllegalOperationException e) {
            throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new LapiException(e, LOGGER);
        }
    }

    /** {@inheritDoc} */
    public int toInteger(int index) throws RepositoryException {
        try {
            return value.toInteger(index);
        } catch (LLIllegalOperationException e) {
            throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new LapiException(e, LOGGER);
        }
    }

    /** {@inheritDoc} */
    public String toString(int index) throws RepositoryException {
        try {
            return value.toString(index);
        } catch (LLIllegalOperationException e) {
            throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new LapiException(e, LOGGER);
        }
    }

    /** {@inheritDoc} */
    public boolean isDefined() throws RepositoryException {
        try {
            return value.isDefined();
        } catch (LLIllegalOperationException e) {
            throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new LapiException(e, LOGGER);
        }
    }

    /** {@inheritDoc} */
    public boolean toBoolean() throws RepositoryException {
        try {
            return value.toBoolean();
        } catch (LLIllegalOperationException e) {
            throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new LapiException(e, LOGGER);
        }
    }

    /** {@inheritDoc} */
    public Date toDate() throws RepositoryException {
        try {
            return value.toDate();
        } catch (LLIllegalOperationException e) {
            throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new LapiException(e, LOGGER);
        }
    }

    /** {@inheritDoc} */
    public double toDouble() throws RepositoryException {
        try {
            return value.toDouble();
        } catch (LLIllegalOperationException e) {
            throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new LapiException(e, LOGGER);
        }
    }

    /** {@inheritDoc} */
    public int toInteger() throws RepositoryException {
        try {
            return value.toInteger();
        } catch (LLIllegalOperationException e) {
            throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new LapiException(e, LOGGER);
        }
    }

    /** {@inheritDoc} */
    public String toString2() throws RepositoryException {
        try {
            return value.toString(); // + " (type = " + value.type() + ")";
        } catch (LLIllegalOperationException e) {
            throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new LapiException(e, LOGGER);
        }
    }

    /** {@inheritDoc} */
    public int add(String key, boolean obj) throws RepositoryException {
        try {
            return this.value.add(key, obj);
        } catch (LLIllegalOperationException e) {
            throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new LapiException(e, LOGGER);
        }
    }
    public int add(String key, char obj) throws RepositoryException {
        try {
            return this.value.add(key, obj);
        } catch (LLIllegalOperationException e) {
            throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new LapiException(e, LOGGER);
        }
    }
    public int add(String key, int obj) throws RepositoryException {
        try {
            return this.value.add(key, obj);
        } catch (LLIllegalOperationException e) {
            throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new LapiException(e, LOGGER);
        }
    }
    public int add(String key, long obj) throws RepositoryException {
        try {
            return this.value.add(key, obj);
        } catch (LLIllegalOperationException e) {
            throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new LapiException(e, LOGGER);
        }
    }
    public int add(String key, float obj) throws RepositoryException {
        try {
            return this.value.add(key, obj);
        } catch (LLIllegalOperationException e) {
            throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new LapiException(e, LOGGER);
        }
    }
    public int add(String key, double obj) throws RepositoryException {
        try {
            return this.value.add(key, obj);
        } catch (LLIllegalOperationException e) {
            throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new LapiException(e, LOGGER);
        }
    }
    public int add(String key, Boolean obj) throws RepositoryException {
        try {
            return this.value.add(key, obj);
        } catch (LLIllegalOperationException e) {
            throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new LapiException(e, LOGGER);
        }
    }
    public int add(String key, Double obj) throws RepositoryException {
        try {
            return this.value.add(key, obj);
        } catch (LLIllegalOperationException e) {
            throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new LapiException(e, LOGGER);
        }
    }
    public int add(String key, Float obj) throws RepositoryException {
        try {
            return this.value.add(key, obj);
        } catch (LLIllegalOperationException e) {
            throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new LapiException(e, LOGGER);
        }
    }
    public int add(String key, Integer obj) throws RepositoryException {
        try {
            return this.value.add(key, obj);
        } catch (LLIllegalOperationException e) {
            throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new LapiException(e, LOGGER);
        }
    }
    public int add(String key, Long obj) throws RepositoryException {
        try {
            return this.value.add(key, obj);
        } catch (LLIllegalOperationException e) {
            throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new LapiException(e, LOGGER);
        }
    }
    public int add(String key, String obj) throws RepositoryException {
        try {
            return this.value.add(key, obj);
        } catch (LLIllegalOperationException e) {
            throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new LapiException(e, LOGGER);
        }
    }
    public int add(String key, java.util.Date obj) throws RepositoryException {
        try {
            return this.value.add(key, obj);
        } catch (LLIllegalOperationException e) {
            throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new LapiException(e, LOGGER);
        }
    }


    /** {@inheritDoc} */
    public int add(boolean obj) throws RepositoryException {
        try {
            return this.value.add(obj);
        } catch (LLIllegalOperationException e) {
            throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new LapiException(e, LOGGER);
        }
    }
    public int add(char obj) throws RepositoryException {
        try {
            return this.value.add(obj);
        } catch (LLIllegalOperationException e) {
            throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new LapiException(e, LOGGER);
        }
    }
    public int add(int obj) throws RepositoryException {
        try {
            return this.value.add(obj);
        } catch (LLIllegalOperationException e) {
            throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new LapiException(e, LOGGER);
        }
    }
    public int add(long obj) throws RepositoryException {
        try {
            return this.value.add(obj);
        } catch (LLIllegalOperationException e) {
            throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new LapiException(e, LOGGER);
        }
    }
    public int add(float obj) throws RepositoryException {
        try {
            return this.value.add(obj);
        } catch (LLIllegalOperationException e) {
            throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new LapiException(e, LOGGER);
        }
    }
    public int add(double obj) throws RepositoryException {
        try {
            return this.value.add(obj);
        } catch (LLIllegalOperationException e) {
            throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new LapiException(e, LOGGER);
        }
    }
    public int add(Boolean obj) throws RepositoryException {
        try {
            return this.value.add(obj);
        } catch (LLIllegalOperationException e) {
            throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new LapiException(e, LOGGER);
        }
    }
    public int add(Double obj) throws RepositoryException {
        try {
            return this.value.add(obj);
        } catch (LLIllegalOperationException e) {
            throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new LapiException(e, LOGGER);
        }
    }
    public int add(Float obj) throws RepositoryException {
        try {
            return this.value.add(obj);
        } catch (LLIllegalOperationException e) {
            throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new LapiException(e, LOGGER);
        }
    }
    public int add(Integer obj) throws RepositoryException {
        try {
            return this.value.add(obj);
        } catch (LLIllegalOperationException e) {
            throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new LapiException(e, LOGGER);
        }
    }
    public int add(Long obj) throws RepositoryException {
        try {
            return this.value.add(obj);
        } catch (LLIllegalOperationException e) {
            throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new LapiException(e, LOGGER);
        }
    }
    public int add(String obj) throws RepositoryException {
        try {
            return this.value.add(obj);
        } catch (LLIllegalOperationException e) {
            throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new LapiException(e, LOGGER);
        }
    }
    public int add(java.util.Date obj) throws RepositoryException {
        try {
            return this.value.add(obj);
        } catch (LLIllegalOperationException e) {
            throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new LapiException(e, LOGGER);
        }
    }

}
