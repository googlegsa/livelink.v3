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
 * Tests FilterDocumentChain.
 */
/* TODO (bmj): This should really be in the Connctor Manager. */
public class FilterDocumentChainTest extends FilterDocumentTest {

  private List<FilterDocumentFactory> factoryList(FilterDocumentFactory... factories) {
    List<FilterDocumentFactory> list = new LinkedList<FilterDocumentFactory>();
    for (FilterDocumentFactory factory : factories) {
      list.add(factory);
    }
    return list;
  }

  /** Test that an empty chain returns original document. */
  public void testEmptyChain() throws Exception {
    FilterDocumentChain chain = new FilterDocumentChain();
    Document original = createDocument();
    Document filter = chain.newFilterDocument(original);
    assertSame(original, filter);
  }

  /** Test that multiple filters work in parallel. */
  public void testMultipleFiltersDifferentProperties() throws Exception {
    FilterDocumentChain chain = new FilterDocumentChain(factoryList(
        new PropertyFilterDocumentFactory(PROP1, PATTERN, SPACE),
        new PropertyFilterDocumentFactory(PROP3, PATTERN, SPACE)));
    Map<String, List<Value>> expectedProps = createProperties();
    expectedProps.put(PROP1, valueList(TEST_STRING, CLEAN_STRING));
    expectedProps.put(PROP3, valueList(TEST_STRING, CLEAN_STRING, EXTRA_STRING));
    checkDocument(chain.newFilterDocument(createDocument()), expectedProps);
  }

  /** Test that multiple filters work in series. */
  public void testMultipleFiltersSameProperty() throws Exception {
    FilterDocumentChain chain = new FilterDocumentChain(factoryList(
        new PropertyFilterDocumentFactory(PROP1, PATTERN, "XYZZY"),
        new PropertyFilterDocumentFactory(PROP1, "XYZZY", SPACE)));
    Map<String, List<Value>> expectedProps = createProperties();
    expectedProps.put(PROP1, valueList(TEST_STRING,
                                       TEST_STRING.replaceAll(PATTERN, "XYZZY"),
                                       CLEAN_STRING));
    checkDocument(chain.newFilterDocument(createDocument()), expectedProps);
  }

  /**
   * Test that multiple filters looking for non-matching things
   * pass the document through unchanged.
   */
  public void testMultipleFiltersSamePropertyNoMatch() throws Exception {
    FilterDocumentChain chain = new FilterDocumentChain(factoryList(
        new PropertyFilterDocumentFactory(PROP1, "foobar", SPACE),
        new PropertyFilterDocumentFactory(PROP3, "xyzzy", SPACE)));
    checkDocument(chain.newFilterDocument(createDocument()), createProperties());
  }
}

