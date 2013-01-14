// Copyright 2007 Google Inc.
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

import java.io.InputStream;
import java.util.Calendar;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.common.annotations.VisibleForTesting;
import com.google.enterprise.connector.spi.Document;
import com.google.enterprise.connector.spi.DocumentList;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.RepositoryDocumentException;
import com.google.enterprise.connector.spi.SkippedDocumentException;
import com.google.enterprise.connector.spi.SpiConstants;
import com.google.enterprise.connector.spi.SpiConstants.ActionType;
import com.google.enterprise.connector.spi.SpiConstants.FeedType;
import com.google.enterprise.connector.spi.TraversalContext;
import com.google.enterprise.connector.spi.Value;
import com.google.enterprise.connector.otex.client.Client;
import com.google.enterprise.connector.otex.client.ClientValue;

/*
 * We use inner classes here to share the recarray and other fields in
 * <code>LivelinkDocumentList</code> with nested classes.
 */
class LivelinkDocumentList implements DocumentList {
  /** The logger for this class. */
  private static final Logger LOGGER =
      Logger.getLogger(LivelinkDocumentList.class.getName());

  /** An immutable false value. */
  private static final Value VALUE_FALSE = Value.getBooleanValue(false);

  /** An immutable true value. */
  private static final Value VALUE_TRUE = Value.getBooleanValue(true);

  /** ObjectInfo and VersionInfo assoc fields that hold UserId or GroupId. */
  private static final String[] USER_FIELD_NAMES = {
    "UserID", "GroupID", "AssignedTo", "CreatedBy", "ReservedBy",
    "LockedBy", "Owner" };

  /** Date formatter used to construct checkpoint dates */
  private final LivelinkDateFormat dateFormat =
      LivelinkDateFormat.getInstance();

  /** The connector contains configuration information. */
  private final LivelinkConnector connector;

  /** The client provides access to the server. */
  private final Client client;

  /** A concrete strategy for retrieving the content from the server. */
  private final ContentHandler contentHandler;

  /**
   * A handler for retrieving category attributes. The scope of this
   * object matters because it contains a cache of searchable category
   * IDs. We do not want to cache categories too long, so that we can
   * react to changes to the category in Livelink. For now, we'll keep
   * the cache for the DocumentList, rather than push it up to the
   * TraversalManager.
   */
  private final CategoryHandler categoryHandler;

  /** A handler for mapping user IDs to user names. */
  private final UserNameHandler nameHandler;

  /** The table of Livelink data, one row per doc, one column per field. */
  @VisibleForTesting
  final ClientValue recArray;

  /** The recarray fields. */
  private final Field[] fields;

  /** The table of Livelink deleted data, one row per object */
  private final ClientValue delArray;

  /** The TraversalContext from TraversalContextAware Interface */
  private final TraversalContext traversalContext;

  /** The current checkpoint reflecting the cursor in the DocumentList */
  @VisibleForTesting
  final Checkpoint checkpoint;

  /** If the traversal client user is the public content user,
   *  then all documents it sees will be publicly available.
   */
  private boolean isPublicContentUser = false;

  /** This subset of documents are authorized as public content. */
  private HashSet<String> publicContentDocs = null;

  /** This is the DocumentList Iterator */
  private final LivelinkDocumentListIterator docIterator;

  /** Count of documents returned or skipped in this batch. */
  private int docsProcessed;

