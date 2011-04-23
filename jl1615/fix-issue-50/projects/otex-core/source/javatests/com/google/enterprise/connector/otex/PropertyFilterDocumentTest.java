// Copyright 2011 Google Inc.
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

import junit.framework.TestCase;

import com.google.enterprise.connector.spi.Document;
import com.google.enterprise.connector.spi.Property;
import com.google.enterprise.connector.spi.SimpleDocument;
import com.google.enterprise.connector.spi.Value;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Tests PropertyFilterDocumentFactory.
 */
/* TODO (bmj): This should really be in the Connctor Manager. */
public class PropertyFilterDocumentTest extends FilterDocumentTest {

  /** Creates a PropertyFilterDocument. */
  protected static Document createFilter(
       String propName, String pattern) {
    return new PropertyFilterDocumentFactory(propName, pattern, SPACE)
          .newFilterDocument(createDocument());
  }

  /** Tests the Factory constructor. */
  public void testFactory() throws Exception {
    PropertyFilterDocumentFactory factory =
        new PropertyFilterDocumentFactory(PROP1, PATTERN, SPACE);
    assertNotNull(factory);
  }

  /** Tests the Factory constructor with illegal arguments. */
  public void testFactoryIllegalArgs() throws Exception {
    PropertyFilterDocumentFactory factory;

    try {
      factory = new PropertyFilterDocumentFactory(null, PATTERN, SPACE);
      fail("IllegalArgumentException expected");
    } catch (IllegalArgumentException expected) {
      // Expected.
    }

    try {
      factory = new PropertyFilterDocumentFactory("", PATTERN, SPACE);
      fail("IllegalArgumentException expected");
    } catch (IllegalArgumentException expected) {
      // Expected.
    }

    try {
      factory = new PropertyFilterDocumentFactory(PROP1, null, SPACE);
      fail("IllegalArgumentException expected");
    } catch (IllegalArgumentException expected) {
      // Expected.
    }

    try {
      factory = new PropertyFilterDocumentFactory(PROP1, "", SPACE);
      fail("IllegalArgumentException expected");
    } catch (IllegalArgumentException expected) {
      // Expected.
    }

    // Null or empty replacements are OK.
    factory = new PropertyFilterDocumentFactory(PROP1, PATTERN, null);
    factory = new PropertyFilterDocumentFactory(PROP1, PATTERN, "");
    assertNotNull(factory);
  }

  /** Tests for non-existent property should return null. */
  public void testNonExistentProperty() throws Exception {
    Document filter = createFilter(PROP1, PATTERN);
    assertNull(filter.findProperty("nonExistentProperty"));
  }

  public void testFilterNoMatchingProperty() throws Exception {
    Document filter = createFilter("nonExistentProperty", PATTERN);
    checkDocument(filter, createProperties());
  }

  /** Tests that the filter doesn't modify any Values that don't match. */
  public void testFilterNoMatchingValues() throws Exception {
    Document filter = createFilter(PROP1, "nonExistentPattern");
    checkDocument(filter, createProperties());
  }

  /**
   * Tests that the filter doesn't molest values in other properties
   * that do match.
   */
  public void testFilterOtherMatchingValuesInSingleValueProperty() throws Exception {
    Document filter = createFilter(PROP2, PATTERN);
    checkDocument(filter, createProperties());
  }

  /**
   * Tests that the filter doesn't molest values in other properties
   * that do match.
   */
  public void testFilterOtherMatchingValuesInMultiValueProperty() throws Exception {
    Document filter = createFilter(PROP4, PATTERN);
    checkDocument(filter, createProperties());
  }

  /** Tests that the filter changes the value in the target property. */
  public void testFilterMatchingValuesInSingleValueProperty() throws Exception {
    Map<String, List<Value>> expectedProps = createProperties();
    expectedProps.put(PROP1, valueList(TEST_STRING, CLEAN_STRING));

    Document filter = createFilter(PROP1, PATTERN);
    checkDocument(filter, expectedProps);
  }

  /** Tests that the filter changes the value in the target property. */
  public void testFilterMatchingFirstValueInMultiValueProperty() throws Exception {
    Map<String, List<Value>> expectedProps = createProperties();
    expectedProps.put(PROP3, valueList(TEST_STRING, CLEAN_STRING, EXTRA_STRING));

    Document filter = createFilter(PROP3, PATTERN);
    checkDocument(filter, expectedProps);
  }

  /** Tests that the filter changes the value in the target property. */
  public void testFilterMatchingLastValueInMultiValueProperty() throws Exception {
    Map<String, List<Value>> expectedProps = createProperties();
    expectedProps.put(PROP5, valueList(CLEAN_STRING, TEST_EXTRA_STRING, EXTRA_STRING));

    Document filter = createFilter(PROP5, PATTERN);
    checkDocument(filter, expectedProps);
  }

  /** Tests that the filter changes the values in the target property. */
  public void testFilterMatchingValuesInMultiValueProperty() throws Exception {
    Map<String, List<Value>> expectedProps = createProperties();
    expectedProps.put(PROP6, valueList(TEST_STRING, CLEAN_STRING, TEST_EXTRA_STRING, EXTRA_STRING));

    Document filter = createFilter(PROP6, PATTERN);
    checkDocument(filter, expectedProps);
  }
}
