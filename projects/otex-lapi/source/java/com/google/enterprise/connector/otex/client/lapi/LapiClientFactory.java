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

package com.google.enterprise.connector.otex.client.lapi;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.enterprise.connector.otex.client.Client;
import com.google.enterprise.connector.otex.client.ClientFactory;

import com.opentext.api.LLNameEnumeration;
import com.opentext.api.LLSession;
import com.opentext.api.LLValue;

/**
 * A direct LAPI client factory implementation.
 */
public final class LapiClientFactory implements ClientFactory {
    /** The logger for this class. */
    private static final Logger LOGGER =
        Logger.getLogger(LapiClientFactory.class.getName());

    private String hostname = null;
    private int port = 0;
    private String database = null;
    private String username = null;
    private String password = null;
    private LLValue config = null;

    private boolean useUsernamePasswordWithWebServer = false;
    
    /** {@inheritDoc} */
    public void setHostname(String value) {
        hostname = value;
    }

    /** {@inheritDoc} */
    public void setPort(int value) {
        port = value;
    }

    /** {@inheritDoc} */
    public void setDatabase(String value) {
        database = value;
    }

    /** {@inheritDoc} */
    public void setUsername(String value) {
        username = value;
    }

    /** {@inheritDoc} */
    public void setPassword(String value) {
        password = value;
    }

    /** {@inheritDoc} */
    public void setDomainName(String value) {
        setConfig("DomainName", value);
    }

    /** {@inheritDoc} */
    public void setEncoding(String value) {
        setConfig("encoding", value);
    }

    /** {@inheritDoc} */
    public void setLivelinkCgi(String value) {
        setConfig("LivelinkCGI", value);
    }

    /** {@inheritDoc} */
    // FIXME: how do we want to handle this? From an
    // administrator's perspective, they're answering yes or no,
    // but LAPI wants an integer.
    public void setUseHttps(boolean value) {
        setConfig("HTTPS", value ? 1 : 0);
    }

    /** {@inheritDoc} */
    public void setHttpUsername(String value) {
        setConfig("HTTPUserName", value);
    }

    /** {@inheritDoc} */
    public void setHttpPassword(String value) {
        setConfig("HTTPPassword", value);
    }
    
    /** {@inheritDoc} */
    public void setVerifyServer(boolean value) {
        setConfig("VerifyServer", value ? 1 : 0);
    }

    /** {@inheritDoc} */
    /* TODO: Can we use the LLSession.GetCARootCerts method to
     * allow the admin to specify a directory of certificate
     * files? It might need to be a URL to a location from which
     * we can read the certificates, write them to a file, then
     * read them, since the admin may not have generic filesystem
     * access to the GSA if the connectors are running there.
     */
    public void setCaRootCerts(java.util.List value) {
        LLValue list = new LLValue().setList(); 
        for (int i = 0; i < value.size(); i++) {
            LLValue cert = new LLValue().setString((String) value.get(i));
            list.add(cert); 
        }
        setConfig("CARootCerts", list);
    }

    /** {@inheritDoc} */
    public void setEnableNtlm(boolean value) {
        setConfig("EnableNTLM", value ? 1 : 0); 
    }

    /** {@inheritDoc} */
    public void setUseUsernamePasswordWithWebServer(boolean value) {
        useUsernamePasswordWithWebServer = value;
    }

    /**
     * Sets a string feature in the config assoc.
     *
     * @param name the feature name
     * @param value the feature value
     */
    /*
     * This method is synchronized to emphasize safety over speed. It
     * is extremely unlikely that this instance will be called from
     * multiple threads, but this is also a very fast, rarely executed
     * operation, so there's no need to cut corners.
     */
    private synchronized void setConfig(String name, String value) {
        if (config == null)
            config = (new LLValue()).setAssocNotSet();
        config.add(name, value);
    }
    
    /**
     * Sets an integer feature in the config assoc.
     *
     * @param name the feature name
     * @param value the feature value
     */
    private synchronized void setConfig(String name, int value) {
        if (config == null)
            config = (new LLValue()).setAssocNotSet();
        config.add(name, value);
    }
    
    /**
     * Sets an LLValue feature in the config assoc.
     *
     * @param name the feature name
     * @param value the feature value
     */
    private synchronized void setConfig(String name, LLValue value) {
        if (config == null)
            config = (new LLValue()).setAssocNotSet();
        config.add(name, value);
    }
    
    /** {@inheritDoc} */ 
    public Client createClient() {
        // Construct a new LLSession and pass that to the client, just
        // to avoid having to pass all of the constructor arguments to
        // LapiClient.
        logProperties(username, password, config); 
        LLSession session = new LLSession(hostname, port, database, username,
            password, config);
        return new LapiClient(session);
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * Construct a new LLSession using the provided username
     * and password instead of the configured ones. This is
     * intended for use by the AuthenticationManager.
     * 
     */
    /*
     * If a separate set of authentication parameters was not
     * specified, the single LapiClientFactory instance used for
     * crawling will also be used for authentication. When it's
     * used for authentication, we want to be sure that no
     * crawling-related identity information is used. There are
     * two places a username/password can be specified: the
     * top-level ones, and the HTTPUserName and HTTPPassword
     * values in the config object. The only way to tell if the
     * config parameters are present is to try to read them and
     * let LAPI thrown an exception; there's no "isDefined" in
     * the Java LAPI client. Rather than work to see if they're
     * present, we'll just copy the config and ensure that
     * they're not.
     */
    public Client createClient(String providedUsername, 
            String providedPassword) {
        // FIXME: Can the username/password ever be null?
        LLValue localConfig = null; 
        if (config != null) {
            localConfig = new LLValue().setAssocNotSet(); 
            LLNameEnumeration names = config.enumerateNames(); 
            while (names.hasMoreElements()) {
                String name = names.nextElement().toString(); 
                if ("HTTPUserName".equals(name) || 
                        "HTTPPassword".equals(name)) {
                    continue;
                }
                localConfig.add(name, config.toValue(name)); 
            }
        }

        if (localConfig != null && useUsernamePasswordWithWebServer) {
            localConfig.add("HTTPUserName", providedUsername); 
            localConfig.add("HTTPPassword", providedPassword); 
        }
        logProperties(providedUsername, providedPassword, localConfig); 
        LLSession session = new LLSession(hostname, port, database, 
            providedUsername, providedPassword, localConfig);
        return new LapiClient(session);
    }

    /**
     * Logs the properties to be used for an LLSession.
     *
     * @param currentUsername the username to log
     * @param currentPassword the password to log
     * @param currentConfig the LLSession extra parameters to log
     */
    private void logProperties(String currentUsername, 
            String currentPassword, LLValue currentConfig) {
        if (LOGGER.isLoggable(Level.FINER)) {
            StringBuffer configString = new StringBuffer(); 
            if (currentConfig == null)
                configString.append("config=null");
            else {
                configString.append("config={"); 
                LLNameEnumeration names = currentConfig.enumerateNames(); 
                while (names.hasMoreElements()) {
                    String name = names.nextElement().toString(); 
                    configString.append(name).append("=").
                        append(currentConfig.toString(name)).
                        append("; "); 
                }
                configString.append("}"); 
            }
            LOGGER.finer("hostname=" + hostname + "; port=" + port +
                "; database=" + database + "; username=" + currentUsername +
                "; password=[" +
                (currentPassword == null ? 0 : currentPassword.length()) +
                "]; useUsernameForWebServer: " + 
                useUsernamePasswordWithWebServer + "; " + configString);
        }
    }
}
