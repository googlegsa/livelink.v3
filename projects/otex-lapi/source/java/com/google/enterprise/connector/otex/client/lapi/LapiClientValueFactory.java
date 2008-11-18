// Copyright (C) 2007-2008 Google Inc.
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

import java.util.logging.Logger;

import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.otex.client.ClientValue;
import com.google.enterprise.connector.otex.client.ClientValueFactory;

import com.opentext.api.LLIllegalOperationException;
import com.opentext.api.LLValue;

/**
 * A direct LAPI ClientValue factory implementation.
 */
public final class LapiClientValueFactory implements ClientValueFactory {
    /** The logger for this class. */
    private static final Logger LOGGER =
        Logger.getLogger(LapiClientValueFactory.class.getName());


    /**
     * Factory method creates a ClientValue without underlying LLValue
     */
    public ClientValue createValue() throws RepositoryException {
        try {
            return new LapiClientValue((LLValue) null);
        } catch (LLIllegalOperationException e) {
            throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new LapiException(e, LOGGER);
        }
    }

    /**
     * Factory method creates a ClientValue with an underlying Assoc.
     */
    public ClientValue createAssoc() throws RepositoryException {
        try {
            return new LapiClientValue( new LLValue().setAssoc() );
        } catch (LLIllegalOperationException e) {
            throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new LapiException(e, LOGGER);
        }
    }

    /**
     * Factory method creates a ClientValue with an underlying List.
     */
    public ClientValue createList() throws RepositoryException {
        try {
            return new LapiClientValue( new LLValue().setList() );
        } catch (LLIllegalOperationException e) {
            throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new LapiException(e, LOGGER);
        }
    }

}
