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
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Base class for configuration-related exceptions.
 */
public class ConfigurationException extends RuntimeException {
    private String key;

    private Object[] params;
    
    public ConfigurationException() {
        super(); 
    }

    public ConfigurationException(String message) {
        super(message); 
    }

    public ConfigurationException(String message, String key, Object[] params) {
        super(message); 
        this.key = key;
        this.params = params;
    }

    public ConfigurationException(String message, Throwable cause) {
        super(message, cause); 
    }

    public ConfigurationException(String message, Throwable cause, 
            String key, Object[] params) {
        super(message, cause); 
        this.key = key;
        this.params = params;
    }

    public ConfigurationException(Throwable cause) {
        super(cause); 
    }

    public ConfigurationException(Throwable cause, String key, 
            Object[] params) {
        super(cause); 
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
}
