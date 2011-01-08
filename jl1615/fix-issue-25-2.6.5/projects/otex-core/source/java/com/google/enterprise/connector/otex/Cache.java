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

  /**
   * The underlying LRU store. We need to subclass LinkedHashMap to
   * override removeEldestEntry.
   */
  private static class CacheMap<K, V> extends LinkedHashMap<K, V> {
    /** The initial and maximum capacity of the cache. */
    private final int capacity;

    public CacheMap(int capacity) {
      // 0.75f is the default load factor; true implies access-order
      super(capacity, 0.75f, true);
      this.capacity = capacity;
    }

    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
      boolean remove = size() > capacity;
      if (remove && LOGGER.isLoggable(Level.FINEST)) {
        LOGGER.finest("CACHE: removing entry " + eldest.getKey());
      }
      return remove;
    }
  }

  /** Object stored in the map for each key in our cache. */
  private static final Object VALUE = new Object();

  /** Backing store for the cache. */
  private final CacheMap<E, Object> store;

  /** Cache hit counter, for logging statistics. */
  private int hits = 0;

  /** Cache miss counter, for logging statistics. */
  private int misses = 0;

  public Cache(int capacity) {
    store = new CacheMap<E, Object>(capacity);
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
    boolean exists = store.get(target) != null;
    if (exists)
      hits++;
    else
      misses++;
    return exists;
  }

  public String statistics() {
    return store.size() + " entries, " + hits + " hits, " + misses + " misses";
  }

  /** A convenience method for logging. */
  public String toString() {
    return store.keySet().toString();
  }
}
