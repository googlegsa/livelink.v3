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
import com.google.enterprise.connector.spiimpl.BinaryValue;

import com.google.enterprise.connector.otex.client.Client;
import com.google.enterprise.connector.otex.client.ClientValue;
import com.google.enterprise.connector.otex.LivelinkTraversalManager;

/**
 * Pushes a single document (DocID) to the GSA
 */
public class OneDocumentTest extends TestCase {
    private LivelinkConnector conn;
    private LivelinkSession sess;
    private Client client;

    public void setUp() throws RepositoryException {
        conn = LivelinkConnectorFactory.getConnector("connector.");
        sess = (LivelinkSession) conn.login();
        client = sess.getFactory().createClient(); 
    }
    
    public void testTraversal() throws RepositoryException {
        int objectId = getId();

        // Extract the LastModifiedDate of the DocID from Livelink
        // to forge a checkpoint.
        ClientValue docInfo = client.GetObjectInfo(0, objectId);
        Checkpoint checkpoint = new Checkpoint();
        checkpoint.setInsertCheckpoint(docInfo.toDate("ModifyDate"),
                                       objectId - 1);

        // Now push that one document
        TraversalManager mgr = sess.getTraversalManager();
        mgr.setBatchHint(1);
        DocumentList rs = mgr.resumeTraversal(checkpoint.toString() + ',');
        processResultSet(rs);
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

    private void processResultSet(DocumentList docList)
            throws RepositoryException {
        // XXX: What's supposed to happen if the result set is empty?
        if (docList == null) {
            System.out.println("No results.");
            return;
        }
        
        Document doc;
        while ((doc = docList.nextDocument()) != null) {
            System.out.println();
            Iterator jt = doc.getPropertyNames().iterator();
            while (jt.hasNext()) {
                String name = (String) jt.next();
                Property prop = doc.findProperty(name);
                Value value;
                while ((value = prop.nextValue()) != null) {
                    String printableValue;
                    if (value instanceof BinaryValue) {
                        try {
                            InputStream in =
                                ((BinaryValue) value).getInputStream();
                            byte[] buffer = new byte[32000];
                            int count = in.read(buffer);
                            in.close();
                            if (count == -1)
                                printableValue = "";
                            else
                                printableValue = new String(buffer, 0, count);
                        } catch (IOException e) {
                            printableValue = e.toString();
                        }
                    } else
                        printableValue = value.toString();
                    System.out.println(name + " = " + printableValue);
                }
            }
        }
    }
}
