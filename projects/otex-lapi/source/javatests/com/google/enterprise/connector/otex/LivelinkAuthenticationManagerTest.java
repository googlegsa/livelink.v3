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
    }


    /**
     * Tests authenticating with LAPI.
     *
     * @throws Exception if an error occurs
     */
    public void testLapi() throws Exception {
        setUpLapi();
        assertTrue("Valid user",
            authenticate("llglobal", "Gibson", true)); 
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
            authenticate("llglobal", "Gibson", true)); 
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
            authenticate("llglobal", "Gibson", true)); 
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
            authenticate("llglobal-external", "Gibson", true)); 
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
            authenticate("Admin", "livelink", true)); 
        assertFalse("Valid user, invalid password", 
            authenticate("Admin", "foo")); 
        assertFalse("Invalid user",
            authenticate("foo", "foo")); 
        assertFalse("External user who shouldn't be able to log in " +
            "through the internal web server", 
            authenticate("llglobal-external", "Gibson")); 
    }


    private boolean authenticate(String username, String password) {
        return authenticate(username, password, false); 
    }
    
    private boolean authenticate(String username, String password,
            boolean log) {
        try {
            return authManager.authenticate(username, password);
        }
        catch (RepositoryException e) {
            if (log)
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
        conn = LivelinkConnectorFactory.getConnector("authenticate-lapi."); 
        finishSetup();
    }


    /**
     * Sets the properties for Livelink CGI connections.
     */
    private void setUpCgi() throws Exception {
        conn = LivelinkConnectorFactory.getConnector("authenticate-cgi."); 
        finishSetup();
    }


    /**
     * Sets the properties for authentication through a proxy in
     * front of the Livelink CGI.
     */
    private void setUpProxy() throws Exception {
        conn = LivelinkConnectorFactory.getConnector("authenticate-proxy."); 
        finishSetup();
    }


    /**
     * Sets the properties for authentication through a Livelink
     * Directory Services-enabled web server.
     */
    private void setUpDsExternal() throws Exception {
        conn = LivelinkConnectorFactory.getConnector("authenticate-ds-external."); 
        finishSetup();
    }


    /**
     * Sets the properties for authentication through the
     * non-Directory Services enabled web server for a DS-enabled
     * instance.
     */
    private void setUpDsInternal() throws Exception {
        conn = LivelinkConnectorFactory.getConnector("authenticate-ds-internal."); 
        finishSetup();
    }
}
