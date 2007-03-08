// Copyright (C) 2006-2007 Google Inc.

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
/* This test depends on known object ids. We'd need a standard
 * set of test data and a way to locate items in the repository
 * to make it more general.
 *
 * The tests are commented out until the Client interface has
 * been updated to include whatever the category
 * attributes-reading method turns out to be.
 */
public class LapiClientTest extends TestCase {

    private static final Logger logger = Logger.getLogger(
        LapiClientTest.class.getName()); 

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
            logger, 695345);
        Map attrs2 = ((LapiClient) client).getCategoryAttributes2(
            logger, 695345);
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
