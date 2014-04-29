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

import com.google.common.annotations.VisibleForTesting;
import com.google.enterprise.connector.otex.CacheMap.CacheStatistics;
import com.google.enterprise.connector.otex.client.Client;
import com.google.enterprise.connector.otex.client.ClientValue;
import com.google.enterprise.connector.spi.RepositoryException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

/** A class that knows about the node hierarchy in DTree. */
class Genealogist {
  /** The logger for this class. */
  private static final Logger LOGGER =
      Logger.getLogger(Genealogist.class.getName());

  /**
   * Duplicates the entries to include their negations: (a) becomes (a,-a).
   *
   * @return a comma-separated string of the entries and their negations
   * @see #getAncestorSet
   */
  public static String getAncestorNodes(String startNodes) {
    // Projects, Discussions, Channels, and TaskLists have a rather
    // strange behavior.  Their contents have a VolumeID that is the
    // same as the container's ObjectID, and an AncestorID that is the
    // negation the container's ObjectID.  To catch that, I am going
    // to create a superset list that adds the negation of everything
    // in the specified list. We believe this is safe, as negative
    // values of standard containers (folders, compound docs, etc)
    // simply should not exist, so we shouldn't get any false
    // positives.
    StringBuilder buffer = new StringBuilder();
    StringTokenizer tok = new StringTokenizer(startNodes, ":;,. \t\n()[]\"\'");
    while (tok.hasMoreTokens()) {
      String objId = tok.nextToken();
      if (buffer.length() > 0)
        buffer.append(',');
      buffer.append(objId);
      try {
        int intId = Integer.parseInt(objId);
        buffer.append(',');
        buffer.append(-intId);
      } catch (NumberFormatException e) {}
    }
    return buffer.toString();
  }

  /**
   * Duplicates the entries to include their negations: (a) becomes (a,-a).
   *
   * @return a set of the entries and their negations
   * @see #getAncestorNodes
   */
  private static Set<Integer> getAncestorSet(String nodes) {
    HashSet<Integer> set = new HashSet<Integer>();
    if (nodes == null || nodes.length() == 0)
      return set;

    StringTokenizer tok = new StringTokenizer(nodes, ":;,. \t\n()[]\"\'");
    while (tok.hasMoreTokens()) {
      String objId = tok.nextToken();
      try {
        int intId = Integer.parseInt(objId);
        set.add(intId);
        set.add(-intId);
      } catch (NumberFormatException e) {
      }
    }
    return set;
  }

  /**
   * Gets a new instance of the given Genealogist class.
   *
   * @param className the {@code Genealogist} class to instantiate
   * @param matching the candidates matching the non-hierarchical filters
   * @param startNodes the includedLocationNodes property value
   * @param excludedNodes the excludedLocationNodes property value
   * @param minCacheSize the initial size of the node cache
   * @param maxCacheSize the maximum size of the node cache
   * @return a new instance of the configured Genealogist class
   * @throws RepositoryException if the class cannot be instantiated
   * or initialized
   */
  public static Genealogist getGenealogist(String className, Client client,
      String startNodes, String excludedNodes, int minCacheSize,
      int maxCacheSize)  throws RepositoryException {
    Genealogist genealogist;
    try {
      genealogist = Class.forName(className)
          .asSubclass(Genealogist.class)
          .getConstructor(Client.class, String.class, String.class, int.class,
                          int.class)
          .newInstance(client, startNodes, excludedNodes, minCacheSize,
                       maxCacheSize);
    } catch (Exception e) {
      throw new LivelinkException(e, LOGGER);
    }
    return genealogist;
  }

  /** The logging level for orphan nodes in the Livelink database. */
  protected static final Level LOG_ORPHANS_LEVEL = Level.WARNING;

  /** The Livelink client to use to execute SQL queries. */
  protected final Client client;

  /** The SQL queries resource bundle wrapper. */
  protected final SqlQueries sqlQueries;

  /** A set based on the includedLocationNodes property value. */
  private final Set<Integer> includedSet;

  /** A set based on the excludedLocationNodes property value. */
  private final Set<Integer> excludedSet;

  /** A cache of items known to be included. */
  @VisibleForTesting
  final Cache<Integer> includedCache;

  /** A cache of items known to be excluded. */
  @VisibleForTesting
  final Cache<Integer> excludedCache;

  /** For logging statistics, the number of nodes processed by this instance.*/
  protected int nodeCount = 0;

