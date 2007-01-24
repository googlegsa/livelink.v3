// Copyright (C) 2006-2007 Google Inc.

package com.google.enterprise.connector.otex.client.lapi;

import java.io.File;
import java.io.OutputStream;
import java.util.logging.Logger;

import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.otex.client.Client;
import com.google.enterprise.connector.otex.client.RecArray;

import com.opentext.api.LAPI_DOCUMENTS;
import com.opentext.api.LAPI_USERS;
import com.opentext.api.LLSession;
import com.opentext.api.LLValue;

/**
 * A direct LAPI client implementation.
 */
final class LapiClient implements Client
{
    /**
     * The Livelink session. LLSession instances are not thread-safe,
     * so all of the methods that access this session, directly or
     * indirectly, must be synchronized.
     */
    private final LLSession session;
    
    private final LAPI_DOCUMENTS documents;

    private final LAPI_USERS users;

    /*
     * Constructs a new client using the given session. Initializes
     * and subsidiary objects that are needed.
     *
     * @param session a new Livelink session
     */
    LapiClient(LLSession session) {
        this.session = session;
        this.documents = new LAPI_DOCUMENTS(session);
        this.users = new LAPI_USERS(session);
    }

    /**
     * {@inheritDoc} 
     * <p>
     * This implementation calls GetServerInfo to retrieve the server
     * encoding.
     */
    public String getEncoding(Logger logger) throws RepositoryException {
        try {
            LLValue value = (new LLValue()).setAssocNotSet();
            if (documents.GetServerInfo(value) != 0)
                throw new LapiException(session, logger);
            int serverEncoding = value.toInteger("CharacterEncoding");
            if (serverEncoding == LAPI_DOCUMENTS.CHARACTER_ENCODING_UTF8)
                return "UTF-8";
            else
                return null;
        } catch (RuntimeException e) {
            throw new LapiException(e, logger);
        }
    }

    public synchronized String getLLCookie(Logger logger)
            throws RepositoryException {
        LLValue cookies = (new LLValue()).setList();
        try {
            if (users.GetCookieInfo(cookies) != 0)
                throw new LapiException(session, logger);
        } catch (RuntimeException e) {
            throw new LapiException(e, logger);
        }
        for (int i = 0; i < cookies.size(); i++) {
            LLValue cookie = cookies.toValue(i);
            if ("LLCookie".equalsIgnoreCase(cookie.toString("Name"))) {
                return cookie.toString("Value");
            }
        }
        // XXX: Or return null and have the caller do this?
        throw new RepositoryException("Missing LLCookie");
    }
    
    /** {@inheritDoc} */
    public synchronized RecArray ListNodes(Logger logger, String query,
            String view, String[] columns) throws RepositoryException {
        LLValue recArray = (new LLValue()).setTable();
        try {
            LLValue args = (new LLValue()).setList();
            LLValue columnsList = (new LLValue()).setList();
            for (int i = 0; i < columns.length; i++)
                columnsList.add(columns[i]);
            if (documents.ListNodes(query, args, view, columnsList, 
                    LAPI_DOCUMENTS.PERM_SEECONTENTS, LLValue.LL_FALSE,
                    recArray) != 0) {
                throw new LapiException(session, logger);
            }
        } catch (RuntimeException e) {
            throw new LapiException(e, logger);
        }
        return new LapiRecArray(recArray);
    }

    /** {@inheritDoc} */
    public synchronized void FetchVersion(final Logger logger,
            int volumeId, int objectId, int versionNumber, File path)
            throws RepositoryException {
        try {
            if (documents.FetchVersion(volumeId, objectId, versionNumber,
                    path.getPath()) != 0) {
                throw new LapiException(session, logger);
            }
        } catch (RuntimeException e) {
            throw new LapiException(e, logger);
        }
    }

    /** {@inheritDoc} */
    public synchronized void FetchVersion(final Logger logger,
            int volumeId, int objectId, int versionNumber, OutputStream out)
            throws RepositoryException {
        try {
            if (documents.FetchVersion(volumeId, objectId,
                    versionNumber, out) != 0) { 
                throw new LapiException(session, logger);
            }
        } catch (RuntimeException e) {
            throw new LapiException(e, logger);
        }
    }


    /**
     * {@inheritDoc}
     */
    public synchronized void ImpersonateUser(final Logger logger, 
            String username) {
        session.ImpersonateUser(username);
    }


    /**
     * {@inheritDoc}
     *
     * <p>This implementation uses AccessEnterpriseWSEx.</p>
     */
    /* User access to the Personal Workspace can be disabled, so
     * try the Enterprise Workspace.  Passing null for the domain
     * parameter should cause the domain to default to the domain
     * of the logged-in user.
     */
    public synchronized boolean ping(final Logger logger)
            throws RepositoryException {
        LLValue info = new LLValue().setAssocNotSet();
        try {
            // TODO: ensure that the LapiClient handles domains.
            if (documents.AccessEnterpriseWSEx(null, info) != 0)
                return false;
        } catch (RuntimeException e) {
            throw new LapiException(e, logger);
        }
        return true;
    }
}