  /**
   * Constructor for non-trivial document set.  Iterate over a
   * RecArray of items returned from Livelink.
   */
  LivelinkDocumentList(LivelinkConnector connector, Client client,
      ContentHandler contentHandler, ClientValue recArray,
      Field[] fields, ClientValue delArray,
      TraversalContext traversalContext, Checkpoint checkpoint,
      String currentUsername) throws RepositoryException {
    this.connector = connector;
    this.client = client;
    this.contentHandler = contentHandler;
    this.categoryHandler = new CategoryHandler(connector, client);
    this.nameHandler = new UserNameHandler(client);
    this.recArray = recArray;
    this.delArray = delArray;
    this.fields = fields;
    this.traversalContext = traversalContext;
    this.checkpoint = checkpoint;

    // Subset the docIds in the recArray into Public and Private Docs.
    findPublicContent(currentUsername);

    // Prime the DocumentList.nextDocument() iterator
    docIterator = new LivelinkDocumentListIterator();
    docsProcessed = 0;
  }

  /**
   * {@inheritDoc}
   */
  public Document nextDocument() throws RepositoryException {
    if (docIterator.hasNext()) {
      // If processing a document throws an exception, we will try to
      // determine if the failure is transient (like server not
      // responding), or permanent (like the document is corrupt and
      // can never be fetched). In the case of transient errors, we
      // rollback the checkpoint to allow the failed document to be
      // retried later. In the case of a permanent failure for a
      // document, we want to simply skip it and go onto the next.
      try {
        Document doc = docIterator.nextDocument();
        docsProcessed++;
        return doc;
      } catch (LivelinkIOException e) {
        return handleTransientException(e);
      } catch (RepositoryDocumentException e) {
        docsProcessed++;
        throw e;
      } catch (Throwable t) {
        LOGGER.severe("Caught exception when fetching a document: " +
            t.getMessage());

        // Ping the Livelink Server to try to determine whether the
        // error is systemic or related to this specific document.
        // This is a guess, because it could be a transient error that
        // will not repeat itself on the ping.
        // TODO: Do a better job of distinguishing transient exceptions
        // from document exceptions when throwing LivelinkException
        // (including LivelinkIOException and LapiException).
        try {
          client.GetCurrentUserID();
        } catch (RepositoryException e) {
          // The failure seems to be systemic, rather than a problem
          // with this particular document.
          return handleTransientException(e);
        }

        // It does not appear to be a transient failure.  Assume this
        // document contains permanent failures, and skip over it.
        docsProcessed++;
        throw new RepositoryDocumentException(t);
      }
    }

    // No more documents available.
    checkpoint.advanceToEnd();
    return null;
  }

  /**
   * Returns a partial batch or throws the given exception. If the
   * batch is partially processed, returns null to indicate the end of
   * the batch, in which case a followup batch will be tried
   * immediately. Otherwise, if the exception occurs at the beginning
   * of a batch, the given exception is thrown, and the error wait
   * will be triggered before a retry.
   */
  private Document handleTransientException(RepositoryException e)
      throws RepositoryException {
    checkpoint.restore();
    if (docsProcessed > 0) {
      return null;
    } else {
      throw e;
    }
  }

  /**
   * {@inheritDoc}
   */
  public String checkpoint() throws RepositoryException {
    String cp = checkpoint.toString();

    if (LOGGER.isLoggable(Level.FINE))
      LOGGER.fine("CHECKPOINT: " + cp);

    return cp;
  }

  /**
   * If we have a Public Content User specified, some of the
   * documents in the repository may be available to the public.
   * If the current user *is* the public content user, then all
   * documents in the recArray are by definition available to the
   * public.  However if the current user is not the public user,
   * we must subset the documents into those that are public and
   * those that are not.
   *
   * @param currentUsername the currently logged in user; may be impersonated
   */
  private void findPublicContent(String currentUsername)
      throws RepositoryException {
    String pcuser = connector.getPublicContentUsername();
    if (pcuser != null && pcuser.length() > 0) {
      isPublicContentUser = pcuser.equals(currentUsername);
      if (!isPublicContentUser) {
        // Get the subset of the DocIds that have public access.
        LivelinkAuthorizationManager authz =
            connector.getPublicContentAuthorizationManager();
        publicContentDocs = new HashSet<String>();
        authz.addAuthorizedDocids(new DocIdIterator(), pcuser,
            publicContentDocs);

        // We only care if there actually are public docs in
        // this batch.
        if (publicContentDocs.isEmpty())
          publicContentDocs = null;
      }
    }
  }