  /** For logging statistics, the number of queries run by this instance.*/
  protected int queryCount = 0;

  public Genealogist(Client client, String startNodes, String excludedNodes,
                     int minCacheSize, int maxCacheSize) {
    this.client = client;

    // TODO(jlacey): We're saying this is SQL Server, because we aren't
    // wired to Connector.isSqlServer. All the genealogist queries are
    // DB agnostic at the moment, so this is (just barely) OK.
    this.sqlQueries = new SqlQueries(true);

    this.includedSet = getAncestorSet(startNodes);
    this.excludedSet = getAncestorSet(excludedNodes);

    // If the included set is empty, then everything that hits the
    // top-level (ParentID = -1) without being excluded should be
    // included. If the included set is not empty, then everything
    // that hits the top-level without being included should excluded.
    (includedSet.isEmpty() ? includedSet : excludedSet).add(-1);

    if (LOGGER.isLoggable(Level.FINEST)) {
      LOGGER.finest("DESCENDANTS: includedSet = " + includedSet);
      LOGGER.finest("DESCENDANTS: excludedSet = " + excludedSet);
      LOGGER.finest("DESCENDANTS: minCacheSize = " + minCacheSize);
      LOGGER.finest("DESCENDANTS: maxCacheSize = " + maxCacheSize);
    }

    this.excludedCache = new Cache<Integer>(minCacheSize, maxCacheSize);
    this.includedCache = new Cache<Integer>(minCacheSize, maxCacheSize);
  }

  /**
   * Finds the included nodes from among the matching candidates. This
   * is the core algorithm behind {@link getMatchingDescendants}. This
   * implementation looks up each parent for each node individually.
   *
   * @param matching the matching nodes to check for inclusion
   * @param descendants a buffer to write a comma-separated list of
   * included node IDs to
   */
  protected void matchDescendants(ClientValue matching,
      StringBuilder descendants) throws RepositoryException {
    for (int i = 0; i < matching.size(); i++) {
      // We do not cache the matches, which are probably mostly documents.
      ArrayList<Integer> cachePossibles = new ArrayList<Integer>();
      final int matchingId = matching.toInteger(i, "DataID");
      Integer parentId = matchingId;

      // TODO: Check for an interrupted traversal in this loop?
      while (!matchParent(matchingId, parentId, cachePossibles, descendants)) {
        parentId = getParent(matchingId, parentId);
        if (parentId == null) {
          break;
        }
        cachePossibles.add(parentId);
      }
    }
  }

  /**
   * Matches the given parent against the included and excluded nodes.
   *
   * @param matchingId the original node or nodes
   * @param parentId the current ancestor node to match
   * @param cachePossibles the ancestors between the two
   * @param descendants a buffer to write the {@code matchingId} and
   * a trailing comma to if the node should be included
   * @return {@code true} if a match was found, whether it is included
   * or excluded, or {@code false} if nothing is known about
   * {@code parentId}
   */
  protected final boolean matchParent(Object matchingId, int parentId,
      List<Integer> cachePossibles, StringBuilder descendants) {
    // We add the cachePossibles to the appropriate cache when we
    // determine an answer for matchingId. In the case of a cache
    // hit, parentID is obviously already in the cache, but the
    // other possibles are not.
    if (excludedCache.contains(parentId)) {
      if (LOGGER.isLoggable(Level.FINEST)) {
        LOGGER.finest("DESCENDANTS: Excluding " + matchingId
            + ", found " + parentId + " in cache after " + cachePossibles);
      }
      excludedCache.addAll(cachePossibles);
      return true;
    } else if (excludedSet.contains(parentId)) {
      if (LOGGER.isLoggable(Level.FINEST)) {
        LOGGER.finest("DESCENDANTS: Excluding " + matchingId
            + ", found " + parentId + " in set after " + cachePossibles);
      }
      excludedCache.addAll(cachePossibles);
      return true;
    } else if (includedCache.contains(parentId)) {
      if (LOGGER.isLoggable(Level.FINEST)) {
        LOGGER.finest("DESCENDANTS: Including " + matchingId
            + ", found " + parentId + " in cache after " + cachePossibles);
      }
      includedCache.addAll(cachePossibles);
      descendants.append(matchingId).append(',');
      return true;
    } else if (includedSet.contains(parentId)) {
      if (LOGGER.isLoggable(Level.FINEST)) {
        LOGGER.finest("DESCENDANTS: Including " + matchingId +
            ", found " + parentId + " in set after " + cachePossibles);
      }
      includedCache.addAll(cachePossibles);
      descendants.append(matchingId).append(',');
      return true;
    } else {
      return false;
    }
  }

