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

import com.google.enterprise.connector.otex.CacheMap.CacheStatistics;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A simple LRU cache that supports a couple of Java Collections
 * operations.
 */
/*
 * We could implement Iterable, Collection, or even Set, but we don't need to.
 */
class Cache<E> {
  /** The logger for this class. */
  private static final Logger LOGGER = Logger.getLogger(Cache.class.getName());

  /** Object stored in the map for each key in our cache. */
  private static final Object VALUE = new Object();

  /** Backing store for the cache. */
  private final CacheMap<E, Object> store;

  /**
   * Constructs a new Cache that starts out at the minCapacity
   * and grows to the maxCapacity before it starts removing LRU items.
   * Due to  the interal resize logic of the underlying implementation,
   * the maxCapacity should be a power of 2 multiple of the minCapacity.
   *
   * @param minCapacity the initial capacity of the cache
   * @param maxCapacity the maximum capacity of the cache
   */
  public Cache(int minCapacity, int maxCapacity) {
    store = new CacheMap<E, Object>(minCapacity, maxCapacity);
  }

  public boolean addAll(Collection<? extends E> collection) {
    boolean modified = false;
    for (E key : collection) {
      if (store.put(key, VALUE) == null)
        modified = true;
    }
    return modified;
  }

  public boolean contains(Object target) {
    // For the access-ordering to work we have to use get here instead
    // of containsKey.
    return (store.get(target) != null);
  }

  public CacheStatistics statistics() {
    return store.statistics();
  }

  /** A convenience method for logging. */
  public String toString() {
    return store.keySet().toString();
  }
}
