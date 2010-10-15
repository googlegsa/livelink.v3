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

package com.google.enterprise.connector.otex.client.lapi;

import java.io.File;
import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.enterprise.connector.otex.LivelinkException;
import com.google.enterprise.connector.otex.LivelinkIOException;
import com.google.enterprise.connector.otex.client.Client;
import com.google.enterprise.connector.otex.client.ClientValue;
import com.google.enterprise.connector.otex.client.ClientValueFactory;
import com.google.enterprise.connector.spi.RepositoryException;
import com.opentext.api.GoogleThunk;
import com.opentext.api.LAPI_ATTRIBUTES;
import com.opentext.api.LAPI_DOCUMENTS;
import com.opentext.api.LAPI_USERS;
import com.opentext.api.LLBadServerCertificateException;
import com.opentext.api.LLCouldNotConnectException;
import com.opentext.api.LLCouldNotConnectHTTPException;
import com.opentext.api.LLHTTPAccessDeniedException;
import com.opentext.api.LLHTTPCGINotFoundException;
import com.opentext.api.LLHTTPClientException;
import com.opentext.api.LLHTTPForbiddenException;
import com.opentext.api.LLHTTPProxyAuthRequiredException;
import com.opentext.api.LLHTTPRedirectionException;
import com.opentext.api.LLHTTPServerException;
import com.opentext.api.LLIOException;
import com.opentext.api.LLIllegalOperationException;
import com.opentext.api.LLSSLNotAvailableException;
import com.opentext.api.LLSecurityProviderException;
import com.opentext.api.LLSession;
import com.opentext.api.LLUnknownFieldException;
import com.opentext.api.LLUnsupportedAuthMethodException;
import com.opentext.api.LLValue;
import com.opentext.api.LLWebAuthInitException;

/**
 * A direct LAPI client implementation.
 */
final class LapiClient implements Client {
  /** The logger for this class. */
  private static final Logger LOGGER =
    Logger.getLogger(LapiClient.class.getName());

