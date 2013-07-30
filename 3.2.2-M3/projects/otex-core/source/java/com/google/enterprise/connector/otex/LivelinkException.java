// Copyright (C) 2007 Google Inc.
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

import java.text.MessageFormat;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import com.google.enterprise.connector.spi.RepositoryException;

/**
 * Extends <code>RepositoryException</code> to include logging all
 * exceptions that are thrown.
 */
public class LivelinkException extends RepositoryException
{
    /** A message key for the connector resource bundle. */
    private String key;

    /** Parameters for the message, if needed. */
    private Object[] params;
    
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
     * Constructs an instance that wraps another exception.
     * 
     * @param e a Livelink-specific runtime exception
     * @param logger a logger instance to log the exception against
     * @param key a resource bundle key
     * @param params message parameters; may be null
     */
    public LivelinkException(Exception e, Logger logger, String key, 
            Object[] params) {
        super(e);
        logMessage(logger);
        this.key = key;
        this.params = params;
    }

    /**
     * Constructs an instance with the given message.
     * 
     * @param message an error message
     * @param logger a logger instance to log the exception against
     */
    public LivelinkException(String message, Logger logger) {
        super(message);
        logMessage(logger);
    }

    /**
     * Constructs an instance with the given message that wraps
     * another exception.
     * 
     * @param message an error message
     * @param e the cause
     * @param logger a logger instance to log the exception against
     */
    public LivelinkException(String message, Exception e, Logger logger) {
        super(message, e);
        logMessage(logger);
    }

    /**
     * Constructs an instance with the given message.
     * 
     * @param message an error message
     * @param logger a logger instance to log the exception against
     * @param key a resource bundle key
     * @param params message parameters; may be null
     */
    public LivelinkException(String message, Logger logger, String key, 
            Object[] params) {
        super(message);
        logMessage(logger);
        this.key = key;
        this.params = params;
    }

    /**
     * Uses the provided ResourceBundle to look up this
     * exception's key and return the bundle's message. If the
     * bundle or key are null or the key is missing from the
     * bundle, the result of calling getLocalizedMessage() is
     * returned.
     *
     * @param bundle a ResourceBundle
     */
    public String getLocalizedMessage(ResourceBundle bundle) {
        if (bundle == null || key == null)
            return super.getLocalizedMessage();
        try {
            if (params == null)
                return bundle.getString(key); 
            return MessageFormat.format(bundle.getString(key), params);
        } catch (MissingResourceException e) {
            return super.getLocalizedMessage();
        } catch (IllegalArgumentException e) {
            return super.getLocalizedMessage();
        }
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
