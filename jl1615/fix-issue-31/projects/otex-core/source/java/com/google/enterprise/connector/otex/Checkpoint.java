// Copyright (C) 2008 Google Inc.
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

import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.otex.client.ClientValue;

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
     * between the GSA and the Connector in the form of a string.
     * This parses the checkpoint string.
     * @param checkpoint  A checkpoint string representation.
     * @throws RepositoryException
     */
    Checkpoint(String checkpoint) throws RepositoryException {
        if ((checkpoint != null) && (checkpoint.length() > 0)) {
            try {
                String [] points = checkpoint.split(",", 5);
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
                throw new LivelinkException("Invalid checkpoint: " + 
                                            checkpoint,
                                            LOGGER);
            }
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
        switch (eventId.type()) {

        case ClientValue.INTEGER:
            deleteEventId = eventId.toInteger();
            break;

        case ClientValue.DOUBLE:
            deleteEventId = (long) eventId.toDouble();
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
    }


    /**
     * Checks to see if this checkpoint is the older style.
     * @param checkpoint a checkpoint string
     * @returns true if checkpoint string is oldstyle
     */
    public static boolean isOldStyle(String checkpoint) {
        return (checkpoint == null) ||
               (checkpoint.indexOf(',') == checkpoint.lastIndexOf(','));
    }


    /**
     * Update a checkpoint string from old-style to new-style.
     * @param oldCheckpoint an old style checkpoint.
     * @return a new style checkpoint, with reasonable defaults.
     */
    public static String upgrade(String oldCheckpoint) {
        if (!isOldStyle(oldCheckpoint))
            return oldCheckpoint;

        // Set an initial delete checkpoint at the first release of the
        // Livelink connector.  (Earliest indexing date for any item.)
        return oldCheckpoint + ",2007-10-08 00:00:00,0";
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
     * @returns the Checkpoint as a String (desired by the GSA).
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
            buffer.append(dateFmt.toSqlString(deleteDate));
            buffer.append(',');
            buffer.append(deleteEventId);
        }

        return  buffer.toString();
    }
}
