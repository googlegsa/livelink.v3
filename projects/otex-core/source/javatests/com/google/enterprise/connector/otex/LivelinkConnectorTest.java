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

import java.util.Map;
import java.util.Date;

import com.google.enterprise.connector.otex.ConfigurationException;
import com.google.enterprise.connector.otex.client.mock.MockClientFactory;
import com.google.enterprise.connector.spi.RepositoryException;
import junit.framework.TestCase;

public class LivelinkConnectorTest extends TestCase {
    private LivelinkConnector connector;
    
    protected void setUp() {
        connector = new LivelinkConnector(
            "com.google.enterprise.connector.otex.client.mock.MockClientFactory");
        connector.setServer(System.getProperty("connector.server"));
        connector.setPort(System.getProperty("connector.port"));
        connector.setUsername(System.getProperty("connector.username"));
        connector.setPassword(System.getProperty("connector.password"));
    }

    public void testSanitizingListsOfIntegers() {
        String[] values = { 
            "",		"",
            "  ",	"",
            " 	 ",	"",
            "	",	"",
            "168",	"168",
            "1,2,3",		"1,2,3",
            " 1 , 2 , 3 ",	"1,2,3",
            "1	,2,,,3",	null,
            "1,2,3) union",	null,
            "1,2,3)",		null,
            "1,2,3,\r\n4,5,6",	"1,2,3,4,5,6",
            "1,2,3\r\n4,5,6",	null,
            "1,a,b",		null,
            "1,,2,3",		null,
            "123, 456, 789",	"123,456,789",
            "123, 456, 789,",	null,
            ",123,456,789",	null,
            "{1,2,3}",		"1,2,3",
            "	{ 1, 2,3}",	"1,2,3",
            "	{ 1, 2,3",	"1,2,3",
            "1,2,3}",		"1,2,3",
            "{ 1 }",		"1",
            "} 1 }",		null,
            "} 1 {",		null,
            "{ 1 {",		null,
        };

        for (int i = 0; i < values.length; i += 2) {
            try {
                String output =
                    LivelinkConnector.sanitizeListOfIntegers(values[i]);
                assertEquals(values[i], values[i + 1], output);
            } catch (IllegalArgumentException e) {
                assertNull(e.toString(), values[i + 1]);
            }
        }
    }

    public void testSanitizingListsOfStrings() {
        String[] values = { 
            "",		"",
            "  ",	"",
            " 	 ",	"",
            "	",	"",
            "168",	"168",
            "a,b,c",		"a,b,c",
            " a , b , c ",	"a,b,c",
            "a	,2,,,-",	"a,2,,,-",
            "1,2,3) union",	"1,2,3) union",
            "1,2,3)",		"1,2,3)",
            "1,2,3,\r\n4,5,6",	"1,2,3,4,5,6",
            "1,2,3\r\n4,5,6",	"1,2,3\r\n4,5,6",
            "1,a,b",		"1,a,b",
            "1,,2,3",		"1,,2,3",
            "123, 456, 789",	"123,456,789",
            "123, 456, 789,",	"123,456,789,",
            ",123,456,789",	",123,456,789",
            "{1,2,3}",		"1,2,3",
            "	{ 1, 2,3}",	"1,2,3",
            "	{ 1, 2,3",	"1,2,3",
            "1,2,3}",		"1,2,3",
            "{ 1 }",		"1",
            "} 1 }",		null,
            "} 1 {",		null,
            "{ 1 {",		null,
        };

        for (int i = 0; i < values.length; i += 2) {
            try {
                String output =
                    LivelinkConnector.sanitizeListOfStrings(values[i]);
                assertEquals(values[i], values[i + 1], output);
            } catch (IllegalArgumentException e) {
                assertNull(e.toString(), values[i + 1]);
            }
        }
    }

    public void testStartDate1() throws Exception {
        connector.setStartDate("2007-09-27 01:12:13");
        connector.login();
        Date startDate = connector.getStartDate();
        assertNotNull(startDate);
        assertEquals("Thu Sep 27 01:12:13 PDT 2007", startDate.toString());
    }

    public void testStartDate2() throws Exception {
        connector.setStartDate("2007-09-27");
        connector.login();
        Date startDate = connector.getStartDate();
        assertNotNull(startDate);
        assertEquals("Thu Sep 27 00:00:00 PDT 2007", startDate.toString());
    }

    public void testStartDate3() throws Exception {
        connector.setStartDate("");
        connector.login();
        assertEquals(null, connector.getStartDate()); 
    }

    public void testStartDate4() throws Exception {
        connector.setStartDate("   ");
        connector.login();
        assertEquals(null, connector.getStartDate()); 
    }

    public void testStartDate5() throws Exception {
        connector.setStartDate("Sep 27, 2007");
        connector.login();
        assertEquals(null, connector.getStartDate()); 
    }

    public void testStartDate6() throws Exception {
        connector.setStartDate("2007-09-27 01:12:13");
        connector.login();
        Date startDate = connector.getStartDate();
        assertNotNull(startDate);
        assertEquals("Thu Sep 27 01:12:13 PDT 2007", startDate.toString());
        connector.login();
        startDate = connector.getStartDate();
        assertNotNull(startDate);
        assertEquals("Thu Sep 27 01:12:13 PDT 2007", startDate.toString());
    }

    public void testStartDate7() throws Exception {
        connector.setStartDate("Sep 27, 2007");
        connector.login();
        assertEquals(null, connector.getStartDate()); 
        connector.login();
        assertEquals(null, connector.getStartDate()); 
    }

    /**
     * A simple test that verifies that at least one of the connecion
     * properties is being correctly assigned in the client factory.
     */
    public void testServer() throws RepositoryException {
        connector.login();
        Map values = MockClientFactory.getInstance().getValues();
        assertTrue(values.toString(), values.containsKey("setServer"));
        assertEquals(values.get("setServer"),
            System.getProperty("connector.server"));
    }

    /**
     * Tests enableNtlm, which requires httpUsername and httpPassword.
     */
    public void testEnableNtlmGood() throws RepositoryException {
        connector.setUseHttpTunneling(true);
        connector.setEnableNtlm(true);
        connector.setHttpUsername("username");
        connector.setHttpPassword("password");
        connector.login();
    }

    /**
     * Tests enableNtlm, which requires httpUsername and httpPassword.
     */
    public void testEnableNtlmBad() throws RepositoryException {
        connector.setUseHttpTunneling(true);
        connector.setEnableNtlm(true);
        try {
            connector.login();
            fail("Exception an exception");
        } catch (ConfigurationException e) {
        }
    }

    /**
     * Tests enableNtlm, which requires httpUsername and httpPassword.
     */
    public void testEnableNtlmIgnored() throws RepositoryException {
        connector.setUseHttpTunneling(false);
        connector.setEnableNtlm(true);
        connector.login();
    }
}
