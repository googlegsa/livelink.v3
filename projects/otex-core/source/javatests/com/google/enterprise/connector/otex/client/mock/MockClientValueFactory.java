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

package com.google.enterprise.connector.otex.client.mock;

import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.otex.client.ClientValue;
import com.google.enterprise.connector.otex.client.ClientValueFactory;


/**
 * A direct Mock ClientValue factory implementation.
 */
public final class MockClientValueFactory implements ClientValueFactory {
    /**
     * Factory method creates a ClientValue without underlying LLValue
     */
    public ClientValue createValue() throws RepositoryException {
        try {
            return new MockClientValue(null, null);
        } catch (Exception e) {
            throw new IllegalArgumentException();
        }
    }

    /**
     * Factory method creates a ClientValue with an underlying Assoc.
     */
    public ClientValue createAssoc() throws RepositoryException {
        try {
            return new MockClientValue(new String[0], new Object[0]);
        } catch (Exception e) {
            throw new IllegalArgumentException();
        }
    }

    /**
     * Factory method creates a ClientValue with an underlying List.
     */
    public ClientValue createList() throws RepositoryException {
        try {
            return new MockClientValue(new Object[0]);
        } catch (Exception e) {
            throw new IllegalArgumentException();
        }
    }
}
