# Introduction #

The connector implementation uses the `LAPI_DOCUMENTS.ListNodes` method,
which is an undocumented LAPI method. This method essentially takes a
SQL query and returns a recarray. This method is used for both traversal
and authorization.

The SQL queries used in the connector were originally designed to work with
SQL Server 7.0 or later, and with Oracle 8i Release 2 (8.1.6). This
enabled the code to work with Livelink 9.0 or later. The exception to
this was that Sybase, which is supported by Livelink 9.2.0.1 and earlier,
was not supported here.

In subsequent releases of the connector, dependencies on Livelink 9.5 were
introduced, specifically the use of the DTreeAncestors database table. Also, some
queries may depend on SQL Server 2000 or Oracle 9i. Earlier versions of Livelink
are not tested.

# Database Versions #

For future updates, here are the database requirements for
various versions of Livelink.

| **Livelink** | **SQL Server** | **Oracle** | **Sybase** |
|:-------------|:---------------|:-----------|:-----------|
| **9.7.1.0** | 2005 SP1 | 10g Release 2 (10.2.0.1) |  |
|  | 2000 SP4<sup>1</sup> | 9i Release 2<sup>1</sup> |  |
| **9.7.0.0** | 2005 SP1 | 10g Release 2 (10.2.0.1) |  |
|  | 2000 SP4 | 9i Release 2 |  |
| **9.6.0.0** | 2000 SP4 | 10g Release 2 |  |
|  |  | 9i Release 2 |  |
| **9.5.0.0**, **9.5.0.1** | 2000 SP3 | 10g Release 1 |  |
|  |  | 9i Release 2 |  |
| **9.2.0.1** | 2000 SP3 | 9i Release 2 | 12.5 |
|  | 7.0 SP3 | 8i Release 3 (8.1.7) |  |
| **9.2.0.0** | 2000 SP3 | 9i Release 2 | 12.5 |
|  | 7.0 SP2 | 8i Release 3 (8.1.7) |  |
| **9.1.0.3**, **9.1.0.4** | 2000 | 9i Release 2 | 12.5 |
|  | 7.0 SP2 | 9i Release 1 | 12.0 |
|  |   | 8i Release 3 (8.1.7) |   |
| **9.1.0.2** | 2000 | 9i Release 1  | 12.0 |
|  | 7.0 SP2 | 8i Release 3 (8.1.7) |  |
| **9.1.0.0**, **9.1.0.1** | 2000 |   | 12.0 |
|  | 7.0 SP2 | 8i Release 3 (8.1.7) |  |
| **9.0.0.1**, **9.0.0.2** | 2000 | 8i Release 3 (8.1.7) | 12.0 |
|  | 7.0 SP2 | 8i Release 2 (8.1.6) |  |
| **9.0.0.0** | 7.0 SP2 | 8i Release 2 (8.1.6) |  |

  1. SQL Server 2000 SP4 and Oracle 9i Release 2 appear to be supported, but customers are warned of less than optimal performance. Open Text recommends upgrading.