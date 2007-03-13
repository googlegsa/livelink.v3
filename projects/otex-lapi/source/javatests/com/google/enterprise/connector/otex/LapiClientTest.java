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
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;
import java.util.Properties;

import com.google.enterprise.connector.otex.client.Client;
//import com.google.enterprise.connector.otex.client.lapi.LapiClient;
import junit.framework.TestCase;

/**
 * Tests LapiClient methods.
 */
/* This test depends on known object IDs. We'd need a standard
 * set of test data and a way to locate items in the repository
 * to make it more general.
 *
 * The tests are commented out until the Client interface has
 * been updated to include whatever the category
 * attributes-reading method turns out to be.
 */
public class LapiClientTest extends TestCase {
    /** The logger for this class. */
    private static final Logger LOGGER =
        Logger.getLogger(LapiClientTest.class.getName()); 

    private Client client;

    protected void setUp() throws Exception {
        LivelinkConnector conn =
            LivelinkConnectorFactory.getConnector("connector."); 
        LivelinkSession session = (LivelinkSession) conn.login();
        client = session.getFactory().createClient(); 
    }

    /**
     * Empty test to serve as a placeholder.
     */
    public void testNothing() {
        assertTrue(true); 
    }


    /**
     * Tests the results of the two candidate implementations.
     * @throws Exception if an error occurs
     */
    /*
    public void testCompareImplementations() throws Exception {
        Map attrs1 = ((LapiClient) client).getCategoryAttributes(
            LOGGER, 695345);
        Map attrs2 = ((LapiClient) client).getCategoryAttributes2(
            LOGGER, 695345);
        printMap(attrs1); 
        System.out.println("*****************");
        printMap(attrs2); 
        assertTrue("Results not equal", attrs1.equals(attrs2)); 
    }
    */

    /**
     * Prints a map with the keys sorted.
     *
     * @param m the map to print
     */
    private void printMap(Map m) {
        Iterator i = new TreeMap(m).entrySet().iterator();
        while (i.hasNext()) {
            Map.Entry e = (Map.Entry) i.next();
            System.out.println(e.getKey() + " = " + e.getValue()); 
        }
    }
}
