// Copyright 2011 Google Inc.
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

import com.google.enterprise.connector.spi.Document;
import com.google.enterprise.connector.spi.Retriever;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.RepositoryDocumentException;
import com.google.enterprise.connector.spi.SpiConstants;
import com.google.enterprise.connector.spi.Value;
import com.google.enterprise.connector.util.Clock;
import com.google.enterprise.connector.util.SystemClock;
import com.google.enterprise.connector.otex.client.Client;
import com.google.enterprise.connector.otex.client.ClientValue;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A {@link Retriever} that provides access to document content,
 * based upon a docid.
 */
class LivelinkRetriever implements Retriever {
  /** The logger for this class. */
  private static final Logger LOGGER =
      Logger.getLogger(LivelinkRetriever.class.getName());

  /** The connector contains configuration information. */
  private final LivelinkConnector connector;

  /** A concrete strategy for retrieving the content from the server. */
  private final ContentHandler contentHandler;

  /**
   * The traversal client provides access to the server as the
   * traversal user.
   */
  private final Client traversalClient;

  /** Clock for getting the current time of day. */
  private final Clock clock;

  /**
   * Cache of recently read Document meta-data.
   *
   * In the case where the GSA has requested content "If-Modified-Since"
   * the initial crawl, the Connector Manager will fetch the LastModify
   * date to see if it can avoid returning content.  The Connector Manager
   * could also request the content MimeType so that it might set the
   * content type of the output stream.  The getContent() method also
   * needs access to the metadata so that it can properly determine
   * whether the document actually has content to return.  In these cases,
   * it is beneficial to avoid a double fetch of metadata from the Livelink
   * repository.
   *
   * This cache holds the Document objects recently returned from getMetaData()
   * for a short period, in case that information is needed again.
   * The documents age out of the cache relatively quickly (1 minute), as
   * the information should only be needed for a single servlet request.
   * The cache is configured hold 100 items, as the GSA may open many
   * concurrent content retrieval requests.
   */
  private final Map<Integer, AgingDocument> documentCache;

  /**
   * The fields used for constructing Properties for getMetaData().
   */
  private static final Field[] FIELDS;

  /**
   * The columns needed in the database query, which are obtained
   * from the field names in {@code FIELDS}.
   */
  private static final String[] SELECT_LIST;

  static {
    // ListNodes requires the DataID and PermID columns to be
    // included here. This implementation requires DataID,
    // ModifyDate, MimeType, SubType, and DataSize.
    ArrayList<Field> list = new ArrayList<Field>();
    list.add(new Field("DataID", "ID", SpiConstants.PROPNAME_DOCID));
    list.add(new Field("PermID"));
    list.add(new Field("ModifyDate", "ModifyDate",
                       SpiConstants.PROPNAME_LASTMODIFIED));
    list.add(new Field("MimeType", "MimeType",
                       SpiConstants.PROPNAME_MIMETYPE));
    list.add(new Field("SubType", "SubType"));
    // Workaround LAPI NumberFormatException/NullPointerException bug
    // returning negative longs.
    list.add(Field.fromExpression(
        "case when DataSize < 0 then 0 else DataSize end DataSize",
        "DataSize", "DataSize" ));
    FIELDS = list.toArray(new Field[0]);

    SELECT_LIST = new String[FIELDS.length];
    for (int i = 0; i < FIELDS.length; i++) {
      SELECT_LIST[i] = FIELDS[i].selectExpression;
    }
  }

  LivelinkRetriever(LivelinkConnector connector, Client traversalClient,
      ContentHandler contentHandler) throws RepositoryException {
    this.connector = connector;
    this.traversalClient = traversalClient;
    this.contentHandler = contentHandler;

    // This cache holds the Document objects recently returned by getMetaData()
    // for a short period, in case that information is needed again.
    // The documents age out of the cache relatively quickly (1 minute), as
    // the information should only be needed for a single servlet request.
    // The cache is configured hold 100 items, as the GSA may open many
    // concurrent content retrieval requests.
    clock = new SystemClock();
    documentCache =
        Collections.synchronizedMap(new AgingDocumentCache(100, 60 * 1000));
  }

  /**
   * Return a {@link Document} instance populated with meta-data for the
   * document identified by {@code docid}.  The meta-data <em>should</em>
   * minimally include the {@code google:lastmodified} Property.
   *
   * @return a Document instance with Properties containing document meta-data
   * @throws RepositoryDocumentException if there was a document-specific
   *         error accessing the metadata, for instance the document does not
   *         exist or should be skipped
   * @throws RepositoryException if there was a problem accessing the document
   *         repository
   */
  public Document getMetaData(String docid) throws RepositoryException {
    try {
      int objid = getObjectId(docid);
      AgingDocument doc = documentCache.get(objid);
      if (doc != null) {
        return doc;
      }
      ClientValue node = traversalClient.ListNodes("DataID = " + docid,
                                                   "WebNodes", SELECT_LIST);
      if (node == null || node.size() == 0) {
        throw new RepositoryDocumentException("Not found: " + docid);
      }
      doc = new AgingDocument(objid);
      // Collect the recarray-based properties.
      for (int i = 0; i < FIELDS.length; i++) {
        if (FIELDS[i].propertyNames.length > 0) {
          ClientValue value = node.toValue(0, FIELDS[i].fieldName);
          if (value.isDefined()) {
            doc.addProperty(FIELDS[i], value);
          }
        }
      }
      documentCache.put(objid, doc);
      return doc;
    } catch (RepositoryDocumentException rde) {
      throw rde;
    } catch (RepositoryException re) {
      if (isDocumentError()) {
        throw new RepositoryDocumentException(
            "Failed to access document content for docid " + docid, re);
      } else {
        throw re;
      }
    }
  }

