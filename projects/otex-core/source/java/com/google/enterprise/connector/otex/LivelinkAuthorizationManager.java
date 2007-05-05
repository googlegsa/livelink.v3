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

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.enterprise.connector.spi.AuthorizationManager;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.ResultSet;
import com.google.enterprise.connector.spi.SimpleProperty;
import com.google.enterprise.connector.spi.SimplePropertyMap;
import com.google.enterprise.connector.spi.SimpleResultSet;
import com.google.enterprise.connector.spi.SpiConstants;
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

    /** Client factory for obtaining client instances. */
    private final ClientFactory clientFactory;


    /**
     * Constructor - caches client factory.
     */
    LivelinkAuthorizationManager(ClientFactory clientFactory) {
        super();
        this.clientFactory = clientFactory;
    }


    /**
     * Returns authorization information for a list of docids.
     *
     * @param docids the list of docids
     * @param username the username for which to check authorization
     * @throws RepositoryException if an error occurs
     */
    /*
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
    public ResultSet authorizeDocids(List docids, String username)
            throws RepositoryException {
        if (LOGGER.isLoggable(Level.FINE))
            LOGGER.fine("AUTHORIZE DOCIDS: " + username);

        Client client = clientFactory.createClient();
        client.ImpersonateUser(username);
        
        HashSet authorized = new HashSet(); 
        final int chunkSize = 10000;
        int fromIndex = 0;
        int toIndex = Math.min(chunkSize, docids.size());
        while (fromIndex < toIndex && toIndex <= docids.size()) {
            String query = getDocidQuery(docids.subList(fromIndex, toIndex));
            LOGGER.finest(query);
            ClientValue results = client.ListNodes(query, "WebNodes",
                new String[] { "DataID", "PermID" });
            for (int i = 0; i < results.size(); i++)
                authorized.add(results.toString(i, "DataID"));
            fromIndex = toIndex;
            toIndex = Math.min(docids.size(), toIndex + chunkSize); 
        }

        SimpleResultSet rs = new SimpleResultSet();
        for (Iterator i = docids.iterator(); i.hasNext(); ) {
            SimplePropertyMap pm = new SimplePropertyMap();
            String docid = (String) i.next(); 
            pm.putProperty(
                new SimpleProperty(SpiConstants.PROPNAME_DOCID, docid));
            if (LOGGER.isLoggable(Level.FINEST)) {
                LOGGER.finest("AUTHORIZED " + docid + ": " +
                    authorized.contains(docid));
            }
            pm.putProperty(
                new SimpleProperty(SpiConstants.PROPNAME_AUTH_VIEWPERMIT,
                    authorized.contains(docid)));
            rs.add(pm);
        }
        // TODO: This is a nice idea, but SimplePropertyMap does not
        // have a toString method that would make this work.
        // LOGGER.finest(rs.toString());
        return rs;
    }


    /**
     * Stub implementation of interface method.
     *
     * @param docids the list of tokens
     * @param username the username for which to check authorization
     * @throws RepositoryException if an error occurs
     */
    public ResultSet authorizeTokens(List docids, String username) {
        LOGGER.fine("AUTHORIZE TOKENS");
        return new SimpleResultSet();
    }


    /**
     * Builds a SQL query of the form "DataId in (...)" where the
     * contents of the list are the provided docids.
     *
     * @param docids the docids to include in the query
     * @return the SQL query string; null if no docids are provided
     */
    private String getDocidQuery(List docids) {
        if (docids == null || docids.size() == 0)
            return null; 
        StringBuffer query = new StringBuffer("DataID in ("); 
        for (Iterator i = docids.iterator(); i.hasNext(); ) 
            query.append((String) i.next()).append(",");
        int len = query.length() - 1;
        query.replace(len, len + 1, ")");
        return query.toString();
    }
    /*
    // This method is much slower than using ListNodes. 
    public ResultSet authorizeDocids1(List docids, String username)
            throws RepositoryException {
        System.out.println("method 1"); 
        Client client = clientFactory.createClient();
        client.ImpersonateUser(username); 

        SimpleResultSet rs = new SimpleResultSet();
        for (Iterator i = docids.iterator(); i.hasNext(); ) {
            SimplePropertyMap pm = new SimplePropertyMap();
            String docid = (String) i.next(); 
            pm.putProperty(
                new SimpleProperty(SpiConstants.PROPNAME_DOCID, docid));
            pm.putProperty(
                new SimpleProperty(SpiConstants.PROPNAME_AUTH_VIEWPERMIT,
                    client.canSeeContents(LOGGER, docid)));
            rs.add(pm);
        }
        return rs;
    }
    */
}
