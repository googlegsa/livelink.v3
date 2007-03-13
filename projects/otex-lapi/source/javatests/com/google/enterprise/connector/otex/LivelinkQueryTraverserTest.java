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

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;

import com.google.enterprise.connector.persist.ConnectorStateStore;
import com.google.enterprise.connector.persist.MockConnectorStateStore;
import com.google.enterprise.connector.pusher.Pusher;
import com.google.enterprise.connector.pusher.DocPusher;
import com.google.enterprise.connector.pusher.MockPusher;
import com.google.enterprise.connector.pusher.MockFeedConnection;
import com.google.enterprise.connector.spi.LoginException;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.QueryTraversalManager;
import com.google.enterprise.connector.spi.Session;
import com.google.enterprise.connector.traversal.QueryTraverser;
import com.google.enterprise.connector.traversal.Traverser;

import junit.framework.Assert;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;

/**
 * This is a copy of the QueryTraverserTest class with modifications
 * to use the Livelink connector rather than the mock JCR connector.
 * 
 * @author ziff@google.com (Your Name Here)
 * @author johnl@vizdom.com (John Lacey)
 */
public class LivelinkQueryTraverserTest extends TestCase {
    public final static void main(String[] args) {
        TestRunner.run(new TestSuite(LivelinkQueryTraverserTest.class));
    }

    private LivelinkConnector conn;
    
    public void setUp() {
        conn = LivelinkConnectorFactory.getConnector("connector.");
    }

    /**
     * Test method for
     * {@link com.google.enterprise.connector.traversal.QueryTraverser
     * #runBatch(int)}.
     * @throws InterruptedException 
     */
    public final void testRunBatch() throws IOException, LoginException,
            RepositoryException, InterruptedException {
    
        runTestBatches(1);
        runTestBatches(5);
        runTestBatches(25);
        runTestBatches(125);
        runTestBatches(625);
    
    }


    private void runTestBatches(int batchSize) throws IOException,
            LoginException, RepositoryException, InterruptedException {

        Session sess = conn.login();
        QueryTraversalManager qtm = sess.getQueryTraversalManager();

        String connectorName = "livelink";
        PrintStream out =
            //System.out;
            new PrintStream(new FileOutputStream("traverser-test.log"));
        Pusher pusher =
            //new MockPusher(System.out);
            //new DocPusher(new MockFeedConnection());
            new DocPusher(new MockFileFeedConnection(out));
        ConnectorStateStore connectorStateStore =
            new MockConnectorStateStore();

        Traverser traverser = new QueryTraverser(pusher, qtm,
            connectorStateStore, connectorName);

        System.out.println();
        System.out.println("Running batch test batchsize " + batchSize);
    
        int docsProcessed = -1;
        int totalDocsProcessed = 0;
        int batchNumber = 0;
        while (docsProcessed != 0) {
            docsProcessed = traverser.runBatch(batchSize);
            totalDocsProcessed += docsProcessed;
            System.out.println("Batch# " + batchNumber + " docs " +
                docsProcessed + " checkpoint " +
                connectorStateStore.getConnectorState(connectorName));
            batchNumber++;
        }
        Assert.assertEquals(378, totalDocsProcessed);
    }
}