  static {
    // Verify that the Client class constants are correct.
    assert TOPICSUBTYPE == LAPI_DOCUMENTS.TOPICSUBTYPE :
      LAPI_DOCUMENTS.TOPICSUBTYPE;
    assert REPLYSUBTYPE == LAPI_DOCUMENTS.REPLYSUBTYPE :
      LAPI_DOCUMENTS.REPLYSUBTYPE;
    assert PROJECTSUBTYPE == LAPI_DOCUMENTS.PROJECTSUBTYPE :
      LAPI_DOCUMENTS.PROJECTSUBTYPE;
    assert TASKSUBTYPE == LAPI_DOCUMENTS.TASKSUBTYPE :
      LAPI_DOCUMENTS.TASKSUBTYPE;
    assert CHANNELSUBTYPE == LAPI_DOCUMENTS.CHANNELSUBTYPE :
      LAPI_DOCUMENTS.CHANNELSUBTYPE;
    assert NEWSSUBTYPE == LAPI_DOCUMENTS.NEWSSUBTYPE :
      LAPI_DOCUMENTS.NEWSSUBTYPE;
    assert POLLSUBTYPE == LAPI_DOCUMENTS.POLLSUBTYPE :
      LAPI_DOCUMENTS.POLLSUBTYPE;
    assert DISPLAYTYPE_HIDDEN == LAPI_DOCUMENTS.DISPLAYTYPE_HIDDEN :
      LAPI_DOCUMENTS.DISPLAYTYPE_HIDDEN;
    assert CHARACTER_ENCODING_NONE ==
      LAPI_DOCUMENTS.CHARACTER_ENCODING_NONE :
      LAPI_DOCUMENTS.CHARACTER_ENCODING_NONE;
    assert CHARACTER_ENCODING_UTF8 ==
      LAPI_DOCUMENTS.CHARACTER_ENCODING_UTF8 :
      LAPI_DOCUMENTS.CHARACTER_ENCODING_UTF8;
    assert PRIV_PERM_BYPASS == LAPI_USERS.PRIV_PERM_BYPASS :
      LAPI_USERS.PRIV_PERM_BYPASS;
    assert ATTR_DATAVALUES == LAPI_ATTRIBUTES.ATTR_DATAVALUES :
      LAPI_ATTRIBUTES.ATTR_DATAVALUES;
    assert ATTR_DEFAULTVALUES == LAPI_ATTRIBUTES.ATTR_DEFAULTVALUES :
      LAPI_ATTRIBUTES.ATTR_DEFAULTVALUES;
    assert ATTR_TYPE_BOOL == LAPI_ATTRIBUTES.ATTR_TYPE_BOOL :
      LAPI_ATTRIBUTES.ATTR_TYPE_BOOL;
    assert ATTR_TYPE_DATE == LAPI_ATTRIBUTES.ATTR_TYPE_DATE :
      LAPI_ATTRIBUTES.ATTR_TYPE_DATE;
    assert ATTR_TYPE_DATEPOPUP == LAPI_ATTRIBUTES.ATTR_TYPE_DATEPOPUP :
      LAPI_ATTRIBUTES.ATTR_TYPE_DATEPOPUP;
    assert ATTR_TYPE_REAL == LAPI_ATTRIBUTES.ATTR_TYPE_REAL :
      LAPI_ATTRIBUTES.ATTR_TYPE_REAL;
    assert ATTR_TYPE_REALPOPUP == LAPI_ATTRIBUTES.ATTR_TYPE_REALPOPUP :
      LAPI_ATTRIBUTES.ATTR_TYPE_REALPOPUP;
    assert ATTR_TYPE_INTPOPUP == LAPI_ATTRIBUTES.ATTR_TYPE_INTPOPUP :
      LAPI_ATTRIBUTES.ATTR_TYPE_INTPOPUP;
    assert ATTR_TYPE_SET == LAPI_ATTRIBUTES.ATTR_TYPE_SET :
      LAPI_ATTRIBUTES.ATTR_TYPE_SET;
    assert ATTR_TYPE_STRFIELD == LAPI_ATTRIBUTES.ATTR_TYPE_STRFIELD :
      LAPI_ATTRIBUTES.ATTR_TYPE_STRFIELD;
    assert ATTR_TYPE_STRMULTI == LAPI_ATTRIBUTES.ATTR_TYPE_STRMULTI :
      LAPI_ATTRIBUTES.ATTR_TYPE_STRMULTI;
    assert ATTR_TYPE_STRPOPUP == LAPI_ATTRIBUTES.ATTR_TYPE_STRPOPUP :
      LAPI_ATTRIBUTES.ATTR_TYPE_STRPOPUP;
    assert ATTR_TYPE_USER == LAPI_ATTRIBUTES.ATTR_TYPE_USER :
      LAPI_ATTRIBUTES.ATTR_TYPE_USER;
    assert CATEGORY_TYPE_LIBRARY == LAPI_ATTRIBUTES.CATEGORY_TYPE_LIBRARY :
      LAPI_ATTRIBUTES.CATEGORY_TYPE_LIBRARY;
    assert CATEGORY_TYPE_WORKFLOW ==
      LAPI_ATTRIBUTES.CATEGORY_TYPE_WORKFLOW :
      LAPI_ATTRIBUTES.CATEGORY_TYPE_WORKFLOW;
  }

  /**
   * Maps a LAPI exception to a LivelinkException or a
    * LivelinkIOException. A LivelinkIOException is explicitly treated
    * as a transient error, rather than document-related error.
    *
    * <p>LAPI exceptions have no hierarchy; they all directly extend
    * RuntimeException. All but one of the occurrences of
    * LLIOException is an I/O-related exception. That one is the
    * exception thrown when a version file length does not match the
    * expected length, and that must not be thrown as a
    * LivelinkIOException here. That happens on corrupt or missing
    * version files, and is document-specific.
    *
    * @param e a LAPI exception
    * @return a matching LivelinkException that wraps the LAPI exception
   */
  private static LivelinkException getLivelinkException(RuntimeException e) {
    if (e instanceof LLIOException) {
      // XXX: Hideous hack, but the LAPI error strings are hard-coded
      // and in English, and I cannot think of another way to
      // distinguish this error, which we must distinguish.
      if ("Premature end-of-data on socket".equals(e.getMessage())) {
        return new LivelinkException(e, LOGGER);
      } else {
        return new LivelinkIOException(e, LOGGER);
      }
    } else if (e instanceof LLBadServerCertificateException
        || e instanceof LLCouldNotConnectException
        || e instanceof LLCouldNotConnectHTTPException
        || e instanceof LLHTTPAccessDeniedException
        || e instanceof LLHTTPCGINotFoundException
        || e instanceof LLHTTPClientException
        || e instanceof LLHTTPForbiddenException
        || e instanceof LLHTTPProxyAuthRequiredException
        || e instanceof LLHTTPRedirectionException
        || e instanceof LLHTTPServerException
        || e instanceof LLSSLNotAvailableException
        || e instanceof LLSecurityProviderException
        || e instanceof LLUnsupportedAuthMethodException
        || e instanceof LLWebAuthInitException) {
      return new LivelinkIOException(e, LOGGER);
    } else {
      return new LivelinkException(e, LOGGER);
    }
  }

