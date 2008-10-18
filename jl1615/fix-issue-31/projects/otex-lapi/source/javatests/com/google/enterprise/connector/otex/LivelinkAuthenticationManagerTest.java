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

import java.lang.reflect.Method;
import java.util.ArrayList;
import junit.framework.TestCase;

import com.google.enterprise.connector.spi.AuthenticationIdentity;
import com.google.enterprise.connector.spi.AuthenticationManager;
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
    private AuthenticationManager authManager;
        
    /**
     * Tests authenticating with LAPI.
     *
     * @throws Exception if an error occurs
     */
    public void testLapi() throws Exception {
        setUpLapi();
        testAuthentication("llglobal", "Gibson");
    }


    /**
     * Tests authenticating with LAPI through the Livelink CGI process. 
     *
     * @throws Exception if an error occurs
     */
    public void testCgi() throws Exception {
        setUpCgi();
        testAuthentication("llglobal", "Gibson");
    }


    /**
     * Tests authenticating with LAPI through a proxy to the
     * Livelink CGI.
     *
     * @throws Exception if an error occurs
     */
    public void testProxy() throws Exception {
        setUpProxy();
        testAuthentication("llglobal", "Gibson");
    }


    /**
     * Tests using the Directory-Services enabled web server with
     * a DS Livelink instance.
     *
     * @throws Exception if an error occurs
     */
    public void testDirectoryServicesExternal() throws Exception {
        setUpDsExternal();
        testAuthentication("llglobal-external", "Gibson");
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
        testAuthentication("Admin", "livelink");
        assertFalse("External user who shouldn't be able to log in " +
            "through the internal web server", 
            authenticate("llglobal-external", "Gibson")); 
    }


    /**
     * Tests using an AuthenticationManagerChain with only the
     * Livelink authentication manager configured.
     *
     * @throws Exception if an error occurs
     */
    public void testAuthenticationManagerChainLivelink() throws Exception {
        setUpChain1(); 
        assertTrue(authManager instanceof AuthenticationManagerChain);
        testAuthentication("llglobal", "Gibson"); 
    }


    /**
     * Tests using an AuthenticationManagerChain with the LDAP
     * and the Livelink authentication managers configured.
     *
     * @throws Exception if an error occurs
     */
    public void testAuthenticationManagerChainLdapLivelink() throws Exception {
        setUpChain2(); 
        assertTrue(authManager instanceof AuthenticationManagerChain);
        // Livelink user. 
        assertTrue("Livelink user not authenticated", 
            authenticate("llglobal", "Gibson")); 
        // LDAP user
        assertTrue("LDAP user not authenticated",
            authenticate("gemerson", "test")); 
    }


    private void testAuthentication(String username, String password)
            throws Exception {
        assertTrue("Valid user: " + username, 
            authenticate(username, password, true)); 
        assertFalse("Valid user, invalid password", 
            authenticate("llglobal", "foo")); 
        assertFalse("Invalid user", 
            authenticate("foo", "foo"));

        // FIXME: llnobody only exists on Livelink 9.5 on
        //swift. Also, we don't look at the enterprise workspace
        //any more?  assertTrue("No Enterprise workspace access",
        //authenticate("llnobody", "Gibson", true));
    }
    

    private boolean authenticate(String username, String password) {
        return authenticate(username, password, false); 
    }
    
    private boolean authenticate(final String username, final String password,
            boolean log) {
        try {
            AuthenticationIdentity identity = new AuthenticationIdentity() {
                    public String getUsername() { return username; }
                    public String getPassword() { return password; }
                };
            return authManager.authenticate(identity).isValid();
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
        authManager = (AuthenticationManager) session.
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

    private void setUpChain1() throws Exception {
        conn = LivelinkConnectorFactory.getConnector("authenticate-lapi."); 

        ArrayList managers = new ArrayList();
        managers.add(new LivelinkAuthenticationManager()); 
        AuthenticationManagerChain chain = new AuthenticationManagerChain(); 
        chain.setAuthenticationManagers(managers);
        conn.setAuthenticationManager(chain); 
        finishSetup();
    }

    private void setUpChain2() throws Exception {
        conn = LivelinkConnectorFactory.getConnector("authenticate-lapi."); 

        LdapAuthenticationManager ldap = new LdapAuthenticationManager();
        ldap.setProviderUrl(System.getProperty("ldap.providerUrl")); 
        ldap.setSecurityPrincipalPattern(
            System.getProperty("ldap.securityPrincipalPattern")); 

        ArrayList managers = new ArrayList();
        managers.add(ldap); 
        managers.add(new LivelinkAuthenticationManager()); 
        
        AuthenticationManagerChain chain = new AuthenticationManagerChain(); 
        chain.setAuthenticationManagers(managers);

        conn.setAuthenticationManager(chain); 
        finishSetup();
    }
}
