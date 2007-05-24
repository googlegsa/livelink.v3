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
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import junit.framework.TestCase;

import com.google.enterprise.connector.spi.Connector;
import com.google.enterprise.connector.spi.Property;
import com.google.enterprise.connector.spi.PropertyMap;
import com.google.enterprise.connector.spi.PropertyMapList;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.Session;
import com.google.enterprise.connector.spi.TraversalManager;
import com.google.enterprise.connector.spi.SpiConstants;
import com.google.enterprise.connector.spi.Value;
import com.google.enterprise.connector.spi.ValueType;

public class LivelinkTest extends TestCase {
    private LivelinkConnector conn;

    public void setUp() {
        conn = LivelinkConnectorFactory.getConnector("connector.");
    }
    
    public void testLogin() throws RepositoryException {
        Session sess = conn.login();
    }

    public void testTraversal() throws RepositoryException {
        Session sess = conn.login();

        TraversalManager mgr = sess.getTraversalManager();
        mgr.setBatchHint(20);

        System.out.println("============ startTraversal ============");
        PropertyMapList pmList = mgr.startTraversal();
        PropertyMap lastNode = processResultSet(pmList);
        while (lastNode != null)
        {
            String checkpoint = mgr.checkpoint(lastNode);
            System.out.println("============ resumeTraversal ============");
            pmList = mgr.resumeTraversal(checkpoint);
            lastNode = processResultSet(pmList);
        }
    }

    private PropertyMap processResultSet(PropertyMapList pmList)
            throws RepositoryException {
        // XXX: What's supposed to happen if the result set is empty?
        PropertyMap map = null;
        Iterator it = pmList.iterator();
        while (it.hasNext()) {
            System.out.println();
            map = (PropertyMap) it.next();
            Iterator jt = map.getProperties();
            while (jt.hasNext()) {
                Property prop = (Property) jt.next();
                String name = prop.getName();
                Value value = prop.getValue();
                String printableValue;
                ValueType type = value.getType();
                if (type == ValueType.BINARY) {
                    try {
                        InputStream in = value.getStream();
                        byte[] buffer = new byte[32];
                        int count = in.read(buffer);
                        in.close();
                        if (count == -1)
                            printableValue = "";
                        else
                            printableValue = new String(buffer);
                    } catch (IOException e) {
                        printableValue = e.toString();
                    }
                } else
                    printableValue = value.getString();
                System.out.println(name + " = " + printableValue);
            }
        }
        return map;
    }

    private int rowCount;

    /**
     * Tests that changing the batch size does not change the number
     * of results returned. If the query restriction used in
     * resumeTraversal is not accurate, then the number of results can
     * vary as the batch size changes.
     */
    public void testResumeTraversal() throws RepositoryException {
        Session sess = conn.login();

        TraversalManager mgr = sess.getTraversalManager();

        // If time comparisons aren't working, then a batch size of
        // one will produce one row for each unique timestamp, so a
        // size of 100, or even two, would necessarily include some of
        // those duplicates that were elided.
        int[] batchHints = { /*1,*/ 100 };
        int previousRowCount = 0;
        for (int i = 0; i < batchHints.length; i++) {
            long before = System.currentTimeMillis();
            mgr.setBatchHint(batchHints[i]);

            rowCount = 0;
            PropertyMapList pmList = mgr.startTraversal();
            PropertyMap lastNode = countResultSet(pmList);
            while (lastNode != null) {
                String checkpoint = mgr.checkpoint(lastNode);
                pmList = mgr.resumeTraversal(checkpoint);
                lastNode = countResultSet(pmList);
            }
            long after = System.currentTimeMillis();
            System.out.println("TIME: " + (after - before));
            if (previousRowCount != 0) {
                assertEquals("Difference at " + batchHints[i],
                    previousRowCount, rowCount);
            }
            System.out.println("ROWCOUNT: " + rowCount);
            previousRowCount = rowCount;
        }
    }

    private PropertyMap countResultSet(PropertyMapList pmList)
            throws RepositoryException {
        PropertyMap map = null;
        Iterator it = pmList.iterator();
        while (it.hasNext()) {
            rowCount++;
            map = (PropertyMap) it.next();
        }
        return map;
    }

    public void testDuplicates() throws RepositoryException {
        Session sess = conn.login();

        TraversalManager mgr = sess.getTraversalManager();

        HashSet nodes = new HashSet();
        PropertyMapList pmList = mgr.startTraversal();
        PropertyMap lastNode = processResultSet(pmList, nodes);
        while (lastNode != null) {
            String checkpoint = mgr.checkpoint(lastNode);
            pmList = mgr.resumeTraversal(checkpoint);
            lastNode = processResultSet(pmList, nodes);
        }
    }

    private PropertyMap processResultSet(PropertyMapList pmList, Set nodes)
            throws RepositoryException {
        PropertyMap map = null;
        Iterator it = pmList.iterator();
        while (it.hasNext()) {
            map = (PropertyMap) it.next();
            Property prop = map.getProperty(SpiConstants.PROPNAME_DOCID);
            String value = prop.getValue().getString();
            assertTrue(value, nodes.add(value));
        }
        return map;
    }
}