  /**
   * The Livelink session. LLSession instances are not thread-safe,
   * so all of the methods that access this session, directly or
   * indirectly, must be synchronized.
   */
  private final LLSession session;

  private final LAPI_DOCUMENTS documents;

  private final LAPI_USERS users;

  private final LAPI_ATTRIBUTES attributes;

  /*
   * Constructs a new client using the given session. Initializes
   * any subsidiary objects that are needed.
   *
   * @param session a new Livelink session
   */
  LapiClient(LLSession session) {
    this.session = session;
    this.documents = new LAPI_DOCUMENTS(session);
    this.users = new LAPI_USERS(session);
    this.attributes = new LAPI_ATTRIBUTES(session);
  }

  /** {@inheritDoc} */
  public ClientValueFactory getClientValueFactory()
      throws RepositoryException {
    return new LapiClientValueFactory();
  }

  /** {@inheritDoc} */
  public synchronized ClientValue GetServerInfo() throws RepositoryException {
    LLValue value = new LLValue();
    try {
      if (documents.GetServerInfo(value) != 0)
        throw new LapiException(session, LOGGER);
    } catch (RuntimeException e) {
      throw getLivelinkException(e);
    }
    return new LapiClientValue(value);
  }

  /** {@inheritDoc} */
  public synchronized int GetCurrentUserID() throws RepositoryException {
    LLValue id = new LLValue();
    try {
      if (users.GetCurrentUserID(id) != 0)
        throw new LapiException(session, LOGGER);
    } catch (RuntimeException e) {
      throw getLivelinkException(e);
    }
    return id.toInteger();
  }

  /** {@inheritDoc} */
  public synchronized ClientValue GetCookieInfo()
      throws RepositoryException {
    LLValue cookies = new LLValue();
    try {
      if (users.GetCookieInfo(cookies) != 0)
        throw new LapiException(session, LOGGER);
    } catch (RuntimeException e) {
      throw getLivelinkException(e);
    }
    return new LapiClientValue(cookies);
  }

  /** {@inheritDoc} */
  public synchronized ClientValue GetUserOrGroupByIDNoThrow(int id)
      throws RepositoryException {
    LLValue userInfo = new LLValue();
    try {
      if (users.GetUserOrGroupByID(id, userInfo) != 0) {
        if (LOGGER.isLoggable(Level.FINE))
          LOGGER.fine(LapiException.buildMessage(session));
        return null;
      }
    } catch (RuntimeException e) {
      throw getLivelinkException(e);
    }
    return new LapiClientValue(userInfo);
  }

  /** {@inheritDoc} */
  public synchronized ClientValue GetUserInfo(String username)
      throws RepositoryException {
    LLValue userInfo = new LLValue();
    try {
      if (users.GetUserInfo(username, userInfo) != 0)
        throw new LapiException(session, LOGGER);
    } catch (RuntimeException e) {
      throw getLivelinkException(e);
    }
    return new LapiClientValue(userInfo);
  }

  /** {@inheritDoc} */
  public synchronized ClientValue AccessEnterpriseWS()
      throws RepositoryException {
    LLValue info = new LLValue();
    try {
      if (documents.AccessEnterpriseWS(info) != 0)
        throw new LapiException(session, LOGGER);
    } catch (RuntimeException e) {
      throw getLivelinkException(e);
    }
    return new LapiClientValue(info);
  }

  /** {@inheritDoc} */
  public synchronized ClientValue ListNodes(String query, String view,
      String[] columns) throws RepositoryException {
    ClientValue recArray = listNodesHelper(query, view, columns);
    if (recArray == null)
      throw new LapiException(session, LOGGER);
    return recArray;
  }

  /** {@inheritDoc} */
  /*
   * This ListNodesNoThrow version returns null rather than throwing
   * an exception on SQL query errors.
   * LivelinkTraversalManager tries to determine the back-end
   * repository DB by running an Oracle-specific query that succeeds
   * on Oracle, but throws an exception for SQL-Server.  The test
   * probe caught the expected exception, but it was still getting
   * logged to the error log (to which users objected).  See Issues 4 and 51:
   * http://code.google.com/p/google-enterprise-connector-otex/issues/detail?id=4
   * http://code.google.com/p/google-enterprise-connector-otex/issues/detail?id=51
   */
  public synchronized ClientValue ListNodesNoThrow(String query, String view,
      String[] columns) throws RepositoryException {
    ClientValue recArray = listNodesHelper(query, view, columns);
    if (recArray == null) {
      // Only log an unexpected error. At FINEST, log the actual
      // error so that we don't miss something.
      if (session.getStatus() != 106601)
        LOGGER.warning(LapiException.buildMessage(session));
      else if (LOGGER.isLoggable(Level.FINEST))
        LOGGER.finest(LapiException.buildMessage(session));
    }
    return recArray;
  }

