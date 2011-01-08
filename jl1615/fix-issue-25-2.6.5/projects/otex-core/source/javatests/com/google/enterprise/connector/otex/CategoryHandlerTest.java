// Copyright 2010 Google Inc.
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

import com.google.enterprise.connector.otex.client.Client;
import com.google.enterprise.connector.otex.client.ClientFactory;
import com.google.enterprise.connector.otex.client.ClientValue;
import com.google.enterprise.connector.otex.client.ClientValueFactory;
import com.google.enterprise.connector.otex.client.mock.MockClientFactory;
import com.google.enterprise.connector.otex.client.mock.MockClientValueFactory;
import com.google.enterprise.connector.spi.RepositoryException;
import junit.framework.TestCase;

public class CategoryHandlerTest extends TestCase {
  private static final Integer CATEGORY_ID = new Integer(42);

  private CategoryHandler getObjectUnderTest(String includedCategories)
      throws RepositoryException {
    LivelinkConnector connector = new LivelinkConnector(
        "com.google.enterprise.connector.otex.client.mock.MockClientFactory");
    connector.setServer(System.getProperty("connector.server"));
    connector.setPort(System.getProperty("connector.port"));
    connector.setUsername(System.getProperty("connector.username"));
    connector.setPassword(System.getProperty("connector.password"));
    connector.setShowHiddenItems("true");

    connector.setIncludedCategories(includedCategories);
    connector.setExcludedCategories("none");
    connector.login();

    ClientFactory clientFactory = connector.getClientFactory();
    Client client = clientFactory.createClient();
    return new CategoryHandler(connector, client);
  }

  /**
   * Calls the method under test, {@link CategoryHandler#getAttributeValue}.
   * MockClient.AttrGetValues always returns an empty list, so we
   * never need nameHandler, props, categoryVersion, or attrSetPath.
   */
  private void getAttributeValue(CategoryHandler categoryHandler,
      ClientValue attrInfo) throws RepositoryException {
    categoryHandler.getAttributeValue(null, null, CATEGORY_ID, null,
        "myattribute", Client.ATTR_TYPE_INT, null, attrInfo);
  }

  /**
   * Tests getAttributeValue with categories indexed. This is a smoke
   * test.
   */
  public void testGetAttributeValue_all() throws RepositoryException {
    // We should not need attrInfo in this configuration.
    CategoryHandler categoryHandler = getObjectUnderTest("all");
    ClientValue attrInfo = null;

    assertNull(categoryHandler.searchableCache);
    getAttributeValue(categoryHandler, attrInfo);
    assertNull(categoryHandler.searchableCache);
  }

  /**
   * Tests getAttributeValue with a null attrInfo. We expect a
   * NullPointerException.
   */
  public void testGetAttributeValue_null() throws RepositoryException {
    // We should need attrInfo in this configuration.
    CategoryHandler categoryHandler = getObjectUnderTest("all,searchable");
    ClientValue attrInfo = null;

    try {
      getAttributeValue(categoryHandler, attrInfo);
      fail("Expected a NullPointerException");
    } catch (NullPointerException e) {
    }
  }

  /**
   * Tests getAttributeValue with an attrInfo with a Search field. Our
   * mock client does not support enough to test true and false values
   * for Search.
   */
  public void testGetAttributeValue_empty()
      throws RepositoryException {
    CategoryHandler categoryHandler = getObjectUnderTest("all,searchable");
    ClientValueFactory valueFactory = new MockClientValueFactory();
    ClientValue attrInfo = valueFactory.createAssoc();

    assertNull(categoryHandler.searchableCache);
    getAttributeValue(categoryHandler, attrInfo);
    assertNotNull(categoryHandler.searchableCache);
    assertTrue(categoryHandler.searchableCache.toString(),
        categoryHandler.searchableCache.contains(CATEGORY_ID));
  }

  /**
   * Tests getAttributeValue with an attrInfo with a Search field. Our
   * mock client does not support enough to test true and false values
   * for Search.
   */
  public void testGetAttributeValue_search()
      throws RepositoryException {
    CategoryHandler categoryHandler = getObjectUnderTest("all,searchable");
    ClientValueFactory valueFactory = new MockClientValueFactory();
    ClientValue attrInfo = valueFactory.createAssoc();
    attrInfo.add("Search", true);

    assertNull(categoryHandler.searchableCache);
    getAttributeValue(categoryHandler, attrInfo);
    assertNull(categoryHandler.searchableCache);
  }
}