  /**
   * This iterates over the DocIDs in the recArray.
   */
  private class DocIdIterator implements Iterator<String> {
    /** The current row of the recarray. */
    private int row;

    /** The size of the recarray. */
    private final int size;

    public DocIdIterator() {
      this.row = 0;
      this.size = (recArray == null) ? 0 : recArray.size();
    }

    public boolean hasNext() {
      return row < size;
    }

    public String next() {
      if (row < size) {
        try {
          return recArray.toString(row, "DataID");
        } catch (RepositoryException e) {
          LOGGER.warning("LivelinkDocumentList.DocIdIterator.next " +
              "caught exception - " + e.getMessage());
          throw new RuntimeException(e);
        } finally {
          row++;
        }
      }
      return null;
    }

    public void remove() {
      throw new UnsupportedOperationException();
    }
  }

  /**
   * Iterates over a <code>DocumentList</code>, returning each
   * <code>Document</code> it contains.
   */
  /*
   * TODO: Eliminate this iterator and implement nextDocument
   * directly using this code. The gap seems to be just that
   * LivelinkTest assumes that it can iterate over the DocumentList
   * multiple times, which the new SPI doesn't allow. We could redo
   * the tests to eliminate that requirement, or add an internal
   * reset/restart/beforeFirst method to cause nextDocument to start
   * over again at the beginning.
   */
  private class LivelinkDocumentListIterator {
    /** The current row of the recArray. */
    private int insRow;

    /** The size of the recArray. */
    private final int insSize;

    /** The current row of the delArray. */
    private int delRow;

    /** The size of the delArray. */
    private final int delSize;

    /** The object ID of the current row. */
    private int objectId;

    /** The volume ID of the current row. */
    private int volumeId;

    /** The object Subtype. */
    private int subType;

    /** The ObjectInfo of the current row, [fetch delayed until needed] */
    private ClientValue objectInfo;

    /** The VersionInfo of the current row, [fetch delayed until needed] */
    private ClientValue versionInfo;

    /** The Document Properties associated with the current row. */
    private LivelinkDocument props;

    LivelinkDocumentListIterator() throws RepositoryException {
      this.delRow = 0;
      this.delSize = (delArray == null) ? 0 : delArray.size();

      this.insRow = 0;
      this.insSize = (recArray == null) ? 0 : recArray.size();
    }

    public boolean hasNext() {
      return (insRow < insSize) || (delRow < delSize);
    }

