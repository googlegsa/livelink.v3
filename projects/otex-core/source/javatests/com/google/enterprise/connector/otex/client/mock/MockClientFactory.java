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

package com.google.enterprise.connector.otex.client.mock;

import com.google.enterprise.connector.otex.client.Client;
import com.google.enterprise.connector.otex.client.ClientFactory;

/**
 * A mock factory for the facade interface that encapsulates the Livelink API.
 * This implementation ignores the configuration properties.
 */
public class MockClientFactory implements ClientFactory {
    /** {@inheritDoc} */
    public void setHostname(String value) {
    }

    /** {@inheritDoc} */
    public void setPort(int value) {
    }

    /** {@inheritDoc} */
    public void setUsername(String value) {
    }

    /** {@inheritDoc} */
    public void setPassword(String value) {
    }

    /** {@inheritDoc} */
    public void setDatabase(String value) {
    }

    /** {@inheritDoc} */
    public void setDomainName(String value) {
    }

    /** {@inheritDoc} */
    public void setEncoding(String value) {
    }
    
    /** {@inheritDoc} */
    public void setLivelinkCgi(String value) {
    }

    /** {@inheritDoc} */
    public void setUseHttps(boolean value) {
    }

    /** {@inheritDoc} */
    public void setHttpUsername(String value) {
    }

    /** {@inheritDoc} */
    public void setHttpPassword(String value) {
    }

    /** {@inheritDoc} */
    public void setVerifyServer(boolean value) {
    }

    /** {@inheritDoc} */
    public void setCaRootCerts(java.util.List value) {
    }

    /** {@inheritDoc} */
    public void setEnableNtlm(boolean value) {
    }

    /** {@inheritDoc} */
    public void setUseUsernamePasswordWithWebServer(boolean value) {
    }

    /** {@inheritDoc} */
    public Client createClient() {
        return new MockClient();
    }

    /** {@inheritDoc} */
    public Client createClient(String username, String password) {
        return new MockClient();
    }
}
