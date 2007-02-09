// Copyright (C) 2007 Google Inc.

package com.google.enterprise.connector.otex;

import junit.framework.TestCase;

public class LivelinkConnectorTest extends TestCase {
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
}
