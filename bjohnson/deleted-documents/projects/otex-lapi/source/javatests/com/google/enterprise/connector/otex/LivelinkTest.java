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
import java.text.ParseException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import junit.framework.TestCase;

import com.google.enterprise.connector.otex.LivelinkDocumentList;

import com.google.enterprise.connector.spi.Connector;
import com.google.enterprise.connector.spi.Property;
import com.google.enterprise.connector.spi.Document;
import com.google.enterprise.connector.spi.DocumentList;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.Session;
import com.google.enterprise.connector.spi.TraversalManager;
import com.google.enterprise.connector.spi.SpiConstants;
import com.google.enterprise.connector.spi.Value;
import com.google.enterprise.connector.spiimpl.BinaryValue;


public class LivelinkTest extends TestCase {
    private LivelinkConnector conn;

    /** The ISO 8601 date format returned in property values. */
    private final SimpleDateFormat iso8601 =
        new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    /** A default date formatter for the current locale. */
    private final DateFormat defaultDateFormat =
        DateFormat.getDateInstance();

    public void setUp() throws RepositoryException {
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
        LivelinkDocumentList docList =
            (LivelinkDocumentList) mgr.startTraversal();
        Document lastNode;
        while ((lastNode = processResultSet(docList)) != null) {
            String checkpoint = docList.checkpoint();
            System.out.println("============ resumeTraversal ============");
            docList = (LivelinkDocumentList) mgr.resumeTraversal(checkpoint);
        }
    }

    
    /**
     * Return the first result from traversal.
     * @param TraversalManager the traversal manager to query
     * @return the propertyMap for the result, or null if there is none.
     * @throws RepositoryException
     */
    private static Document getFirstResult(TraversalManager mgr)
        throws RepositoryException
    {
        mgr.setBatchHint(1);    // we only want the first result...
        LivelinkDocumentList docList =
            (LivelinkDocumentList) mgr.startTraversal();
        Iterator iter = docList.iterator();
        if ( !iter.hasNext() )
            return null;
        else
            return (Document) iter.next();
    }


