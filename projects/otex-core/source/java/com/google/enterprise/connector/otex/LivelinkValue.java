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

package com.google.enterprise.connector.otex;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.TimeZone;

import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.SimpleValue;
import com.google.enterprise.connector.spi.SpiConstants;
import com.google.enterprise.connector.spi.Value;
import com.google.enterprise.connector.spi.ValueType;
import com.google.enterprise.connector.otex.client.Client;
import com.google.enterprise.connector.otex.client.ClientValue;

/**
 * {@inheritDoc}
 * <p>
 * This implementation provides a Google <code>Value</code> 
 * interface over atomic <code>ClientValue</code>s.  Complex
 * <code>ClientValue</code> objects (RecArrays or Lists), must be
 * decomposed into individual atomic components before wrapping
 * in a <code>LivelinkValue</code>.
 */

/*
 * TODO: Value conversion across types. We're relying on
 * the behavior of the LLInstance subclasses here.
 */
public class LivelinkValue implements Value {
    /** The logger for this class. */
    private static final Logger LOGGER =
        Logger.getLogger(LivelinkResultSet.class.getName());


    private final ValueType type;
    private final ClientValue clientValue;

    /**
     * Wraps a <code>ClientValue</code> as an SPI
     * <code>Value</code>.
     *
     * @param clientValue the value to be wrapped
     */
    LivelinkValue(ClientValue clientValue) {
        switch (clientValue.type()) {
        case ClientValue.BOOLEAN: type = ValueType.BOOLEAN; break;
        case ClientValue.DATE: type = ValueType.DATE; break;
        case ClientValue.DOUBLE: type = ValueType.DOUBLE; break;
        case ClientValue.INTEGER: type = ValueType.LONG; break;
        case ClientValue.STRING: type = ValueType.STRING; break;
        default:
            throw new AssertionError("The clientValue must have an atomic type.");
        }
        this.clientValue = clientValue;
    }

    /**
     * Wraps a <code>ClientValue</code> as an SPI
     * <code>Value</code>.
     *
     * @param type the expected value type
     * @param clientValue the value to be wrapped
     */
    LivelinkValue(ValueType type, ClientValue clientValue) {
        this(clientValue);
        
        // This isn't a functional problem, I don't think, but it
        // might be nice to know if this can happen.
        if (this.type != type && LOGGER.isLoggable(Level.WARNING)) {
            LOGGER.warning("Unexpected property type, expected: " +
                           type + "; got " + this.type);
        }
    }


    public boolean getBoolean() throws RepositoryException {
        return clientValue.toBoolean();
    }

    public Calendar getDate() throws RepositoryException {
        Calendar c = Calendar.getInstance();
        c.setTime(clientValue.toDate());
        return c;
    }

    public double getDouble() throws RepositoryException {
        return clientValue.toDouble();
    }

    public long getLong() throws RepositoryException {
        return clientValue.toInteger();
    }

    public InputStream getStream() throws RepositoryException {
        try {
            return new ByteArrayInputStream(getString().getBytes("UTF8"));
        } catch (UnsupportedEncodingException e) {
            // This can't happen.
            RuntimeException re = new IllegalArgumentException();
            re.initCause(e);
            throw re;
        }
    }

    public String getString() throws RepositoryException {
        if (type == ValueType.DATE) {
            Date date = clientValue.toDate();
            return LivelinkDateFormat.getInstance().toIso8601String(date);
        }
        else
            return clientValue.toString2();
    }

    public ValueType getType() {
        return type;
    }


    /**
     * {@inheritDoc}
     * <p>
     * This implementation returns the string value, or if an
     * exception is thrown obtaining the value, it returns the
     * string value of the exception. This is intended for
     * debugging and logging.
     */
    public String toString() {
        try {
            return getString() + " #" + getType();
        } catch (RepositoryException e) {
            return e.toString();
        }
    }
}