    public LivelinkDocument nextDocument() throws RepositoryException {
      if (!hasNext())
        return null;

      // Walk two separate lists: inserts and deletes.
      // Process the request in date order.  If an insert and
      // a delete have the same date, process inserts first.
      Date insDate = null, delDate = null;
      int dateComp = 0;
      objectInfo = null;
      versionInfo = null;

      // Peek at the next item to insert,
      if (insRow < insSize) {
        try {
          insDate = recArray.toDate(insRow, "ModifyDate");
        } catch (RepositoryException e1) {
          insRow++;
          throw e1;
        }
      } else
        dateComp = 1;

      // ... and the next item to delete.
      if (delRow < delSize) {
        try {
          delDate = dateFormat.parse(delArray.toString(delRow,
                  "AuditDate"));
        } catch (RepositoryException e1) {
          delRow++;
          throw e1;
        }
      } else
        dateComp = -1;

      // Process earlier modification/deletion times first.
      // If timestamps are the same, process Inserts before Deletes.
      // This way, if an item is added to the Livelink DB
      // then deleted immediately we will be sure to process
      // the insert before the delete.
      if (dateComp == 0)
        dateComp = insDate.compareTo(delDate);

      if (dateComp <= 0) {
        try {
          // Return an Inserted Item.
          objectId = recArray.toInteger(insRow, "DataID");
          volumeId = recArray.toInteger(insRow, "OwnerID");
          subType  = recArray.toInteger(insRow, "SubType");

          props = new LivelinkDocument(objectId, fields.length*2);

          // Collect the various properties for this row.
          collectRecArrayProperties();
          collectObjectInfoProperties();
          collectVersionProperties();
          collectCategoryAttributes();
          collectDerivedProperties();
        } finally {
          // Establish the checkpoint for this row.
          checkpoint.setInsertCheckpoint(insDate, objectId);
          insRow++;
        }
      } else {
        try {
          // return a Deleted Item
          objectId = delArray.toInteger(delRow, "DataID");

          // LOGGER.fine("Deleted item[" + delRow + "]: DataID " +
          //     objectId + "   AuditDate " + delDate +
          //     "    EventID " +
          //     delArray.toValue(delRow, "EventID").toString2());
          props = new LivelinkDocument(objectId, 3);
          collectDeletedObjectAttributes(delDate);
        } finally {
          // Establish the checkpoint for this row.
          checkpoint.setDeleteCheckpoint(delDate,
              delArray.toValue(delRow, "EventID"));
          delRow++;
        }
      }

      return props;
    }

    /**
     * For items to be deleted from the index, we need only supply
     * the GSA the DocId, lastModified date, and a Delete action
     * properties.
     */
    private void collectDeletedObjectAttributes(Date deleteDate)
        throws RepositoryException
    {
      props.addProperty(SpiConstants.PROPNAME_DOCID,
          Value.getLongValue(objectId));
      Calendar c = Calendar.getInstance();
      c.setTime(deleteDate); // sic; we have milliseconds here.
      props.addProperty(SpiConstants.PROPNAME_LASTMODIFIED,
          Value.getDateValue(c));
      props.addProperty(SpiConstants.PROPNAME_ACTION,
          Value.getStringValue(ActionType.DELETE.toString()));
    }

    /** Collects the recarray-based properties. */
    /*
     * TODO: Undefined values will not be added to the property
     * map. We may want some other value, possibly different
     * default values for each column (e.g., MimeType =
     * "application/octet-stream").
     */
    private void collectRecArrayProperties() throws RepositoryException {
      for (int i = 0; i < fields.length; i++) {
        if (fields[i].propertyNames.length > 0) {
          ClientValue value =
              recArray.toValue(insRow, fields[i].fieldName);
          if (value.isDefined()) {
            if (isUserIdOrGroupId(fields[i].fieldName)) {
              // FIXME: hack knows that UserID has 1 propertyName
              nameHandler.addUserByName(fields[i].propertyNames[0], value,
                  props);
            } else
              props.addProperty(fields[i], value);
          }
        }
      }
    }

    /** Collects additional properties derived from the recarray. */
    private void collectDerivedProperties() throws RepositoryException {
      // Flag the document as publicly accessible (or not).
      boolean isPublic = isPublicContentUser ||
          (publicContentDocs != null &&
              publicContentDocs.contains(Integer.toString(objectId)));
      props.addProperty(SpiConstants.PROPNAME_ISPUBLIC,
          isPublic ? VALUE_TRUE : VALUE_FALSE);

      if (connector.getFeedType() == FeedType.CONTENTURL) {
        // If we are not using content feeds, don't supply the content yet.
        // TODO: What about SkippedDocumentExceptions?
        Value value = Value.getStringValue(FeedType.CONTENTURL.toString());
        props.addProperty(SpiConstants.PROPNAME_FEEDTYPE, value);
      } else {
        // Fetch the content.
        collectContentProperty();
      }

      // Add the ExtendedData as MetaData properties.
      collectExtendedDataProperties();

      // DISPLAYURL
      String displayUrl = isPublic ?
          connector.getPublicContentDisplayUrl() :
          connector.getDisplayUrl();
      String url = connector.getDisplayUrl(displayUrl,
          subType, objectId, volumeId, getDownloadFileName());
      props.addProperty(SpiConstants.PROPNAME_DISPLAYURL,
          Value.getStringValue(url));
    }

