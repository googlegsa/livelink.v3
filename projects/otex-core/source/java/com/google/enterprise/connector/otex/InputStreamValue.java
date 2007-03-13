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
    /** The logger for this class. */
    private static final Logger LOGGER =
        Logger.getLogger(InputStreamValue.class.getName());

    /** The wrapped stream. */
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
