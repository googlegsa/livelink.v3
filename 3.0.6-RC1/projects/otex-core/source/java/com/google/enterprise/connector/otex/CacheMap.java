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

import com.google.common.base.Preconditions;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A simple LRU cache map.
 * We need to subclass LinkedHashMap to override removeEldestEntry.
 */
class CacheMap<K, V> extends LinkedHashMap<K, V> {
  /** The Maximum Cache size. */
  public static final int MAXIMUM_CAPACITY = (1 << 24);

  /** The logger for this class. */
  private static final Logger LOGGER =
      Logger.getLogger(CacheMap.class.getName());

  /** The maximum capacity of the cache. */
  private final int maxCapacity;

  /** Cache hit counter, for logging statistics. */
  private int hits = 0;

  /** Cache miss counter, for logging statistics. */
  private int misses = 0;

  /**
   * Constructs a new CacheMap that starts out at the minCapacity
   * and grows to the maxCapacity before it starts removing LRU items.
   * Due to  the interal resize logic of HashMap, the maxCapacity
   * should be a power of 2 multiple of the minCapacity.
   *
   * @param minCapacity the initial capacity of the cache
   * @param maxCapacity the maximum capacity of the cache
   */
  public CacheMap(int minCapacity, int maxCapacity) {
    // Adjust the initial capacity to account for the load factor.
    // This allows the allocated LinkedHashMap to hold the actual
    // minimum number of items before resizing and the actual maximum
    // number of items before we start purging LRU entries.
    // + 1 because LinkedHashMap resizes on >= capacity.  I want the
    // cache to hold the configured number of items without resizing (yet).
    super((int)((minCapacity / 0.75f) + 1), 0.75f, true);

    Preconditions.checkArgument(minCapacity > 0,
        "minCapacity must be positive");  // Avoid divide by 0.
    Preconditions.checkArgument(maxCapacity >= minCapacity,
        "maxCapacity must be at least as large as minCapacity");
    Preconditions.checkArgument(maxCapacity <= MAXIMUM_CAPACITY,
        "maxCapacity must be less than " + MAXIMUM_CAPACITY);

    this.maxCapacity = maxCapacity;
  }

  @Override
  protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
    boolean remove = size() > maxCapacity;
    if (remove && LOGGER.isLoggable(Level.FINEST)) {
      LOGGER.finest("CACHE: removing entry " + eldest.getKey());
    }
    return remove;
  }

  /** Returns the cached value for the given key, or null if not cached. */
  @Override
  public V get(Object key) {
    V value = super.get(key);
    if (value == null)
      misses++;
    else
      hits++;
    return value;
  }

  public static class CacheStatistics {
    public final int entries; // Number of entries in the CacheMap.
    public final int hits;    // number of cache hits accessing the CacheMap.
    public final int misses;  // Number of cache misses accessing the CacheMap.

    public CacheStatistics(int entries, int hits, int misses) {
      this.entries = entries;
      this.hits = hits;
      this.misses = misses;
    }

    public String toString() {
      return entries + " entries, " + hits + " hits, " + misses + " misses";
    }
  }

  /** Returns a snapshot of the current CacheMap Statistics. */
  public CacheStatistics statistics() {
    return new CacheStatistics(size(), CacheMap.this.hits, CacheMap.this.misses);
  }
}