    /**
     * Construct a download filename based upon the object name,
     * its mimetype, and the filename of its version.  The download
     * name is URL safe, containing no whitespaces or special characters.
     *
     * @return download filename, may be null.
     */
    private String getDownloadFileName() {
      String fileName = null;

      // If we have content, try to include a filename in the displayUrl.
      try {
        // We're guessing here that if MimeType is
        // non-null then there should be a content.
        if (!recArray.isDefined(insRow, "MimeType"))
          return null;
        if (recArray.isDefined(insRow, "Name"))
          fileName = recArray.toString(insRow, "Name");
      } catch (RepositoryException e) {
        return null;
      }

      // If the name has no extension, pull the filename extension off
      // of the VersionInfo FileName.
      // TODO: The Livelink code uses a mimetype-to-extension map to do this.
      if (fileName != null && fileName.lastIndexOf('.') <= 0) {
        try {
          if (versionInfo == null)
            versionInfo = client.GetVersionInfo(volumeId, objectId, 0);
          if (versionInfo != null && versionInfo.hasValue()
              && versionInfo.isDefined("FileName")) {
            String fn = versionInfo.toString("FileName");
            int ext;
            if (fileName.trim().length() == 0)
              fileName = fn;
            else if ((ext = fn.lastIndexOf('.')) > 0)
              fileName += fn.substring(ext);
          }
        } catch (RepositoryException re) {
          // Object does not have VersionInfo or FileName property.
        }
      }

      // Now make the filename URL safe, repacing 'bad chars' with either
      // underscores or URL encoded escapes.
      if (fileName != null) {
        if (fileName.trim().length() > 0) {
          // Mangle filename like Livelink does.
          fileName = fileName.replaceAll("[\\s\\|\\?/\\\\<>;\\*%'\"]", "_");
          try {
            fileName = java.net.URLEncoder.encode(fileName, "UTF-8");
          } catch (java.io.UnsupportedEncodingException e) {
            // Shouldn't happen with UTF-8, but if it does, forget the filename.
            fileName = null;
          }
        } else {
          fileName = null;
        }
      }
      return fileName;
    }

