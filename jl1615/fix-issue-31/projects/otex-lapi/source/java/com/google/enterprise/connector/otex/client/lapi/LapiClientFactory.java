// Copyright (C) 2007-2008 Google Inc.
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

import java.io.File;
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

    private String server = null;
    private int port = 0;
    private String connection = null;
    private String username = null;
    private String password = null;
    private LLValue config = null;

    private boolean useUsernamePasswordWithWebServer = false;

    private String windowsDomain = null;
    
    /** {@inheritDoc} */
    public void setServer(String value) {
        server = value;
    }

    /** {@inheritDoc} */
    public void setPort(int value) {
        port = value;
    }

    /** {@inheritDoc} */
    public void setConnection(String value) {
        connection = value;
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
    public void setHttps(boolean value) {
        // We've treated this as a boolean, but LAPI wants an integer.
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
    /* TODO: For the beta release, with the connector managers
     * running outside the GSA, support the use of directories as
     * entries in the caRootCerts property. In the future, it
     * might need to be a URL to a location from which we can
     * read the certificates, write them to a file, then read
     * them, since the admin may not have generic filesystem
     * access to the GSA if the connectors are running there.
     */
    public void setCaRootCerts(java.util.List value) {
        LLValue list = new LLValue().setList(); 
        for (int i = 0; i < value.size(); i++) {
            String certEntry = (String) value.get(i); 
            // If the config form property is empty, and a
            // directory is provided in connectorInstance.xml
            // (making the list non-empty), skip the empty
            // string. I don't think it's possible to get a null
            // value in the list, but it won't hurt to check.
            if (certEntry == null || certEntry.length() == 0)
                continue;
            File certDir = new File(certEntry);
            if (certDir.exists() && certDir.isDirectory()) {
                if (LOGGER.isLoggable(Level.CONFIG)) {
                    LOGGER.config("Reading certificates from " +
                        certDir.getAbsolutePath());
                }
                LLValue rootCACertsList = new LLValue();
                LLSession.GetCARootCerts(certDir.getAbsolutePath(),
                    rootCACertsList); 
                int size = rootCACertsList.size();
                if (LOGGER.isLoggable(Level.CONFIG)) 
                    LOGGER.config("Found " + size + " certificates"); 
                for (int j = 0; j < size; j++) 
                    list.add(rootCACertsList.toValue(j)); 
            }
            else {
                LLValue cert = new LLValue().setString(certEntry); 
                list.add(cert);
            }
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

    /** {@inheritDoc} */
    public void setWindowsDomain(String value) {
        windowsDomain = value;
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
        LLSession session = new LLSession(server, port, connection, username,
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
     * let LAPI thrown an exception; there's no "IsFeature" in
     * the Java LAPI client. Rather than work to see if they're
     * present, we'll just copy the config and ensure that
     * they're not.
     *
     * FIXME: Can the username/password ever be null?
     *
     * XXX: We are intentionally shadowing the username and password fields
     * in this method to prevent their accidental use.
     */
    public Client createClient(String originalUsername, String password) {
        // Check for a domain value.
        String username;
        int index = originalUsername.indexOf("@");
        if (index != -1) {
            String user = originalUsername.substring(0, index);
            String domain = originalUsername.substring(index + 1);
            username = domain + '\\' + user;
        } else if (windowsDomain != null && windowsDomain.length() > 0)
            username = windowsDomain + '\\' + originalUsername;
        else
            username = originalUsername;
        if (LOGGER.isLoggable(Level.FINER)) {
            if (username.indexOf('\\') != -1)
                LOGGER.finer("AUTHENTICATE AS: " + username);
        }

        // Copy the config.
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

        // Add the web credentials if needed.
        if (localConfig != null && useUsernamePasswordWithWebServer) {
            localConfig.add("HTTPUserName", username); 
            localConfig.add("HTTPPassword", password); 
        }
        
        logProperties(username, password, localConfig); 
        LLSession session = new LLSession(server, port, connection, 
            username, password, localConfig);
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
                    configString.append(name).append("=");
                    if (name.endsWith("assword"))
                        configString.append("[...]");
                    else
                        configString.append(currentConfig.toString(name));
                    configString.append("; ");
                }
                configString.append("}"); 
            }
            LOGGER.finer("server=" + server + "; port=" + port +
                "; connection=" + connection + "; username=" +
                currentUsername +
                "; password=[...]; useUsernameForWebServer: " + 
                useUsernamePasswordWithWebServer + "; " + configString);
        }
    }
}
