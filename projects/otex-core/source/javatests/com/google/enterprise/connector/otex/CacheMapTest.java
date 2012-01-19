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

/** Tests the {@link CacheMap} class, a simple LRU cache map. */
public class CacheMapTest extends TestCase {
  private CacheMap<Integer, String> cache;

  protected void setUp() {
    cache = new CacheMap<Integer, String>(3, 3);
    cache.put(1, "one");
    cache.put(2, "two");
    cache.put(3, "three");
  }

  public void testInvalidArgs() {
    // minCapacity must be positive.
    try {
      cache = new CacheMap<Integer, String>(-1, -1);
    } catch (IllegalArgumentException expected) {
      // Expected.
    }

    // minCapacity must not be zero.
    try {
      cache = new CacheMap<Integer, String>(0, 1);
    } catch (IllegalArgumentException expected) {
      // Expected.
    }

    // maxCapacity must be >= minCapacity.
    try {
      cache = new CacheMap<Integer, String>(5, 4);
    } catch (IllegalArgumentException expected) {
      // Expected.
    }

    // maxCapacity must not exceed MAXIMUM_CAPACITY
    try {
      cache = new CacheMap<Integer, String>(1, CacheMap.MAXIMUM_CAPACITY + 1);
    } catch (IllegalArgumentException expected) {
      // Expected.
    }
  }

  public void testCacheHit() {
    assertTrue(cache.toString(), cache.containsKey(2));
    assertEquals("two", cache.get(2));
  }

  public void testCacheMiss() {
    assertFalse(cache.toString(), cache.containsKey(5));
  }

  public void testCacheOverflow() {
    cache.put(4, "four");
    assertFalse(cache.toString(), cache.containsKey(1));
    assertTrue(cache.toString(), cache.containsKey(4));
  }

  public void testCacheAccessOrder() {
    // Touch the oldest entry and add a new entry.
    assertEquals("one", cache.get(1));
    cache.put(4, "four");

    assertTrue(cache.toString(), cache.containsKey(1));
    assertFalse(cache.toString(), cache.containsKey(2));
    assertTrue(cache.toString(), cache.containsKey(3));
    assertTrue(cache.toString(), cache.containsKey(4));
  }

  public void testModified() {
    // Cache contains 1, 2, 3.
    assertEquals("one", cache.put(1, "uno"));
    assertNull(cache.put(4, "quatro"));

    assertTrue(cache.toString(), cache.containsKey(1));
    assertFalse(cache.toString(), cache.containsKey(2));
    assertTrue(cache.toString(), cache.containsKey(3));
    assertTrue(cache.toString(), cache.containsKey(4));
  }

  public void testStatistics() {
    assertEquals("3 entries, 0 hits, 0 misses", cache.statistics().toString());
    assertEquals("one", cache.get(1));
    assertEquals("two", cache.get(2));
    assertNull(cache.get(4));
    assertEquals("3 entries, 2 hits, 1 misses", cache.statistics().toString());
  }
}