    /**
     * Collect the content property for the item.
     * If the item does not (or must not) have content, then no
     * content property is generated.  If the item's content is
     * not acceptable according to the TraversalContext, then no
     * content is generated.
     */
    // NOTE: Some of the logic here has been replicated in
    // Retriever.getContent().  Changes here should probably be
    // reflected there, and vice-versa.
    // TODO: Extract the common logic out into a shared utility method.
    private void collectContentProperty() throws RepositoryException {
      if (LOGGER.isLoggable(Level.FINER))
        LOGGER.finer("CONTENT WITH SUBTYPE = " + subType);

      List<Integer> unsupportedTypes =
          connector.getUnsupportedFetchVersionTypes();
      if (unsupportedTypes.contains(subType)) {
        if (LOGGER.isLoggable(Level.FINEST)) {
          LOGGER.finest("NO CONTENT PROPERTY FOR FOR UNSUPPORTED SUBTYPE = "
              + subType);
        }
        return;
      }

      // DataSize is the only non-nullable column from
      // DVersData that appears in the WebNodes view,
      // but there are cases (such as categories) where
      // there are rows in DVersData but FetchVersion
      // fails. So we're guessing here that if MimeType
      // is non-null then there should be a blob.
      ClientValue mimeType = recArray.toValue(insRow, "MimeType");
      if (!mimeType.isDefined())
        return;

      // XXX: This value might be wrong. There are
      // data size callbacks which can change this
      // value. For example, the value returned by
      // GetObjectInfo may be different than the
      // value retrieved from the database.
      int size = recArray.toInteger(insRow, "DataSize");
      if (LOGGER.isLoggable(Level.FINER))
        LOGGER.finer("CONTENT DATASIZE = " + size);

      // The TraversalContext Interface provides additional
      // screening based upon content size and mimetype.
      if (traversalContext != null) {
        String mt = mimeType.toString2();
        if (LOGGER.isLoggable(Level.FINER))
          LOGGER.finer("CONTENT TYPE = " + mt);

        // Is this MimeType supported?  If not, don't feed content.
        int supportLevel = traversalContext.mimeTypeSupportLevel(mt);
        if (supportLevel == 0)
          return;

        // Is this MimeType excluded?  If so, skip the whole document.
        if (supportLevel < 0)
          throw new SkippedDocumentException("Excluded by content type: " + mt);

        // Is the content too large?
        if (((long) size) > traversalContext.maxDocumentSize())
          return;
      } else {
        // If there is no traversal context, we'll enforce a size
        // limit of 30 MB. This limit is hard-coded in the GSA anyway.
        if (size > 30 * 1024 * 1024)
          return;
      }

      // If there is no actual data, don't feed empty content.
      if (size <= 0)
        return;

      // If we pass the gauntlet, create a content stream property
      // and add it to the property map.
      InputStream is =
          contentHandler.getInputStream(volumeId, objectId, 0, size);
      Value contentValue = Value.getBinaryValue(is);
      props.addProperty(SpiConstants.PROPNAME_CONTENT, contentValue);
    }

    /**
     * Collect properties from the ExtendedData assoc.
     */
    private void collectExtendedDataProperties()
        throws RepositoryException {
      String[] fields = connector.getExtendedDataKeys(subType);
      if (fields == null)
        return;

      if (objectInfo == null)
        objectInfo = client.GetObjectInfo(volumeId, objectId);
      ClientValue extendedData = objectInfo.toValue("ExtendedData");
      if (extendedData == null || !extendedData.hasValue())
        return;

      // Make a set of the names in the assoc.
      HashSet<String> names = new HashSet<String>();
      Enumeration<String> it = extendedData.enumerateNames();
      while (it.hasMoreElements())
        names.add(it.nextElement());

      // Decompose the ExtendedData into its atomic values,
      // and add them as properties.
      for (int i = 0; i < fields.length; i++) {
        if (names.contains(fields[i])) {
          ClientValue value = extendedData.toValue(fields[i]);
          if (value != null && value.hasValue()) {
            // XXX: For polls, the Questions field is a
            // stringified list of assoc. We're only handling
            // this one case, rather than handling stringified
            // values generally.
            if (subType == Client.POLLSUBTYPE &&
                fields[i].equals("Questions")) {
              value = value.stringToValue();
            }

            collectValueProperties(fields[i], value);
          }
        } else {
          if (LOGGER.isLoggable(Level.WARNING)) {
            LOGGER.warning("ExtendedData for " + objectId +
                " (subtype " + subType + ") has no " +
                fields[i] + " feature.");
          }
        }
      }
    }

    /**
     * Collects ObjectInfo properties.
     */
    private void collectObjectInfoProperties() throws RepositoryException {
      // First check for a request for ObjectInfo data.
      // By default, no ObjectInfo fields are specified.
      String[] fields = connector.getObjectInfoKeys();
      if (fields == null)
        return;

      if (objectInfo == null)
        objectInfo = client.GetObjectInfo(volumeId, objectId);
      if (objectInfo == null || !objectInfo.hasValue())
        return;

      // Extract the objectInfo items of interest and add them to the
      // property map.  We know there are no compound objectInfo fields.
      for (int i = 0; i < fields.length; i++) {
        ClientValue value = objectInfo.toValue(fields[i]);
        if (value != null && value.hasValue()) {
          // ExtendedData is the only non-atomic type, so explode it.
          // If the client specified it, slurp all the ExtendedData.
          // If the client wished to be more selective, they should
          // have added specific ExtendedData fields to the map.
          if ("ExtendedData".equalsIgnoreCase(fields[i]))
            collectValueProperties(fields[i], value);
          else if (isUserIdOrGroupId(fields[i]))
            nameHandler.addUserByName(fields[i], value, props);
          else
            props.addProperty(fields[i], value);
        }
      }
    }

