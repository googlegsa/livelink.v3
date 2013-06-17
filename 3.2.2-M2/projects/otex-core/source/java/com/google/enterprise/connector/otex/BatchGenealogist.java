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
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/** A class that knows about the node hierarchy in DTree. */
class BatchGenealogist extends HybridGenealogist {
  /** The logger for this class. */
  private static final Logger LOGGER =
      Logger.getLogger(BatchGenealogist.class.getName());

  public BatchGenealogist(Client client, String startNodes,
      String excludedNodes, int minCacheSize, int maxCacheSize) {
    // TODO: We may want to disable the cache for this implementation.
    super(client, startNodes, excludedNodes, minCacheSize, maxCacheSize);
  }

  /**
   * Finds the included nodes from among the matching candidates. This
   * is the core algorithm behind {@link getMatchingDescendants}. This
   * implementation uses the batch query for each level of lookup.
   *
   * @param matching the matching nodes to check for inclusion
   * @param descendants a buffer to write a comma-separated list of
   * included node IDs to
   */
  @Override
  protected void matchDescendants(ClientValue matching,
      StringBuilder descendants) throws RepositoryException {
    // First, check the matching nodes themselves.
    StringBuilder undecideds = new StringBuilder();
    for (int i = 0; i < matching.size(); i++) {
      final int matchingId = matching.toInteger(i, "DataID");
      if (!matchParent(matchingId, matchingId,
              Collections.<Integer>emptyList(), descendants))
        undecideds.append(matchingId).append(',');
    }

    if (undecideds.length() > 0) {
      undecideds.deleteCharAt(undecideds.length() - 1);

      // Next, get all of the parents of the remaining nodes at once,
      // and then enter the loop that continues to look up the further
      // ancestors one level at a time.
      // TODO: If we combine Tree.put and Tree.merge, we could have a
      // straight do-while loop here.
      // TODO: We could roll this first call to getParents into the
      // getMatching call just before getMatchingDescendants in
      // LivelinkTraversalManager by copying the select list there.
      Tree tree = new Tree();
      Parents parents = getParents(undecideds.toString());
      for (int i = 0; i < parents.size(); i++) {
        int matchingId = parents.getDataID(i);
        int parentId = parents.getParentID(i);
        tree.put(matchingId, parentId);
      }

      // TODO: Check for an interrupted traversal in this loop?
      while (true) {
        if (LOGGER.isLoggable(Level.FINEST))
          LOGGER.finest("DESCENDANTS: Checking parents: " + tree.getParents());

        // Check each parent entry in the current tree, removing nodes
        // that are found from the tree.
        Iterator<Map.Entry<Integer, Node>> it = tree.entrySet().iterator();
        while (it.hasNext()) {
          Map.Entry<Integer, Node> entry = it.next();
          Node n = entry.getValue();
          if (matchParent(n.getMatches(), entry.getKey(), n.getPossibles(),
                  descendants)) {
            it.remove();
          }
        }

        if (tree.isEmpty())
          break;

        // Get the next level of parents. The merge moves the nodes up
        // one level in the hierarchy. We merge into a new tree so that
        // orphan nodes (with missing parents) are left behind rather
        // than staying in the tree forever.
        Tree newTree = new Tree();
        parents = getParents(tree.getParents());
        for (int i = 0; i < parents.size(); i++) {
          int objectId = parents.getDataID(i);
          int parentId = parents.getParentID(i);
          tree.merge(objectId, parentId, newTree);
        }
        logOrphans(tree);
        tree = newTree;
      }
    }
  }

  /**
   * Represents knowledge about the descendants of a single node in
   * the tree.
   */
  private static final class Node {
    /** The ancestors between the matches and this node. */
    private final ArrayList<Integer> cachePossibles = new ArrayList<Integer>();

    /** The matching descendants of this node. */
    private final StringBuilder matches = new StringBuilder();

    void addPossible(int parentId) {
      cachePossibles.add(parentId);
    }

    void addPossibles(List<Integer> possibles) {
      cachePossibles.addAll(possibles);
    }

    /** Gets the ancestors for insertion into one of the caches. */
    public List<Integer> getPossibles() {
      return cachePossibles;
    }

    void addMatch(int objectId) {
      matches.append(objectId).append(',');
    }

    void addMatches(CharSequence matches) {
      // The argument is indirectly from getMatches rather than
      // matches, so we need to add a comma here.
      this.matches.append(matches).append(',');
    }

    /** Gets the matches for appending to the final descendants string. */
    public CharSequence getMatches() {
      // Remove the trailing comma.
      return matches.substring(0, matches.length() - 1);
    }
  }

  /**
   * The tree represents all of the parent nodes whose status is
   * unknown. At any given time, the tree contains the highest known
   * nodes in the tree. Each node contains information about its
   * descendants.
   */
  private static final class Tree {
    /** Maps node IDs to the info about that node. */
    private final Map<Integer, Node> map = new HashMap<Integer, Node>();

    public Set<Map.Entry<Integer, Node>> entrySet() {
      return map.entrySet();
    }

    public boolean isEmpty() {
      return map.isEmpty();
    }

    /** Gets a comma-separated string of the current node IDs. */
    public String getParents() {
      if (isEmpty())
        return "";

      StringBuilder buffer = new StringBuilder();
      for (Integer parent : map.keySet()) {
        buffer.append(parent).append(',');
      }
      buffer.deleteCharAt(buffer.length() - 1);
      return buffer.toString();
    }

    /**
     * Inserts a new matching node into the tree. This method is used
     * to build the initial tree from the matching nodes.
     *
     * @param matchinId the object ID of the matching leaf node
     * @param parentId the ID of the parent node to merge into
     */
    public void put(int matchingId, int parentId) {
      Node n = map.get(parentId);
      if (n == null) {
        n = new Node();
        n.addPossible(parentId);
        map.put(parentId, n);
      }
      n.addMatch(matchingId);
    }

    /**
     * Merges a node into its parent's node. The old child node is
     * removed. This method is used to move up the tree from the
     * currently known nodes to their parents, one child node at a
     * time.
     *
     * @param objectId the object ID of the existing node
     * @param parentId the ID of the parent node to merge into
     */
    public void merge(int objectId, int parentId, Tree target) {
      Node old = map.get(objectId);
      assert old != null;
      Node n = target.map.get(parentId);
      if (n == null) {
        n = old;
        n.addPossible(parentId);
        target.map.put(parentId, n);
      } else {
        assert n.getPossibles().contains(parentId);
        n.addPossibles(old.getPossibles());
        n.addMatches(old.getMatches());
      }

      // We do not functionally have to remove nodes from this tree,
      // but it helps detect unmerged (that is, orphan) nodes.
      map.remove(objectId);
    }
  }

  /**
   * Logs a warning for orphans discovered in the hierarchy.
   *
   * @param tree the tree containing orphans
   */
  private void logOrphans(Tree tree) {
    if (LOGGER.isLoggable(LOG_ORPHANS_LEVEL)) {
      for (Map.Entry<Integer, Node> entry : tree.entrySet()) {
        logOrphans(entry.getValue().getMatches(), entry.getKey());
      }
    }
  }
}
