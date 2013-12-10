// Copyright (C) 2007-2009 Google Inc.
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

import java.util.Set;

import com.google.enterprise.connector.spi.AuthenticationIdentity;
import com.google.enterprise.connector.spi.AuthenticationResponse;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.SimpleAuthenticationIdentity;

import junit.framework.TestCase;

public class NoOpAuthenticationManagerTest extends TestCase {
    private NoOpAuthenticationManager manager;

    private AuthenticationIdentity identity;

    @Override
    protected void setUp() {
        manager = new NoOpAuthenticationManager();
        identity = new SimpleAuthenticationIdentity("nobody", "goodPassword");
    }

    public void testNoPassword() throws RepositoryException {
        AuthenticationResponse response = manager.authenticate(identity);
        assertTrue(response.isValid());
    }

    public void testGoodSharedPassword() throws RepositoryException {
        manager.setSharedPassword("goodPassword");
        AuthenticationResponse response = manager.authenticate(identity);
        assertTrue(response.isValid());
    }

    public void testBadSharedPassword() throws RepositoryException {
        manager.setSharedPassword("badPassword");
        AuthenticationResponse response = manager.authenticate(identity);
        assertFalse(response.isValid());
    }
}
