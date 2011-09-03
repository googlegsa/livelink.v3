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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A simple LRU cache map.
 * We need to subclass LinkedHashMap to override removeEldestEntry.
 */
class CacheMap<K, V> extends LinkedHashMap<K, V> {
  /** The logger for this class. */
  private static final Logger LOGGER =
      Logger.getLogger(CacheMap.class.getName());

  /** The initial and maximum capacity of the cache. */
  private final int capacity;

  /** Cache hit counter, for logging statistics. */
  private int hits = 0;

  /** Cache miss counter, for logging statistics. */
  private int misses = 0;

  public CacheMap(int capacity) {
    // 0.75f is the default load factor; true implies access-order
    super(capacity, 0.75f, true);
    this.capacity = capacity;
  }

  @Override
  protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
    boolean remove = size() > capacity;
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

  public String statistics() {
    return size() + " entries, " + hits + " hits, " + misses + " misses";
  }
}
