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

import java.io.File;

import junit.framework.TestCase;

import com.google.enterprise.connector.spi.Document;
import com.google.enterprise.connector.spi.DocumentList;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.TraversalManager;
import com.google.enterprise.connector.manager.Context;
import com.google.enterprise.connector.pusher.FeedException;
import com.google.enterprise.connector.pusher.Pusher;
import com.google.enterprise.connector.pusher.PushException;
import com.google.enterprise.connector.pusher.DocPusher;
import com.google.enterprise.connector.pusher.GsaFeedConnection;
import com.google.enterprise.connector.traversal.FileSizeLimitInfo;
import com.google.enterprise.connector.util.filter.DocumentFilterChain;
import com.google.enterprise.connector.otex.client.Client;
import com.google.enterprise.connector.otex.client.ClientValue;

/**
 * Pushes a single document (DocID) to the GSA
 */
public class PushOneDocumentTest extends TestCase {
    private LivelinkConnector conn;
    private LivelinkSession sess;
    private Client client;
    private Pusher pusher;

    // TODO: get pusher server and port from [connector.?] properties
    private String feedServer = "gogol";
    private int feedPort = 19900;

    @Override
    protected void setUp() throws Exception {
        conn = LivelinkConnectorFactory.getConnector("connector.");
        sess = (LivelinkSession) conn.login();
        client = sess.getFactory().createClient();
        pusher = new DocPusher(
            new GsaFeedConnection(null, feedServer, feedPort, -1),
            "livelink", new FileSizeLimitInfo(), new DocumentFilterChain());

        // Iinitialize the Context for DocPusher.take.
        // FIXME: This code is duplicated in LivelinkQueryTraverserTest..
        String cmDir = System.getProperty("connector-manager.dir");
        if (cmDir == null)
            throw new Exception("Missing connector-manager.dir property.");
        String dir = "file:" + cmDir + File.separator;
        Context.getInstance().setStandaloneContext(
            dir + Context.DEFAULT_JUNIT_CONTEXT_LOCATION,
            dir + Context.DEFAULT_JUNIT_COMMON_DIR_PATH);
    }

    public void testTraversal() throws Exception {
        int objectId = getId();

        // Extract the LastModifiedDate of the DocID from Livelink
        // to forge a checkpoint.
        ClientValue docInfo = client.GetObjectInfo(0, objectId);
        Checkpoint checkpoint = new Checkpoint();
        checkpoint.setInsertCheckpoint(docInfo.toDate("ModifyDate"),
                                       objectId - 1);

        // Now push that one document.
        TraversalManager mgr = sess.getTraversalManager();
        mgr.setBatchHint(1);
        DocumentList rs = mgr.resumeTraversal(checkpoint.toString() + ',');
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
        throws RepositoryException, PushException, FeedException {

        Document doc;

        while ((doc = docList.nextDocument()) != null) {
            // TODO: make the feed name a property, along with the feed server and port
            pusher.take(doc);
        }
    }
}
