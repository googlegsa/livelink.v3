// Copyright 2007 Google Inc.
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

import com.google.enterprise.connector.spi.Connector;
import com.google.enterprise.connector.spi.RepositoryException;

/**
 * Provides an interface marking objects which should be passed a
 * connector instance.
 */
public interface ConnectorAware {
    /**
     * Sets a connector instance. Generally, this method should be
     * called during initialization, before the object it is called on
     * is used.
     *
     * @param connector a fully-configured connector instance
     * @throws RepositoryException if a repository error occurs
     */
    void setConnector(Connector connector) throws RepositoryException;
}