    private Document processResultSet(LivelinkDocumentList docList)
            throws RepositoryException {
        // XXX: What's supposed to happen if the result set is empty?
        if (docList == null)
            return null;
        
        Document doc = null;
        Iterator it = docList.iterator();
        while (it.hasNext()) {
            System.out.println();
            doc = (Document) it.next();
            Iterator jt = doc.getPropertyNames().iterator();
            while (jt.hasNext()) {
                String name = (String) jt.next();
                Value value = doc.findProperty(name).nextValue();
                String printableValue;
                if (value instanceof BinaryValue) {
                    try {
                        InputStream in = ((BinaryValue) value).getInputStream();
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
                    printableValue = value.toString();
                System.out.println(name + " = " + printableValue);
            }
        }
        return doc;
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
        // FIXME: A batch size of 1 was killing one of the Livelink
        // instances on swift with too many requests. Without it this
        // test is somewhat pointless, so we should figure out if we
        // can put in a delay, get a better Livelink instance, get a
        // better Solaris server, or something. I'm leaving the "1"
        // in place commented out, using // for convenience.
        int[] batchHints = { // 1,
            100 };
        int previousRowCount = 0;
        for (int i = 0; i < batchHints.length; i++) {
            long before = System.currentTimeMillis();
            mgr.setBatchHint(batchHints[i]);

            rowCount = 0;
            LivelinkDocumentList docList =
                (LivelinkDocumentList) mgr.startTraversal();
            Document lastNode = countResultSet(docList);
            while (lastNode != null) {
                String checkpoint = docList.checkpoint();
                docList =
                    (LivelinkDocumentList) mgr.resumeTraversal(checkpoint);
                lastNode = countResultSet(docList);
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


    /**
     * Test that the startDate connector property is effective.
     * @throws RepositoryException on failure
     */
    public void testStartDate() throws RepositoryException {
        String startDateString = "May 10, 2007"; // Why not?
        Date startDate = null;
        try {
            startDate = defaultDateFormat.parse(startDateString);
        }
        catch (ParseException e) {
            fail("Unable to parse start date: " + startDateString);
        }

        // First, get an ordinary traversal manager, start it,
        // and see what the first doc is that it returns.
        // Verify that this date is before our sanctioned startDate
        Session sess = conn.login();
        TraversalManager tm = sess.getTraversalManager();
        Document doc = getFirstResult(tm);
        assertNotNull("First doc is null.", doc);
        String dateStr = doc.findProperty("ModifyDate").nextValue().toString();
        try {
            Date docDate = iso8601.parse(dateStr);

            assertTrue("First doc is newer than startDate.",
                docDate.before(startDate));
        }
        catch (ParseException e) {
            fail("Unable to parse document modified date: " + dateStr);
        }

        // Now, get another traversal manager, with a connector
        // with an appropriate start date(SD).  Traverse the
        // repository and ensure that nothing older than the
        // start date is returned.
        LivelinkConnector connSD =
            LivelinkConnectorFactory.getConnector("connector.");
        connSD.setStartDate(startDateString);
        Session sessSD = connSD.login();
        TraversalManager tmSD = sessSD.getTraversalManager();

        // Look for any results that are too old
        LivelinkDocumentList results =
            (LivelinkDocumentList) tmSD.startTraversal();
        Document lastNode = assertNoResultsOlderThan(results, startDate);
        while (lastNode != null) {
            String checkpoint = results.checkpoint();
            results = (LivelinkDocumentList) tmSD.resumeTraversal(checkpoint);
            lastNode = assertNoResultsOlderThan(results, startDate);
        }
    }


    /**
     * Process a resultset and check for any results older than the
     * given date.
     */
    private Document assertNoResultsOlderThan(LivelinkDocumentList docList,
        Date date) throws RepositoryException {
        Document doc = null;
        Iterator it = docList.iterator();
        while (it.hasNext()) {
            doc = (Document) it.next();
            String docId = doc.findProperty("ID").nextValue().toString();
            String dateStr =
                doc.findProperty("ModifyDate").nextValue().toString();

            // Check that the modify date is new enough
            try {
                Date docDate = iso8601.parse(dateStr);
                assertFalse("Document is older than " +
                    defaultDateFormat.format(date) +
                    ". (Id="+ docId + "; Name=\"" +
                    doc.findProperty("Name").nextValue().toString() +
                    "\"; Mod Date=" + defaultDateFormat.format(docDate) + ")",
                    docDate.before(date));
                boolean verbose = false;
                if ( verbose ) {
                    System.out.println("Examining:(Id="+ docId +
                        "; Name=\"" +
                        doc.findProperty("Name").nextValue().toString() +
                        "\"; Mod Date=" + defaultDateFormat.format(docDate) +
                        ")");
                }
            }
            catch (ParseException e) {
                fail("Unable to parse document modified date: " + dateStr);
            }
        }
        return doc;
    }


    private Document countResultSet(LivelinkDocumentList docList)
            throws RepositoryException {
        Document doc = null;
        Iterator it = docList.iterator();
        while (it.hasNext()) {
            rowCount++;
            doc = (Document) it.next();
        }
        return doc;
    }

    public void testDuplicates() throws RepositoryException {
        Session sess = conn.login();

        TraversalManager mgr = sess.getTraversalManager();

        HashSet nodes = new HashSet();
        LivelinkDocumentList docList =
            (LivelinkDocumentList) mgr.startTraversal();
        Document lastNode = processResultSet(docList, nodes);
        while (lastNode != null) {
            String checkpoint = docList.checkpoint();
            docList = (LivelinkDocumentList) mgr.resumeTraversal(checkpoint);
            lastNode = processResultSet(docList, nodes);
        }
    }

    private Document processResultSet(LivelinkDocumentList docList, Set nodes)
            throws RepositoryException {
        Document doc = null;
        Iterator it = docList.iterator();
        while (it.hasNext()) {
            doc = (Document) it.next();
            Property prop = doc.findProperty(SpiConstants.PROPNAME_DOCID);
            String value = prop.nextValue().toString();
            assertTrue(value, nodes.add(value));
        }
        return doc;
    }
}
