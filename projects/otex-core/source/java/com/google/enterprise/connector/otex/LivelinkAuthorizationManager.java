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

package com.google.enterprise.connector.otex;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.lang.StringBuffer;

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

    /** Do we exclude Deleted Documents from search results? */
    private boolean purgeDeletedDocs = false;


    /**
     * Constructor - caches client factory, connector, and
     * looks to see if deleted documents are to be excluded
     * from search results.
     */
    LivelinkAuthorizationManager(LivelinkConnector connector,
                                 ClientFactory clientFactory)
        throws RepositoryException
    {
        super();
        this.clientFactory = clientFactory;
        this.connector = connector;
        this.purgeDeletedDocs = excludeDeletedDocuments();
    }


    /**
     * Our returned Collection of authorized DocIds will be a simple ArrayList,
     * however I override the add() method to add AuthorizationResponse
     * objects for each DocId. I do this because addAuthorizedDocids() is used 
     * by two callers, one wants a Collection of docids, the other wants
     * a Collection of AuthorizationResponse objects.
     */
    private class AuthzDocList extends ArrayList {
        /**
         * Initial capacity constructor for the ArrayList.
         *
         * @param size initial capacity for the ArrayList.
         */
        public AuthzDocList(int size) { super(size); }

        /**
         * Override add(), to add an AuthorizationResponse object
         * for the supplied docid.
         *
         * @param docid The docid to add to the authorized list.
         * @return true (as per the general contract of Collection.add).
         */
        public boolean add(Object docid) {
            return super.add(new AuthorizationResponse(true, (String) docid));
        }
    }


    /**
     * Returns authorization information for a list of docids.
     *
     * @param docids the Collection of docids
     * @param identity the user identity for which to check authorization
     * @throws RepositoryException if an error occurs
     */
    public Collection authorizeDocids(Collection docids,
                                      AuthenticationIdentity identity)
            throws RepositoryException
    {
        String username = identity.getUsername();

        if (LOGGER.isLoggable(Level.FINE))
            LOGGER.fine("AUTHORIZE DOCIDS: " + username);

        AuthzDocList authorized = new AuthzDocList(docids.size());
        addAuthorizedDocids(docids.iterator(), username, authorized);
        authorized.trimToSize();

        if (LOGGER.isLoggable(Level.FINEST)) {
            for (Iterator i = docids.iterator(); i.hasNext(); ) {
                String docid = (String) i.next(); 
                AuthorizationResponse ar;
                ar = new AuthorizationResponse(true, (String) docid);
                // Hack: the Collection either contains docids or
                // AuthorizationResponse objects.
                boolean ok = (authorized.contains(docid) ||
                              authorized.contains(ar));
                LOGGER.finest("AUTHORIZED " + docid + ": " + ok);
            }
        }

        return authorized;
    }


    /**
     * Adds authorized documents from the list to the collection.
     *
     * @param iterator Iterator over the list of doc IDs
     * @param username the username for which to check authorization
     * @param authorized the collection to add authorized doc IDs to
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
    public void addAuthorizedDocids(Iterator iterator, String username,
            Collection authorized) throws RepositoryException
    {
        Client client = clientFactory.createClient();
        client.ImpersonateUserEx(username, connector.getDomainName());

        String query;
        while ((query = getDocidQuery(iterator)) != null) {
            LOGGER.finest(query);
            ClientValue results = client.ListNodes(query, "WebNodes",
                new String[] { "DataID", "PermID" });
            for (int i = 0; i < results.size(); i++)
                authorized.add(results.toString(i, "DataID"));
        }
    }


    /**
     * Builds a SQL query of the form "DataId in (...)" where the
     * contents of the iterator's list are the provided docids.  
     * At most, 1,000 docids will be added to the query due to SQL
     * syntax limits in Oracle.
     *
     * If we are excluding Deleted Documents from the result set,
     * add a subquery to eliminate those docids with the that are
     * in the DeletedDocuments table.
     *
     * @param docids the docids to include in the query
     * @return the SQL query string; null if no docids are provided
     */
    private String getDocidQuery(Iterator iterator) {
        if (!iterator.hasNext())
            return null; 

        StringBuffer query = new StringBuffer("DataID in ("); 
        for (int i = 0; i < 1000 && iterator.hasNext(); i++ ) 
            query.append((String) iterator.next()).append(',');
        query.setCharAt(query.length() - 1, ')');

        if (purgeDeletedDocs) {
            String docids = query.substring(query.indexOf("("));
            query.append(" and DataID not in (select NodeID from");
            query.append(" DeletedDocs where NodeID in ");
            query.append(docids);
            query.append(')');
        }

        return query.toString();
    }


    /**
     * Returns a boolean representing whether the Undelete Workspace
     * is excluded from indexing.  If deleted documents are excluded
     * from indexing, then we should also remove documents from
     * search results that were deleted after they were indexed.
     *
     * The connector can be configured to exclude Undelete Workspace
     * either by specifying the Deleted Document Volumes SubType (402)
     * in the excludedVolumeTypes, or by specifying the NodeID of 
     * the Undelete Workspace in the excludedLocationNodes.
     *
     * @return true if the Undelete Workspace is excluded from indexing,
     * false otherwise.
     */
    private boolean excludeDeletedDocuments() throws RepositoryException {
        // First look for Deleted Documents Volume subtype 402 specified
        // in the excludeVolumeTypes.
        String exclVolTypes = connector.getExcludedVolumeTypes();
        if ((exclVolTypes != null) && (exclVolTypes.length() > 0)) {
            String[] subtypes = exclVolTypes.split(",");
            for (int i = 0; i < subtypes.length; i++) {
                if ("402".equals(subtypes[i]))
                    return true;
            }
        }

        // If volume subtype 402 was not excluded, then look for
        // explicitly excluded Undelete Workspace nodes in
        // excludedLocationNodes.
        String exclNodes = connector.getExcludedLocationNodes();
        if ((exclNodes != null) && (exclNodes.length() > 0)) {
            String query = "SubType = 402 and DataID in (" + exclNodes + ")";
            LOGGER.finest(query);                                                  
            Client client = clientFactory.createClient();
            StringBuffer buf = new StringBuffer();
            ClientValue results = client.ListNodes(query, "DTree",
                                         new String[] { "DataID", "PermId" });
            if (results.size() > 0) 
                return true;
        }

        return false;
    }
}
