// Copyright (C) 2006-2007 Google Inc.

package com.google.enterprise.connector.otex;

import java.io.InputStream;
import java.io.IOException;
import java.util.Calendar;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.Value;
import com.google.enterprise.connector.spi.ValueType;

/*
 * TODO: Value conversion across types.
 */
class InputStreamValue implements Value {
    private static final Logger LOGGER =
        Logger.getLogger(LivelinkQueryTraversalManager.class.getName());

    private final InputStream in;

    InputStreamValue(InputStream in) {
        this.in = in;
    }

    public boolean getBoolean() {
        throw new IllegalArgumentException();
    }

    public Calendar getDate() {
        throw new IllegalArgumentException();
    }

    public double getDouble() {
        throw new IllegalArgumentException();
    }

    public long getLong() {
        throw new IllegalArgumentException();
    }

    public InputStream getStream() {
        return in;
    }

    public String getString() throws RepositoryException {
        byte[] buffer = new byte[32];
        try {
            int count = in.read(buffer);
            return new String(buffer, 0, count);
        } catch (IOException e) {
            throw new LivelinkException(e, LOGGER);
        }
    }

    public ValueType getType() throws RepositoryException {
        return ValueType.BINARY;
    }
}
