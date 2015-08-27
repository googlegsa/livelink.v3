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

import com.google.common.annotations.VisibleForTesting;
import com.google.enterprise.connector.otex.client.Client;
import com.google.enterprise.connector.otex.client.ClientFactory;
import com.google.enterprise.connector.otex.client.ClientValue;
import com.google.enterprise.connector.spi.DocumentList;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.TraversalContext;
import com.google.enterprise.connector.spi.TraversalContextAware;
import com.google.enterprise.connector.spi.TraversalManager;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Delegates to a LivelinkTraversalManager, using two strategies.
 * For direct connections to Livelink, we have a single, final
 * delegate. For HTTP tunneling, where Livelink errors lead to a
 * permanently unusable LLSession, we get a new delegate for each
 * batch.
 * <p>
 * This class uses two separate sets of fields and if statements
 * instead of subclasses. For direct connections, the {@code traverser}
 * field is used, and for HTTP tunneling, the {@code clientFactory},
 * {@code batchSize}, and {@code traversalContext} fields are used.
 */
class TraversalManagerWrapper
    implements TraversalManager, TraversalContextAware {
  private static final Logger LOGGER =
      Logger.getLogger(TraversalManagerWrapper.class.getName());

  /** The connector contains configuration information. */
  private final LivelinkConnector connector;

  /**
   * The traversal manager for direct connections, or null for HTTP
   * tunneling.
   */
  private final LivelinkTraversalManager traverser;

  /**
   * The client factory for creating HTTP tunneling clients later, or
   * null for direct connections.
   */
  private final ClientFactory clientFactory;

  /** The number of results to return in each batch. */
  private volatile int batchSize = 100;

  /** The TraversalContext from TraversalContextAware Interface */
  private TraversalContext traversalContext = null;

  TraversalManagerWrapper(LivelinkConnector connector,
      ClientFactory clientFactory) throws RepositoryException {
    this.connector = connector;

    if (connector.getUseHttpTunneling()) {
      this.traverser = null;
      this.clientFactory = clientFactory;
    } else {
      this.traverser = newTraversalManager(clientFactory);
      this.clientFactory = null;
    }
  }

  private LivelinkTraversalManager newTraversalManager(
      ClientFactory clientFactory) throws RepositoryException {
    LOGGER.log(Level.FINE,
        "CREATING A NEW TRAVERSAL MANAGER; HTTP TUNNELING: {0}",
        connector.getUseHttpTunneling());
    Client traversalClient = clientFactory.createClient();

    // Get the current username to compare to the configured
    // traversalUsername and publicContentUsername.
    String username = getCurrentUsername(traversalClient);

    // If there is a separately specified traversal user (different
    // than our current user), then impersonate that traversal user
    // when building the list of documents to index.
    String currentUsername;
    Client sysadminClient;
    String traversalUsername = connector.getTraversalUsername();
    if (impersonateUser(traversalClient, username, traversalUsername)) {
      currentUsername = traversalUsername;
      sysadminClient = clientFactory.createClient();
    } else {
      currentUsername = username;
      sysadminClient = traversalClient;
    }

    return new LivelinkTraversalManager(connector, traversalClient,
        currentUsername, sysadminClient,
        connector.getContentHandler(traversalClient));
  }

  private String getCurrentUsername(Client client) {
    String username = null;
    try {
      int id = client.GetCurrentUserID();
      ClientValue userInfo = client.GetUserOrGroupByIDNoThrow(id);
      if (userInfo != null)
        username = userInfo.toString("Name");
    } catch (RepositoryException e) {
      // Ignore exceptions, which is conservative. Worst case is
      // that we will impersonate the already logged in user
      // and gratuitously check the permissions on all content.
    }
    return username;
  }

  /**
   * Impersonates a user.
   *
   * @return {@code true} if the new username is impersonated, and
   * {@code false} if the new username is null or already the current
   * username
   */
  private boolean impersonateUser(Client client, String currentUsername,
      String newUsername) throws RepositoryException {
    if (newUsername != null && !newUsername.equals(currentUsername)) {
      client.ImpersonateUserEx(newUsername, connector.getDomainName());
      return true;
    } else {
      return false;
    }
  }

  @Override
  public void setTraversalContext(TraversalContext traversalContext) {
    if (traverser == null) {
      this.traversalContext = traversalContext;
    } else {
      traverser.setTraversalContext(traversalContext);
    }
  }

  @Override
  public void setBatchHint(int hint) {
    if (traverser == null) {
      this.batchSize = hint;
    } else {
      traverser.setBatchHint(hint);
    }
  }

  @Override
  public DocumentList startTraversal() throws RepositoryException {
    return getTraversalManager().startTraversal();
  }

  @Override
  public DocumentList resumeTraversal(String checkpoint)
      throws RepositoryException {
    return getTraversalManager().resumeTraversal(checkpoint);
  }

  @VisibleForTesting
  LivelinkTraversalManager getTraversalManager() throws RepositoryException {
    if (traverser == null) {
      LivelinkTraversalManager tm = newTraversalManager(clientFactory);
      tm.setTraversalContext(traversalContext);
      tm.setBatchHint(batchSize);
      return tm;
    } else {
      return traverser;
    }
  }
}
