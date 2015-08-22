// Copyright 2015 Google Inc. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.enterprise.connector.otex;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertEquals;

import com.google.common.collect.ImmutableList;
import com.google.enterprise.connector.otex.client.Client;
import com.google.enterprise.connector.otex.client.mock.MockClientValue;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.Value;
import com.google.enterprise.connector.util.testing.Logging;

import org.junit.Test;

import java.util.ArrayList;

public class UserNameHandlerTest {
  @Test
  public void testCache() throws RepositoryException {
    Client client = createMock(Client.class);
    expect(client.GetUserOrGroupByIDNoThrow(1000)).andReturn(
        new MockClientValue(new String[] { "Name" }, new Object[] { "Admin" }));
    replay(client);

    UserNameHandler out = new UserNameHandler(client);
    LivelinkDocument props = new LivelinkDocument(2000, 1);
    out.addUserByName("UserID", new MockClientValue(1000), 666, props);
    assertEquals("Admin", Value.getSingleValueString(props, "UserID"));

    // Check the cache; the mock client is not expecting a second call.
    props = new LivelinkDocument(2000, 1);
    out.addUserByName("UserID", new MockClientValue(1000), 666, props);
    assertEquals("Admin", Value.getSingleValueString(props, "UserID"));

    verify(client);
  }

  @Test
  public void testNotFound() throws RepositoryException {
    Client client = createMock(Client.class);
    expect(client.GetUserOrGroupByIDNoThrow(1000)).andReturn(null);
    replay(client);

    ArrayList<String> messages = new ArrayList<String>();
    Logging.captureLogMessages(UserNameHandler.class, "found", messages);

    UserNameHandler out = new UserNameHandler(client);
    LivelinkDocument props = new LivelinkDocument(2000, 1);
    out.addUserByName("UserID", new MockClientValue(1000), 666, props);
    assertEquals(null, Value.getSingleValueString(props, "UserID"));
    assertEquals(ImmutableList.of(
            "No user or group name found for ID {0} in attribute {1}"),
        messages);
    verify(client);
  }

  @Test
  public void testOwner() throws RepositoryException {
    Client client = createMock(Client.class);
    expect(client.GetUserOrGroupByIDNoThrow(1000)).andReturn(
        new MockClientValue(new String[] { "Name" }, new Object[] { "Admin" }));
    replay(client);

    UserNameHandler out = new UserNameHandler(client);
    LivelinkDocument props = new LivelinkDocument(2000, 1);
    out.addUserByName("Owner", new MockClientValue(Client.RIGHT_OWNER),
        1000, props);
    assertEquals("Admin", Value.getSingleValueString(props, "Owner"));
    verify(client);
  }

  /** Tests non-positive ID values that do not call the client. */
  private void testNoClientCall(int id, int ownerId)
      throws RepositoryException {
    Client client = createMock(Client.class);
    replay(client);

    UserNameHandler out = new UserNameHandler(client);
    LivelinkDocument props = new LivelinkDocument(2000, 1);
    out.addUserByName("Owner", new MockClientValue(id), ownerId, props);
    assertEquals(null, props.findProperty("Owner"));
    verify(client);
  }

  @Test
  public void testZero() throws RepositoryException {
    testNoClientCall(0, 666);
  }

  @Test
  public void testNoOwner() throws RepositoryException {
    testNoClientCall(Client.RIGHT_OWNER, 0);
  }

  @Test
  public void testOtherNegative() throws RepositoryException {
    testNoClientCall(-42, 666);
  }
}
