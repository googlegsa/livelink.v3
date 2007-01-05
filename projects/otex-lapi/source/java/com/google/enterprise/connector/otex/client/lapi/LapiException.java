// Copyright (C) 2006-2007 Google Inc.

package com.google.enterprise.connector.otex.client.lapi;

import java.text.MessageFormat;
import java.util.logging.Logger;

import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.otex.LivelinkException;

import com.opentext.api.LLSession;

class LapiException extends LivelinkException
{
    private static final MessageFormat with =
        new MessageFormat("{0} ({1}) {2} ({3})");

    private static final MessageFormat without =
        new MessageFormat("{0} ({1})");
    

    private static String buildMessage(LLSession session) {
        int status = session.getStatus();
        if (status != 0) {
            String message = session.getStatusMessage();

            String apiError = session.getApiError();
            if (apiError == null) {
                return without.format(new String[] {
                    message, String.valueOf(status) });
            } else {
                String errorMessage = session.getErrMsg();
                return with.format(new String[] {
                    message, String.valueOf(status), errorMessage, apiError });
            }
        } else
            return null;
    }

    
    /**
     * @param e a Livelink-specific runtime exception
     */
    LapiException(Exception e, Logger logger) {
        super(e, logger);
    }

    
    LapiException(LLSession session, Logger logger) {
        super(buildMessage(session), logger);
    }
}