  /**
   * Gets the ParentID of the given node.
   *
   * @param matchingId the original descendent node or nodes
   * @param objectID the object ID of the node
   * @return the parent ID of the node
   */
  protected Integer getParent(Object matchingId, int objectId)
      throws RepositoryException {
    // Get the parent of the current node, and also check for the
    // parent of the related node (with a negated object ID) in the
    // case of volumes such as projects. The query can return 0, 1, or
    // 2 results, since we are asking for the parents of two nodes,
    // one or both of which might not exist.
    queryCount++;
    ClientValue parent = sqlQueries.execute(client, null,
        "Genealogist.getParent", objectId, -objectId);
    switch (parent.size()) {
      case 0:
        // The nodes do not exist.
        logOrphans(matchingId, objectId);
        return null;
      case 1:
        // The usual case.
        return parent.toInteger(0, "ParentID");
      case 2:
        // Both nodes exist, so one is a volume with a parent of -1.
        // Return the other one.
        if (parent.toInteger(0, "ParentID") != -1) {
          return parent.toInteger(0, "ParentID");
        } else {
          return parent.toInteger(1, "ParentID");
        }
      default:
        throw new AssertionError(String.valueOf(parent.size()));
    }
  }

  /**
   * Logs a warning for orphans discovered in the hierarchy.
   *
   * @param orphans the discovered descendants of the missing node
   * @param parentId the missing node
   */
  protected final void logOrphans(Object orphans, int parentId) {
    // We've already detected an orphan at this point, which is very
    // rare, so don't bother checking the logging level.
    LOGGER.log(LOG_ORPHANS_LEVEL, "DESCENDANTS: Excluding " + orphans
        + ", ancestor " + parentId + " does not exist.");
  }

  /**
   * Finds the included nodes from among the matching candidates.
   *
   * @param matching the matching nodes to check for inclusion
   * @return a comma-separated list of included node IDs to
   */
  public final String getMatchingDescendants(ClientValue matching)
      throws RepositoryException {
    StringBuilder descendants = new StringBuilder();
    matchDescendants(matching, descendants);

    if (LOGGER.isLoggable(Level.FINEST)) {
      nodeCount += matching.size();
      LOGGER.finest("DESCENDANTS: Query statistics: " + nodeCount + " nodes, "
          + queryCount + " queries");
      LOGGER.finest("DESCENDANTS: Excluded cache statistics: "
          + excludedCache.statistics());
      LOGGER.finest("DESCENDANTS: Included cache statistics: "
          + includedCache.statistics());
    }

    if (descendants.length() > 0) {
      descendants.deleteCharAt(descendants.length() - 1);
      if (LOGGER.isLoggable(Level.FINER))
        LOGGER.finer("DESCENDANTS: Matching descendants: " + descendants);
      return descendants.toString();
    } else {
      LOGGER.finer("DESCENDANTS: No matching descendants.");
      return null;
    }
  }

  /* Used for testing and instrumentation. */
  public class Statistics {
    public final int nodeCount;   // Number of nodes processed by this instance.
    public final int queryCount;  // Number of queries run by this instance.
    public final CacheStatistics includedStats; // Statistics for includedCache.
    public final CacheStatistics excludedStats; // Statistics for excludedCache.

    public Statistics(int nodeCount, int queryCount,
        CacheStatistics includedStats, CacheStatistics excludedStats) {
      this.nodeCount = nodeCount;
      this.queryCount = queryCount;
      this.includedStats = includedCache.statistics();
      this.excludedStats = excludedCache.statistics();
    }

    public String toString() {
      return "nodes: " + nodeCount + ", queries: " + queryCount
          + ", includedCache: ( " + includedStats + " ), excludedCache: ( "
          + excludedStats + " )";
    }
  }

  /**
   * Returns a structure that contains the accumulated Genealogist and Cache
   * statistics.
   *
   * @return a Genealogist.Statistics structure.
   */
  /* Used for testing and instrumentation. */
  public synchronized Statistics statistics() {
    return new Statistics(nodeCount, queryCount, includedCache.statistics(),
                          excludedCache.statistics());
  }
}
