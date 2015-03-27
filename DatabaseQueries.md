

# Note about Syntax #

Due to the constraints of the Livelink API, all queries have a table correlation named "`a`" appended to the table expression, and all queries must have a where clause. Values specified at runtime are shown using the invented synax `$parameter`.

Optional pieces of a query are shown in square brackets: `[ and DataID in ($excludedLocationNodes)  ]`. Exclusive choices are separated by a vertical bar: `[ 1=1 | ModifyDate > $modifyDate ]`.

In many cases, different query syntax must be used depending on whether Livelink is backed by Oracle or SQL Server. Both queries are shown here.

All queries are submitted as literal strings without SQL parameters. The Livelink API may prepare a statement before execution, but the prepared statement is not cached for future execution.

## Query Parameters ##

| `$includedLocationNodes` | A comma-separated list of object IDs, taken from Items to Index |
|:-------------------------|:----------------------------------------------------------------|
| `$excludedLocationNodes` | A comma-separated list of object IDs. An advanced configuration property |
| `$excludedNodeTypes` | A comma-separated list of subtypes. An advanced configuration property |
| `$excludedVolumeTypes` | A comma-separated list of subtypes. An advanced configuration property |
| `$includedSelectExpressions` | A comma-separated list of SQL SELECT expressions, derived from an advanced configuration property of the same name |
| `$sqlWhereCondition` | An extra SQL condition applied to the traversal candidates. An advanced configuration property |
| `$ancestorNodes` | A comma-separated list of object or volume IDs, derived from `$includedLocationNodes` |
| `$enterpriseID` | The object ID of the Enterprise workspace, often but not always 2000 |
| `$enterpriseVolumeID` | The volume ID of the Enterprise workspace, often but not always -2000 |
| `$volumeSubType` | The Undelete workspace (402) or workflow volume (161) subtypes |
| `$undeleteVolumeID` | The volume ID of the Undelete workspace  |
| `$workflowVolumeID` |  The volume ID of the workflow volume |
| `$modifyDate` | The current ModifyDate in the traversal order |
| `$dataID` | The current DataID (object ID) in the traversal order |
| `$batchSize` | The requested batch size, based on the traversal rate, with a default of 500, up to a configurable maximum of 500 with a hard ceiling of 1000 |
| `$candidates` | A comma-separated list of up to `$batchSize` object IDs returned by the first traversal query |
| `$descendants` | A comma-separated list of object IDs, including those candidates that are located within the Items to Index |
| `$deleteDate` | The current ModifyDate in the traversal order for deleted documents |
| `$eventID` | The current EventID in the traversal order for deleted documents |
| `$nodeID` | The current node when looking to see whether a candidate is in the Items to Index |
| `$nodeIDs` | The current list of nodes when looking to see whether the candidates are in the Items to Index |


# Configuration Queries #

These queries are executed when validating the configuration form in the admin console.

  1. Determine the database type. If the servtype property is not configured, then the following two queries are run to determine whether Livelink is backed by Oracle or SQL Server. The first query is a baseline check for database connectivity.
```
select 42 from KDual a where 1=1
```
  1. The second query is Oracle-specific, and an error indicates the use of SQL Server.
```
select 42 from dual a where 1=1
```
  1. If Items to Index are specified or hidden items are not to be indexed, ensure that the DTreeAncestors table is not empty.
    * **Oracle**
```
select DataID from DTreeAncestors a where rownum = 1
```
    * **SQL Server**
```
select TOP 1 DataID from DTreeAncestors a where 1=1
```
  1. If Items to Index are specified, ensure that at least one of the items to index appears in the DTreeAncestors table.
```
select min(ModifyDate) as minModifyDate 
from DTree a
where DataID in (select DataID from DTreeAncestors where AncestorID in ($ancestorNodes)) 
    or DataID in ($includedLocationNodes)
```
  1. If Items to Index are specified or hidden items are not to be indexed, ensure that the DTreeAncestors table contains everything in the Enterprise workspace.
    * **Oracle**
```
select DataID
from DTree a 
where not exists (select DataID from DTreeAncestors where DataID = a.DataID and AncestorID = $enterpriseID) 
    and OwnerID = $enterpriseVolumeID and DataID <> $enterpriseID and rownum = 1
```
    * **SQL Server**
```
select TOP 1 DataID
from DTree a 
where not exists (select DataID from DTreeAncestors where DataID = a.DataID and AncestorID = $enterpriseID) 
    and OwnerID = $enterpriseVolumeID and DataID <> $enterpriseID
```
  1. If a SQL WHERE condition is specified, make sure that it is valid syntactically and returns at least one row from `WebNodes`.
    * **Oracle**
```
select DataID, PermID from WebNodes a where ($sqlWhereCondition) and rownum = 1
```
    * **SQL Server**
```
select TOP 1 DataID, PermID from WebNodes a where $sqlWhereCondition
```


# Authorization Queries #

These queries are executed when authorizing users for search results.

  1. Determines the Undelete and workflow volume IDs. The optional condition checks the excluded object IDs for the found volume ID.
```
select DataID, PermID from DTree a where SubType = $volumeSubType
    [ and DataID in ($excludedLocationNodes) ]
```
  1. Checks whether the search user has permission for the items with the given object IDs. The optional conditions correspond to exclusions in the traversal. The first optional condition excludes the Undelete workspace. The second optional condition excludes the workflow volume. The third optional condition excludes hidden items, and it has nested optional conditions if Items to Index are specified.
