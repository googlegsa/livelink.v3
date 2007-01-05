// Copyright (C) 2006-2007 Google Inc.

package com.google.enterprise.connector.otex.client.lapi;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.IOException;
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
    private final LLSession session;
    
    private final LAPI_DOCUMENTS documents;
    
    LapiClient(LLSession session) {
        this.session = session;
        this.documents = new LAPI_DOCUMENTS(session);
    }

    public synchronized RecArray ListNodes(Logger logger, String query,
            String view, String[] columns) throws RepositoryException {
        LLValue recArr = (new LLValue()).setTable();
        try {
            LLValue args = (new LLValue()).setList();
            LLValue columnsList = (new LLValue()).setList();
            for (int i = 0; i < columns.length; i++)
                columnsList.add(columns[i]);
            if (documents.ListNodes(query, args, view, columnsList, 
                    LAPI_DOCUMENTS.PERM_SEECONTENTS, LLValue.LL_FALSE,
                    recArr) != 0) {
                throw new LapiException(session, logger);
            }
        } catch (RuntimeException e) {
            throw new LapiException(e, logger);
        }
        return new LapiRecArray(recArr);
    }
    

    public synchronized InputStream FetchVersion(final Logger logger,
            int volumeId, int objectId, int versionNumber)
            throws RepositoryException {
        try {
            final File temporaryFile = File.createTempFile("gsall", null);
            logger.fine("TEMP FILE: " + temporaryFile);
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
                                logger.fine("DELETE: " + temporaryFile);
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
}