    /**
     * Collects version properties.  We only support the "Current" version.
     */
    private void collectVersionProperties() throws RepositoryException {
      // First check for a request for version info data.
      // By default, no versionInfo fields are specified.
      String[] fields = connector.getVersionInfoKeys();
      if (fields == null)
        return;

      // Make sure this item has versions. See the MimeType, Version and
      // DataSize comments in collectContentProperty().  If DataSize is
      // not defined, then the item has no versions.
      ClientValue dataSize = recArray.toValue(insRow, "DataSize");
      if (!dataSize.isDefined())
        return;

      if (versionInfo == null)
        versionInfo = client.GetVersionInfo(volumeId, objectId, 0);
      if (versionInfo == null || !versionInfo.hasValue())
        return;

      // Extract the versionInfo items of interest and add them to the
      // property map.  We know there are no compound versionInfo fields.
      for (int i = 0; i < fields.length; i++) {
        ClientValue value = versionInfo.toValue(fields[i]);
        if (value != null && value.hasValue()) {
          if (isUserIdOrGroupId(fields[i]))
            nameHandler.addUserByName(fields[i], value, props);
          else
            props.addProperty(fields[i], value);
        }
      }
    }

    /**
     * Collects properties in an LLValue.
     *
     * @param name the property name
     * @param value the property value
     * @see #collectExtendedDataProperties
     */
    private void collectValueProperties(String name, ClientValue value)
        throws RepositoryException {
      if (LOGGER.isLoggable(Level.FINEST)) {
        LOGGER.finest("Type: " + value.type() + "; value: " +
            value.toString2());
      }

      switch (value.type()) {
        case ClientValue.LIST:
          for (int i = 0; i < value.size(); i++)
            collectValueProperties(name, value.toValue(i));
          break;

        case ClientValue.ASSOC:
          Enumeration<String> keys = value.enumerateNames();
          while (keys.hasMoreElements()) {
            String key = keys.nextElement();
            collectValueProperties(key, value.toValue(key));
          }
          break;

        case ClientValue.BOOLEAN:
        case ClientValue.DATE:
        case ClientValue.DOUBLE:
        case ClientValue.INTEGER:
        case ClientValue.STRING:
          props.addProperty(name, value);
          break;

        default:
          LOGGER.finest("Ignoring an unimplemented type.");
          break;
      }
    }

    /**
     * Gets the category attribute values for the indicated
     * object. Each attribute name is mapped to a linked list of
     * values for each occurrence of that attribute. Attributes
     * with the same name in different categories will have their
     * values merged into a single list. Attribute sets are
     * supported, but nested attribute sets are not. User
     * attributes are resolved into the user or group name.
     *
     * @throws RepositoryException if an error occurs
     */
    private void collectCategoryAttributes() throws RepositoryException {
      categoryHandler.collectCategoryAttributes(objectId, nameHandler, props);
    }

    /**
     * Determine whether this field contains a UserID or GroupID value.
     *
     * @param fieldName the name of the field in the ObjectInfo or
     * VersionInfo assoc.
     * @return true if the field contains a UserID or a GroupID value.
     */
    private boolean isUserIdOrGroupId(String fieldName) {
      for (int i = 0; i < USER_FIELD_NAMES.length; i++) {
        if (USER_FIELD_NAMES[i].equalsIgnoreCase(fieldName))
          return true;
      }
      return false;
    }
  }
}
