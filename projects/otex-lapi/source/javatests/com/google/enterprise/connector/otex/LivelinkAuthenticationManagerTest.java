// Copyright (C) 2006-2007 Google Inc.

package com.google.enterprise.connector.otex;

import java.lang.reflect.Method;
import junit.framework.TestCase;

import com.google.enterprise.connector.spi.RepositoryException;


/**
 * Tests the LivelinkAuthenticationManager.
 */
/* This test depends on externally-configured Livelink connection
 * properties. 
 */
public class LivelinkAuthenticationManagerTest extends TestCase {

    /** The Connector. */
    private LivelinkConnector conn;

    /** The Session. */
    private LivelinkSession session;

    /** The AuthenticationManager. */
    private LivelinkAuthenticationManager authManager;
        

    /**
     * Establishes a session and obtains an AuthenticationManager
     * for testing. 
     *
     * @throws RepositoryException if login fails
     */
    public void setUp() throws RepositoryException {
        conn = new LivelinkConnector();
    }


    /**
     * Tests authenticating with LAPI.
     *
     * @throws Exception if an error occurs
     */
    public void testLapi() throws Exception {
        setUpLapi();
        assertTrue("Valid user",
            authenticate("llglobal", "Gibson")); 
        assertFalse("Valid user, invalid password", 
            authenticate("llglobal", "foo")); 
        assertFalse("Invalid user", 
            authenticate("foo", "foo")); 
    }


    /**
     * Tests authenticating with LAPI through the Livelink CGI process. 
     *
     * @throws Exception if an error occurs
     */
    public void testCgi() throws Exception {
        setUpCgi();
        assertTrue("Valid user", 
            authenticate("llglobal", "Gibson")); 
        assertFalse("Valid user, invalid password", 
            authenticate("llglobal", "foo")); 
        assertFalse("Invalid user", 
            authenticate("foo", "foo")); 
    }


    /**
     * Tests authenticating with LAPI through a proxy to the
     * Livelink CGI.
     *
     * @throws Exception if an error occurs
     */
    public void testProxy() throws Exception {
        setUpProxy();
        assertTrue("Valid user", 
            authenticate("llglobal", "Gibson")); 
        assertFalse("Valid user, invalid password", 
            authenticate("llglobal", "foo")); 
        assertFalse("Invalid user", 
            authenticate("foo", "foo")); 
    }


    /**
     * Tests using the Directory-Services enabled web server with
     * a DS Livelink instance.
     *
     * @throws Exception if an error occurs
     */
    public void testDirectoryServicesExternal() throws Exception {
        setUpDsExternal();
        assertTrue("Valid user", 
            authenticate("llglobal-external", "Gibson")); 
        assertFalse("Valid user, invalid password", 
            authenticate("llglobal-external", "foo")); 
        assertFalse("Invalid user",
            authenticate("foo", "foo")); 
        assertFalse("Internal user who shouldn't be able to log in " +
            "through the external web server", 
            authenticate("llglobal", "Gibson")); 
    }


    /**
     * Tests using the DirectoryServices-enabled Livelink through
     * the non-authenticated web server.
     *
     * @throws Exception if an error occurs
     */
    public void testDirectoryServicesInternal() throws Exception {
        setUpDsInternal();

        assertTrue("Valid user", 
            authenticate("Admin", "livelink")); 
        assertFalse("Valid user, invalid password", 
            authenticate("Admin", "foo")); 
        assertFalse("Invalid user",
            authenticate("foo", "foo")); 
        assertFalse("External user who shouldn't be able to log in " +
            "through the internal web server", 
            authenticate("llglobal-external", "Gibson")); 
    }


    private boolean authenticate(String username, String password) {
        try {
            return authManager.authenticate(username, password);
        }
        catch (RepositoryException e) {
            System.out.println(getName() + " " + e.getMessage()); 
            return false;
        }
    }



    /**
     * Creates the Session and AuthenticationManager.
     *
     * @throws RepositoryException if an error occurs
     */
    private void finishSetup() throws RepositoryException {
        session = (LivelinkSession) conn.login();
        authManager = (LivelinkAuthenticationManager) session.
            getAuthenticationManager();
    }
        

    /**
     * Sets the properties for direct LAPI connections.
     */
    private void setUpLapi() throws Exception {
        setProperties("authenticate-lapi."); 
        finishSetup();
    }


