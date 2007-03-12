// Copyright (C) 2006-2007 Google Inc.

package com.google.enterprise.connector.otex;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import com.google.enterprise.connector.spi.Connector;
import com.google.enterprise.connector.spi.LoginException;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.Session;
import com.google.enterprise.connector.otex.client.Client;
import com.google.enterprise.connector.otex.client.ClientFactory;

public class LivelinkConnector implements Connector {

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
        else
            throw new IllegalArgumentException(list);
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

    /** The display URL prefix for the search results. */
    private String displayUrl;

    /** The open action URL suffix for the search results. */
    private String openAction;

    /** The database server type, either "MSSQL" or "Oracle". */
    private String servtype;
    
    /** The node types that you want to exclude from traversal. */
    private String excludedNodeTypes;

    /** The volume types that you want to exclude from traversal. */
    private String excludedVolumeTypes;

    /** The node IDs that you want to exclude from traversal. */
    private String excludedLocationNodes;

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
     * Sets the display URL prefix for the search results, e.g.,
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
     * Gets the display URL prefix for the search results.
     *
     * @return the display URL prefix
     */
    public String getDisplayUrl() {
        return displayUrl;
    }
    
    /**
     * Sets the open action URL suffix for the search results.
     * The parameters are:
     * <dl>
     * <dt> 0
     * <dd> The default open action, usually <code>"open"</code> but
     * it may vary; e.g., for discussion topics the default action is
     * <code>"view"</code>.
     * <dt> 1
     * <dd> The object ID.
     * <dt> 2 
     * <dd> The volume ID.
     * <dt> 3
     * <dd> The subtype.
     * <dt> 4
     * <dd> The parent object ID.
     * </dl>
     *
     * <p>The default value is "?func=ll&objAction={0}&objId={1}".
     * 
     * @param openAction the open action URL suffix
     */
    public void setOpenAction(String openAction) {
        if (LOGGER.isLoggable(Level.CONFIG))
            LOGGER.config("DISPLAY URL: " + openAction);
        this.openAction = openAction;
    }
    
    /**
     * Gets the open action URL suffix for the search results.
     *
     * @return the open action suffix
     */
    public String getOpenAction() {
        return openAction;
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
    public String getExcludedNodeTypes() {
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
    public String getExcludedVolumeTypes() {
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
    public String getExcludedLocationNodes() {
        return excludedLocationNodes;
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
    public String getContentHandler() {
        return contentHandler;
    }
    
    /** {@inheritDoc} */
    public Session login() throws LoginException, RepositoryException {
        if (LOGGER.isLoggable(Level.FINE))
            LOGGER.fine("LOGIN");
        
        // TODO: move this to a Spring-callable "after all
        // properties have been set" method.
        if (!useSeparateAuthentication)
            authenticationClientFactory = null; 

        // Two birds with one stone. Getting the character encoding
        // from the server verifies connectivity, and we use the
        // result to set the character encoding for future clients.
        Client client = clientFactory.createClient();
        String encoding = client.getEncoding(LOGGER);
        if (encoding != null) {
            if (LOGGER.isLoggable(Level.CONFIG))
                LOGGER.config("ENCODING: " + encoding);
            clientFactory.setEncoding(encoding);
            if (authenticationClientFactory != null)
                authenticationClientFactory.setEncoding(encoding);
        }
        return new LivelinkSession(this, clientFactory, 
            authenticationClientFactory);
    }
}
