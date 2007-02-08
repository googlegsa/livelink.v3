// Copyright (C) 2006-2007 Google Inc.

package com.google.enterprise.connector.otex.client.lapi;

import java.util.Date;
import java.util.logging.Logger;

import com.opentext.api.LLIllegalOperationException;
import com.opentext.api.LLValue;

import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.otex.client.RecArray;

/**
 * A wrapper implementation on an <code>LLValue</code> containing a recarray.
 */
public final class LapiRecArray implements RecArray {
    private final Logger logger;

    private final LLValue value;

    /**
     * Wraps an <code>LLValue</code> containing a recarray.
     *
     * @param value the recarray
     * @throws IllegalArgumentException if the value is not a recarray
     */
    LapiRecArray(Logger logger, LLValue value) {
        if (value.type() != LLValue.LL_TABLE)
            throw new IllegalArgumentException();
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
}