  /**
   * Wraps the <code>LAPI_DOCUMENTS.ListNodes</code> method.
   * Not all of the arguments of that method are exposed here.
   * <p>
   * The LAPI <code>ListNodes</code> implementation requires the
   * DataID and PermID columns to be included in the selected
   * columns.
   *
   * @param query a SQL condition, used in the WHERE clause
   * @param view a SQL table expression, used in the FROM clause
   * @param columns a SQL select list, used in the SELECT clause
   * @return <code>null</code> if the call to <code>ListNodes</code>
   * returns a non-zero value, or the recarray if the call succeeeds
   * @throws RepositoryException if a runtime error occurs
   */
  private ClientValue listNodesHelper(String query, String view,
      String[] columns) throws RepositoryException {
    LLValue recArray = new LLValue();
    LLValue args = (new LLValue()).setList();
    LLValue columnsList = (new LLValue()).setList();

    for (int i = 0; i < columns.length; i++)
      columnsList.add(columns[i]);

    try {
      if (documents.ListNodes(query, args, view, columnsList,
              LAPI_DOCUMENTS.PERM_SEECONTENTS,
              LLValue.LL_FALSE, recArray) != 0) {
        return null;
      }
    } catch (LLIllegalOperationException e) {
      // See FetchVersion.
      LOGGER.info("Trying unMarshall workaround...");
      unMarshall();
      if (session.getStatus() == 0) {
        throw new LapiException("unMarshall workaround failed", e, LOGGER);
      } else {
        throw new LapiException(session, e, LOGGER);
      }
    } catch (RuntimeException e) {
      throw getLivelinkException(e);
    }
    return new LapiClientValue(recArray);
  }

  /** {@inheritDoc} */
  public synchronized ClientValue GetObjectInfo(int volumeId, int objectId)
      throws RepositoryException {
    LLValue objectInfo = new LLValue();
    try {
      if (documents.GetObjectInfo(volumeId, objectId, objectInfo) != 0) {
        throw new LapiException(session, LOGGER);
      }
    } catch (RuntimeException e) {
      throw getLivelinkException(e);
    }
    return new LapiClientValue(objectInfo);
  }

  /** {@inheritDoc} */
  public synchronized ClientValue GetObjectAttributesEx(
      ClientValue objectIdAssoc, ClientValue categoryIdAssoc)
      throws RepositoryException {
    LLValue categoryVersion = new LLValue();
    try {
      LLValue objIDa = ((LapiClientValue) objectIdAssoc).getLLValue();
      LLValue catIDa = ((LapiClientValue) categoryIdAssoc).getLLValue();
      if (documents.GetObjectAttributesEx(objIDa, catIDa,
              categoryVersion) != 0) {
        throw new LapiException(session, LOGGER);
      }
    } catch (RuntimeException e) {
      throw getLivelinkException(e);
    }
    return new LapiClientValue(categoryVersion);
  }

  /** {@inheritDoc} */
  public synchronized ClientValue AttrListNames(ClientValue categoryVersion,
      ClientValue attributeSetPath) throws RepositoryException {
    LLValue attrNames = new LLValue();
    try {
      LLValue catVersion =
          ((LapiClientValue) categoryVersion).getLLValue();
      LLValue attrPath = (attributeSetPath == null) ? null :
          ((LapiClientValue) attributeSetPath).getLLValue();

      // LAPI AttrListNames method does not reset the session status.
      session.setError(0, "");
      if (attributes.AttrListNames(catVersion, attrPath,
              attrNames) != 0) {
        throw new LapiException(session, LOGGER);
      }
    } catch (RuntimeException e) {
      throw getLivelinkException(e);
    }
    return new LapiClientValue(attrNames);
  }

  /** {@inheritDoc} */
  public synchronized ClientValue AttrGetInfo(ClientValue categoryVersion,
      String attributeName, ClientValue attributeSetPath)
      throws RepositoryException {
    LLValue info = new LLValue();
    try {
      LLValue catVersion =
          ((LapiClientValue) categoryVersion).getLLValue();
      LLValue attrPath = (attributeSetPath == null) ? null :
          ((LapiClientValue) attributeSetPath).getLLValue();

      // LAPI AttrGetInfo method does not reset the session status.
      session.setError(0, "");
      if (attributes.AttrGetInfo(catVersion, attributeName, attrPath,
              info) != 0) {
        throw new LapiException(session, LOGGER);
      }
    } catch (RuntimeException e) {
      throw getLivelinkException(e);
    }
    return new LapiClientValue(info);
  }

