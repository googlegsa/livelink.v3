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

import com.google.enterprise.connector.otex.client.ClientValue;
import com.google.enterprise.connector.otex.client.mock.MockClientValue;
import com.google.enterprise.connector.spi.RepositoryException;

import junit.framework.TestCase;

/**
 * Constructs a mock hierarchy by overriding the getParent and
 * getParents methods and tests each Genealogist implementation.
 */
public class GenealogistTest extends TestCase {
  /* The includedLocationNodes property value. */
  private static final String INCLUDED_NODES = "10";

  /* The excludedLocationNodes property value. */
  private static final String EXCLUDED_NODES = "2";

  /* The nodes to call getMatchingDescendants with. */
  private static final Integer[][] MATCHING_NODES = {
    { 1000 }, { 1001 }, { 1010 }, { 2000 }, { 2001 }, { 2002 },
    { 3000 }, { 3010 }, { 3100 }, {10100 }, };

  /**
   * A mock implementation of Genealogist.getParent.
   *
   * Our tree structure has children with an ID ten times their
   * parents, plus ones for siblings. For example, 10 is the first
   * child of 1, 11 is the second child, and so on.
   */
  private static int getMockParent(int objectId) {
    int parent = objectId / 10;
    return (parent == 0) ? -1 : parent;
  }

  /** A mock implementation of HybridGenealogist.getParents. */
  private static ClientValue getMockParents(String objectIds) {
    String[] strings = objectIds.split(",");
    Integer[][] values = new Integer[strings.length][];
    for (int i = 0; i < strings.length; i++) {
      int objectId = Integer.parseInt(strings[i]);
      values[i] = new Integer[] { objectId, getMockParent(objectId), null };
    }
    return new MockClientValue(
        new String[] { "DataID", "ParentID", "StepParentID" }, values);
  }

  private static class MockGenealogist extends Genealogist {
    private MockGenealogist() {
      super(null, INCLUDED_NODES, EXCLUDED_NODES, 10);
    }

    @Override
    protected Integer getParent(Object matchingId, int objectId) {
      queryCount++;
      return getMockParent(objectId);
    }
  }

  private static class MockHybridGenealogist extends HybridGenealogist {
    private MockHybridGenealogist() {
      super(null, INCLUDED_NODES, EXCLUDED_NODES, 10);
    }

    @Override
    protected Integer getParent(Object matchingId, int objectId) {
      queryCount++;
      return getMockParent(objectId);
    }

    @Override
    protected Parents getParents(String objectIds) {
      queryCount++;
      return new Parents(getMockParents(objectIds));
    }
  }

  private static class MockBatchGenealogist extends BatchGenealogist {
    private MockBatchGenealogist() {
      super(null, INCLUDED_NODES, EXCLUDED_NODES, 10);
    }

    @Override
    protected Integer getParent(Object matchingId, int objectId) {
      fail("getParent should not be called");
      return 0;
    }

    @Override
    protected Parents getParents(String objectIds) {
      queryCount++;
      return new Parents(getMockParents(objectIds));
    }
  }

  /** Helper method that runs the test for a given implementation. */
  private void testGenealogist(Genealogist gen) throws RepositoryException {
    MockClientValue matching =
        new MockClientValue(new String[] { "DataID" }, MATCHING_NODES);
    Object desc = gen.getMatchingDescendants(matching);
    assertEquals("1000,1001,1010,10100", desc);
  }

  public void testBase() throws RepositoryException {
    testGenealogist(new MockGenealogist());
  }

  public void testHybrid() throws RepositoryException {
    testGenealogist(new MockHybridGenealogist());
  }

  public void testBatch() throws RepositoryException {
    testGenealogist(new MockBatchGenealogist());
  }
}
