// Copyright (C) 2006-2007 Google Inc.

package com.google.enterprise.connector.otex;

import java.util.ArrayList;
import java.util.Map;
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

    public void testNothing() { assertTrue(true); }

    /* This test looks at Doorways test data/categories/document.txt */
/*
    public void testDocumentWithCategories() throws Exception {
        Map attrs = ((LapiClient) client).getCategoryAttributes(logger, 657666);
        System.out.println(attrs); 
    }
*/

    /* This test looks at a custom document with a category where
     * the category has several versions. I wanted to verify that
     * we only got one version of the category back.
     */
/*
    public void testVersionedCategory() throws Exception {
        Map attrs = ((LapiClient) client).getCategoryAttributes(logger, 658201);
        System.out.println(attrs); 
    }
*/
}
