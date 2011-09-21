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

import junit.framework.TestCase;

import java.util.Arrays;

/** Tests the {@link Cache} class, a simple LRU cache. */
public class CacheTest extends TestCase {
  private Cache<Integer> cache;

  protected void setUp() {
    cache = new Cache<Integer>(3);
    addAll(cache, 1, 2, 3);
  }

  private boolean addAll(Cache<Integer> cache, Integer... entries) {
    return cache.addAll(Arrays.asList(entries));
  }

  public void testCacheHit() {
    assertTrue(cache.toString(), cache.contains(2));
  }

  public void testCacheMiss() {
    assertFalse(cache.toString(), cache.contains(5));
  }

  public void testCacheOverflow() {
    addAll(cache, 4);
    assertFalse(cache.toString(), cache.contains(1));
  }

  public void testCacheAccessOrder() {
    // Touch the oldest entry and add a new entry.
    assertTrue(cache.toString(), cache.contains(1));
    addAll(cache, 4);

    assertTrue(cache.toString(), cache.contains(1));
    assertFalse(cache.toString(), cache.contains(2));
    assertTrue(cache.toString(), cache.contains(3));
    assertTrue(cache.toString(), cache.contains(4));
  }

  public void testModified() {
    // Cache contains 1, 2, 3.
    assertTrue(cache.toString(), addAll(cache, 4));
    assertTrue(cache.toString(), addAll(cache, 5, 3));
    assertFalse(cache.toString(), addAll(cache, 3, 4, 5));
  }
}
