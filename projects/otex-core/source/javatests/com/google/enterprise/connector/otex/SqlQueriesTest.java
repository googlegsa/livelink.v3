// Copyright 2015 Google Inc. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.enterprise.connector.otex;

import static com.google.enterprise.connector.otex.SqlQueries.choice;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableList;

import org.junit.Test;

public class SqlQueriesTest {
  private static final SqlQueries ORACLE = new SqlQueries(false);

  private static final SqlQueries SQL_SERVER = new SqlQueries(true);

  @Test
  public void getSelect() {
    assertArrayEquals(new String[] { "ModifyDate", "DataID" },
        ORACLE.getSelect("LivelinkTraversalManager.getCandidates"));
  }

  @Test
  public void getFrom_withoutParameters() {
    assertEquals("DTreeAncestors",
        ORACLE.getFrom("", "LivelinkConnector.validateDTreeAncestors"));
  }

  @Test
  public void getFrom_withoutJoin_oracle() {
    assertEquals("(select * from DTree order by ModifyDate, DataID)",
        ORACLE.getFrom("", "LivelinkTraversalManager.getCandidates",
            choice(false)));
  }

  @Test
  public void getFrom_withJoin_oracle() {
    assertEquals("(select * from DTree join DTreeAncestors using (DataID) " +
        "order by ModifyDate, DataID)",
        ORACLE.getFrom("", "LivelinkTraversalManager.getCandidates",
            choice(true)));
  }

  @Test
  public void getFrom_withoutJoin_sqlServer() {
    assertEquals("DTree",
        SQL_SERVER.getFrom("", "LivelinkTraversalManager.getCandidates",
            choice(false)));
  }

  /** The join is in the WHERE clause for SQL Server. */
  @Test
  public void getFrom_withJoin_sqlServer() {
    assertEquals("DTree",
        SQL_SERVER.getFrom("", "LivelinkTraversalManager.getCandidates",
            choice(true)));
  }

  @Test
  public void getWhere_startTraversal_withoutJoin() {
    assertEquals("rownum <= 1000",
        ORACLE.getWhere("", "LivelinkTraversalManager.getCandidates",
            choice(false), null, null, choice(false), "2000-01-01", 42, 1000));
  }

  @Test
  public void getWhere_startTraversal_withJoin() {
    assertEquals("(AncestorID in (6,-6) or DataID in (6)) and rownum <= 1000",
        ORACLE.getWhere("", "LivelinkTraversalManager.getCandidates",
            choice(true), "6,-6", "6",
            choice(false), "2000-01-01", 42, 1000));
  }

  @Test
  public void getWhere_resumeTraversal_withoutJoin() {
    assertEquals("(ModifyDate > TIMESTAMP'2000-01-01' or "
        + "(ModifyDate = TIMESTAMP'2000-01-01' and DataID > 42)) "
        + "and rownum <= 1000",
        ORACLE.getWhere("", "LivelinkTraversalManager.getCandidates",
            choice(false), null, null, choice(true), "2000-01-01", 42, 1000));
  }

  @Test
  public void getWhere_resumeTraversal_sqlServer() {
    String withJoin =
        SQL_SERVER.getWhere("", "LivelinkTraversalManager.getCandidates",
            choice(true), "6,-6", "6",
            choice(true), "2000-01-01", 42, 1000);
    String withoutJoin =
        SQL_SERVER.getWhere("", "LivelinkTraversalManager.getCandidates",
            choice(false), null, null, choice(true), "2000-01-01", 42, 1000);
    String expectedJoinSnippet =
        " join DTreeAncestors Anc on T.DataID = Anc.DataID "
        + "where (AncestorID in (6,-6) or T.DataID in (6)) and ";
    int index = withJoin.indexOf(expectedJoinSnippet);
    assertTrue(withJoin, index != -1);

    // Other than the join snippet, the queries should be essentially equal.
    assertEquals(withoutJoin, withJoin.substring(0, index) + " where " +
        withJoin.substring(index + expectedJoinSnippet.length()));
  }
}
