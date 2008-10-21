// Copyright (C) 2007-2008 Google Inc.
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

import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Map;

import com.google.enterprise.connector.spi.Document;
import com.google.enterprise.connector.spi.DocumentList;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.SimpleDocument;
import com.google.enterprise.connector.spi.SpiConstants;
import com.google.enterprise.connector.spi.Value;

/**
 * Models an empty <code>DocumentList</code> for version 1.1 of the
 * Connector Manager. In that version, an empty list would lead to a
 * five minute pause in the traversal, and worse, checkpoint would not
 * be called. This implementation returns a single document that is an
 * invalid delete request that will be ignored by the GSA, so the
 * connector manager will continue the traversal immediately, but
 * nothing will be indexed.
 */
class EmptyDocumentList implements DocumentList {
    /** The logger for this class. */
    private static final Logger LOGGER =
        Logger.getLogger(EmptyDocumentList.class.getName());

    /**
     * The values of the empty document, shared across all document
     * instances.
     */
    private static final Map VALUES;

    private static final String DOCID = "invalid-document-please-ignore";
    
    /**
     * Helper method to add a value to the map needed for
     * <code>SimpleDocument</code>.
     *
     * @param values the map of values to add an entry to
     * @param name the map key
     * @param value the map value, which is wrapped in a one-element list
     */
    private static void addValue(Map values, String name, Value value) {
        values.put(name, Collections.singletonList(value));
    }

    static {
        // An invalid delete request.
        VALUES = new HashMap();
        addValue(VALUES, SpiConstants.PROPNAME_DOCID,
            Value.getStringValue(DOCID));
        addValue(VALUES, SpiConstants.PROPNAME_LASTMODIFIED,
            Value.getDateValue(Calendar.getInstance()));
        addValue(VALUES, SpiConstants.PROPNAME_ACTION,
            Value.getStringValue(SpiConstants.ActionType.DELETE.toString()));
    }
    
    /** The checkpoint for the last seen (and skipped) Livelink item. */
    private final Checkpoint checkpoint;

    /**
     * Fakes a list with one element. True until
     * <code>nextDocument</code> is called.
     */
    private boolean hasNext;

    /** Constructs an empty document list with the given checkpoint. */
    EmptyDocumentList(Checkpoint checkpoint) {
        this.checkpoint = checkpoint;
        this.hasNext = true;
    }
        
    /**
     * {@inheritDoc}
     * <p>
     * This implementation returns an invalid document delete request
     * that the GSA will ignore.
     */
    public Document nextDocument() {
        if (hasNext) {
            hasNext = false;
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("Intentionally sending an invalid document " +
                    "delete request for docid=" + DOCID);
            }
            return new SimpleDocument(VALUES);
        } else
            return null;
    }

    /**
     * {@inheritDoc}
     */
    /* This implementation matches LivelinkDocumentList.checkpoint. */
    public String checkpoint() {
        String cp = checkpoint.toString();

        if (LOGGER.isLoggable(Level.FINE))
            LOGGER.fine("CHECKPOINT: " + cp);

        return cp;
    }
}
