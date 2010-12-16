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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.enterprise.connector.spi.AuthenticationIdentity;
import com.google.enterprise.connector.spi.AuthorizationManager;
import com.google.enterprise.connector.spi.AuthorizationResponse;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.otex.client.Client;
import com.google.enterprise.connector.otex.client.ClientFactory;
import com.google.enterprise.connector.otex.client.ClientValue;

/**
 * Implements an AuthorizationManager for the Livelink connector.
 */
class LivelinkAuthorizationManager implements AuthorizationManager {
  /** The logger for this class. */
  private static final Logger LOGGER =
      Logger.getLogger(LivelinkAuthorizationManager.class.getName());

  /** The connector contains configuration information. */
  private final LivelinkConnector connector;

  /** Client factory for obtaining client instances. */
  private final ClientFactory clientFactory;

  /** 
   * If deleted documents are excluded from indexing, then we should
   * also remove documents from search results that were deleted
   * after they were indexed.
   */
  private final int undeleteVolumeId;

  /**
   * FIXME: Temporary patch to remove workflow attachments from the
   * search results if the workflow volume is excluded from
   * indexing.
   */
  private final int workflowVolumeId;

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
  private final boolean showHiddenItems;

  /** Lowercase usernames hack. */
  private boolean tryLowercaseUsernames;

  /**
   * Constructor - caches client factory, connector, and for
   * additional kinds of items that should be excluded from search
   * results.
   */
  LivelinkAuthorizationManager(LivelinkConnector connector,
      ClientFactory clientFactory) throws RepositoryException {
    this.clientFactory = clientFactory;
    this.connector = connector;
    Client client = clientFactory.createClient();
    this.undeleteVolumeId = getExcludedVolumeId(402, "UNDELETE", client);
    this.workflowVolumeId = getExcludedVolumeId(161, "WORKFLOW", client);
    this.showHiddenItems = connector.getShowHiddenItems().contains("all");
    this.tryLowercaseUsernames = connector.isTryLowercaseUsernames();
  }


  /**
   * Returns authorization information for a list of docids.
   *
   * @param docids the Collection of docids
   * @param identity the user identity for which to check authorization
   * @throws RepositoryException if an error occurs
   */
  public Collection<AuthorizationResponse> authorizeDocids(
      Collection<String> docids, AuthenticationIdentity identity)
      throws RepositoryException {
    String username = identity.getUsername();

    // Remove the DNS-style Windows domain, if there is one.
    int index = username.indexOf("@");
    if (index != -1)
      username = username.substring(0, index);

    if (LOGGER.isLoggable(Level.FINE)) {
      LOGGER.fine("AUTHORIZE DOCIDS: " + new ArrayList<String>(docids) +
          " FOR: " + username);
    }

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
  void addAuthorizedDocids(Iterator<String> iterator, String username,
      Collection<String> authorized) throws RepositoryException {
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

    String query;
    while ((query = getDocidQuery(iterator)) != null) {
      if (LOGGER.isLoggable(Level.FINEST))
        LOGGER.finest("AUTHORIZATION QUERY: " + query);
      ClientValue results = client.ListNodes(query, "WebNodes",
          new String[] { "DataID", "PermID" });
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
   * Builds a SQL query of the form "DataId in (...)" where the
   * contents of the iterator's list are the provided docids.  
   * At most, 1,000 docids will be added to the query due to SQL
   * syntax limits in Oracle.
   *
   * If we are excluding deleted documents from the result set,
   * add a subquery to eliminate those docids that are
   * in the DeletedDocs table.
   *
   * If we are excluding items in the workflow volume, add a subquery
   * to eliminate those.
   *
   * If we are excluding hidden items, add a subquery to eliminate
   * those.
   *
   * @param docids the docids to include in the query
   * @return the SQL query string; null if no docids are provided
   */
  /* This method has package access so that it can be unit tested. */
  String getDocidQuery(Iterator<String> iterator) {
    if (!iterator.hasNext())
      return null; 

    StringBuilder query = new StringBuilder("DataID in ("); 
    for (int i = 0; i < 1000 && iterator.hasNext(); i++ ) 
      query.append(iterator.next()).append(',');
    query.setCharAt(query.length() - 1, ')');

    if (undeleteVolumeId != 0) {
      // Open Text uses ParentID in the Undelete OScript code, so we
      // will, too.
      query.append(" and ParentID <> " + undeleteVolumeId);
    }

    if (workflowVolumeId != 0)
      query.append(" and OwnerID <> -" + workflowVolumeId);

    if (!showHiddenItems) {
      // This is a correlated subquery (using the implicit "a"
      // range variable added by LAPI) to only check for the
      // documents we're authorizing.
      String hidden = String.valueOf(Client.DISPLAYTYPE_HIDDEN);
      query.append(" and Catalog <> ");
      query.append(hidden);
      query.append(" and DataID not in (select Anc.DataID ");
      query.append("from DTreeAncestors Anc join DTree T ");
      query.append("on Anc.AncestorID = T.DataID ");
      query.append("where Anc.DataID = a.DataID ");
      query.append("and T.Catalog = ");
      query.append(hidden);

      // Explicitly included items and their descendants are allowed
      // even if they are hidden.
      String startNodes = connector.getIncludedLocationNodes();
      if (startNodes != null && startNodes.length() > 0) {
        String ancestorNodes =
            LivelinkTraversalManager.getAncestorNodes(startNodes);
        query.append(" and Anc.AncestorID not in (");
        query.append(startNodes);
        query.append(") and Anc.AncestorID not in ");
        query.append("(select AncestorID from DTreeAncestors ");
        query.append("where DataID in (");
        query.append(ancestorNodes);
        query.append("))");
      }

      query.append(')');
    }

    return query.toString();
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
  /* This method has package access so that it can be unit tested. */
  int getExcludedVolumeId(int subtype, String label, Client client)
      throws RepositoryException {
    // First look for volume subtype in the excludedVolumeTypes.
    String exclVolTypes = connector.getExcludedVolumeTypes();
    if ((exclVolTypes != null) && (exclVolTypes.length() > 0)) {
      String[] subtypes = exclVolTypes.split(",");
      for (int i = 0; i < subtypes.length; i++) {
        if (String.valueOf(subtype).equals(subtypes[i]))
          return getNodeId("SubType = " + subtype, label, client);
      }
    }

    // If volume subtype was not excluded, then look for
    // explicitly excluded volume nodes in excludedLocationNodes.
    String exclNodes = connector.getExcludedLocationNodes();
    if ((exclNodes != null) && (exclNodes.length() > 0)) {
      String query =
          "SubType = " + subtype + " and DataID in (" + exclNodes + ")";
      LOGGER.finest(query);
      return getNodeId(query, label, client);
    }

    return 0;
  }

  private int getNodeId(String query, String label, Client client)
      throws RepositoryException {
    ClientValue results = client.ListNodes(query, "DTree",
        new String[] { "DataID", "PermId" });
    if (results.size() > 0) {
      int volumeId = results.toInteger(0, "DataID");
      if (LOGGER.isLoggable(Level.FINEST))
        LOGGER.finest("EXCLUDING " + label + " VOLUME: " + volumeId);
      return volumeId;
    } else
      return 0;
  }
}