    /**
     * Sets the properties for Livelink CGI connections.
     */
    private void setUpCgi() throws Exception {
        setProperties("authenticate-cgi."); 
        finishSetup();
    }


    /**
     * Sets the properties for authentication through a proxy in
     * front of the Livelink CGI.
     */
    private void setUpProxy() throws Exception {
        setProperties("authenticate-proxy."); 
        finishSetup();
    }


    /**
     * Sets the properties for authentication through a Livelink
     * Directory Services-enabled web server.
     */
    private void setUpDsExternal() throws Exception {
        setProperties("authenticate-ds-external."); 
        finishSetup();
    }


    /**
     * Sets the properties for authentication through the
     * non-Directory Services enabled web server for a DS-enabled
     * instance.
     */
    private void setUpDsInternal() throws Exception {
        setProperties("authenticate-ds-internal."); 
        finishSetup();
    }


    /**
     * Reads the properties and uses reflection to call the
     * appropriate setters on the LivelinkConnector object.
     *
     * @param prefix the property prefix identifying the set of
     * properties to read
     */
    private void setProperties(String prefix) throws Exception {
        Class[] stringParam = { String.class };
        Class[] booleanParam = { boolean.class }; 
        Class[] intParam = { int.class }; 
        ConnProperty[] properties = {
            new ConnProperty("hostname", "setHostname", stringParam), 
            new ConnProperty("port", "setPort", intParam),
            new ConnProperty("database", "setDatabase", stringParam),
            new ConnProperty("username", "setUsername", stringParam), 
            new ConnProperty("password", "setPassword", stringParam), 
            new ConnProperty("domainName", "setDomainName", stringParam),
            new ConnProperty("encoding", "setEncoding", stringParam), 
            new ConnProperty("livelinkCgi", "setLivelinkCgi", stringParam),
            new ConnProperty("httpUsername", "setHttpUsername", stringParam),
            new ConnProperty("httpPassword", "setHttpPassword", stringParam),
            new ConnProperty("useHttps", "setUseHttps", booleanParam),
            new ConnProperty("verifyServer", "setVerifyServer", booleanParam),
            new ConnProperty("enableNtlm", "setEnableNtlm", booleanParam), 
            new ConnProperty("useUsernamePasswordWithWebServer",
                "setUseUsernamePasswordWithWebServer", booleanParam), 

            // Authentication parameters
            new ConnProperty("authenticationHostname", 
                "setAuthenticationHostname", stringParam), 
            new ConnProperty("authenticationPort", 
                "setAuthenticationPort", intParam),
            new ConnProperty("authenticationDatabase", 
                "setAuthenticationDatabase", stringParam),
            new ConnProperty("authenticationDomainName", 
                "setAuthenticationDomainName", stringParam),
            new ConnProperty("authenticationLivelinkCgi",
                "setAuthenticationLivelinkCgi", stringParam),
            new ConnProperty("authenticationUseHttps",
                "setAuthenticationUseHttps", booleanParam),
            new ConnProperty("authenticationVerifyServer", 
                "setAuthenticationVerifyServer", booleanParam),
            new ConnProperty("authenticationEnableNtlm",
                "setAuthenticationEnableNtlm", booleanParam), 
            new ConnProperty("authenticationUseUsernamePasswordWithWebServer",
                "setAuthenticationUseUsernamePasswordWithWebServer",
                booleanParam) 
        }; 

        for (int i = 0; i < properties.length; i++) {
            ConnProperty p = properties[i];
            String value = System.getProperty(prefix + p.name);
            if (value == null)
                continue;
            Object[] args = null; 
            if (p.params == stringParam)
                args = new String[] { value };
            else if (p.params == intParam)
                args = new Integer[] { new Integer(value) };
            else if (p.params == booleanParam)
                args = new Boolean[] { new Boolean(value) };

            Method m = conn.getClass().getMethod(p.method, p.params);
            m.invoke(conn, args); 
        }
    }

    /**
     * Utility container for a property name, method name, and
     * method parameter type array.
     */
    class ConnProperty {
        String name;
        String method;
        Class[] params;
        ConnProperty(String n, String m, Class[] p) {
            name = n;
            method = m;
            params = p;
        }
    }; 
}
