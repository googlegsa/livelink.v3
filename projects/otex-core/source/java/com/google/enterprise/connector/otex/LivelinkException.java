// Copyright (C) 2006-2007 Google Inc.

package com.google.enterprise.connector.otex;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.enterprise.connector.spi.RepositoryException;

/**
 * Extends <code>RepositoryException</code> to include logging all
 * exceptions that are thrown.
 */
public class LivelinkException extends RepositoryException
{
    /**
     * Constructs an instance that wraps another exception.
     * 
     * @param e a Livelink-specific runtime exception
     * @param logger a logger instance to log the exception against
     */
    public LivelinkException(Exception e, Logger logger) {
        super(e);
        logMessage(logger);
    }

    
    /**
     * Constructs an instance with the given message
     * 
     * @param message an error message
     * @param logger a logger instance to log the exception against
     */
    public LivelinkException(String message, Logger logger) {
        super(message);
        logMessage(logger);
    }


    /**
     * Logs the exception.
     *
     * @param logger a logger instance to log the exception against
     */
    private void logMessage(Logger logger) {
        logger.log(Level.SEVERE, getMessage(), this);
    }
}
