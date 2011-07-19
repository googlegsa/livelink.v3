// Copyright 2011 Google Inc.
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

import com.google.enterprise.connector.spi.AuthenticationManager;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.SimpleAuthenticationIdentity;

import junit.framework.TestCase;

/** Tests the {@code LivelinkAuthenticationManager}. */
public class LivelinkAuthenticationManagerTest extends TestCase {
  public void testNullClientFactory() throws RepositoryException {
    try {
      AuthenticationManager lam = new LivelinkAuthenticationManager();
      lam.authenticate(new SimpleAuthenticationIdentity("fred"));
      fail();
    } catch (RepositoryException e) {
      if (e.getMessage().indexOf("client factory") == -1) {
        throw e;
      }
    }
  }
}
