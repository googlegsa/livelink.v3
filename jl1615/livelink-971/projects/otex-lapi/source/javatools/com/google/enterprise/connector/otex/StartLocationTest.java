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

import java.io.InputStream;
import java.io.IOException;
import java.util.Iterator;

import junit.framework.TestCase;

import com.google.enterprise.connector.spi.Property;
import com.google.enterprise.connector.spi.Document;
import com.google.enterprise.connector.spi.DocumentList;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.Session;
import com.google.enterprise.connector.spi.TraversalManager;
import com.google.enterprise.connector.spi.Value;
import com.google.enterprise.connector.spiimpl.BinaryValue;

public class StartLocationTest extends TestCase {
    private LivelinkConnector conn;

    public void setUp() throws RepositoryException {
        conn = LivelinkConnectorFactory.getConnector("connector.");
    }

    public void testTraversal() throws RepositoryException {
        Session sess = conn.login();

        conn.setIncludedLocationNodes(getStartNodes());

        TraversalManager mgr = sess.getTraversalManager();
        mgr.setBatchHint(3000);

        DocumentList rs = mgr.startTraversal();
        processResultSet(rs);
    }

    private String getStartNodes() throws RepositoryException {
        try {
            String obj = System.getProperty("test.docids",
                         System.getProperty("test.docid", "BAD"));
            return LivelinkConnector.sanitizeListOfIntegers(obj);
        } catch (Exception e) {
            throw new RepositoryException("Please specify a list of start " +
                      "nodes using the test.docids property: " +
                      "-Dtest.docids=\"12345,12346\"");
        }
    }

    private void processResultSet(DocumentList docList)
            throws RepositoryException {
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
                            byte[] buffer = new byte[32];
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
