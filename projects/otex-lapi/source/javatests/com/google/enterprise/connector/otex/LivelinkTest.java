
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

    /** The ISO 8601 date format returned in property values. */
    private final SimpleDateFormat iso8601 =
        new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

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


    /**
     * Return the first result from traversal.
     * @param TraversalManager the traversal manager to query
     * @return the propertyMap for the result, or null if there is none.
     * @throws RepositoryException
     */
    private static PropertyMap getFirstResult(TraversalManager mgr)
        throws RepositoryException
    {
        mgr.setBatchHint(1);    // we only want the first result...
        PropertyMapList pmList = mgr.startTraversal();
        Iterator iter = pmList.iterator();
        if ( !iter.hasNext() )
            return null;
        else
            return (PropertyMap) iter.next();
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
        PropertyMap doc = getFirstResult(tm);
        assertNotNull("First doc is null.", doc);
        String dateStr = doc.getProperty("ModifyDate").getValue().getString();
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
        PropertyMapList results = tmSD.startTraversal();
        PropertyMap lastNode = assertNoResultsOlderThan(results, startDate);
        while (lastNode != null) {
            String checkpoint = tmSD.checkpoint(lastNode);
            results = tmSD.resumeTraversal(checkpoint);
            lastNode = assertNoResultsOlderThan(results, startDate);
        }
    }


    /**
     * Process a resultset and check for any results older than the
     * given date.
     */
    private PropertyMap assertNoResultsOlderThan(PropertyMapList pmList,
        Date date) throws RepositoryException {
        PropertyMap map = null;
        Iterator it = pmList.iterator();
        while (it.hasNext()) {
            map = (PropertyMap) it.next();
            String docId = map.getProperty("ID").getValue().getString();
            String dateStr =
                map.getProperty("ModifyDate").getValue().getString();

            // Check that the modify date is new enough
            try {
                Date docDate = iso8601.parse(dateStr);
                assertFalse("Document is older than " +
                    defaultDateFormat.format(date) +
                    ". (Id="+ docId + "; Name=\"" +
                    map.getProperty("Name").getValue().getString() +
                    "\"; Mod Date=" + defaultDateFormat.format(docDate) + ")",
                    docDate.before(date));
                boolean verbose = false;
                if ( verbose ) {
                    System.out.println("Examining:(Id="+ docId +
                        "; Name=\"" +
                        map.getProperty("Name").getValue().getString() +
                        "\"; Mod Date=" + defaultDateFormat.format(docDate) +
                        ")");
                }
            }
            catch (ParseException e) {
                fail("Unable to parse document modified date: " + dateStr);
            }
        }
        return map;
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
