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

import java.text.Format;
import java.text.MessageFormat;
import java.text.NumberFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Map;
import java.util.regex.Pattern;

import com.google.enterprise.connector.spi.Connector;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.RepositoryLoginException;
import com.google.enterprise.connector.spi.Session;
import com.google.enterprise.connector.otex.client.Client;
import com.google.enterprise.connector.otex.client.ClientFactory;
import com.google.enterprise.connector.otex.client.ClientValue;

public class LivelinkConnector implements Connector {
    /** The logger for this class. */
    private static final Logger LOGGER =
        Logger.getLogger(LivelinkConnector.class.getName());

    /**
     * A pattern describing a comma-separated list of unsigned
     * integers, with optional whitespace and brace delimiters. The
     * list may be empty or consist entirely of whitespace. The
     * delimiters do not need to appear in a matching pair.
     */
    private static final Pattern LIST_OF_INTEGERS =
        Pattern.compile("\\s*\\{?\\s*(?:\\d+\\s*(?:,\\s*\\d+\\s*)*)?\\}?\\s*");


    /**
     * Sanitizes a list of integers by checking for invalid characters
     * in the string and removing all whitespace.
     *
     * @param list a list of comma-separated integers
     * @return the list with all whitespace characters removed
     * @throws IllegalArgumentException if the list contains anything
     * but whitespace, digits, or commas
     */
    /* This method has package access so that it can be unit tested. */
    static String sanitizeListOfIntegers(String list) {
        if (LIST_OF_INTEGERS.matcher(list).matches())
            return list.replaceAll("[\\s{}]", "");
        else {
            RuntimeException e = new IllegalArgumentException(list);
            LOGGER.log(Level.SEVERE, e.getMessage(), e);
            throw e;
        }
    }

    
    /**
     * The client factory used to configure and instantiate the
     * client facade.
     */
    private final ClientFactory clientFactory;

    /**
     * The client factory used to configure and instantiate the
     * client facade when a client is needed for authentication;
     * may remain null if no custom authentication configuration
     * is provided.
     */
    private ClientFactory authenticationClientFactory;

    /** The base display URL for the search results. */
    private String displayUrl;

    /**
     * The map from subtypes to relative display URL pattern for the
     * search results.
     */
    private Map displayPatterns = new HashMap(Collections.singletonMap(
        null, getFormat("?func=ll&objId={0}&objAction={3}")));

    /** The map from subtypes to Livelink display action names. */
    private Map displayActions =
        new HashMap(Collections.singletonMap(null, "properties"));

    /**
     * The non-grouping number format for IDs and subtypes in the
     * display URL. This is a non-static field to avoid extra thread
     * contention, and try to balance object creation and
     * synchronization.
     */
    private final NumberFormat nonGroupingNumberFormat;

    /** The database server type, either "MSSQL" or "Oracle". */
    private String servtype;
    
    /** The node types that you want to exclude from traversal. */
    private String excludedNodeTypes;

    /** The volume types that you want to exclude from traversal. */
    private String excludedVolumeTypes;

    /** The node IDs that you want to exclude from traversal. */
    private String excludedLocationNodes;

    /** The node IDs that you want to include in the traversal. */
    private String includedLocationNodes;

    /** The <code>ContentHandler</code> implementation class. */
    private String contentHandler =
        "com.google.enterprise.connector.otex.FileContentHandler";

    /** The flag indicating that this connector has a separate
     * set of authentication parameters. 
     */
    private boolean useSeparateAuthentication;

    
    /**
     * Constructs a connector instance for a specific Livelink
     * repository, using the default client factory class.

     */
    LivelinkConnector() {
        this("com.google.enterprise.connector.otex.client.lapi." +
            "LapiClientFactory");
    }

