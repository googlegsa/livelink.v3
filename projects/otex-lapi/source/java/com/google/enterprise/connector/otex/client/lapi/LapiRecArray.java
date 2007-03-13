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

import java.util.Date;
import java.util.logging.Logger;

import com.opentext.api.LLIllegalOperationException;
import com.opentext.api.LLValue;

import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.otex.client.RecArray;

/**
 * A wrapper implementation on an <code>LLValue</code>.
 */
/*
 * TODO: Retrieving values by index rather than by string is about ten
 * times faster. Even building a map and doing a map lookup before
 * each call is five times faster.
 */
public final class LapiRecArray implements RecArray {
    /** The logger for this class. */
    private static final Logger LOGGER =
        Logger.getLogger(LapiRecArray.class.getName());

    private final LLValue value;

    /**
     * Wraps an <code>LLValue</code>.
     *
     * @param value the value to wrap
     */
    LapiRecArray(LLValue value) {
        this.value = value;
    }
    
    /** {@inheritDoc} */
    public int size() {
        return value.size();
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
    public RecArray toValue(int row, String field)
            throws RepositoryException {
        try {
            return new LapiRecArray(value.toValue(row, field));
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
    public RecArray toValue(String field) throws RepositoryException {
        try {
            return new LapiRecArray(value.toValue(field));
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
            return value.toString();
        } catch (LLIllegalOperationException e) {
            throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new LapiException(e, LOGGER);
        }
    }
}
