## Introduction ##

If a user deletes a document from the Livelink Repository, the Google
Search Appliance (GSA) will retain the deleted items' information in its search
index until it has been notified of the deletion.  In practice, this
does not impact the user.  Search hits on deleted items are filtered
out of the result set when the Livelink Connector authorizes search
hits for user access.  However, maintaining index and meta-data
information for large numbers of deleted items would slow down
searches, waste CPU and storage resources, and consume document
licenses.

Livelink Connector versions 1.0.4 and later support notifying the
Google Search Appliance when items have been deleted from the
Livelink repository.  However, enabling the Livelink Connector to detect
item deletions requires that a Livelink Administrator turn on a
specific auditing feature in the Livelink Server.

In addition, connector deployments with several Livelink Connector
instances indexing the same Livelink repository requires thoughtful
handling of deleted items.

## Enable Auditing of Delete Events in Livelink ##

Allowing the Livelink Connector to detect
item deletions requires that a Livelink Administrator turn on a
specific auditing feature in the Livelink Server.

To enable Livelink auditing of deleted items:
  1. Navigate to the "Livelink Administration" page on the Livelink server.
  1. Under the "System Administration" section, select "Administer Event Auditing".
  1. Select "Set Auditing Interests".
  1. If "Delete" events are not already enabled, do so.

If auditing is already enabled for many different events (especially
verbose ones like login,logout, and view) you may want to add a
database index for the **AuditID** field of the **DAuditNew** table of the
Livelink backend database.  The instructions for doing so are
different for each database vendor and should only be done by a
qualified Livelink database administrator.

## Latencies When Tracking Delete Events ##

When Livelink Containers (Folders, Projects, etc) are deleted,
the documents they contain are moved to the Undelete Workspace
(where they may be retrieved by a Livelink System Administrator),
then the Container object is deleted immediately.  Items in the
Undelete Workspace are purged periodically (by default after
1 week) or by a trigger from the Livelink administrator.

From the perspective of the GSA, it will see requests to remove
Container items from the index soon after they are deleted,
but may not see requests to remove documents for several days
after they were first "deleted" by the user.

## Tracking Deleted Items over Multiple Connector Instances ##

The Livelink Audit Log does not preserve the original location from
where items  were deleted.  If the Livelink Connector was configured
to only index items from a small set of folders, the Connector cannot
determine if deleted items originated from that set of folders.  Therefore,
all deleted items are fed to the GSA, whether the documents have
been indexed or not.  The GSA efficiently handles delete requests for
items it never indexed.

TODO: talk about setting up a connector dedicated to deletes.