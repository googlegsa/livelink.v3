// Copyright (C) 2006-2007 Google Inc.

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
    private final Logger logger;

    private final LLValue value;

    /**
     * Wraps an <code>LLValue</code>.
     *
     * @param logger a logger instance to use
     * @param value the value to wrap
     */
    LapiRecArray(Logger logger, LLValue value) {
        this.logger = logger;
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
            throw new LapiException(e, logger);
        }
    }

    /** {@inheritDoc} */
    public RecArray toValue(int row, String field)
            throws RepositoryException {
        try {
            return new LapiRecArray(logger, value.toValue(row, field));
        } catch (LLIllegalOperationException e) {
            throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new LapiException(e, logger);
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
            throw new LapiException(e, logger);
        }
    }

    /** {@inheritDoc} */
    public Date toDate(int row, String field) throws RepositoryException {
        try {
            return value.toDate(row, field);
        } catch (LLIllegalOperationException e) {
            throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new LapiException(e, logger);
        }
    }

    /** {@inheritDoc} */
    public double toDouble(int row, String field) throws RepositoryException {
        try {
            return value.toDouble(row, field);
        } catch (LLIllegalOperationException e) {
            throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new LapiException(e, logger);
        }
    }

    /** {@inheritDoc} */
    public int toInteger(int row, String field) throws RepositoryException {
        try {
            return value.toInteger(row, field);
        } catch (LLIllegalOperationException e) {
            throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new LapiException(e, logger);
        }
    }

    /** {@inheritDoc} */
    public String toString(int row, String field) throws RepositoryException {
        try {
            return value.toString(row, field);
        } catch (LLIllegalOperationException e) {
            throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new LapiException(e, logger);
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
            throw new LapiException(e, logger);
        }
    }

    /** {@inheritDoc} */
    public RecArray toValue(String field) throws RepositoryException {
        try {
            return new LapiRecArray(logger, value.toValue(field));
        } catch (LLIllegalOperationException e) {
            throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new LapiException(e, logger);
        }
    }

    /** {@inheritDoc} */
    public boolean toBoolean(String field) throws RepositoryException {
        try {
            return value.toBoolean(field);
        } catch (LLIllegalOperationException e) {
            throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new LapiException(e, logger);
        }
    }

    /** {@inheritDoc} */
    public Date toDate(String field) throws RepositoryException {
        try {
            return value.toDate(field);
        } catch (LLIllegalOperationException e) {
            throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new LapiException(e, logger);
        }
    }

    /** {@inheritDoc} */
    public double toDouble(String field) throws RepositoryException {
        try {
            return value.toDouble(field);
        } catch (LLIllegalOperationException e) {
            throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new LapiException(e, logger);
        }
    }

    /** {@inheritDoc} */
    public int toInteger(String field) throws RepositoryException {
        try {
            return value.toInteger(field);
        } catch (LLIllegalOperationException e) {
            throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new LapiException(e, logger);
        }
    }

    /** {@inheritDoc} */
    public String toString(String field) throws RepositoryException {
        try {
            return value.toString(field);
        } catch (LLIllegalOperationException e) {
            throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new LapiException(e, logger);
        }
    }

    /** {@inheritDoc} */
    public boolean isDefined() throws RepositoryException {
        try {
            return value.isDefined();
        } catch (LLIllegalOperationException e) {
            throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new LapiException(e, logger);
        }
    }

    /** {@inheritDoc} */
    public boolean toBoolean() throws RepositoryException {
        try {
            return value.toBoolean();
        } catch (LLIllegalOperationException e) {
            throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new LapiException(e, logger);
        }
    }

    /** {@inheritDoc} */
    public Date toDate() throws RepositoryException {
        try {
            return value.toDate();
        } catch (LLIllegalOperationException e) {
            throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new LapiException(e, logger);
        }
    }

    /** {@inheritDoc} */
    public double toDouble() throws RepositoryException {
        try {
            return value.toDouble();
        } catch (LLIllegalOperationException e) {
            throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new LapiException(e, logger);
        }
    }

    /** {@inheritDoc} */
    public int toInteger() throws RepositoryException {
        try {
            return value.toInteger();
        } catch (LLIllegalOperationException e) {
            throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new LapiException(e, logger);
        }
    }

    /** {@inheritDoc} */
    public String toString2() throws RepositoryException {
        try {
            return value.toString();
        } catch (LLIllegalOperationException e) {
            throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new LapiException(e, logger);
        }
    }
}