  /**
   * Return an {@code InputStream} that may be used to access content for the
   * document identified by {@code docid}.
   *
   * @param docid the document identifier
   * @return an InputStream for the document content or {@code null} if the
   *         document has no content.
   * @throws RepositoryDocumentException if there was a document-specific
   *         error accessing the content, for instance the document does not
   *         exist or should be skipped
   * @throws RepositoryException if there was a problem accessing the document
   *         repository
   */
  // NOTE: This duplicates some, but not all, of the logic from
  // LivelinkDocumentList.collectContentProperty().  Changes here
  // should probably be reflected there, and vice-versa.
  // TODO: Extract the common logic out into a shared utility method.
  /* @Override */
  public InputStream getContent(String docid) throws RepositoryException {
    try {
      int objid = getObjectId(docid);
      Document doc = getMetaData(docid);

      // TODO: There are subtle differences between how content
      // is handled here and how it is handled in the TraversalManager.
      // For instance, skipping content based upon MimeType or document size
      // is not done here because we don't have access to a TraversalContext.
      // Empty content is also handled differently in the Connector Manager
      // for Retriever content, so we might consider providing meaningful
      // stub content here.

      // TODO: Make this list configurable.
      switch (getSingleValueInt(doc, "SubType", -1)) {
        case -1:     // No subType?
        case 356:    // Blog
        case 357:    // Blog Entry
        case 123469: // Forum
        case 123470: // Forum Topics & Replies
        case 123475: // FAQ
        case 123476: // FAQ Entry
          return null;
        default:
          break;
      }

      // DataSize is the only non-nullable column from
      // DVersData that appears in the WebNodes view,
      // but there are cases (such as categories) where
      // there are rows in DVersData but FetchVersion
      // fails. So we're guessing here that if MimeType
      // is non-null then there should be a blob.
      if (Value.getSingleValueString(doc, "MimeType") == null) {
        return null;
      }

      // XXX: This value might be wrong. There are
      // data size callbacks which can change this
      // value. For example, the value returned by
      // GetObjectInfo may be different than the
      // value retrieved from the database.
      int dataSize = getSingleValueInt(doc, "DataSize", 0);
      if (dataSize <= 0) {
        return null;
      }

      return contentHandler.getInputStream(0, objid, 0, dataSize);
    } catch (RepositoryDocumentException rde) {
      throw rde;
    } catch (RepositoryException re) {
      if (isDocumentError()) {
        throw new RepositoryDocumentException(
            "Failed to access document content for docid " + docid, re);
      } else {
        throw re;
      }
    }
  }

  /** Extract the Livelink ObjectID from the supplied docid string. */
  private int getObjectId(String docid) throws RepositoryException {
    try {
      return Integer.parseInt(docid);
    } catch (NumberFormatException nfe) {
      LOGGER.warning("Invalid docid: " + docid);
      throw new RepositoryDocumentException("Invalid docid: " + docid, nfe);
    }
  }

  /** Convenience function for access to a single int value from a document. */
  private int getSingleValueInt(Document doc, String propertyName,
      int defaultValue) throws RepositoryException {
    String value = Value.getSingleValueString(doc, propertyName);
    if (value == null) {
      return defaultValue;
    }
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException nfe) {
      throw new RepositoryException(propertyName + " is not an integer: "
                                    + value, nfe);
    }
  }

  /**
   * Return true if an encountered error seems specific to the document,
   * or false if it appears systemic.
   */
  // NOTE: This logic was lifted from the error handler in LivelinkDocumentList.
  private boolean isDocumentError() {
    // Ping the Livelink Server to try to determine whether the
    // error is systemic or related to this specific document.
    // This is a guess, because it could be a transient error that
    // will not repeat itself on the ping.
    // TODO: Do a better job of distinguishing transient exceptions
    // from document exceptions when throwing LivelinkException
    // (including LivelinkIOException and LapiException).
    try {
      traversalClient.GetCurrentUserID();
      return true;
    } catch (RepositoryException e) {
      // The failure seems to be systemic, rather than a problem
      // with this particular document.
      return false;
    }
  }

  /**
   * A subclass of LivelinkDocument with a timestamp of its creation,
   * so that it might expire.
   */
  private class AgingDocument extends LivelinkDocument {
    // Object creation timestamp.
    public final long timestamp;

    public AgingDocument(int docid) throws RepositoryException {
      super(docid, FIELDS.length);
      timestamp = clock.getTimeMillis();
    }
  }

  /** A LivelinkDocument cache that discards any document that is expired. */
  // TODO: Remove aged documents in the background.
  private class AgingDocumentCache extends CacheMap<Integer, AgingDocument> {
    // Maximum age of document, before discarding.
    private final long timeout;

    public AgingDocumentCache(int size, long timeout) {
      super(size, size * 8);
      this.timeout = timeout;
    }

    /**
     * Returns cached Document, if not expired.  Returns {@code null} for
     * expired documents or cache misses.
     */
    public AgingDocument get(Integer key) {
      AgingDocument doc = super.get(key);
      if (doc == null) {
        return null;
      } else if (doc.timestamp + timeout > clock.getTimeMillis()) {
        return doc;
      } else {
        remove(key);
        return null;
      }
    }
  }
}