```
select DataID, PermID from WebNodes a where DataID in ($objectIDs)
    [ and ParentID <> $undeleteVolumeID ]
    [ and OwnerID <> $workflowVolumeID ]
    [ and Catalog <> 2
      and DataID not in (select Anc.DataID
                         from DTreeAncestors Anc join DTree T on Anc.AncestorID = T.DataID
                         where Anc.DataID = a.DataID
                             and T.Catalog = 2
                             [ and Anc.AncestorID not in ($includedLocationNodes)
                               and Anc.AncestorID not in (select AncestorID
                                                          from DTreeAncestors
                                                          where DataID in ($ancestorNodes))
                             ]
                        )
    ]
```


# Traversal Queries #

These queries are executed when indexing content. The traversal runs in batches, in modification date order.

  1. Find the latest event in the audit trail so that we do not process older delete events.
    * **Oracle**
```
select TO_CHAR(AuditDate, 'YYYY-MM-DD HH24:MI:SS') as AuditDate, EventID
from DAuditNew a
where EventID in (select max(EventID) from DAuditNew)
```
    * **SQL Server**
```
select CONVERT(VARCHAR(23), AuditDate, 121) as AuditDate, EventID
from DAuditNew a
where EventID in (select max(EventID) from DAuditNew)
```
  1. Gets candidates for the next batch. The traversal queries are split into two, one gets candidate batches of the appropriate size, and the other applies restrictions. This avoids a large multi-join across full tables. The optional conditions are always used except when starting a new traversal.
    * **Oracle**
```
select ModifyDate, DataID
from (select * from DTree order by ModifyDate, DataID) a
where
    [ (ModifyDate > TO_DATE($modifyDate, 'YYYY-MM-DD HH24:MI:SS') 
       or (ModifyDate = TO_DATE($modifyDate, 'YYYY-MM-DD HH24:MI:SS') and DataID > $dataID))
      and
    ]
    rownum <= $batchSize
```
    * **SQL Server**
```
select top $batchSize ModifyDate, DataID
from DTree a
where
    [ 1=1
    | (ModifyDate > $modifyDate 
       or (ModifyDate = $modifyDate and DataID > $dataID))
    ]
order by ModifyDate, DataID
```
  1. Second query of the traversal, applying restrictions to the candidates from the first query, including the permissions of the traversal user and the configured restrictions such as Items to Index and the advanced configuration of included and excluded types and items. The first optional condition is used if Items to Index are specified. The other optional conditions are used if the advanced configuration properties they refer to are non-empty.
```
select DataID, ModifyDate, MimeType, Name, DComment, CreateDate, OwnerName,
    SubType, OwnerID, UserID, case when DataSize < 0 then 0 else DataSize end DataSize,
    PermID, $includedSelectExpressions
from WebNodes a
where DataID in ($candidates)
    [ and (DataID in ($ancestorNodes)
           or DataID in (select DataID
                         from DTreeAncestors
                         where DataID in ($candidates) and AncestorID in ($ancestorNodes)))
    | [ and -OwnerID not in (select DataID from DTree where SubType in ($excludedVolumeTypes)) ]
    ]
    [ and SubType not in ($excludedNodeTypes) ]
    [ and DataID not in (select DataID 
                         from DTreeAncestors
                         where DataID in ($candidates) and AncestorID in ($excludedLocationNodes)) ]
    [ and $sqlWhereCondition ]
order by ModifyDate, DataID
```
  1. Gets items to be deleted, based on the audit trail. The optional conditions are used if the advanced configuration properties they refer to are non-empty.
    * **Oracle**
```
select TO_CHAR(AuditDate, 'YYYY-MM-DD HH24:MI:SS') as AuditDate, EventID, DataID
from (select * from DAuditNew order by AuditDate, EventID) a
where AuditID = 2
    and (AuditDate > TO_DATE($deleteDate, 'YYYY-MM-DD HH24:MI:SS') 
         or (AuditDate = TO_DATE($deleteDate, 'YYYY-MM-DD HH24:MI:SS') and EventID > $eventID))
    [ and SubType not in ($excludedNodeTypes) ]
    and rownum <= $batchSize
```
    * **SQL Server**
```
select top $batchSize CONVERT(VARCHAR(23), AuditDate, 121) as AuditDate, EventID, DataID
from DAuditNew a
where AuditID = 2
    and (AuditDate > $deleteDate 
         or (AuditDate = $deleteDate and EventID > $eventID))
    [ and SubType not in ($excludedNodeTypes) ]
order by ModifyDate, DataID
```


# Genealogist Queries #

These queries are used during traversals, altering the second query of the traversal, and adding subsequent queries to determine the items to index.

  1. Using a genealogist alters the second query of the traversal above, simplifying the select list. The optional elements here are paired: `$sqlWhereCondition` requires `WebNodes`, otherwise `DTree` is used.
```
select DataID from [ WebNodes | DTree ] a
    ... 
    [ and ($sqlWhereCondition) ]
order by ModifyDate, DataID
```
  1. The select list from the original second query of the traversal is then applied later to the items that are found to be descendants of the Items to Index.
```
select DataID, ModifyDate, MimeType, Name, DComment, CreateDate, OwnerName,
    SubType, OwnerID, UserID, case when DataSize < 0 then 0 else DataSize end DataSize,
    PermID, $includedSelectExpressions
from WebNodes a
where DataID in ($descendants)
```
  1. Finds the parent of a given item. Used by the base Genealogist implementation.
```
select ParentID from DTree a where DataID in ($nodeID, -$nodeID)
```
  1. Finds the parents of a given list of items. Used by the HybridGenealogist and the default BatchGenealogist implementations.
```
select DataID, ParentID, 
    (select ParentID from DTree b where -a.DataID = b.DataID and b.ParentID <> -1) as StepParentID
from DTree a where DataID in ($nodeIDs)
```


# References #

  * The AdvancedConfiguration wiki page describes the advanced configuration properties in more detail.