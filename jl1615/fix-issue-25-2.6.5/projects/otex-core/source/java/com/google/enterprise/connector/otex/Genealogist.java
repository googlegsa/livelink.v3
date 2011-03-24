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

  /** The Livelink client to use to execute SQL queries. */
  protected final Client client;

  /** A set based on the includedLocationNodes property value. */
  private final Set<Integer> includedSet;

  /** A set based on the excludedLocationNodes property value. */
  private final Set<Integer> excludedSet;

  /** A cache of items known to be included. */
  private final Cache<Integer> includedCache;

  /** A cache of items known to be excluded. */
  private final Cache<Integer> excludedCache;

  /** For logging statistics, the number of nodes processed by this instance.*/
  protected int nodeCount = 0;

  /** For logging statistics, the number of queries run by this instance.*/
  protected int queryCount = 0;

  public Genealogist(Client client, String startNodes, String excludedNodes,
      int cacheSize) {
    this.client = client;

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
    }

    this.excludedCache = new Cache<Integer>(cacheSize);
    this.includedCache = new Cache<Integer>(cacheSize);
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
      int parentId = matchingId;

      // TODO: Check for an interrupted traversal in this loop?
      while (!matchParent(matchingId, parentId, cachePossibles, descendants)) {
        parentId = getParent(parentId);
        cachePossibles.add(parentId);

        // FIXME: Leave this in, or wrap it in a config property?
        if (LOGGER.isLoggable(Level.FINEST)) {
          LOGGER.finest("DESCENDANTS: Checking " + matchingId + " parent: "
              + parentId);
        }
      }
    }
  }

  /**
   * Matches the given parent against the included and excluded nodes.
   *
   * @param matchingId the original node
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
            + ", found in cache after " + cachePossibles);
      }
      excludedCache.addAll(cachePossibles);
      return true;
    } else if (excludedSet.contains(parentId)) {
      if (LOGGER.isLoggable(Level.FINEST)) {
        LOGGER.finest("DESCENDANTS: Excluding " + matchingId
            + ", found in set after " + cachePossibles);
      }
      excludedCache.addAll(cachePossibles);
      return true;
    } else if (includedCache.contains(parentId)) {
      if (LOGGER.isLoggable(Level.FINEST)) {
        LOGGER.finest("DESCENDANTS: Including " + matchingId
            + ", found in cache after " + cachePossibles);
      }
      includedCache.addAll(cachePossibles);
      descendants.append(matchingId).append(',');
      return true;
    } else if (includedSet.contains(parentId)) {
      if (LOGGER.isLoggable(Level.FINEST)) {
        LOGGER.finest("DESCENDANTS: Including " + matchingId +
            ", found in set after " + cachePossibles);
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
   * @param objectID the object ID of the node
   * @return the parent ID of the node
   */
  protected int getParent(int objectId) throws RepositoryException {
    // Excluding -1 and then adding it back again is not
    // pointless. Without the exclusion, the query might return
    // two results, but one of them would be -1 and is ignorable
    // (the other result is the real parent). The exclusion means
    // that we only get the real parent back, but it also means
    // that the query won't return a -1 as the only result, so we
    // have to add that back again.
    queryCount++;
    ClientValue parent = client.ListNodes(
        "DataID in (" + objectId + "," + -objectId + ") and ParentID <> -1",
        "DTree", new String[] { "ParentID" });
    return (parent.size() == 0) ? -1 : parent.toInteger(0, "ParentID");
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
}
