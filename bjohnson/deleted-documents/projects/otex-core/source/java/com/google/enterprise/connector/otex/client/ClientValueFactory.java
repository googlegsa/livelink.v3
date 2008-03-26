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

package com.google.enterprise.connector.otex.client;

import com.google.enterprise.connector.spi.RepositoryException;

/**
 * A factory for the facade interface that encapsulates the Livelink Value Objects.
 *
 * @see ClientValue
 */
public interface ClientValueFactory {

    /** Create a ClientValue instance of no particular type */
    public ClientValue createValue()  throws RepositoryException;

    /** Create a ClientValue instance that enacapsulates an Assoc */
    public ClientValue createAssoc()  throws RepositoryException;

    /** Create a ClientValue instance that enacapsulates a List */
    public ClientValue createList()  throws RepositoryException;
}
