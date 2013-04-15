// Copyright 2007 Google Inc.
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

import com.google.common.collect.ImmutableList;
import com.google.enterprise.connector.otex.ConfigurationException;
import com.google.enterprise.connector.otex.client.mock.MockClientFactory;
import com.google.enterprise.connector.spi.RepositoryException;
import junit.framework.TestCase;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Date;

public class LivelinkConnectorTest extends TestCase {
  private LivelinkConnector connector;

  private final JdbcFixture jdbcFixture = new JdbcFixture();

  protected void setUp() throws SQLException {
    // TODO: Move this fixture and its tests to a separate test class.
    jdbcFixture.setUp();
    jdbcFixture.executeUpdate(
        "insert into DTreeAncestors(DataID, AncestorID) "
        + "values(4104, 2002)", // The values do not matter.
        "insert into KDual values(104 /* does not matter */)",
        "insert into WebNodes(DataID, PermID, MimeType) "
        + "values(42, 0, 'text/xml')");

    connector = new LivelinkConnector(new MockClientFactory());
    connector.setServer(System.getProperty("connector.server"));
    connector.setPort(System.getProperty("connector.port"));
    connector.setUsername(System.getProperty("connector.username"));
    connector.setPassword(System.getProperty("connector.password"));

    connector.setShowHiddenItems("true");
  }

  protected void tearDown() throws SQLException {
    jdbcFixture.tearDown();
  }

  public void testSanitizingListsOfIntegers() {
    String[] values = {
      "",         "",
      "  ",       "",
      " \t ",     "",
      "\t",       "",
      ",",        null,
      "168",      "168",
      "1,2,3",            "1,2,3",
      " 1 , 2 , 3 ",      "1,2,3",
      "1\t,2,,,3",        null,
      "1,2,3) union",     null,
      "1,2,3)",           null,
      "1,2,3,\r\n4,5,6",  "1,2,3,4,5,6",
      "1,2,3\r\n4,5,6",   null,
      "1,a,b",            null,
      "1,,2,3",           null,
      "123, 456, 789",    "123,456,789",
      "123, 456, 789,",   null,
      ",123,456,789",     null,
      "{1,2,3}",          "1,2,3",
      "\t{ 1, 2,3}",     "1,2,3",
      "\t{ 1, 2,3",      "1,2,3",
      "1,2,3}",           "1,2,3",
      "{ 1 }",            "1",
      "} 1 }",            null,
      "} 1 {",            null,
      "{ 1 {",            null,
    };

    for (int i = 0; i < values.length; i += 2) {
      try {
        String output = LivelinkConnector.sanitizeListOfIntegers(values[i]);
        assertEquals(values[i], values[i + 1], output);
      } catch (IllegalArgumentException e) {
        assertNull(e.toString(), values[i + 1]);
      }
    }
  }

  public void testSanitizingListsOfStrings() {
    String[] values = {
      "",         "",
      "  ",       "",
      " \t ",     "",
      "\t",       "",
      ",",        ",",
      "168",      "168",
      "a,b,c",            "a,b,c",
      " a , b , c ",      "a,b,c",
      "a\t,2,,,-",        "a,2,,,-",
      "1,2,3) union",     "1,2,3) union",
      "1,2,3)",           "1,2,3)",
      "1,2,3,\r\n4,5,6",  "1,2,3,4,5,6",
      "1,2,3\r\n4,5,6",   "1,2,3\r\n4,5,6",
      "1,a,b",            "1,a,b",
      "1,,2,3",           "1,,2,3",
      "123, 456, 789",    "123,456,789",
      "123, 456, 789,",   "123,456,789,",
      ",123,456,789",     ",123,456,789",
      "{1,2,3}",          "1,2,3",
      "\t{ 1, 2,3}",      "1,2,3",
      "\t{ 1, 2,3",       "1,2,3",
      "1,2,3}",           "1,2,3",
      "{ 1 }",            "1",
      "} 1 }",            null,
      "} 1 {",            null,
      "{ 1 {",            null,
    };

    for (int i = 0; i < values.length; i += 2) {
      try {
        String output = LivelinkConnector.sanitizeListOfStrings(values[i]);
        assertEquals(values[i], values[i + 1], output);
      } catch (IllegalArgumentException e) {
        assertNull(e.toString(), values[i + 1]);
      }
    }
  }

