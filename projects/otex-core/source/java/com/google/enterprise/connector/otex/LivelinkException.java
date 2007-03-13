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
