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

import java.io.InputStream;
import java.io.IOException;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import junit.framework.TestCase;

import com.google.enterprise.connector.spi.Connector;
import com.google.enterprise.connector.spi.Property;
import com.google.enterprise.connector.spi.Document;
import com.google.enterprise.connector.spi.DocumentList;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.Session;
import com.google.enterprise.connector.spi.SpiConstants;
import com.google.enterprise.connector.spi.TraversalManager;
import com.google.enterprise.connector.spi.Value;
import com.google.enterprise.connector.pusher.Pusher;
import com.google.enterprise.connector.pusher.PushException;
import com.google.enterprise.connector.pusher.DocPusher;
import com.google.enterprise.connector.pusher.GsaFeedConnection;

import com.google.enterprise.connector.otex.client.Client;
import com.google.enterprise.connector.otex.client.ClientValue;
import com.google.enterprise.connector.otex.LivelinkDateFormat;

/**
 * Pushes a single document (DocID) to the GSA
 */
public class PushOneDocumentTest extends TestCase {
    private LivelinkConnector conn;
    private LivelinkSession sess;
    private Client client;
    private Pusher pusher;

    // TODO: get pusher server and port from [connector.?] properties
    //private String feedServer = "8.6.49.37";
    private String feedServer = "gogol.vizdom.com";
    private int feedPort = 19900;
            
    public void setUp() throws RepositoryException {
        conn = LivelinkConnectorFactory.getConnector("connector.");
        sess = (LivelinkSession) conn.login();
        client = sess.getFactory().createClient(); 
        pusher = new DocPusher(new GsaFeedConnection(feedServer, feedPort));
    }
    
    public void testTraversal() throws RepositoryException, PushException {
        int objectId = getId();

        // Extract the LastModifiedDate of the DocID from Livelink
        // to construct a checkpoint
        ClientValue docInfo = client.GetObjectInfo(0, objectId);
        Date modDate = docInfo.toDate("ModifyDate");
        String checkpoint = LivelinkDateFormat.getInstance().toSqlString(modDate) +
            ','  + (objectId-1);

        // Now push that one document
        TraversalManager mgr = sess.getTraversalManager();
        mgr.setBatchHint(1);
        DocumentList rs = mgr.resumeTraversal(checkpoint);
        pushResultSet(rs);
    }

    private int getId() throws RepositoryException {
        try {
            String obj = System.getProperty("test.docid");
            if (obj == null)
                obj = "BAD";
            return Integer.parseInt(obj);
        } catch (NumberFormatException e) {
            throw new RepositoryException("Please specify an integer test.docid property: -Dtest.docid=12345");
        }
    }

    private void pushResultSet(DocumentList docList)
        throws RepositoryException, PushException {

        Document doc;

        while ((doc = docList.nextDocument()) != null) {
            // TODO: make the feed name a property, along with the feed server and port
            pusher.take(doc, "livelink");
        }
    }
}
