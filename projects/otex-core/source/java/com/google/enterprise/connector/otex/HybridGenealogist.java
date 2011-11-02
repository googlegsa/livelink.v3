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
import java.util.logging.Level;
import java.util.logging.Logger;

/** A class that knows about the node hierarchy in DTree. */
class HybridGenealogist extends Genealogist {
  /** The logger for this class. */
  private static final Logger LOGGER =
      Logger.getLogger(HybridGenealogist.class.getName());

  public HybridGenealogist(Client client, String startNodes,
      String excludedNodes, int minCacheSize, int maxCacheSize) {
    super(client, startNodes, excludedNodes, minCacheSize, maxCacheSize);
  }

  /**
   * Finds the included nodes from among the matching candidates. This
   * is the core algorithm behind {@link getMatchingDescendants}. This
   * implementation uses an initial query to get the parents of all
   * the matching nodes. The subsequent parents are looked up
   * individually.
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
      // and then enter the loop that looks up the further ancestors
      // one at a time.
      // TODO: If any of the undecideds are direct orphans (their
      // parents do not exist), then this loop will correctly exclude
      // the orphans, but it won't log a message that it did so. If
      // orphans are found in the inner loop, they will be logged.
      Parents parents = getParents(undecideds.toString());
      for (int i = 0; i < parents.size(); i++) {
        ArrayList<Integer> cachePossibles = new ArrayList<Integer>();
        final int matchingId = parents.getDataID(i);
        Integer parentId = parents.getParentID(i);
        cachePossibles.add(parentId);

        // TODO: Check for an interrupted traversal in this loop?
        while (!matchParent(matchingId, parentId, cachePossibles,
            descendants)) {
          parentId = getParent(matchingId, parentId);
          if (parentId == null) {
            break;
          }
          cachePossibles.add(parentId);
        }
      }
    }
  }

  /**
   * Gets the ParentIDs of the given nodes.
   *
   * @param objectIds a comma-separated list of object IDs
   * @return a {@code Parents} object containing the IDs of the
   * objects and their parents.
   */
  protected Parents getParents(String objectIds)
      throws RepositoryException {
    // This uses a correlated subquery (using the implicit "a" range
    // variable added by LAPI) to handle the negated pairs created by
    // volumes. The more obvious left outer join fails precisely
    // because of the added "a" range variable on the join expression.
    // The "b" range variable is only for clarity. N.B.: The minus
    // sign must be on the correlated column (a.DataID) and not
    // b.DataID, or Oracle will avoid using an index.
    queryCount++;
    ClientValue parents = client.ListNodes("DataID in (" + objectIds + ")",
        "DTree", new String[] { "DataID", "ParentID",
            "(select ParentID from DTree b where -a.DataID = b.DataID "
            + "and b.ParentID <> -1) as StepParentID" });
    return new Parents(parents);
  }

  /**
   * A simple wrapper on ClientValue to handle stepparents. The
   * ClientValue must be a three-column recarray of integers: DataID,
   * ParentID, and StepParentID. In most cases the StepParentID will
   * be null, but in the case of volumes with a negated object ID
   * node, it will be the negated node's parent ID.
   */
  /*
   * We could just have a getParentID method, but this wrapper class
   * means that we can't forget to call that method and thereby
   * mishandle stepparents.
   */
  protected static final class Parents {
    private final ClientValue parents;

    public Parents(ClientValue parents) {
      this.parents = parents;
    }

    public int size() {
      return parents.size();
    }

    public int getDataID(int i) throws RepositoryException {
        return parents.toInteger(i, "DataID");
    }

    /**
     * Handle stepparents for volume nodes, where ParentID(objectID)
     * is -1 but ParentID(-objectID) is the stepparent.
     *
     * @see HybridGenealogist#getParents
     */
    public int getParentID(int i) throws RepositoryException {
      int parentId = parents.toInteger(i, "ParentID");
      if (parentId == -1 && parents.isDefined(i, "StepParentID")) {
        parentId = parents.toInteger(i, "StepParentID");
        if (LOGGER.isLoggable(Level.FINEST)) {
          LOGGER.finest("DESCENDANTS: Substituting " + parentId
              + " as stepparent for " + getDataID(i));
        }
      }
      return parentId;
    }
  }
}
