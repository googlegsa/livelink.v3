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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import com.google.enterprise.connector.manager.Context;
import com.google.enterprise.connector.instantiator.MockInstantiator;
import com.google.enterprise.connector.instantiator.ThreadPool;
import com.google.enterprise.connector.persist.ConnectorNotFoundException;
import com.google.enterprise.connector.pusher.DocPusherFactory;
import com.google.enterprise.connector.pusher.PusherFactory;
import com.google.enterprise.connector.pusher.MockFeedConnection;
import com.google.enterprise.connector.spi.RepositoryLoginException;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.Session;
import com.google.enterprise.connector.spi.TraversalManager;
import com.google.enterprise.connector.traversal.BatchResult;
import com.google.enterprise.connector.traversal.BatchSize;
import com.google.enterprise.connector.traversal.QueryTraverser;
import com.google.enterprise.connector.traversal.Traverser;

import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;

/**
 * This is a copy of the QueryTraverserTest class with modifications
 * to use the Livelink connector rather than the mock JCR connector.
 */
public class LivelinkQueryTraverserTest extends TestCase {
    public final static void main(String[] args) {
        TestRunner.run(new TestSuite(LivelinkQueryTraverserTest.class));
    }

    private LivelinkConnector conn;

    public void setUp() throws Exception {
        conn = LivelinkConnectorFactory.getConnector("connector.");

        // Iinitialize the Context for DocPusher.take.
        // FIXME: This code is duplicated in PushOneDocument.
        String cmDir = System.getProperty("connector-manager.dir");
        if (cmDir == null)
            throw new Exception("Missing connector-manager.dir property.");
        String dir = "file:" + cmDir + File.separator;
        Context.getInstance().setStandaloneContext(
            dir + Context.DEFAULT_JUNIT_CONTEXT_LOCATION,
            dir + Context.DEFAULT_JUNIT_COMMON_DIR_PATH);
    }

    /**
     * Test method for
     * {@link com.google.enterprise.connector.traversal.QueryTraverser#runBatch(int)}.
     *
     * @throws InterruptedException
     */
    public final void testRunBatch() throws IOException,
            RepositoryLoginException, RepositoryException,
            InterruptedException, ConnectorNotFoundException {

        runTestBatches(1);
        runTestBatches(5);
        runTestBatches(25);
        runTestBatches(125);
        runTestBatches(625);
    }


    private void runTestBatches(int batchHint) throws IOException,
            RepositoryLoginException, RepositoryException,
            InterruptedException, ConnectorNotFoundException {

        Session sess = conn.login();
        TraversalManager qtm = sess.getTraversalManager();

        String connectorName = "livelink";

        OutputStream out =
            new FileOutputStream("traverser-test.log");

        PusherFactory pusherFactory =
            new DocPusherFactory(new MockFileFeedConnection(out));

        MockInstantiator instantiator =
            new MockInstantiator(ThreadPool.newThreadPool(300));

        Traverser traverser = new QueryTraverser(pusherFactory, qtm,
            instantiator.getTraversalStateStore(connectorName),
            connectorName, null);

        instantiator.setupTraverser(connectorName, traverser);

        System.out.println();
        System.out.println("Running batch test batchsize " + batchHint);

        int docsProcessed = 0;
        int totalDocsProcessed = 0;
        int batchNumber = 0;
        BatchSize batchSize = new BatchSize(batchHint, batchHint);
        do {
            BatchResult result = traverser.runBatch(batchSize);
            docsProcessed = result.getCountProcessed();
            if (docsProcessed > 0) {
                totalDocsProcessed += docsProcessed;
            }
            System.out.println("Batch# " + batchNumber + " docs " +
                docsProcessed + " checkpoint " +
                instantiator.getConnectorState(connectorName));
            batchNumber++;
        } while (docsProcessed > 0);

        // TODO: There should be some way to parameterize the
        // expected number of documents.
        assertEquals(691, totalDocsProcessed);
    }
}
