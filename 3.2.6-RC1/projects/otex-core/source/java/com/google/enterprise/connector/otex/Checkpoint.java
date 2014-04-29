// Copyright 2008 Google Inc. All Rights Reserved.
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

import com.google.enterprise.connector.otex.client.ClientValue;
import com.google.enterprise.connector.spi.RepositoryException;

import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Create and parse the checkpoint strings passed back and forth between
 * the connector and the Google appliance traverser.
 */
class Checkpoint {
    /** The logger for this class. */
    private static final Logger LOGGER =
        Logger.getLogger(Checkpoint.class.getName());

    /** The formatter used for the Date portions of checkpoints. */
    private static final LivelinkDateFormat dateFmt =
        LivelinkDateFormat.getInstance();

    /** DataID of the last item inserted. */
    public int  insertDataId;

    /** ModifyDate of the last item inserted. */
    public Date insertDate;

    /** EventID of the last item deleted. */
    public long deleteEventId;

    /** EventDate of the last item deleted. */
    public Date deleteDate;

    /** DataID of the last inserted item candidate. */
    private int  advInsertDataId;

    /** ModifyDate of the last inserted item candidate. */
    private Date advInsertDate;

    /**
     * Backup versions of the insert and delete checkpoints,
     * so that we may restore a checkpoint when attempting to
     * retry failed items.
     */
    private int oldInsertDataId;
    private Date oldInsertDate;
    private long oldDeleteEventId;
    private Date oldDeleteDate;

    /** Generic Constructor */
    Checkpoint() {
    }

    /**
     * Constructor given a checkpoint string.  Checkpoints are passed
     * between the Connector Manager and the Connector in the form of
     * a string.  This parses the checkpoint string.
     * @param checkpoint  A checkpoint String representation.
     * @throws RepositoryException
     */
    Checkpoint(String checkpoint) throws RepositoryException {
        if ((checkpoint != null) && (checkpoint.trim().length() > 0)) {
            try {
                // Push entries beyond the first four into a fifth
                // array element, which we ignore. This is to avoid
                // failing completely on newer checkpoint strings.
                String [] points = checkpoint.trim().split(",", 5);
                if (points.length < 2)
                    throw new Exception();

                // Parse the Date component of the insert items.
                if (points[0].length() > 0) {
                    insertDate = dateFmt.parse(points[0]);
                    oldInsertDate = insertDate;
                }

                // Parse the DataId component of the insert items.
                if (points[1].length() > 0) {
                    insertDataId = Integer.parseInt(points[1]);
                    oldInsertDataId = insertDataId;
                }

                // The Delete checkpoint is optional.
                if ((points.length > 2) && (points[2].length() > 0)) {
                    // Parse the Date component of the delete items.
                    deleteDate = dateFmt.parse(points[2]);
                    oldDeleteDate = deleteDate;

                    // Parse the EventId component of the delete items.
                    if (points[3].length() > 0) {
                        deleteEventId = Long.parseLong(points[3]);
                        oldDeleteEventId = deleteEventId;
                    }
                }
            } catch (Exception e) {
                throw new LivelinkException(
                    "Invalid checkpoint: " + checkpoint, e, LOGGER);
            }
        }
    }

  /**
   * Logs the given checkpoint portion.
   *
   * @param portion the name of the checkpoint portion for the log message
   * @param date the ModifyDate of the item
   * @param dataId the DataID of the item
   */
  private void log(String portion, Date date, long dataId) {
    if (LOGGER.isLoggable(Level.FINER)) {
      // Use String.valueOf to avoid locale-specific formatting of the DataID.
      LOGGER.log(Level.FINER, "{0}: {1},{2}", new Object[] {
          portion, dateFmt.toSqlString(date), String.valueOf(dataId) });
    }
  }

    /**
     * Set the Inserted Items portion of the checkpoint.
     *
     * @param date the ModifyDate of the last item inserted.
     * @param dataId the DataID of the last item inserted.
     */
    public void setInsertCheckpoint(Date date, int dataId) {
        // Remember previous insert checkpoint as a restore point.
        oldInsertDate = insertDate;
        oldInsertDataId = insertDataId;

        // Set the new insert checkpoint.
        insertDate = date;
        insertDataId = dataId;
        log("INSERT CHECKPOINT", date, dataId);
    }

