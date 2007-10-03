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

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Date;
import java.text.Format;
import java.text.MessageFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.enterprise.connector.spi.AuthenticationManager;
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

    /** A value type of string in a configuration map. */
    private static final int STRING = 0;

    /**
     * A value type of <code>MessageFormat</code> pattern in a
     * configuration map.
     */
    private static final int PATTERN = 1;

    /** A value type of list of strings in a configuration map. */
    private static final int LIST_OF_STRINGS = 2;

    /**
     * A pattern describing a comma-separated list of unsigned
     * integers, with optional whitespace and brace delimiters. The
     * list may be empty or consist entirely of whitespace. The
     * delimiters do not need to appear in a matching pair.
     */
    private static final Pattern LIST_OF_INTEGERS_PATTERN =
        Pattern.compile("\\s*\\{?\\s*(?:\\d+\\s*(?:,\\s*\\d+\\s*)*)?\\}?\\s*");

    /**
     * A pattern describing a comma-separated list of strings, with
     * optional whitespace and brace delimiters. The list may be empty
     * or consist entirely of whitespace. The delimiters do not need
     * to appear in a matching pair.
     */
    private static final Pattern LIST_OF_STRINGS_PATTERN =
        Pattern.compile("\\s*\\{?\\s*([^{}]*?)\\s*\\}?\\s*");


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
        if (LIST_OF_INTEGERS_PATTERN.matcher(list).matches())
            return list.replaceAll("[\\s{}]", "");
        else {
            RuntimeException e = new IllegalArgumentException(list);
            LOGGER.log(Level.SEVERE, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Sanitizes a list of strings by checking for invalid characters
     * in the string and removing all surrounding whitespace. Leading
     * and trailing whitespace, and whitespace around the commas is
     * removed. Any whitespace inside of non-whitespace strings is
     * kept.
     *
     * @param list a list of comma-separated strings
     * @return the list with surrounding whitespace characters removed
     */
    /* This method has package access so that it can be unit tested. */
    static String sanitizeListOfStrings(String list) {
        Matcher matcher = LIST_OF_STRINGS_PATTERN.matcher(list);
        if (matcher.matches()) {
            String trimmed = matcher.group(1);
            return trimmed.replaceAll("\\s*,\\s*", ",");
        } else {
            RuntimeException e = new IllegalArgumentException(list);
            LOGGER.log(Level.SEVERE, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Simple wrapper for a validate method.
     */
    private static class PropertyValidator {
        void validate() throws IllegalArgumentException {
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

    /** Enables or disables HTTP tunneling. */
    private boolean useHttpTunneling;

    /**
     * The flag indicating that this connector has a separate set of
     * authentication parameters.
     */
    private boolean useSeparateAuthentication;

    /** The base display URL for the search results. */
    private String displayUrl;

    /**
     * The map from subtypes to relative display URL pattern for the
     * search results.
     */
    private Map displayPatterns = new HashMap();

    /** The map from subtypes to Livelink display action names. */
    private Map displayActions = new HashMap();

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

    /** The map from subtypes to ExtendedData assoc keys. */
    private Map extendedDataKeys = new HashMap();

    /** The list of ObjectInfo assoc keys. */
    private String[] objectInfoKeys = null;

    /** The list of VersionInfo assoc keys. */
    private String[] versionInfoKeys = null;

    /** The set of Categories to include. */
    private HashSet includedCategoryIds = null;

    /** The set of Categories to exclude. */
    private HashSet excludedCategoryIds = null;

    /** The set of Subtypes for which we index hidden items. */
    private HashSet hiddenItemsSubtypes = null;

    /** The <code>ContentHandler</code> implementation class. */
    private String contentHandler;

    /** The earliest modification date that should be indexed. */
    private String startDate = null;

    /**
     * If set, this property will be used as initial checkpoint from
     * which the traversal will start, corresponding to the given date
     * with a document ID of zero.
     */
    private String startCheckpoint = null;

    /** The Livelink traverser client username. */
    private String username;

    /** The enableNtlm flag. */
    private boolean enableNtlm;

    /** The httpUsername. */
    private String httpUsername;

    /** The httpPassword. */
    private String httpPassword;    

    /** The Livelink Public Content client username. */
    private String publicContentUsername;

    /** The Livelink Public Content client display URL. */
    private String publicContentDisplayUrl;

    /** The authentication manager to use. */
    private AuthenticationManager authenticationManager;

    /** A list of PropertyValidator instances. */
    private List propertyValidators = new ArrayList(); 

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
     * Sets the host name or IP address of the server.
     *
     * @param server the host name or IP address of the server
     */
    public void setServer(final String server) {
        if (LOGGER.isLoggable(Level.CONFIG))
            LOGGER.config("SERVER: " + server);
        clientFactory.setServer(server);
        propertyValidators.add(new PropertyValidator() {
                void validate() {
                    if (server == null || server.trim().length() == 0) {
                        throw new ConfigurationException(
                            "A host name or IP address is required.", 
                            "missingHost", null);
                    }
                }
        }); 

    }

    /**
     * Sets the Livelink server port. This should be the LAPI
     * port, unless HTTP tunneling is used, in which case it
     * should be the HTTP port.
     *
     * @param port the port number
     */
    public void setPort(final String port) {
        if (LOGGER.isLoggable(Level.CONFIG))
            LOGGER.config("PORT: " + port);
        propertyValidators.add(new PropertyValidator() {
                void validate() {
                    clientFactory.setPort(Integer.parseInt(port));
                }
        }); 
    }

    /**
     * Sets the database connection to use. This property is optional.
     *
     * @param connection the database name
     */
    public void setConnection(String connection) {
        if (LOGGER.isLoggable(Level.CONFIG))
            LOGGER.config("CONNECTION: " + connection);
        clientFactory.setConnection(connection);
    }

    /**
     * Sets the Livelink username.
     *
     * @param username the username
     */
    public void setUsername(String username) {
        if (LOGGER.isLoggable(Level.CONFIG))
            LOGGER.config("USERNAME: " + username);
        clientFactory.setUsername(username);
        this.username = username;
    }

    /**
     * Gets the Livelink username.
     *
     * @return the username
     */
    String getUsername() {
        return username;
    }

    /**
     * Sets the Livelink password.
     *
     * @param password the password
     */
    public void setPassword(String password) {
        if (LOGGER.isLoggable(Level.CONFIG))
            LOGGER.config("PASSWORD: [...]");
        clientFactory.setPassword(password);
    }

    /**
     * Sets a flag indicating that HTTP tunneling is enabled.
     *
     * @param useHttpTunneling <code>true</code> if HTTP tunneling is enabled
     */
    public void setUseHttpTunneling(boolean useHttpTunneling) {
        if (LOGGER.isLoggable(Level.CONFIG))
            LOGGER.config("USE HTTP TUNNELING: " + useHttpTunneling);
        this.useHttpTunneling = useHttpTunneling;
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
     * Sets the HTTPS property. Set to true to use HTTPS when
     * tunneling through a web server. If this property is set to
     * true, the LivelinkCGI property is set, and Livelink Secure
     * Connect is not installed, connections will fail.
     *
     * @param useHttps true if HTTPS should be used; false otherwise
     */
    public void setHttps(boolean useHttps) {
        if (LOGGER.isLoggable(Level.CONFIG))
            LOGGER.config("HTTPS: " + useHttps);
        clientFactory.setHttps(useHttps);
    }

    /**
     * Sets the EnableNTLM property.
     *
     * @param enableNtlm true if the NTLM subsystem should be used
     */
    public void setEnableNtlm(final boolean enableNtlm) {
        if (LOGGER.isLoggable(Level.CONFIG))
            LOGGER.config("ENABLE NTLM: " + enableNtlm);
        clientFactory.setEnableNtlm(enableNtlm);
        this.enableNtlm = enableNtlm;
        propertyValidators.add(new PropertyValidator() {
                void validate() {
                    if (!useHttpTunneling)
                        return;
                    if (enableNtlm &&
                            ((httpUsername == null || 
                                httpUsername.trim().length() == 0) ||
                            (httpPassword == null || 
                                httpPassword.trim().length() == 0))) {
                        throw new ConfigurationException(
                            "An HTTP username and HTTP password are required " +
                            "when NTLM authentication is enabled.",
                            "missingNtlmCredentials", null); 
                    }
                }
            }); 
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
        this.httpUsername = httpUsername;
    }

    /**
     * Sets a password to be used for HTTP authentication when
     * accessing Livelink through a web server.
     *
     * @param httpPassword the password
     */
    public void setHttpPassword(String httpPassword) {
        if (LOGGER.isLoggable(Level.CONFIG))
            LOGGER.config("HTTP PASSWORD: [...]");
        clientFactory.setHttpPassword(httpPassword);
        this.httpPassword = httpPassword;
    }

    /**
     * Sets the VerifyServer property. This property may be used
     * with {@link #setHttps} and {@link #setCaRootCerts}.
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
     * @param domainName the domain name
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
     * @param displayPatternsParam a map from subtypes to relative display
     * URL patterns
     */
    public void setDisplayPatterns(final Map displayPatternsParam) {
        propertyValidators.add(new PropertyValidator() {
                void validate() {
                    setSubtypeMap("DISPLAY PATTERNS", displayPatternsParam,
                        displayPatterns, PATTERN);
                }
            });
    }

    /**
     * Sets the display action for each subtype. The map contains
     * keys that consist of comma-separated subtype integers, or the
     * special string "default". These are mapped to Livelink action
     * names, such as "browse" or "overview".
     *
     * @param displayActionsParam a map from subtypes to Livelink actions
     */
    public void setDisplayActions(final Map displayActionsParam) {
        propertyValidators.add(new PropertyValidator() {
                void validate() {
                    setSubtypeMap("DISPLAY ACTIONS", displayActionsParam,
                        displayActions, STRING);
                }
            });
    }

    /**
     * Converts a user-specified map into a system map. The user map
     * has string keys for each subtype, and the default entry key is
     * "default". The system map has <code>Integer</code> keys for
     * each subtype, and the default entry has a <code>null</code>
     * key. If the user map values are <code>MessageFormat</code>
     * patterns, then the system map will contain
     * <code>MessageFormat</code> instances for those patterns.
     *
     * @param userMap the user map to read from
     * @param systemMap the system map to add converted entries to
     * @param valueType one of the value type constants, <code>STRING</code>,
     * <code>PATTERN</code>, or <code>LIST_OF_STRINGS</code>
     */
    private void setSubtypeMap(String logPrefix, Map userMap,
            Map systemMap, int valueType) {
        if (LOGGER.isLoggable(Level.CONFIG))
            LOGGER.config(logPrefix + ": " + userMap);
        Iterator it = userMap.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry entry = (Map.Entry) it.next();
            String key = (String) entry.getKey();
            Object value = entry.getValue();

            switch (valueType) {
            case STRING:
                break;
            case PATTERN:
                value = getFormat((String) value);
                break;
            case LIST_OF_STRINGS:
                value = sanitizeListOfStrings((String) value).split(",");
                break;
            default:
                throw new AssertionError("This can't happen.");
            }

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
     * @param url the display URL to use; caller may choose default or public
     * @param subType the subtype, used to select a pattern and an action
     * @param volumeId the volume ID of the object
     * @param objectId the object ID of the object
     */
    String getDisplayUrl(String url, int subType, int objectId, int volumeId) {
        Integer subTypeInteger = new Integer(subType);
        MessageFormat mf = (MessageFormat) displayPatterns.get(subTypeInteger);
        if (mf == null)
            mf = (MessageFormat) displayPatterns.get(null);
        Object action = displayActions.get(subTypeInteger);
        if (action == null)
            action = displayActions.get(null);

        StringBuffer buffer = new StringBuffer();
        buffer.append(url);
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
        if (LOGGER.isLoggable(Level.CONFIG))
            LOGGER.config("USE USERNAME WITH WEB SERVER: " + useWeb);
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
        if (LOGGER.isLoggable(Level.CONFIG))
            LOGGER.config("USE SEPARATE AUTHENTICATION CONFIG: " + useAuth);
        useSeparateAuthentication = useAuth;
    }


    // Authentication parameters

    /**
     * Sets the database server type.
     *
     * @param servtypeParam the database server type, either
     * "MSSQL" or "Oracle"; the empty string is also accepted but
     * is ignored
     */
    /*
     * There are strings like "MSSQL70" and "ORACLE80" in the Livelink
     * source, so we'll let those work if someone copies them verbatim
     * from their opentext.ini file.
     */
    public void setServtype(final String servtypeParam) {
        if (servtypeParam == null || servtypeParam.length() == 0)
            return;
        propertyValidators.add(new PropertyValidator() {
                void validate() {
                    if (!servtypeParam.regionMatches(true, 0, "MSSQL", 0, 5) &&
                        !servtypeParam.regionMatches(true, 0, "Oracle", 0, 6)) {
                        throw new IllegalArgumentException(servtypeParam);
                    }
                    servtype = servtypeParam;
                    if (LOGGER.isLoggable(Level.CONFIG))
                        LOGGER.config("SERVTYPE: " + servtype);
                }
            }); 
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
     * Sets the startDate property, and, as a side-effect, the
     * startCheckpoint.
     *
     * @param property is the date string from the config file.
     */
    public void setStartDate(final String property) {
        propertyValidators.add(new PropertyValidator() {
                void validate() {
                    validateStartDate(property);
                }
            }); 
    }

    private void validateStartDate(final String property) {
        // If we've validated already, don't do it again.
        if (startDate != null && startCheckpoint != null)
            return;
        if (property.trim().length() == 0) {
            if (LOGGER.isLoggable(Level.FINE))
                LOGGER.fine("STARTDATE: No start date specified.");
            return;
        }
        Date parsedDate = null; 
        try {
            SimpleDateFormat dateTime = 
                new SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ss"); 
            parsedDate = dateTime.parse(property);
        } catch (ParseException p1) {
            try {
                SimpleDateFormat date = new SimpleDateFormat("yyyy-MM-dd"); 
                parsedDate = date.parse(property);
            } catch (ParseException p2) {
                if (LOGGER.isLoggable(Level.WARNING)) {
                    LOGGER.warning(
                        "STARTDATE: Unable to parse startDate property (\"" +
                        property + "\").  Starting at beginning.");
                }
                return;
            }
        }
        startDate = property;
        startCheckpoint = LivelinkTraversalManager.getCheckpoint(
            parsedDate, 0);
        if (LOGGER.isLoggable(Level.CONFIG))
            LOGGER.config("STARTDATE: " + property); 
    }

    /**
     * Gets the startDate property.
     *
     * @return the startDate
     */
    String getStartDate() {
        return startDate;
    }

    /**
     * Gets the startDate checkpoint.
     *
     * @return the checkpoint string
     */
    String getStartCheckpoint() {
        return startCheckpoint;
    }

    /**
     * Sets the Livelink public content username.
     *
     * @param username the username
     */
    public void setPublicContentUsername(String username) {
        if (LOGGER.isLoggable(Level.CONFIG))
            LOGGER.config("PUBLIC CONTENT USERNAME: " + username);
        if (username != null && username.length() > 0)
            this.publicContentUsername = username;
    }

    /**
     * Gets the Livelink public content username.
     *
     * @return the username
     */
    String getPublicContentUsername() {
        return publicContentUsername;
    }

    /**
     * Sets the Livelink public content display URL.
     *
     * @param username the URL
     */
    public void setPublicContentDisplayUrl(String url) {
        if (LOGGER.isLoggable(Level.CONFIG))
            LOGGER.config("PUBLIC CONTENT DISPLAY URL: " + url);
        if (url != null && url.length() > 0)
            this.publicContentDisplayUrl = url;
    }

    /**
     * Gets the Livelink public content display URL.
     *
     * @return the URL
     */
    String getPublicContentDisplayUrl() {
        return publicContentDisplayUrl;
    }

    /**
     * Sets the node types that you want to exclude from traversal.
     *
     * @param excludedNodeTypesParam the excluded node types
     */
    public void setExcludedNodeTypes(final String excludedNodeTypesParam) {
        propertyValidators.add(new PropertyValidator() {
                void validate() {
                    excludedNodeTypes = 
                        sanitizeListOfIntegers(excludedNodeTypesParam);
                    if (LOGGER.isLoggable(Level.CONFIG)) {
                        LOGGER.config("EXCLUDED NODE TYPES: " + 
                            excludedNodeTypes);
                    }
                }
            }); 
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
     * @param excludedVolumeTypesParam the excluded volume types
     */
    public void setExcludedVolumeTypes(final String excludedVolumeTypesParam) {
        propertyValidators.add(new PropertyValidator() {
                void validate() {
                    excludedVolumeTypes = 
                        sanitizeListOfIntegers(excludedVolumeTypesParam);
                    if (LOGGER.isLoggable(Level.CONFIG)) {
                        LOGGER.config("EXCLUDED VOLUME TYPES: " +
                            excludedVolumeTypes);
                    }
                }
            }); 
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
     * @param excludedLocationNodesParam the excluded node IDs
     */
    public void setExcludedLocationNodes(final String
            excludedLocationNodesParam) {
        propertyValidators.add(new PropertyValidator() {
                void validate() {
                    excludedLocationNodes =
                        sanitizeListOfIntegers(excludedLocationNodesParam);
                    if (LOGGER.isLoggable(Level.CONFIG))
                        LOGGER.config("EXCLUDED NODE IDS: " + 
                            excludedLocationNodes);
                }
            }); 
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
     * @param includedLocationNodesParam the included node IDs
     */
    public void setIncludedLocationNodes(
            final String includedLocationNodesParam) {
        propertyValidators.add(new PropertyValidator() {
                void validate() {
                    includedLocationNodes =
                        sanitizeListOfIntegers(includedLocationNodesParam);
                    if (LOGGER.isLoggable(Level.CONFIG)) {
                        LOGGER.config("INCLUDED NODE IDS: " + 
                            includedLocationNodes);
                    }
                }
            });
    }

    /**
     * Gets the node IDs that you want to include in the traversal.
     *
     * @return the included node IDs
     */
    /* Only valid after init(). */
    String getIncludedLocationNodes() {
        return includedLocationNodes;
    }

    /**
     * Sets the fields from ExtendedData to index for each subtype.
     * The map contains keys that consist of comma-separated subtype
     * integers. The special string "default" is not supported. The
     * values are comma-separated lists of attribute names in the
     * ExtendedData assoc for that subtype.
     *
     * @param extendedDataKeysParam a map from subtypes to
     * ExtendedData assoc keys
     */
    /*
     * XXX: If we support pulling metadata from ExtendedData without
     * constructing HTML content, then it might make sense to support
     * the default key here. See the call to
     * collectExtendedDataProperties in LivelinkDocument...
     *
     * XXX: Note that the lack of support for "default" is not
     * enforced here, but maybe it should be. If there's a null key in
     * the map, it's just ignored. All lookups are done by subtype.
     * (For details, see collectExtendedDataProperties in LivelinkDocument.)
     */
    public void setIncludedExtendedData(final Map extendedDataKeysParam) {
        propertyValidators.add(new PropertyValidator() {
                void validate() {
                    setSubtypeMap("EXTENDEDDATA KEYS", extendedDataKeysParam,
                        extendedDataKeys, LIST_OF_STRINGS);
                }
            });
    }

    /**
     * Gets the fields from ExtendedData to index.
     *
     * @param subType the subtype of the item
     * @return the map from integer subtypes
     */
    String[] getExtendedDataKeys(int subType) {
        if (extendedDataKeys == null)
            return null;
        return (String[]) extendedDataKeys.get(new Integer(subType));
    }


    /**
     * Sets the fields from ObjectInfo to index.
     *
     * @param objectInfoKeysParam a comma-separated list of attributes
     * in the ObjectInfo assoc to include in the index.
     */
    public void setIncludedObjectInfo(final String objectInfoKeysParam) {
        if (LOGGER.isLoggable(Level.CONFIG)) 
            LOGGER.config("INCLUDED OBJECTINFO: " + objectInfoKeysParam);

        if (objectInfoKeysParam != null) {
            propertyValidators.add(new PropertyValidator() {
                void validate() {
                    validateIncludedObjectInfo(objectInfoKeysParam); 
                }
            }); 
        }
    }

    private void validateIncludedObjectInfo(final String objectInfoKeysParam) {

        String sanikeys = sanitizeListOfStrings(
            objectInfoKeysParam);
        if ((sanikeys != null) && (sanikeys.length() > 0)) {
            ArrayList keys = new ArrayList(
                Arrays.asList((Object[]) sanikeys.split(",")));
            Field[] fields = LivelinkTraversalManager.FIELDS;
                        
            for (int i = 0; i < keys.size(); i++) {
                String key = (String) keys.get(i);
                            
                // If the client asks to index all
                // ExtendedData, then don't bother
                // with the selective ExendedData by
                // subtype map.
                // TODO: unless we can guarantee the order in
                // which the properties are set,
                // extendedDataKeys may not have been
                // initialized yet, so it might overwrite the
                // null value set here.
                if ("ExtendedData".equalsIgnoreCase(key)) {
                    extendedDataKeys = null;
                    continue;
                }

                // Many ObjectInfo fields are already
                // indexed by default.  Filter out
                // any duplicates specified here.
                for (int j = 0; j < fields.length; j++) {
                    if ((fields[j].propertyNames.length > 0) &&
                        (fields[j].propertyNames[0].equalsIgnoreCase(key))) {
                        keys.remove(i);
                        i--; 	// ArrayList.remove shuffles everything down.
                        break;
                    }
                }
            }
                        
            // Remember anything left after pruning duplicates.
            if (!keys.isEmpty()) {
                objectInfoKeys = (String[]) keys.
                    toArray(new String[keys.size()]);
            }
        }
    }



    /**
     * Gets the fields from ObjectInfo to index.
     *
     * @return the array of ObjectInfo assoc attribute keys to index.
     */
    public String[] getObjectInfoKeys() {
        return this.objectInfoKeys;
    }


    /**
     * Sets the fields from VersionInfo to index.
     *
     * @param versionInfoKeysParam a comma-separated list of attributes
     * in the VersionInfo assoc to include in the index.
     */
    public void setIncludedVersionInfo(final String versionInfoKeysParam) {
        if (LOGGER.isLoggable(Level.CONFIG)) 
            LOGGER.config("INCLUDED VERSIONINFO: " + versionInfoKeysParam);
        propertyValidators.add(new PropertyValidator() {
            void validate() {
                if (versionInfoKeysParam != null) {
                    String keys = sanitizeListOfStrings(versionInfoKeysParam);
                    if ((keys != null) && (keys.length() > 0))
                        versionInfoKeys = keys.split(",");
                }
            }
            }); 
    }

    /**
     * Gets the fields from VersionInfo to index.
     *
     * @return the array of VersionInfo assoc attribute keys to index.
     */
    String[] getVersionInfoKeys() {
        return this.versionInfoKeys;
    }


    /**
     * Sets the Categories to include.  The value is either a comma-separated
     * list of Category ObjectIDs, or the special keywords: "all", "none",
     * "searchable".  Default is "all,searchable".
     *
     * @param categories a list of Category IDs or one of the special keywords.
     */
    public void setIncludedCategories(final String categories) {
        propertyValidators.add(new PropertyValidator() {
            void validate() {
                includedCategoryIds = parseCategories(categories,
                    "all,searchable");
            }
        });
    }

    /**
     * Returns the set of the Categories to include.
     *
     * @return HashSet of parsed Category IDs and String special keywords.
     */
    HashSet getIncludedCategories() {
        return this.includedCategoryIds;
    }


    /**
     * Sets the Categories to exclude.  The value is either a comma-separated
     * list of Category ObjectIDs, or the special keywords: "all", "none",
     * "searchable".  Default is "none".
     *
     * @param categories a list of Category IDs or one of the special keywords.
     */
    public void setExcludedCategories(final String categories) {
        propertyValidators.add(new PropertyValidator() {
            void validate() {
                excludedCategoryIds = parseCategories(categories, "none");
            }
        });
    }

    /**
     * Returns the set of the Categories to exclude.
     *
     * @return HashSet of parsed Category IDs and String special keywords.
     */
    HashSet getExcludedCategories() {
        return this.excludedCategoryIds;
    }


    /**
     * Parse the list of Category ObjectIDs or special keyword.  Build up a
     * HashSet to quickly look up the items.
     *
     * @param categories a list of Category IDs or one of the special keywords.
     * @return HashSet of parsed Integers or String keywords.
     */
    private HashSet parseCategories(String categories, String defaultStr) {
        String s = sanitizeListOfStrings(categories);
        if ((s == null) || (s.length() == 0))
            s = defaultStr;

        String ids[] = s.split(",");
        HashSet set = new HashSet(ids.length);
        for (int i = 0; i < ids.length; i++) {
            try {
                // If it is an integer, it represents a Category ID
                set.add(Integer.valueOf(ids[i]));
            } catch (NumberFormatException e) {
                // Otherwise, it should be one of the special keywords.
                set.add(ids[i].toLowerCase());
            }
        }
        return set;
    }


    /**
     * Set the rules for handling the ObjectInfo.Catalog.DISPLAYTYPE_HIDDEN
     * attribute for an object.  Specifies whether hidden items are indexed
     * and searchable.  The supplied value is a list of subtypes for which
     * to index hidden items, or the special keywords "true", "false", "all",
     * or "'ALL'",  optionally in braces {}.  The obtuse syntax matches that
     * used in the opentext.ini file.
     *
     * @param hidden comma-separated list of subtypes or special keywords.
     */
    public void setShowHiddenItems(final String hidden) {

        propertyValidators.add(new PropertyValidator() {
            void validate() {
                hiddenItemsSubtypes = new HashSet();
                String s = sanitizeListOfStrings(hidden);
                
                // An empty set here indicates that no hidden
                // content should be indexed.
                if ((s == null) || (s.length() == 0) ||
                        "false".equalsIgnoreCase(s)) {
                    return;
                }
                String ids[] = s.split(",");
                for (int i = 0; i < ids.length; i++) {
                    try {
                        // If it is an integer, it represents a Subtype.
                        hiddenItemsSubtypes.add(Integer.valueOf(ids[i]));
                    } catch (NumberFormatException e) {
                        // Otherwise, it should be one of the special keywords.
                        String word = ids[i].toLowerCase();
                        // "All" in the set means all hidden
                        // content will get indexed.
                        if ("true".equals(word) || "all".equals(word) || 
                                "'all'".equals(word)) {
                            hiddenItemsSubtypes.add("all");
                        }
                        else
                            hiddenItemsSubtypes.add(word);
                    }
                }
            }
        }); 
    }

    /**
     * Return the set of subtypes for which we show hidden items.
     * If the Set is null, then show all hidden items, avoiding an
     * expensive check before processing each document.
     *
     * @return Set of subtypes for which we show hidden items.
     */
    HashSet getShowHiddenItems() {
        return hiddenItemsSubtypes;
    }


    /**
     * Creates an empty client factory instance for use with
     * authentication configuration parameters. This will use the
     * same implementation class as the default client factory.
     */
    /*
     * Assumes that there aren't multiple threads configuring a
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
     * Sets the host name or IP address for authentication. See {@link
     * #setServer}.
     *
     * @param server the host name or IP address of the server
     */
    public void setAuthenticationServer(String server) {
        createAuthenticationClientFactory();
        if (LOGGER.isLoggable(Level.CONFIG))
            LOGGER.config("AUTHENTICATION SERVER: " + server);
        authenticationClientFactory.setServer(server);
    }

    /**
     * Sets the port to use. See {@link #setPort}.
     *
     * @param port the port number
     */
    public void setAuthenticationPort(final String port) {
        createAuthenticationClientFactory();
        if (LOGGER.isLoggable(Level.CONFIG))
            LOGGER.config("AUTHENTICATION PORT: " + port);
        propertyValidators.add(new PropertyValidator() {
                void validate() {
                    authenticationClientFactory.setPort(Integer.parseInt(port));
                }
        }); 
    }

    /**
     * Sets the database connection to use when
     * authenticating. See {@link #setConnection}.
     *
     * @param connection the database name
     */
    public void setAuthenticationConnection(String connection) {
        createAuthenticationClientFactory();
        if (LOGGER.isLoggable(Level.CONFIG))
            LOGGER.config("AUTHENTICATION CONNECTION: " + connection);
        authenticationClientFactory.setConnection(connection);
    }

    /**
     * Sets the HTTPS property. See {@link #setHttps}.
     *
     * @param useHttps true if HTTPS should be used; false otherwise
     */
    public void setAuthenticationHttps(boolean useHttps) {
        createAuthenticationClientFactory();
        if (LOGGER.isLoggable(Level.CONFIG))
            LOGGER.config("AUTHENTICATION HTTPS: " + useHttps);
        authenticationClientFactory.setHttps(useHttps);
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
     * @param domainName the domain name
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

    /**
     * Sets the AuthenticationManager implementation to use.
     *
     * @param authenticationManager an authentication manager
     */
    public void setAuthenticationManager(
            AuthenticationManager authenticationManager) {
        if (LOGGER.isLoggable(Level.CONFIG))
            LOGGER.config("AUTHENTICATION MANAGER: " + authenticationManager);
        this.authenticationManager = authenticationManager;
    }

    /**
     * Gets the <code>ClientFactory</code> for this Connector.
     *
     * @return the <code>ClientFactory</code>
     */
    ClientFactory getClientFactory() {
        return clientFactory;
    }

    /**
     * Gets the <code>ClientFactory</code> for this Connector to
     * use with authentication.
     *
     * @return the <code>ClientFactory</code>
     */
    ClientFactory getAuthenticationClientFactory() {
        if (useSeparateAuthentication && authenticationClientFactory != null)
            return authenticationClientFactory;
        return clientFactory;
    }

    /**
     * Finishes initializing the connector after all properties
     * have been set.
     */
    /*
     * This is essentially a Spring init-method. There's currently
     * no reason to expose it. For the 1.0.2 release of the
     * Connector Manager, this method needs to not be a Spring
     * init-method, because that leads to Spring instantiation
     * failures that are not properly handled.
     */
    private void init() {
        for (int i = 0; i < propertyValidators.size(); i++) {
            ((PropertyValidator) propertyValidators.get(i)).validate(); 
        }

        if (!useHttpTunneling) {
            LOGGER.finer("DISABLING HTTP TUNNELING");
            clientFactory.setLivelinkCgi("");
            clientFactory.setUseUsernamePasswordWithWebServer(false);
        }
        if (publicContentDisplayUrl == null || 
                publicContentDisplayUrl.length() == 0) {
            publicContentDisplayUrl = displayUrl;
        }
        
        // Must be the last thing in this method so that the
        // connector is fully configured when used here.
        if (authenticationManager instanceof ConnectorAware)
            ((ConnectorAware) authenticationManager).setConnector(this); 
    }

    /** {@inheritDoc} */
    public Session login()
            throws RepositoryLoginException, RepositoryException {
        if (LOGGER.isLoggable(Level.FINE))
            LOGGER.fine("LOGIN");

        init(); 

        // Several birds with one stone. Getting the server info
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

        // [task 4046] The connector requires Livelink 9.5 or later.
        if ((majorVersion < 9) || (majorVersion == 9 && minorVersion < 5)) {
            throw new LivelinkException(
                "Livelink 9.5 or later is required.", LOGGER, 
                "unsupportedVersion", new String[] { "9.5" });
        }

        /* [task 4046] Limiting support to 9.5 or greater.
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
        */
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

        return new LivelinkSession(this, clientFactory, authenticationManager);
    }
}
