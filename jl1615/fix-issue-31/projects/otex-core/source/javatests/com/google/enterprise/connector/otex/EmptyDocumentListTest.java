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

import com.google.enterprise.connector.spi.*;
import junit.framework.TestCase;

/**
 * Tests the empty document list returned to indicate that we are
 * making progress, but don't have any documents to return.
 */
public class EmptyDocumentListTest extends TestCase {
    /** Tests multiple instances to make sure the shared static data is OK. */
    public void testMultipleInstances() throws RepositoryException {
        for (int i = 0; i < 3; i++) {
            DocumentList docs = new EmptyDocumentList(null);
            Document doc = docs.nextDocument();
            assertNotNull(doc);
            Property docidProperty =
                doc.findProperty(SpiConstants.PROPNAME_DOCID);
            assertNotNull(docidProperty);
            Value docidValue = docidProperty.nextValue();
            assertNotNull(docidValue);
            String docid = docidValue.toString();
            assertNotNull(docid);
            assertNull(docs.nextDocument());
        }
    }

    /** Tests the checkpoint method. */
    public void testCheckpoint() throws RepositoryException  {
        // The checkpoint string must be valid. These dates are both
        // in the past, and differ from any default values, and the
        // object IDs are invalid but not a default value.
        String checkpoint = "2008-10-19 00:10:00,1,2008-10-18 00:09:00,1";
        DocumentList docs = new EmptyDocumentList(new Checkpoint(checkpoint));
        assertEquals(checkpoint, docs.checkpoint());
    }

    /** Tests the checkpoint method with a null checkpoint. */
    public void testNullCheckpoint() throws RepositoryException  {
        DocumentList docs = new EmptyDocumentList(new Checkpoint(null));
        assertNull(docs.checkpoint());
    }
}
