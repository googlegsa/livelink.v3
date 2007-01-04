// Copyright (C) 2006-2007 Google Inc.

package com.google.enterprise.connector.otex;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.enterprise.connector.spi.RepositoryException;

import com.opentext.api.LLSession;

public class LivelinkException extends RepositoryException
{
    /**
     * @param e a Livelink-specific runtime exception
     */
    public LivelinkException(Exception e, Logger logger) {
        super(e);
        logMessage(logger);
    }

    
    public LivelinkException(String message, Logger logger) {
        super(message);
        logMessage(logger);
    }


    private void logMessage(Logger logger) {
        logger.log(Level.SEVERE, getMessage(), this);
    }
}
