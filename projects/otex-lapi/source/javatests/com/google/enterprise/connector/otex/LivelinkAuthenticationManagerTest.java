// Copyright (C) 2006-2007 Google Inc.

package com.google.enterprise.connector.otex;

import junit.framework.TestCase;

import com.google.enterprise.connector.spi.RepositoryException;


/**
 * Tests the LivelinkAuthenticationManager.
 */
/* This test depends on known Livelink users. */
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
        conn.setHostname(System.getProperty("connector.host"));
        try {
            conn.setPort(Integer.parseInt(
                             System.getProperty("connector.port")));
        } catch (NumberFormatException e) {
            // TODO
        }
        conn.setUsername(System.getProperty("connector.username"));
        conn.setPassword(System.getProperty("connector.password"));
        conn.setDisplayUrl(System.getProperty("connector.displayUrl"));
        session = (LivelinkSession) conn.login();
        authManager = (LivelinkAuthenticationManager) session.
            getAuthenticationManager();
    }


    /**
     * Tests a valid user. 
     *
     * @throws RepositoryException if an error occurs
     */
    public void testValidUser() throws RepositoryException {
        assertTrue(authManager.authenticate("llglobal", "Gibson")); 
    }


    /**
     * Tests a valid user with invalid password. 
     *
     * @throws RepositoryException if an error occurs
     */
    public void testValidUserInvalidPassword() throws RepositoryException {
        assertFalse(authManager.authenticate("llglobal", "foo")); 
    }


    /**
     * Tests an invalid username. 
     *
     * @throws RepositoryException if an error occurs
     */
    public void testInvalidUsername() throws RepositoryException {
        assertFalse(authManager.authenticate("foo", "foo")); 
    }
}
