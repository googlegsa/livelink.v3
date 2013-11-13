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

import static com.google.enterprise.connector.otex.SqlQueries.choice;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.enterprise.connector.spi.AuthenticationIdentity;
import com.google.enterprise.connector.spi.AuthorizationManager;
import com.google.enterprise.connector.spi.AuthorizationResponse;
import com.google.enterprise.connector.spi.Connector;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.otex.client.Client;
import com.google.enterprise.connector.otex.client.ClientFactory;
import com.google.enterprise.connector.otex.client.ClientValue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implements an AuthorizationManager for the Livelink connector.
 */
public class LivelinkAuthorizationManager
    implements AuthorizationManager, ConnectorAware {
  /** The logger for this class. */
  private static final Logger LOGGER =
      Logger.getLogger(LivelinkAuthorizationManager.class.getName());

  /** The connector contains configuration information. */
  private LivelinkConnector connector;

  /** Client factory for obtaining client instances. */
  private ClientFactory clientFactory;

  /** The SQL queries resource bundle wrapper. */
  private SqlQueries sqlQueries;

  /** 
   * If deleted documents are excluded from indexing, then we should
   * also remove documents from search results that were deleted
   * after they were indexed.
   */
  private int undeleteVolumeId;

  /**
   * FIXME: Temporary patch to remove workflow attachments from the
   * search results if the workflow volume is excluded from
   * indexing.
   */
  private int workflowVolumeId;

  /**
   * If showHiddenItems is false, then we need to excluded hidden
   * items from the authorized documents. We handle hidden items at
   * authorization time, like a special permission, rather than at
   * indexing time.
   */
  /*
   * TODO: This doesn't handle the subtypes yet. If showHiddenItems
   * is a list of subtypes, then hidden items will not be
   * authorized.
   */
  private boolean showHiddenItems;

  /** Lowercase usernames hack. */
  private boolean tryLowercaseUsernames;

  /** The mapper from the GSA identity to the Livelink username. */
  private IdentityResolver identityResolver;

  /** Default constructor for bean instantiation. */
  public LivelinkAuthorizationManager() {
  }

  /**
   * Caches client factory, connector, and additional kinds of items
   * that should be excluded from search results.
   *
   * @param connector the current LivelinkConnector
   */
  /*
   * This method will be called before any other methods in this
   * class. The accessible methods in this class are synchronized
   * because initialization (via this method) and other method calls
   * happen in different threads, and we do not control the threads.
   */
  public synchronized void setConnector(Connector connector)
      throws RepositoryException {
    this.connector = (LivelinkConnector) connector;
    this.clientFactory = this.connector.getClientFactory();
    Client client = clientFactory.createClient();
    this.sqlQueries = new SqlQueries(this.connector.isSqlServer());
    this.undeleteVolumeId = getExcludedVolumeId(402, "UNDELETE", client);
    this.workflowVolumeId = getExcludedVolumeId(161, "WORKFLOW", client);
    this.showHiddenItems = this.connector.getShowHiddenItems().contains("all");
    this.tryLowercaseUsernames = this.connector.isTryLowercaseUsernames();
    this.identityResolver=
        new IdentityResolver(this.connector.getDomainAndName());
  }


  /**
   * Returns authorization information for a list of docids.
   *
   * @param docids the Collection of docids
   * @param identity the user identity for which to check authorization
   * @throws RepositoryException if an error occurs
   */
  public synchronized Collection<AuthorizationResponse> authorizeDocids(
      Collection<String> docids, AuthenticationIdentity identity)
      throws RepositoryException {
    if (LOGGER.isLoggable(Level.FINE)) {
      LOGGER.fine("AUTHORIZE DOCIDS: " + new ArrayList<String>(docids) +
          " FOR: " + identity.getUsername());
    }

    String username = identityResolver.getAuthorizationIdentity(identity);
    return authorizeDocids(docids, username);
  }

  /**
   * Returns authorization information for a list of docids. This
   * separate helper method prevents access to the original
   * {@link AuthenticationIdentity} parameter, to avoid using the
   * wrong username by accident.
   *
   * @param docids the Collection of docids
   * @param username the username for which to check authorization
   * @throws RepositoryException if an error occurs
   */
  private Collection<AuthorizationResponse> authorizeDocids(
      Collection<String> docids, String username) throws RepositoryException {
    ArrayList<AuthorizationResponse> authorized =
        new ArrayList<AuthorizationResponse>(docids.size());
    if (tryLowercaseUsernames) {
      try {
        // Hack: try lower case version of username first.
        addAuthorizedDocids(docids, username.toLowerCase(), authorized);
      } catch (RepositoryException e) {
        // TODO: Only try this if the name was not lowercase to begin with.
        LOGGER.finest("LOWERCASE USERNAME FAILED: " + e.getMessage());
        addAuthorizedDocids(docids, username, authorized);
      }
    } else
      addAuthorizedDocids(docids, username, authorized);
    authorized.trimToSize();

    if (LOGGER.isLoggable(Level.FINEST)) {
      for (String docid : docids) {
        AuthorizationResponse ar = new AuthorizationResponse(true, docid);
        LOGGER.finest("AUTHORIZED " + docid + ": " + authorized.contains(ar));
      }
    } else if (LOGGER.isLoggable(Level.FINE))
      LOGGER.fine("AUTHORIZED: " + authorized.size() + " documents.");

    return authorized;
  }

  /**
   * Adds an <code>AuthorizationResponse</code> instance to the
   * collection for each authorized document from the list.
   *
   * @param iterator Iterator over the list of doc IDs
   * @param username the username for which to check authorization
   * @param authorized the collection to add authorized doc IDs to
   * @throws RepositoryException if an error occurs
   */
  private void addAuthorizedDocids(Collection<String> docids, String username,
      Collection<AuthorizationResponse> authorized) throws RepositoryException {
    addAuthorizedDocids(docids.iterator(), username, authorized,
        new AuthzCreator());
  }

  /**
   * Adds the docid string to the collection for each authorized
   * document from the list.
   *
   * @param iterator Iterator over the list of doc IDs
   * @param username the username for which to check authorization
   * @param authorized the collection to add authorized doc IDs to
   * @throws RepositoryException if an error occurs
   */
  final synchronized void addAuthorizedDocids(Iterator<String> iterator,
      String username, Collection<String> authorized)
      throws RepositoryException {
    addAuthorizedDocids(iterator, username, authorized, new StringCreator());
  }

  /**
   * Adds authorized documents from the list to the collection. The
   * <code>Creator</code> is used to create objects to add to the
   * collection. I do this because addAuthorizedDocids() is used by
   * two callers, one wants a Collection of docids, the other wants a
   * Collection of AuthorizationResponse objects.
   *
   * @param iterator Iterator over the list of doc IDs
   * @param username the username for which to check authorization
   * @param authorized the collection to add authorized doc IDs to
   * @param creator a factory for the objects added to the collection
   * @throws RepositoryException if an error occurs
   */
  /*
    Using ListNodes to authorize docids in bulk is much faster (up
    to 300 times faster in testing) that authorizing a single docid
    at a time.

    There are limits to the size of the SQL query in the
    backing databases. They're pretty big, but on general
    principles we're breaking the query into pieces.

    Max query size: (SQL Server 2000)16777216 (Oracle9i)16777216

    Larger chunk sizes for the query seem to be better: 

    [junit] Chunk size: 100
    [junit] Admin: docs/time = 2479/1325
    [junit] llglobal: docs/time = 2479/1516
    [junit] llglobal-external: docs/time = 2479/1546

    [junit] Chunk size: 1000
    [junit] Admin: docs/time = 2479/873
    [junit] llglobal: docs/time = 2479/1185
    [junit] llglobal-external: docs/time = 2479/1265

    [junit] Chunk size: 10000
    [junit] Admin: docs/time = 2479/352
    [junit] llglobal: docs/time = 2479/813
    [junit] llglobal-external: docs/time = 2479/522

    For now, we're creating a new client (and LLSession) on
    each request. We could synchronize this method and share a
    client instance (synchronization is needed to prevent
    problems with impersonation). If there are too many
    requests coming from the ConnectorManager for the Livelink
    server, we could consider synchronizing access to the
    ListNodes call on the class object or something.
  */
  private <T> void addAuthorizedDocids(Iterator<String> iterator,
      String username, Collection<T> authorized, Creator<T> creator)
      throws RepositoryException {
    Client client = clientFactory.createClient();
    client.ImpersonateUserEx(username, connector.getDomainName());

    String docids;
    while ((docids = getDocids(iterator)) != null) {
      String ancestorNodes;
      String startNodes = connector.getIncludedLocationNodes();
      if (Strings.isNullOrEmpty(startNodes)) {
        ancestorNodes = null;
      } else {
        ancestorNodes = Genealogist.getAncestorNodes(startNodes);
      }

      ClientValue results = sqlQueries.execute(client, "AUTHORIZATION QUERY",
          "LivelinkAuthorizationManager.addAuthorizedDocids",
          /* 0 */ docids,
          /* 1 */ choice(undeleteVolumeId != 0), undeleteVolumeId,
          /* 3 */ choice(workflowVolumeId != 0), -workflowVolumeId,
          /* 5 */ choice(!showHiddenItems), Client.DISPLAYTYPE_HIDDEN,
          /* 7 */ choice(!Strings.isNullOrEmpty(startNodes)), startNodes,
          /* 9 */ ancestorNodes);
      for (int i = 0; i < results.size(); i++)
        authorized.add(creator.fromString(results.toString(i, "DataID")));
    }
  }

  /** A factory interface for authorization response objects. */
  private static interface Creator<T> {
    T fromString(String value);
  }

  /** A factory interface for strings. */
  private static class StringCreator implements Creator<String> {
    public String fromString(String value) {
      return value;
    }
  }

  /** A factory interface for <code>AuthorizationResponse</code> objects. */
  private static class AuthzCreator implements Creator<AuthorizationResponse> {
    public AuthorizationResponse fromString(String value) {
      return new AuthorizationResponse(true, value);
    }
  }

  /**
   * Builds a comma-separated string of up to 1,000 docids from the
   * given iterator. At most, 1,000 docids will be added to the query
   * due to SQL syntax limits in Oracle.
   *
   * @param docids the docids to include in the query
   * @return the comma-separated string; null if no docids are provided
   */
  @VisibleForTesting
  String getDocids(Iterator<String> iterator) {
    if (!iterator.hasNext())
      return null; 

    StringBuilder buffer = new StringBuilder();
    for (int i = 0; i < 1000 && iterator.hasNext(); i++ ) 
      buffer.append(iterator.next()).append(',');
    buffer.deleteCharAt(buffer.length() - 1);
    return buffer.toString();
  }

  /**
   * Gets the volume ID of an excluded volume type.  
   *
   * The connector can be configured to exclude volumes either by
   * specifying the volume's SubType in the excludedVolumeTypes, or
   * by specifying the NodeID of the volume in the
   * excludedLocationNodes.
   *
   * @param subtype the volume subtype to check for
   * @param label the descriptive string to log for this volume subtype
   * @param client the client to use if one is needed
   * @return the volume ID if the volume is excluded from indexing,
   * zero otherwise.
   */
  @VisibleForTesting
  int getExcludedVolumeId(int subtype, String label, Client client)
      throws RepositoryException {
    // First look for volume subtype in the excludedVolumeTypes.
    String exclVolTypes = connector.getExcludedVolumeTypes();
    if (!Strings.isNullOrEmpty(exclVolTypes)
        && arrayContains(exclVolTypes.split(","), String.valueOf(subtype))) {
      return getNodeId(label, client, subtype, choice(false));
    }

    // If volume subtype was not excluded, then look for
    // explicitly excluded volume nodes in excludedLocationNodes.
    String exclNodes = connector.getExcludedLocationNodes();
    if (!Strings.isNullOrEmpty(exclNodes)) {
      return getNodeId(label, client, subtype, choice(true), exclNodes);
    }

    return 0;
  }

  private boolean arrayContains(String[] array, String target) {
    for (String element : array) {
      if (element.equals(target)) {
        return true;
      }
    }
    return false;
  }

  private int getNodeId(String label, Client client, Object... parameters)
      throws RepositoryException {
    ClientValue results = sqlQueries.execute(client, null,
        "LivelinkAuthorizationManager.getExcludedVolumeId", parameters);
    if (results.size() > 0) {
      int volumeId = results.toInteger(0, "DataID");
      if (LOGGER.isLoggable(Level.FINEST))
        LOGGER.finest("EXCLUDING " + label + " VOLUME: " + volumeId);
      return volumeId;
    } else
      return 0;
  }
}