    /**
     * Sets the Deleted Items portion of the checkpoint. Livelink
     * returns the EventID from Oracle as an integer, and from SQL
     * Server as a double. LAPI doesn't support a long integer, so
     * casting the SQL Server column to BIGINT doesn't work. Casting
     * to an INT might lose precision. Instead, this method accepts a
     * <code>ClientValue</code>, which can be either an
     * <code>INTEGER</code> or a <code>DOUBLE</code>.
     *
     * @param date the AuditDate of the last item deleted.
     * @param eventId the EventID of the last item deleted.
     * @throws RepositoryException if an unexpected runtime error occurs
     */
    public void setDeleteCheckpoint(Date date, ClientValue eventId)
            throws RepositoryException {
        // Remember the current delete checkpoint as a restore point.
        oldDeleteDate = deleteDate;
        oldDeleteEventId = deleteEventId;

        // Set the new delete checkpoint.
        deleteDate = date;

        if (eventId == null) {
            deleteEventId = 0;
            return;
        }

        switch (eventId.type()) {
        case ClientValue.INTEGER:
            // Oracle, Livelink 9.7 and earlier.
            deleteEventId = eventId.toInteger();
            break;

        case ClientValue.DOUBLE:
            // SQL Server, Livelink 9.7 and earlier.
            deleteEventId = (long) eventId.toDouble();
            break;

        case ClientValue.LONG:
            // Oracle and SQL Server, Livelink 9.7.1.
            deleteEventId = eventId.toLong();
            break;

        default:
            // XXX: EventID is a NUMBER(10) in Oracle, and a NUMERIC(10,0)
            // in SQL Server, so 9,999,999,999 is the largest number that
            // can be represented. By choosing this, we are sure to make
            // progress, although it is possible that we will skip some
            // deletes. That is better than an infinite loop.
            LOGGER.info("UNKNOWN EVENT ID TYPE: " + eventId.type() +
                "; value = " + eventId.toString2());
            deleteEventId = 9999999999L;
            break;
        }
        log("DELETE CHECKPOINT", date, deleteEventId);
    }

    /**
     * Set the checkpoint for the last Inserted Items Candidate.
     *
     * @param date the ModifyDate of the last insert candidate.
     * @param dataId the DataID of the last insert candidate.
     */
    public void setAdvanceCheckpoint(Date date, int dataId) {
        // Set the final insert candidate checkpoint.
        advInsertDate = date;
        advInsertDataId = dataId;
        log("ADVANCE CHECKPOINT", date, dataId);
    }

    /**
     * Advance the checkpoint passed the last candidates
     * considered (not just passed the last documents returned).
     * This can help avoid reconsidering candidate documents
     * that have already been rejected.  Especially useful
     * for sparse retrieval situations.
     */
    public void advanceToEnd() {
        if (advInsertDate != null) {
            insertDate = advInsertDate;
            insertDataId = advInsertDataId;
        }
    }

    /**
     * Checks to see if this checkpoint is the older style.
     * @param checkpoint a checkpoint string
     * @returns true if checkpoint string is old style
     */
    public static boolean isOldStyle(String checkpoint) {
        return (checkpoint == null) ||
               (checkpoint.indexOf(',') == checkpoint.lastIndexOf(','));
    }

    /**
     * Returns true if some component of the checkpoint has changed.
     * (In other words, restore() would result in a different checkpoint.)
     */
    public boolean hasChanged() {
        return (insertDate != oldInsertDate ||
                insertDataId != oldInsertDataId ||
                deleteDate != oldDeleteDate ||
                deleteEventId != oldDeleteEventId);
    }

    /**
     * Restores the checkpoint to its previous state.  This is used when
     * we wish to retry an item that seems to have failed.
     */
    public void restore() {
        insertDate = oldInsertDate;
        insertDataId = oldInsertDataId;
        deleteDate = oldDeleteDate;
        deleteEventId = oldDeleteEventId;
    }

    /**
     * @returns the Checkpoint as a String.
     */
    public String toString() {
        // A null checkpoint is OK.
        if ((insertDate == null) && (deleteDate == null))
            return null;

        StringBuffer buffer = new StringBuffer();

        // The Inserted items checkpoint.
        if (insertDate != null)
            buffer.append(dateFmt.toSqlString(insertDate));
        buffer.append(',');
        buffer.append(insertDataId);

        // The Deleted items checkpoint is optional.
        if (deleteDate != null) {
            buffer.append(',');
            buffer.append(dateFmt.toSqlMillisString(deleteDate));
            buffer.append(',');
            buffer.append(deleteEventId);
        }

        return  buffer.toString();
    }
}