  public void testAutoDetectServtype() throws RepositoryException {
    // Force the call to autoDetectServtype
    connector.login();
    // H2 supports Oracle's rownum, so it looks like Oracle to us.
    assertFalse(connector.isSqlServer());
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
    MockClientFactory clientFactory =
        (MockClientFactory) connector.getClientFactory();
    Map<String, Object> values = clientFactory.getValues();
    assertTrue(values.toString(), values.containsKey("setServer"));
    assertEquals(System.getProperty("connector.server"),
        values.get("setServer"));
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
      fail("Expected an exception");
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

  /**
   * Tests the check for System Administration rights.
   */
  public void testNoAdminRights() throws RepositoryException {
    // This value can be anything other than "Admin".
    connector.setUsername("llglobal");
    try {
      connector.login();
      fail("Expected an exception");
    } catch (ConfigurationException e) {
    }
  }

  /** Tests showHiddenItems with an invalid null value. */
  public void testGetHiddenItemsSubtypes_null() {
    try {
      LivelinkConnector.getHiddenItemsSubtypes(null);
      fail("Expected a NullPointerException.");
    } catch (NullPointerException e) {
    }
  }

  /** Tests showHiddenItems with empty or false values. */
  public void testGetHiddenItemsSubtypes_empty() {
    HashSet<Object> expected = new HashSet<Object>();
    assertEquals(expected, LivelinkConnector.getHiddenItemsSubtypes(""));
    assertEquals(expected, LivelinkConnector.getHiddenItemsSubtypes(" "));
    assertEquals(expected, LivelinkConnector.getHiddenItemsSubtypes("\t"));
    assertEquals(expected, LivelinkConnector.getHiddenItemsSubtypes("false"));
    assertEquals(expected,
        LivelinkConnector.getHiddenItemsSubtypes("\tfaLsE  "));
  }

  /** Tests showHiddenItems with "all" or true values. */
  public void testGetHiddenItemsSubtypes_all() {
    HashSet<Object> expected = new HashSet<Object>();
    expected.add("all");
    assertEquals(expected, LivelinkConnector.getHiddenItemsSubtypes("All"));
  }

  /** Tests showHiddenItems with numeric subtype values. */
  public void testGetHiddenItemsSubtypes_subtypes() {
    HashSet<Object> expected = new HashSet<Object>();
    expected.add(new Integer(42));
    expected.add(new Integer(1729));
    assertEquals(expected,
        LivelinkConnector.getHiddenItemsSubtypes("42,1729"));
    assertEquals(expected,
        LivelinkConnector.getHiddenItemsSubtypes("1729,42"));
  }

  /** Tests showHiddenItems with "all" plus numeric subtype values. */
  public void testGetHiddenItemsSubtypes_allPlusSubtype() {
    HashSet<Object> expected = new HashSet<Object>();
    expected.add("all");
    expected.add(new Integer(42));
    assertEquals(expected, LivelinkConnector.getHiddenItemsSubtypes("All,42"));
    assertEquals(expected, LivelinkConnector.getHiddenItemsSubtypes("42,All"));
  }

  /** Tests showHiddenItems with other non-numeric values. */
  public void testGetHiddenItemsSubtypes_invalid() {
    // FIXME: Why is this allowed?
    HashSet<Object> expected = new HashSet<Object>();
    expected.add("invalid");
    assertEquals(expected,
        LivelinkConnector.getHiddenItemsSubtypes("Invalid"));
  }

  /** Tests showHiddenItems = false with useDTreeAncestors = true. */
  public void testShowHiddenItems_useDTreeAncestorsTrue()
      throws RepositoryException {
    connector.setShowHiddenItems("false");
    connector.setUseDTreeAncestors(true);
    connector.login();
  }

  /** Tests showHiddenItems = false with useDTreeAncestors = false. */
  public void testShowHiddenItems_useDTreeAncestorsFalseOK()
      throws RepositoryException {
    connector.setShowHiddenItems("true");
    connector.setUseDTreeAncestors(false);
    connector.login();
  }

  /** Tests showHiddenItems = false with useDTreeAncestors = false. */
  public void testShowHiddenItems_useDTreeAncestorsFalseError()
      throws RepositoryException {
    connector.setShowHiddenItems("false");
    connector.setUseDTreeAncestors(false);
    try {
      connector.login();
      fail("Expected an exception");
    } catch (ConfigurationException e) {
    }
  }

  public void testDomainAndName_null() throws RepositoryException {
    connector.setDomainAndName(null);
    try {
      connector.login();
      fail("Expected an exception");
    } catch (IllegalArgumentException e) {
      assertEquals(null, e.getMessage());
    }
  }

  public void testDomainAndName_empty() throws RepositoryException {
    connector.setDomainAndName("");
    try {
      connector.login();
      fail("Expected an exception");
    } catch (IllegalArgumentException e) {
      assertEquals("", e.getMessage());
    }
  }

  public void testDomainAndName_invalid() throws RepositoryException {
    connector.setDomainAndName("whenever");
    try {
      connector.login();
      fail("Expected an exception");
    } catch (IllegalArgumentException e) {
      assertTrue(e.getMessage(), e.getMessage().matches("(?i).*whenever.*"));
    }
  }

  public void testDomainAndName_values() throws RepositoryException {
    DomainAndName[] values = DomainAndName.values();
    assertTrue(Arrays.toString(values), values.length > 0);

    for (DomainAndName value : values) {
      connector.setDomainAndName(value.toString());
      connector.login();
    }
  }

  public void testDomainAndName_lowercaseValues() throws RepositoryException {
    for (DomainAndName value : DomainAndName.values()) {
      connector.setDomainAndName(value.toString().toLowerCase());
      connector.login();
    }
  }

  public void testUnsupportedFetchVersionTypes_null() throws Exception {
    connector.setUnsupportedFetchVersionTypes(null);
    connector.login();
    assertEquals(ImmutableList.<Integer>of(),
        connector.getUnsupportedFetchVersionTypes());
  }

  public void testUnsupportedFetchVersionTypes_empty() throws Exception {
    connector.setUnsupportedFetchVersionTypes("");
    connector.login();
    assertEquals(ImmutableList.<Integer>of(),
        connector.getUnsupportedFetchVersionTypes());
  }

  public void testUnsupportedFetchVersionTypes_one() throws Exception {
    connector.setUnsupportedFetchVersionTypes("123");
    connector.login();
    assertEquals(ImmutableList.of(123),
        connector.getUnsupportedFetchVersionTypes());
  }

  public void testUnsupportedFetchVersionTypes_many() throws Exception {
    connector.setUnsupportedFetchVersionTypes("123, 456  , 789");
    connector.login();
    assertEquals(ImmutableList.of(123, 456, 789),
        connector.getUnsupportedFetchVersionTypes());
  }

  /**
   * Tests a missing integer (two consecutive comma separators).
   * setUnsupportedFetchVersionTypes relies on the internal
   * sanitizeListOfIntegers method to handle this case.
   */
  public void testUnsupportedFetchVersionTypes_missing() throws Exception {
    String missingValue = "123,,789";
    connector.setUnsupportedFetchVersionTypes(missingValue);
    try {
      connector.login();
      fail("Expected an exception");
    } catch (IllegalArgumentException e) {
      assertEquals(missingValue, e.getMessage());
    }
  }

  public void testSqlWhereCondition_empty() throws RepositoryException {
    connector.setSqlWhereCondition("DataID = 0");
    try {
      connector.login();
      fail("Expected an exception");
    } catch (RepositoryException e) {
      assertTrue(e.getMessage(), e.getMessage().contains("SQL WHERE"));
    }
  }

  public void testSqlWhereCondition_invalid() throws RepositoryException {
    connector.setSqlWhereCondition("DataID is zero");
    try {
      connector.login();
      fail("Expected an exception");
    } catch (RepositoryException e) {
      assertTrue(e.getMessage(), e.getMessage().contains("zero"));
    }
  }

  public void testSqlWhereCondition_everything() throws RepositoryException {
    connector.setSqlWhereCondition("1=1");
    connector.login();
  }

  public void testSqlWhereCondition_condition() throws RepositoryException {
    connector.setSqlWhereCondition("DataID = 42");
    connector.login();
  }

  /** Tests selecting a column that only exists in WebNodes and not DTree. */
  public void testSqlWhereCondition_webnodes() throws RepositoryException {
    connector.setSqlWhereCondition("MimeType is not null");
    connector.login();
  }

  /** Tests setGenealogist and setGenealogistMin/MaxCacheSizes */
  public void testGenealogist() throws RepositoryException {
    String name = "com.google.enterprise.connector.otex.HybridGenealogist";
    connector.setGenealogist(name);
    connector.setGenealogistMinCacheSize(2000);
    connector.setGenealogistMaxCacheSize(8000);
    connector.login();
    assertEquals(name, connector.getGenealogist());
    assertEquals(2000, connector.getGenealogistMinCacheSize());
    assertEquals(8000, connector.getGenealogistMaxCacheSize());
    Genealogist genealogist = Genealogist.getGenealogist(
        connector.getGenealogist(), null, "2000", "1999",
        connector.getGenealogistMinCacheSize(),
        connector.getGenealogistMaxCacheSize());
    assertEquals(name, genealogist.getClass().getName());
  }

  /** Tests bad genealogist cache sizes: invalid min size. */
  public void testBadGenealogistCacheSize1() throws RepositoryException {
    try {
      connector.setGenealogistMinCacheSize(0);
      connector.login();
      fail("Expected ConfigurationException.");
    } catch (ConfigurationException expected) {
      assertTrue(expected.getMessage().contains("genealogistMinCacheSize"));
    }
  }

  /** Tests bad genealogist cache sizes: invalid min size. */
  public void testBadGenealogistCacheSize2() throws RepositoryException {
    try {
      connector.setGenealogistMinCacheSize(-1);
      connector.login();
      fail("Expected ConfigurationException.");
    } catch (ConfigurationException expected) {
      assertTrue(expected.getMessage().contains("genealogistMinCacheSize"));
    }
  }

  /** Tests bad genealogist cache sizes: invalid min size. */
  public void testBadGenealogistCacheSize3() throws RepositoryException {
    try {
      connector.setGenealogistMinCacheSize(Integer.MAX_VALUE);
      connector.login();
      fail("Expected ConfigurationException.");
    } catch (ConfigurationException expected) {
      assertTrue(expected.getMessage().contains("genealogistMinCacheSize"));
    }
  }

  /** Tests bad genealogist cache sizes: invalid max size. */
  public void testBadGenealogistCacheSize4() throws RepositoryException {
    try {
      connector.setGenealogistMaxCacheSize(0);
      connector.login();
      fail("Expected ConfigurationException.");
    } catch (ConfigurationException expected) {
      assertTrue(expected.getMessage().contains("genealogistMaxCacheSize"));
    }
  }

  /** Tests bad genealogist cache sizes: invalid max size. */
  public void testBadGenealogistCacheSize5() throws RepositoryException {
    try {
      connector.setGenealogistMaxCacheSize(-1);
      connector.login();
      fail("Expected ConfigurationException.");
    } catch (ConfigurationException expected) {
      assertTrue(expected.getMessage().contains("genealogistMaxCacheSize"));
    }
  }

  /** Tests bad genealogist cache sizes: invalid max size. */
  public void testBadGenealogistCacheSize6() throws RepositoryException {
    try {
      connector.setGenealogistMaxCacheSize(Integer.MAX_VALUE);
      connector.login();
      fail("Expected ConfigurationException.");
    } catch (ConfigurationException expected) {
      assertTrue(expected.getMessage().contains("genealogistMaxCacheSize"));
    }
  }
}
