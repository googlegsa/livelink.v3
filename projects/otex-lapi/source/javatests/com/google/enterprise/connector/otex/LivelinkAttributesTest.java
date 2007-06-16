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
import java.util.*;

import com.google.enterprise.connector.spi.RepositoryLoginException;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.Session;

import com.google.enterprise.connector.otex.client.Client;

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
 * @author Brett.Michael.Johnson@gmail.com (Brett Johnson)
 */
public class LivelinkAttributesTest extends TestCase {
    public final static void main(String[] args) {
        TestRunner.run(new TestSuite(LivelinkAttributesTest.class));
    }

    private LivelinkConnector conn;
    private LivelinkSession sess;
    private Client client;
    private LivelinkAttributes attr;
    
    public void setUp() throws IOException,
            RepositoryLoginException, RepositoryException,
            InterruptedException, Exception {
        conn = LivelinkConnectorFactory.getConnector("connector.");
        sess = (LivelinkSession) conn.login();
        client = sess.getFactory().createClient();
        attr = new LivelinkAttributes(conn, client);
    }

    /**
     * Test method for getCategoryAttributes() on a document with
     * category attributes, specifically, the test data document
     * /Enterprise/llglobal/TestData-.../categories/attrSetTestDocument.txt
     *
     * @throws InterruptedException, IOException, RepositoryLoginException, RepositoryException
     */
    public final void testGetAttrsFromDocWithAttrs() throws IOException,
            RepositoryLoginException, RepositoryException,
            InterruptedException {
    
        PrintStream out =
            //System.out;
            new PrintStream(new FileOutputStream("attribute-test.log"));

        System.out.println();
        System.out.println("Extracting Attributes from document attrSetTestDocument.txt");
    
        // docid=/Enterprise/llglobal/TestData-.../categories/attrSetTestDocument.txt
        Map catAttrs = attr.getCategoryAttributes(31143);	// [XXX - bmj] make docid a property
        
        // This document has category attributes, so make sure we got some back
        Assert.assertEquals(4, catAttrs.size());
        
        // For now iterate over the returned map, printing out the name:value pairs
        Iterator i = catAttrs.entrySet().iterator();
        while (i.hasNext()) {
            Map.Entry entry = (Map.Entry) i.next();
            String key = entry.getKey().toString();
            String value = entry.getValue().toString();
            if (key.equals("Text Field Attribute in Set"))
                Assert.assertEquals(value, "[Text field value, Text field value 2]");
            else if (key.equals("Integer Popup Attribute in Set"))
                Assert.assertEquals(value, "[1, 2]");
            else if (key.equals("Date Field Attribute in Set"))
                Assert.assertEquals(value, "[D/2003/9/19:0:0:0]");
            else if (key.equals("User Field Attribute in Set"))
                Assert.assertEquals(value, "[llglobal]");
            else
                Assert.fail("Unexpected category attribute: " + key + " = " + value);
        }
    }

    /**
     * Test method for getCategoryAttributes2() on a document with
     * category attributes, specifically, the test data document
     * /Enterprise/llglobal/TestData-.../categories/attrSetTestDocument.txt
     *
     * @throws InterruptedException, IOException, RepositoryLoginException, RepositoryException
     */
    public final void testGetAttrs2FromDocWithAttrs() throws IOException,
            RepositoryLoginException, RepositoryException,
            InterruptedException {
    
        PrintStream out =
            //System.out;
            new PrintStream(new FileOutputStream("attribute-test.log"));

        System.out.println();
        System.out.println("Extracting Attributes from document attrSetTestDocument.txt");
    
        // docid=/Enterprise/llglobal/TestData-.../categories/attrSetTestDocument.txt
        Map catAttrs = attr.getCategoryAttributes2(31143);	// [XXX - bmj] make docid a property
        
        // This document has category attributes, so make sure we got some back
        Assert.assertEquals(4, catAttrs.size());
        
        // For now iterate over the returned map, printing out the name:value pairs
        Iterator i = catAttrs.entrySet().iterator();
        while (i.hasNext()) {
            Map.Entry entry = (Map.Entry) i.next();
            String key = entry.getKey().toString();
            String value = entry.getValue().toString();
            if (key.equals("Text Field Attribute in Set"))
                Assert.assertEquals(value, "[Text field value, Text field value 2]");
            else if (key.equals("Integer Popup Attribute in Set"))
                Assert.assertEquals(value, "[1, 2]");
            else if (key.equals("Date Field Attribute in Set"))
                Assert.assertEquals(value, "[D/2003/9/19:0:0:0]");
            else if (key.equals("User Field Attribute in Set"))
                Assert.assertEquals(value, "[llglobal]");
            else
                Assert.fail("Unexpected category attribute: " + key + " = " + value);
        }
    }


    /**
     * Test method for getCategoryAttributes() on a document with NO
     * category attributes, specifically, the test data document
     * /Enterprise/llglobal/TestData-.../other/empty.txt
     *
     * @throws InterruptedException, IOException, RepositoryLoginException, RepositoryException
     */
    public final void testGetAttrsFromDocWithoutAttrs() throws IOException,
            RepositoryLoginException, RepositoryException,
            InterruptedException {
    
        PrintStream out =
            //System.out;
            new PrintStream(new FileOutputStream("attribute-test.log"));

        System.out.println();
        System.out.println("Extracting Attributes from document empty.txt");
    
        // docid=/Enterprise/llglobal/TestData-.../other/empty.txt
        Map catAttrs = attr.getCategoryAttributes(30779);	// [XXX - bmj] make docid a property
        
        // This document has no category attributes, so make sure we got none back
        Assert.assertEquals(0, catAttrs.size());
        
    }

}
