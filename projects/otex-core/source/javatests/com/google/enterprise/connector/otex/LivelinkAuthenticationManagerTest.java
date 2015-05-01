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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.enterprise.connector.spi.AuthenticationManager;
import com.google.enterprise.connector.spi.AuthenticationResponse;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.SimpleAuthenticationIdentity;
import com.google.enterprise.connector.util.testing.Logging;

import org.junit.Test;

import java.util.ArrayList;

/** Tests the {@code LivelinkAuthenticationManager}. */
public class LivelinkAuthenticationManagerTest {
  @Test
  public void testNullClientFactory() throws RepositoryException {
    try {
      AuthenticationManager lam = new LivelinkAuthenticationManager();
      lam.authenticate(new SimpleAuthenticationIdentity("fred", "opensesame"));
      fail();
    } catch (RepositoryException e) {
      if (e.getMessage().indexOf("client factory") == -1) {
        throw e;
      }
    }
  }

  @Test
  public void testGroupLookup() throws RepositoryException {
    String expectedMessage = "Group lookup is not supported";
    ArrayList<String> logMessages = new ArrayList<String>();
    Logging.captureLogMessages(LivelinkAuthenticationManager.class,
        expectedMessage, logMessages);

    AuthenticationManager lam = new LivelinkAuthenticationManager();
    AuthenticationResponse response =
        lam.authenticate(new SimpleAuthenticationIdentity("fred"));
    assertFalse(response.toString(), response.isValid());
    assertNull(response.toString(), response.getGroups());
    assertEquals(logMessages.toString(), 1, logMessages.size());
    assertTrue(logMessages.toString(),
        logMessages.get(0).contains(expectedMessage));
  }
}
