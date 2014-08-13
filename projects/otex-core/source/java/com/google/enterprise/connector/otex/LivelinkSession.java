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

import com.google.enterprise.connector.otex.client.Client;
import com.google.enterprise.connector.otex.client.ClientFactory;
import com.google.enterprise.connector.otex.client.ClientValue;
import com.google.enterprise.connector.spi.AuthenticationManager;
import com.google.enterprise.connector.spi.AuthorizationManager;
import com.google.enterprise.connector.spi.Lister;
import com.google.enterprise.connector.spi.ListerAware;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.Retriever;
import com.google.enterprise.connector.spi.RetrieverAware;
import com.google.enterprise.connector.spi.Session;
import com.google.enterprise.connector.spi.TraversalManager;

class LivelinkSession implements Session, ListerAware, RetrieverAware {
    /** The connector instance. */
    private final LivelinkConnector connector;

    /** The client factory for traversal and authorization clients. */
    private final ClientFactory clientFactory;

    /** The list of authentication managers. */
    private final AuthenticationManager authenticationManager;

    /** The authorization manager. */
    private final AuthorizationManager authorizationManager;

    /** The Lister. */
    private Lister lister;

    /** The Retriever. */
    private Retriever retriever;

    /**
     *
     * @param connector a connector instance
     * @param clientFactory a client factory
     * @param authenticationManager the configured AuthenticationManager
     * @param authorizationManager the configured AuthorizationManager
     * @throws RepositoryException not thrown
     */
    public LivelinkSession(LivelinkConnector connector,
            ClientFactory clientFactory,
            AuthenticationManager authenticationManager,
            AuthorizationManager authorizationManager)
            throws RepositoryException {
        this.connector = connector;
        this.clientFactory = clientFactory;
        this.authenticationManager = authenticationManager;
        this.authorizationManager = authorizationManager;
        this.retriever = null;
        GroupAdaptor groupAdaptor =
            new GroupAdaptor(connector, clientFactory.createClient());
        this.lister = new GroupLister(connector, groupAdaptor);
    }

  /**
   * Gets a TraversalManager to implement query-based traversal
   *
   * @return a TraversalManager
   * @throws RepositoryException
   */
  @Override
  public TraversalManager getTraversalManager() throws RepositoryException {
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

    /**
     * Gets an AuthenticationManager to implement per-user authentication.
     *
     * @return an AuthenticationManager
     * @throws RepositoryException
     */
    @Override
    public AuthenticationManager getAuthenticationManager() {
        // XXX: Should we ever return null, indicating that
        // authentication is handled by the GSA?
        return authenticationManager;
    }

    /**
     * Gets an AuthorizationManager to implement per-user authorization.
     *
     * @return an AuthorizationManager
     * @throws RepositoryException
     */
    @Override
    public AuthorizationManager getAuthorizationManager()
            throws RepositoryException {
        return authorizationManager;
    }

    @Override
    public Lister getLister() throws RepositoryException {
      return lister;
    }

  /**
   * Gets the Retriever to implement Content URL Web feed.
   *
   * @return a Retriever
   * @throws RepositoryException
   */
  @Override
  public synchronized Retriever getRetriever() throws RepositoryException {
    if (retriever == null) {
      Client traversalClient = clientFactory.createClient();
      String username = getCurrentUsername(traversalClient);
      String traversalUsername = connector.getTraversalUsername();
      impersonateUser(traversalClient, username, traversalUsername);
      retriever = new LivelinkRetriever(connector, traversalClient,
          connector.getContentHandler(traversalClient));
    }
    return retriever;
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

    /**
     * Returns the ClientFactory for test use.
     */
    ClientFactory getFactory() {
        return clientFactory;
    }
}
