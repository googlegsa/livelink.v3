// Copyright (C) 2007-2008 Google Inc.
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

    /** 
     * If deleted documents are excluded from indexing, then we should
     * also remove documents from search results that were deleted
     * after they were indexed.
     */
    private final boolean purgeDeletedDocs;

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
        this.purgeDeletedDocs = isExcludedVolume(402);
        if (isExcludedVolume(161)) {
            // There isn't an AccessWorkflowWS function.
            Client client = clientFactory.createClient();
            ClientValue results = client.ListNodes("SubType = 161", "DTree",
                new String[] { "DataID", "PermId" });
            if (results.size() > 0) {
                workflowVolumeId = results.toInteger(0, "DataID");
                LOGGER.finest("EXCLUDING WORKFLOW VOLUME: " +
                    workflowVolumeId);
            } else
                workflowVolumeId = 0;
        } else
            workflowVolumeId = 0;
        this.showHiddenItems = connector.getShowHiddenItems().contains("all");
        this.tryLowercaseUsernames = connector.isTryLowercaseUsernames();
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
        public AuthzDocList(int size) {
            super(size);
        }

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
            AuthenticationIdentity identity) throws RepositoryException {
        String username = identity.getUsername();

        // Remove the DNS-style Windows domain, if there is one.
        int index = username.indexOf("@");
        if (index != -1)
            username = username.substring(0, index);

        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("AUTHORIZE DOCIDS: " + new ArrayList(docids) +
                " FOR: " + username);
        }

        AuthzDocList authorized = new AuthzDocList(docids.size());
        if (tryLowercaseUsernames) {
            try {
                // Hack: try lower case version of username first.
                addAuthorizedDocids(docids.iterator(), username.toLowerCase(),
                    authorized);
            } catch (RepositoryException e) {
                LOGGER.finest("LOWERCASE USERNAME FAILED: " + e.getMessage());
                addAuthorizedDocids(docids.iterator(), username, authorized);
            }
        } else
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
        } else if (LOGGER.isLoggable(Level.FINE))
            LOGGER.fine("AUTHORIZED: " + authorized.size() + " documents.");

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
            Collection authorized) throws RepositoryException {
        Client client = clientFactory.createClient();
        client.ImpersonateUserEx(username, connector.getDomainName());

        String query;
        while ((query = getDocidQuery(iterator)) != null) {
            if (LOGGER.isLoggable(Level.FINEST))
                LOGGER.finest("AUTHORIZATION QUERY: " + query);
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
    String getDocidQuery(Iterator iterator) {
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
     * Returns a boolean representing whether the given volume
     * is excluded from indexing.  
     *
     * The connector can be configured to exclude volumes either by
     * specifying the volume's SubType in the excludedVolumeTypes, or
     * by specifying the NodeID of the volume in the
     * excludedLocationNodes.
     *
     * @return true if the volume is excluded from indexing,
     * false otherwise.
     */
    private boolean isExcludedVolume(int subtype) throws RepositoryException {
        // First look for volume subtype in the excludedVolumeTypes.
        String exclVolTypes = connector.getExcludedVolumeTypes();
        if ((exclVolTypes != null) && (exclVolTypes.length() > 0)) {
            String[] subtypes = exclVolTypes.split(",");
            for (int i = 0; i < subtypes.length; i++) {
                if (String.valueOf(subtype).equals(subtypes[i]))
                    return true;
            }
        }

        // If volume subtype was not excluded, then look for
        // explicitly excluded volume nodes in excludedLocationNodes.
        String exclNodes = connector.getExcludedLocationNodes();
        if ((exclNodes != null) && (exclNodes.length() > 0)) {
            String query =
                "SubType = " + subtype + " and DataID in (" + exclNodes + ")";
            LOGGER.finest(query);
            Client client = clientFactory.createClient();
            ClientValue results = client.ListNodes(query, "DTree",
                new String[] { "DataID", "PermId" });
            if (results.size() > 0) 
                return true;
        }

        return false;
    }
}
