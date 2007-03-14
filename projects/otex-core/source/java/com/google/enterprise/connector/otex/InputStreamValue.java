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

import java.io.InputStream;
import java.util.Calendar;

import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.Value;
import com.google.enterprise.connector.spi.ValueType;

/*
 * This <code>Value</code> implementation represents binary streams,
 * and only supports the {@link #getStream} method.
 */
final class InputStreamValue implements Value {
    /** The wrapped stream. */
    private InputStream in;

    InputStreamValue(InputStream in) {
        if (in == null)
            throw IllegalArgumentException();
        this.in = in;
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation always throws an
     * <code>IllegalArgumentException</code>
     */
    public boolean getBoolean() throws RepositoryException {
        throw new IllegalArgumentException();
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation always throws an
     * <code>IllegalArgumentException</code>
     */
    public Calendar getDate() {
        throw new IllegalArgumentException();
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation always throws an
     * <code>IllegalArgumentException</code>
     */
    public double getDouble() throws RepositoryException {
        throw new IllegalArgumentException();
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation always throws an
     * <code>IllegalArgumentException</code>
     */
    public long getLong() throws RepositoryException {
        throw new IllegalArgumentException();
    }

    /** {@inheritDoc} */
    public synchronized InputStream getStream() throws RepositoryException {
        if (in == null)
            throw new IllegalStateException();
        else {
            InputStream is = in;
            in = null;
            return is;
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation always throws an
     * <code>IllegalArgumentException</code>
     */
    public String getString() throws RepositoryException {
        throw new IllegalArgumentException();
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation always returns <code>ValueType.BINARY</code>.
     */     
    public ValueType getType() {
        return ValueType.BINARY;
    }
}