    /**
     * Constructs a connector instance for a specific Livelink
     * repository, using the specified client factory class.
     */
    /* XXX: Should we throw the relevant checked exceptions instead? */
    LivelinkConnector(String clientFactoryClass) {
        if (LOGGER.isLoggable(Level.CONFIG))
            LOGGER.config("NEW INSTANCE: " + clientFactoryClass);
        try {
            clientFactory = (ClientFactory)
                Class.forName(clientFactoryClass).newInstance();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, e.getMessage(), e);
            throw new RuntimeException(e); // XXX: More specific exception?
        }
        nonGroupingNumberFormat = NumberFormat.getIntegerInstance();
        nonGroupingNumberFormat.setGroupingUsed(false);
    }

    /**
     * Sets the hostname of the Livelink server.
     * 
     * @param hostname the host name to set
     */
    public void setHostname(String hostname) {
        if (LOGGER.isLoggable(Level.CONFIG))
            LOGGER.config("HOSTNAME: " + hostname);
        clientFactory.setHostname(hostname);
    }

    /**
     * Sets the Livelink server port. This should be the LAPI
     * port, unless HTTP tunneling is used, in which case it
     * should be the HTTP port.
     * 
     * @param port the port number to set
     */
    public void setPort(int port) {
        if (LOGGER.isLoggable(Level.CONFIG))
            LOGGER.config("PORT: " + port);
        clientFactory.setPort(port);
    }

    /**
     * Sets the database connection to use. This property is optional.
     * 
     * @param database the database name to set
     */
    public void setDatabase(String database) {
        if (LOGGER.isLoggable(Level.CONFIG))
            LOGGER.config("DATABASE: " + database);
        clientFactory.setDatabase(database);
    }

    /**
     * Sets the Livelink username.
     * 
     * @param username the username to set
     */
    public void setUsername(String username) {
        if (LOGGER.isLoggable(Level.CONFIG))
            LOGGER.config("USERNAME: " + username);
        clientFactory.setUsername(username);
    }

    /**
     * Sets the Livelink password.
     * 
     * @param password the password to set
     */
    public void setPassword(String password) {
        if (LOGGER.isLoggable(Level.CONFIG))
            LOGGER.config("PASSWORD: " + password);
        clientFactory.setPassword(password);
    }

    /**
     * Sets the UseHTTPS property. Set to true to use HTTPS when
     * tunneling through a web server. The default value for this
     * property is "true". If this property is set to true, the
     * LivelinkCGI property is set, and Livelink Secure Connect
     * is not installed, connections will fail.
     *
     * @param useHttps true if HTTPS should be used; false otherwise
     */
    public void setUseHttps(boolean useHttps) {
        if (LOGGER.isLoggable(Level.CONFIG))
            LOGGER.config("USE HTTPS: " + useHttps);
        clientFactory.setUseHttps(useHttps);
    }

    /**
     * Sets the EnableNTLM property. The default Livelink value is true.
     *
     * @param enableNtlm true if the NTLM subsystem should be used
     */
    public void setEnableNtlm(boolean enableNtlm) {
        if (LOGGER.isLoggable(Level.CONFIG))
            LOGGER.config("ENABLE NTLM: " + enableNtlm);
        clientFactory.setEnableNtlm(enableNtlm);
    }


    /**
     * Sets the Livelink CGI path to use when tunneling LAPI
     * requests through the Livelink web server. If a proxy
     * server is used, this value must be the complete URL to the
     * Livelink CGI (e.g.,
     * http://host:port/Livelink/livelink). If no proxy server is
     * being used, only the path needs to be provided (e.g.,
     * /Livelink/livelink).
     *
     * @param livelinkCgi the path or URL to the Livelink CGI 
     */
    public void setLivelinkCgi(String livelinkCgi) {
        if (LOGGER.isLoggable(Level.CONFIG))
            LOGGER.config("LIVELINK CGI: " + livelinkCgi);
        clientFactory.setLivelinkCgi(livelinkCgi);
    }

    /**
     * Sets a username to be used for HTTP authentication when
     * accessing Livelink through a web server.
     *
     * @param httpUsername the username
     */
    public void setHttpUsername(String httpUsername) {
        if (LOGGER.isLoggable(Level.CONFIG))
            LOGGER.config("HTTP USERNAME: " + httpUsername);
        clientFactory.setHttpUsername(httpUsername);
    }

    /**
     * Sets a password to be used for HTTP authentication when
     * accessing Livelink through a web server.
     *
     * @param httpPassword the password
     */
    public void setHttpPassword(String httpPassword) {
        if (LOGGER.isLoggable(Level.CONFIG))
            LOGGER.config("HTTP PASSWORD: " + httpPassword);
        clientFactory.setHttpPassword(httpPassword);
    }

    /**
     * Sets the VerifyServer property. This property may be used
     * with {@link #setUseHttps} and {@link #setCaRootCerts}.
     *
     * @param verifyServer true if the server certificate should
     * be verified
     */
    public void setVerifyServer(boolean verifyServer) {
        if (LOGGER.isLoggable(Level.CONFIG))
            LOGGER.config("VERIFY SERVER: " + verifyServer);
        clientFactory.setVerifyServer(verifyServer);
    }

    /**
     * Sets the CaRootCerts property.
     *
     * @param caRootCerts a list of certificate authority root certificates
     */
    public void setCaRootCerts(List caRootCerts) {
        if (LOGGER.isLoggable(Level.CONFIG))
            LOGGER.config("CA ROOT CERTS: " + caRootCerts);
        clientFactory.setCaRootCerts(caRootCerts);
    }

    /**
     * Sets the Livelink domain name. This property is optional.
     * 
     * @param domainName the domain name to set
     */
    public void setDomainName(String domainName) {
        if (LOGGER.isLoggable(Level.CONFIG))
            LOGGER.config("DOMAIN NAME: " + domainName);
        clientFactory.setDomainName(domainName);
    }

    /**
     * Sets the base display URL for the search results, e.g.,
     * "http://myhostname/Livelink/livelink.exe".
     * 
     * @param displayUrl the display URL prefix
     */
    public void setDisplayUrl(String displayUrl) {
        if (LOGGER.isLoggable(Level.CONFIG))
            LOGGER.config("DISPLAY URL: " + displayUrl);
        this.displayUrl = displayUrl;
    }
    
    /**
     * Gets the base display URL for the search results.
     *
     * @return the display URL prefix
     */
    String getDisplayUrl() {
        return displayUrl;
    }
    
    /**
     * Sets the relative display URL for each subtype. The map
     * contains keys that consist of comma-separated subtype integers,
     * or the special string "default". These are mapped to a {@link
     * java.text.MessageFormat MessageFormat} pattern. The pattern
     * arguments are:
     * <dl>
     * <dt> 0
     * <dd> The object ID.
     * <dt> 1
     * <dd> The volume ID.
     * <dt> 2 
     * <dd> The subtype.
     * <dt> 3
     * <dd> The display action, which varies by subtype and is
     * configured by {@link #setDisplayActions}.
     * </dl>
     *
     * <p>The default value is a map with one entry mapping "default"
     * to "?func=ll&objId={0}&objAction={3}".
     * 
     * @param displayPatterns a map from subtypes to relative display
     * URL patterns
     */
    public void setDisplayPatterns(Map displayPatterns) {
        setDisplayMap("DISPLAY PATTERNS", displayPatterns,
            this.displayPatterns, true);
    }
    
    /**
     * Sets the display action for each subtype. The map contains
     * keys that consist of comma-separated subtype integers, or the
     * special string "default". These are mapped to Livelink action
     * names, such as "browse" or "overview".
     * 
     * <p>The default value is a map with one entry mapping "default"
     * to "properties".
     *
     * @param displayActions a map from subtypes to Livelink actions
     */
    public void setDisplayActions(Map displayActions) {
        setDisplayMap("DISPLAY ACTIONS", displayActions,
            this.displayActions, false);
    }

    /**
     * Converts a user-specified map into a system map. The user map
     * has string keys for each subtype, and the default entry key is
     * "default." The system map has <code>Integer</code> keys for
     * each subtype, and the default entry has a <code>null</code>
     * key. If the user map values are <code>MessageFormat</code>
     * patterns, then the system map will contain
     * <code>MessageFormat</code> instances for those patterns.
     *
     * @param userMap the user map to read from
     * @param systemMap the system map to add converted entries to
     * @param isPatterns <code>true</code> if the map values are
     * <code>MessageFormat</code> patterns, or <code>false</code>
     * otherwise
     */
    private void setDisplayMap(String logPrefix, Map userMap,
            Map systemMap, boolean isPatterns) {
        if (LOGGER.isLoggable(Level.CONFIG))
            LOGGER.config(logPrefix + ": " + userMap);
        Iterator it = userMap.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry entry = (Map.Entry) it.next();
            String key = (String) entry.getKey();
            Object value = entry.getValue();
            if (isPatterns)
                value = getFormat((String) value);
            if ("default".equals(key))
                systemMap.put(null, value);
            else {
                String[] subtypes = sanitizeListOfIntegers(key).split(",");
                for (int i = 0; i < subtypes.length; i++)
                    systemMap.put(new Integer(subtypes[i]), value);
            }
        }
        if (LOGGER.isLoggable(Level.FINE))
            LOGGER.fine(logPrefix + " PARSED: " + systemMap);
    }

    /**
     * We don't control the relative display URL patterns. If the
     * patterns do not specify the formats to be used for the IDs, we
     * specify a format that does not use grouping. Note that the
     * formats array is not complete, but it has entries up to the
     * highest argument actually used in the pattern.
     *
     * @param pattern a <code>MessageFormat</code> pattern
     * @see #setDisplayPatterns
     */
    private MessageFormat getFormat(String pattern) {
        MessageFormat mf = new MessageFormat(pattern);
        Format[] formats = mf.getFormatsByArgumentIndex();
        for (int i = 0; i < 3 && i < formats.length; i++) {
            if (formats[i] == null)
                mf.setFormatByArgumentIndex(i, nonGroupingNumberFormat);
        }
        return mf;
    }
    
    /**
     * Gets the display URL for the google:displayurl property. This
     * assembles the base display URL and the subtype-specific
     * relative display URL pattern, which may use the
     * subtype-specific display action.
     *
     * @param subType the subtype, used to select a pattern and an action
     * @param volumeId the volume ID of the object
     * @param objectId the object ID of the object
     */
    String getDisplayUrl(int subType, int objectId, int volumeId) {
        Integer subTypeInteger = new Integer(subType);
        MessageFormat mf = (MessageFormat) displayPatterns.get(subTypeInteger);
        if (mf == null)
            mf = (MessageFormat) displayPatterns.get(null);
        Object action = displayActions.get(subTypeInteger);
        if (action == null)
            action = displayActions.get(null);

        StringBuffer buffer = new StringBuffer();
        buffer.append(displayUrl);
        Object[] args = { new Integer(objectId), new Integer(volumeId),
            subTypeInteger, action };
        synchronized (displayPatterns) {
            mf.format(args, buffer, null);
        }
        return buffer.toString();
    }

    /**
     * Sets a property which indicates that any username and
     * password values which need to be authenticated should be
     * used as the HTTP username and password values.
     *
     * @param useWeb true if the username and password should be
     * used for HTTP authentication
     */
    public void setUseUsernamePasswordWithWebServer(boolean useWeb) {
        if (LOGGER.isLoggable(Level.CONFIG)) {
            LOGGER.config("USE USERNAME WITH WEB SERVER: " + 
                useWeb);
        }
        clientFactory.setUseUsernamePasswordWithWebServer(useWeb);
    }

    /**
     * Sets a flag indicating that a separate set of
     * authentication parameters will be provided.
     *
     * @param useAuth true if a separate set of authentication
     * parameters will be provided
     */
    public void setUseSeparateAuthentication(boolean useAuth) {
        if (LOGGER.isLoggable(Level.CONFIG)) {
            LOGGER.config("USE SEPARATE AUTHENTICATION CONFIG: " + 
                useAuth);
        }
        useSeparateAuthentication = useAuth;
    }


    // Authentication parameters

    /**
     * Sets the database server type.
     * 
     * @param servtype the database server type, either "MSSQL" or "Oracle";
     *     the empty string is also accepted but is ignored
     */
    /*
     * There are strings like "MSSQL70" and "ORACLE80" in the Livelink
     * source, so we'll let those work if someone copies them verbatim
     * from their opentext.ini file.
     */
    public void setServtype(String servtype) {
        if (servtype == null || servtype.length() == 0)
            return;
        if (!servtype.regionMatches(true, 0, "MSSQL", 0, 5) &&
                !servtype.regionMatches(true, 0, "Oracle", 0, 6)) {
            throw new IllegalArgumentException(servtype);
        }
        this.servtype = servtype;
        if (LOGGER.isLoggable(Level.CONFIG))
            LOGGER.config("SERVTYPE: " + this.servtype);
    }
    
    /**
     * Gets the database server type, either "MSSQL" or "Oracle", or
     * <code>null</code> if the database type is not configured.
     *
     * @return the database server type
     */
    String getServtype() {
        return servtype;
    }

    /**
     * Sets the node types that you want to exclude from traversal.
     * 
     * @param excludedNodeTypes the excluded node types
     */
    public void setExcludedNodeTypes(String excludedNodeTypes) {
        this.excludedNodeTypes = sanitizeListOfIntegers(excludedNodeTypes);
        if (LOGGER.isLoggable(Level.CONFIG))
            LOGGER.config("EXCLUDED NODE TYPES: " + this.excludedNodeTypes);
    }
    
    /**
     * Gets the node types that you want to exclude from traversal.
     *
     * @return the excluded node types
     */
    String getExcludedNodeTypes() {
        return excludedNodeTypes;
    }
    
    /**
     * Sets the volume types that you want to exclude from traversal.
     * 
     * @param excludedVolumeTypes the excluded volume types
     */
    public void setExcludedVolumeTypes(String excludedVolumeTypes) {
        this.excludedVolumeTypes = sanitizeListOfIntegers(excludedVolumeTypes);
        if (LOGGER.isLoggable(Level.CONFIG)) {
            LOGGER.config("EXCLUDED VOLUME TYPES: " +
                this.excludedVolumeTypes);
        }
    }
    
    /**
     * Gets the volume types that you want to exclude from traversal.
     *
     * @return the excluded volume types
     */
    String getExcludedVolumeTypes() {
        return excludedVolumeTypes;
    }
    
    /**
     * Sets the node IDs that you want to exclude from traversal.
     * 
     * @param excludedLocationNodes the excluded node IDs
     */
    public void setExcludedLocationNodes(String excludedLocationNodes) {
        this.excludedLocationNodes =
            sanitizeListOfIntegers(excludedLocationNodes);
        if (LOGGER.isLoggable(Level.CONFIG))
            LOGGER.config("EXCLUDED NODE IDS: " + this.excludedLocationNodes);
    }
    
    /**
     * Gets the node IDs that you want to exclude from traversal.
     *
     * @return the excluded node IDs
     */
    String getExcludedLocationNodes() {
        return excludedLocationNodes;
    }
    
    /**
     * Sets the node IDs that you want to included in the traversal.
     * 
     * @param includedLocationNodes the included node IDs
     */
    public void setIncludedLocationNodes(String includedLocationNodes) {
        this.includedLocationNodes =
            sanitizeListOfIntegers(includedLocationNodes);
        if (LOGGER.isLoggable(Level.CONFIG))
            LOGGER.config("INCLUDED NODE IDS: " + this.includedLocationNodes);
    }
    
    /**
     * Gets the node IDs that you want to include in the traversal.
     *
     * @return the included node IDs
     */
    String getIncludedLocationNodes() {
        return includedLocationNodes;
    }
    
    /**
     * Creates an empty client factory instance for use with
     * authentication configuration parameters. This will use the
     * same implementation class as the default client factory.
     */
    /* Assumes that there aren't multiple threads configuring a 
     * single LivelinkConnector instance. 
     */
    private void createAuthenticationClientFactory() {
        if (authenticationClientFactory == null) {
            if (LOGGER.isLoggable(Level.CONFIG)) {
                LOGGER.config("NEW AUTHENTICATION INSTANCE: " + 
                    clientFactory.getClass().getName());
            }
            try {
                authenticationClientFactory = (ClientFactory)
                    clientFactory.getClass().newInstance();
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, e.getMessage(), e);
                throw new RuntimeException(e); // XXX: More specific exception?
            }
        }
    }

    /**
     * Sets the hostname for authentication. See {@link #setHostname}. 
     * 
     * @param hostname the host name to set
     */
    public void setAuthenticationHostname(String hostname) {
        createAuthenticationClientFactory();
        if (LOGGER.isLoggable(Level.CONFIG))
            LOGGER.config("AUTHENTICATION HOSTNAME: " + hostname);
        authenticationClientFactory.setHostname(hostname);
    }

    /**
     * Sets the port to use. See {@link #setPort}. 
     * 
     * @param port the port number to set
     */
    public void setAuthenticationPort(int port) {
        createAuthenticationClientFactory();
        if (LOGGER.isLoggable(Level.CONFIG))
            LOGGER.config("AUTHENTICATION PORT: " + port);
        authenticationClientFactory.setPort(port);
    }

    /**
     * Sets the database connection to use when
     * authenticating. See {@link #setDatabase}. 
     * 
     * @param database the database name to set
     */
    public void setAuthenticationDatabase(String database) {
        createAuthenticationClientFactory();
        if (LOGGER.isLoggable(Level.CONFIG))
            LOGGER.config("AUTHENTICATION DATABASE: " + database);
        authenticationClientFactory.setDatabase(database);
    }

    /**
     * Sets the UseHTTPS property. See {@link #setUseHttps}. 
     *
     * @param useHttps true if HTTPS should be used; false otherwise
     */
    public void setAuthenticationUseHttps(boolean useHttps) {
        createAuthenticationClientFactory();
        if (LOGGER.isLoggable(Level.CONFIG))
            LOGGER.config("AUTHENTICATION USE HTTPS: " + useHttps);
        authenticationClientFactory.setUseHttps(useHttps);
    }

    /**
     * Sets the EnableNTLM property. See {@link @setEnableNtlm}. 
     *
     * @param verifyServer true if the NTLM subsystem should be used
     */
    public void setAuthenticationEnableNtlm(boolean enableNtlm) {
        createAuthenticationClientFactory();
        if (LOGGER.isLoggable(Level.CONFIG))
            LOGGER.config("AUTHENTICATION ENABLE NTLM: " + enableNtlm);
        authenticationClientFactory.setEnableNtlm(enableNtlm);
    }

    /**
     * Sets the Livelink CGI path for use when tunneling requests
     * through a web server. See {@link #setLivelinkCgi}. 
     *
     * @param livelinkCgi the Livelink CGI path or URL
     */
    public void setAuthenticationLivelinkCgi(String livelinkCgi) {
        createAuthenticationClientFactory();
        if (LOGGER.isLoggable(Level.CONFIG))
            LOGGER.config("AUTHENTICATION LIVELINK CGI: " + livelinkCgi);
        authenticationClientFactory.setLivelinkCgi(livelinkCgi);
    }

    /**
     * Sets the Verify Server property. See {@link @setVerifyServer}. 
     *
     * @param verifyServer true if the server certificate should
     * be verified
     */
    public void setAuthenticationVerifyServer(boolean verifyServer) {
        createAuthenticationClientFactory();
        if (LOGGER.isLoggable(Level.CONFIG))
            LOGGER.config("AUTHENTICATION VERIFY SERVER: " + verifyServer);
        authenticationClientFactory.setVerifyServer(verifyServer);
    }

    /**
     * Sets the CaRootCerts property. See {@link #setCaRootCerts}. 
     *
     * @param caRootCerts a list of certificate authority root certificates
     */
    public void setAuthenticationCaRootCerts(List caRootCerts) {
        createAuthenticationClientFactory();
        if (LOGGER.isLoggable(Level.CONFIG))
            LOGGER.config("AUTHENTICATION CA ROOT CERTS: " + caRootCerts);
        authenticationClientFactory.setCaRootCerts(caRootCerts);
    }

    /**
     * Sets the Livelink domain name. See {@link #setDomainName}. 
     * 
     * @param domainName the domain name to set
     */
    public void setAuthenticationDomainName(String domainName) {
        createAuthenticationClientFactory();
        if (LOGGER.isLoggable(Level.CONFIG))
            LOGGER.config("AUTHENTICATION DOMAIN NAME: " + domainName);
        authenticationClientFactory.setDomainName(domainName);
    }

    /**
     * Sets a property which indicates that any username and
     * password values which need to be authenticated should be
     * used as the HTTP username and password values.
     *
     * @param useWeb true if the username and password should be
     * used for HTTP authentication
     */
    public void setAuthenticationUseUsernamePasswordWithWebServer(
            boolean useWeb) {
        createAuthenticationClientFactory();
        if (LOGGER.isLoggable(Level.CONFIG)) {
            LOGGER.config("AUTHENTICATION USE USERNAME WITH WEB SERVER: " + 
                useWeb);
        }
        authenticationClientFactory.setUseUsernamePasswordWithWebServer(
            useWeb);
    }

    /**
     * Sets the concrete implementation for the
     * <code>ContentHandler</code> interface.
     *
     * @param contentHandler the fully-qualified name of the
     * <code>ContentHandler</code> implementation to use
     */
    public void setContentHandler(String contentHandler) {
        if (LOGGER.isLoggable(Level.CONFIG))
            LOGGER.config("CONTENT HANDLER: " + contentHandler);
        this.contentHandler = contentHandler;
    }
    
    /**
     * Gets the <code>ContentHandler</code> implementation class.
     *
     * @return the fully-qualified name of the <code>ContentHandler</code>
     * implementation to use
     */
    String getContentHandler() {
        return contentHandler;
    }
    
    /** {@inheritDoc} */
    public Session login()
            throws RepositoryLoginException, RepositoryException {
        if (LOGGER.isLoggable(Level.FINE))
            LOGGER.fine("LOGIN");
        
        // TODO: move this to a Spring-callable "after all
        // properties have been set" method.
        if (!useSeparateAuthentication)
            authenticationClientFactory = null; 

        // Serveral birds with one stone. Getting the server info
        // verifies connectivity with the server, and we use the
        // results to set the character encoding for future clients
        // and confirm the availability of the overview action.
        Client client = clientFactory.createClient();
        ClientValue serverInfo = client.GetServerInfo();
        boolean hasCharacterEncoding; // Set below.

        // Get the server version, which is a string like "9.5.0".
        String serverVersion = serverInfo.toString("ServerVersion");
        if (LOGGER.isLoggable(Level.CONFIG))
            LOGGER.config("SERVER VERSION: " + serverVersion);
        String[] versions = serverVersion.split("\\.");
        int majorVersion;
        int minorVersion;
        if (versions.length >= 2) {
            majorVersion = Integer.parseInt(versions[0]);
            minorVersion = Integer.parseInt(versions[1]);
        } else {
            if (LOGGER.isLoggable(Level.WARNING)) {
                LOGGER.warning(
                    "Unable to parse Livelink server version; assuming 9.5.");
            }
            majorVersion = 9;
            minorVersion = 5;
        }

        // The connector requires Livelink 9.0 or later.
        if (majorVersion < 9) {
            throw new RepositoryException(
                "Livelink 9.0 or later is required.");
        }
        
        // Check for Livelink 9.2 or earlier; omit excluded volumes
        // and locations if needed. XXX: The DTreeAncestors table was
        // introduced in Livelink 9.5. Just bailing like this is weak,
        // but Livelink 9.2 is not officially supported, so there are
        // limits to how much work is worthwhile here.
        if (majorVersion == 9 && minorVersion <= 2) {
            if (LOGGER.isLoggable(Level.WARNING)) {
                LOGGER.warning("ExcludedVolumeTypes and " +
                    "ExcludedLocationNodes are not supported under " +
                    "Livelink 9.2 or earlier.");
            }
            excludedVolumeTypes = null;
            excludedLocationNodes = null;

            // Livelink 9.2 does not return the CharacterEncoding field.
            hasCharacterEncoding = false;
        } else
            hasCharacterEncoding = true;

        // Check for Livelink 9.5 or earlier; change overview action
        // if needed. We only check for the default entry, because if
        // the map has been customized, perhaps the user knows what
        // they are doing. If a user has a Livelink 9.5 with an custom
        // overview action, they can configure that in the
        // displayPatterns and we won't see it. We are leaving the
        // modified entry in the map rather than removing it and
        // relying on the default action because there are lots and
        // lots of documents, and this avoids a map lookup miss in
        // getDisplayUrl for every one of them.
        if (majorVersion == 9 && minorVersion <= 5) {
            Integer docSubType = new Integer(144);
            Object action = displayActions.get(docSubType);
            if ("overview".equals(action))
                displayActions.put(docSubType, "properties");
        }
        
        // Set the character encodings in the client factories if needed.
        if (hasCharacterEncoding) {
            int serverEncoding = serverInfo.toInteger("CharacterEncoding");
            if (serverEncoding == Client.CHARACTER_ENCODING_UTF8) {
                LOGGER.config("ENCODING: UTF-8");
                clientFactory.setEncoding("UTF-8");
                if (authenticationClientFactory != null)
                    authenticationClientFactory.setEncoding("UTF-8");
            }
        }

        return new LivelinkSession(this, clientFactory, 
            authenticationClientFactory);
    }
}