  /** {@inheritDoc} */
  public synchronized ClientValue AttrGetValues(ClientValue categoryVersion,
      String attributeName, ClientValue attributeSetPath)
      throws RepositoryException {
    LLValue attrValues = new LLValue();
    try {
      LLValue catVersion =
          ((LapiClientValue) categoryVersion).getLLValue();
      LLValue attrPath = (attributeSetPath == null) ? null :
          ((LapiClientValue) attributeSetPath).getLLValue();

      // LAPI AttrGetValues method does not reset the session status.
      session.setError(0, "");
      if (attributes.AttrGetValues(catVersion, attributeName,
              LAPI_ATTRIBUTES.ATTR_DATAVALUES, attrPath,
              attrValues) != 0) {
        throw new LapiException(session, LOGGER);
      }
    } catch (RuntimeException e) {
      throw getLivelinkException(e);
    }
    return new LapiClientValue(attrValues);
  }

  /** {@inheritDoc} */
  public synchronized ClientValue ListObjectCategoryIDs(
      ClientValue objectIdAssoc) throws RepositoryException {
    LLValue categoryIds = new LLValue();
    try {
      LLValue objIDa = ((LapiClientValue) objectIdAssoc).getLLValue();
      if (documents.ListObjectCategoryIDs(objIDa, categoryIds) != 0)
        throw new LapiException(session, LOGGER);
    } catch (RuntimeException e) {
      throw getLivelinkException(e);
    }
    return new LapiClientValue(categoryIds);
  }

  /** {@inheritDoc} */
  public synchronized void FetchVersion(int volumeId, int objectId,
      int versionNumber, File path) throws RepositoryException {
    try {
      if (documents.FetchVersion(volumeId, objectId, versionNumber,
              path.getPath()) != 0) {
        throw new LapiException(session, LOGGER);
      }
    } catch (LLUnknownFieldException e) {
      // Workaround Open Text bug LPO-109. In some (or perhaps
      // all) cases when FetchVersion returns an error, the
      // unmarshalling fails with the following
      // LLUnknownFieldException:
      //
      //     LLValue unknown field name: FileAttributes
      //
      // The status and error messages fields are not available
      // in the LLSession object when this exception is thrown.
      // If, however, we catch the LLUnknownFieldException and
      // call LLSession.unMarshall, then we can extract the
      // error from the session.
      unMarshall();
      throw new LapiException(session, LOGGER);
    } catch (RuntimeException e) {
      throw getLivelinkException(e);
    }
  }

  /** {@inheritDoc} */
  public synchronized void FetchVersion(int volumeId, int objectId,
      int versionNumber, OutputStream out) throws RepositoryException {
    try {
      if (documents.FetchVersion(volumeId, objectId,
              versionNumber, out) != 0) {
        throw new LapiException(session, LOGGER);
      }
    } catch (LLUnknownFieldException e) {
      // Workaround Open Text bug LPO-109. See the other
      // FetchVersion overload for details.
      unMarshall();
      throw new LapiException(session, LOGGER);
    } catch (RuntimeException e) {
      throw getLivelinkException(e);
    }
  }

  /** Wraps the <code>LLSession.unMarshall</code> method. */
  private void unMarshall() throws RepositoryException {
    try {
      // The unMarshall method has default access, so we need
      // this thunk in the com.opentext.api package in order to
      // call it. We could use reflection, but that would fail
      // in the presence of a security manager.
      GoogleThunk.unMarshall(session);
    } catch (RuntimeException e) {
      throw getLivelinkException(e);
    }
  }

  /** {@inheritDoc} */
  public synchronized ClientValue GetVersionInfo(int volumeId, int objectId,
      int versionNumber) throws RepositoryException {
    LLValue versionInfo = new LLValue();
    try {
      if (documents.GetVersionInfo(volumeId, objectId, versionNumber,
              versionInfo) != 0) {
        throw new LapiException(session, LOGGER);
      }
    } catch (RuntimeException e) {
      throw getLivelinkException(e);
    }
    return new LapiClientValue(versionInfo);
  }

  /**
   * {@inheritDoc}
   */
  public synchronized void ImpersonateUser(String username) {
    session.ImpersonateUser(username);
  }

  /**
   * {@inheritDoc}
   */
  public synchronized void ImpersonateUserEx(String username, String domain) {
    if ((domain == null) || (domain.length() == 0))
      session.ImpersonateUser(username);
    else
      session.ImpersonateUserEx(username, domain);
  }
}
