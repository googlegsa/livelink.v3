// Copyright (C) 2006-2007 Google Inc.

package com.google.enterprise.connector.otex.client.lapi;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.otex.client.Client;
import com.google.enterprise.connector.otex.client.RecArray;

import com.opentext.api.LAPI_DOCUMENTS;
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

    /*
     * Constructs a new client using the given session. Initializes
     * and subsidiary objects that are needed.
     *
     * @param session a new Livelink session
     */
    LapiClient(LLSession session) {
        this.session = session;
        this.documents = new LAPI_DOCUMENTS(session);
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
    
    /**
     * {@inheritDoc}
     */
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
    

    /**
     * {@inheritDoc}
     * <p>
     * This implementation uses <code>FetchVersion</code> and returns
     * a <code>FileInputStream</code> subclass that deletes the the
     * temporary file when the stream is closed.
     */
    /*
     * TODO: Compare this implementation to one that uses an
     * HttpURLConnection.
     */
    public synchronized InputStream FetchVersion(final Logger logger,
            int volumeId, int objectId, int versionNumber)
            throws RepositoryException {
        try {
            final File temporaryFile = File.createTempFile("gsa-otex-", null);
            if (logger.isLoggable(Level.FINER))
                logger.finer("TEMP FILE: " + temporaryFile);
            if (documents.FetchVersion(volumeId, objectId,
                    versionNumber, temporaryFile.getPath()) != 0) { 
                throw new LapiException(session, logger);
            }
            try {
                return new FileInputStream(temporaryFile) {
                        public void close() throws IOException {
                            try {
                                super.close();
                            } finally {
                                if (logger.isLoggable(Level.FINER))
                                    logger.finer("DELETE: " + temporaryFile);
                                temporaryFile.delete();
                            }
                        }
                    };
            } catch (FileNotFoundException e) {
                throw new LapiException(e, logger);
            }
        } catch (IOException e) {
            throw new LapiException(e, logger);
        } catch (RuntimeException e) {
            throw new LapiException(e, logger);
        }
    }


    /**
     * {@inheritDoc}
     */
    public synchronized void ImpersonateUser(String username) {
        session.ImpersonateUser(username);
    }
}
