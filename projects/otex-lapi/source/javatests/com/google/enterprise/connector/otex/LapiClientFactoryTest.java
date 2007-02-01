// Copyright (C) 2006-2007 Google Inc.

package com.google.enterprise.connector.otex;

import java.util.ArrayList;
import junit.framework.TestCase;

import com.google.enterprise.connector.otex.client.lapi.LapiClientFactory;

/**
 * Tests some properties of the LapiClientFactory with various
 * sets of configuration parameters. The test is intended to
 * verify that, if the right setters are called (by
 * LivelinkConnector), that the right parameters are passed to
 * the LLSession constructor. At the moment, the test simply
 * allows LapiClientFactory to log the parameters; the parameters
 * aren't exposed in any way that would allow us to read them
 * here.
 */
public class LapiClientFactoryTest extends TestCase {

    /** The factory instance under test. */
    private LapiClientFactory factory; 


    /**
     * Establishes a session and obtains an AuthenticationManager
     * for testing. 
     *
     * @throws RepositoryException if login fails
     */
    public void setUp() throws Exception {
        factory = new LapiClientFactory(); 
        factory.setHostname(getName()); 
        factory.setPort(1111);
        factory.setDatabase("testDatabase");
    }


    /**
     * Tests properties for a direct LAPI connection. The config
     * object should be null. 
     */
    public void testDirectConnection() throws Exception {
        factory.setUsername("testUsername");
        factory.setPassword("testPassword");
        factory.createClient(); 
    }


    /**
     * Tests adding a domain.
     */
    public void testDirectConnectionWithDomain() throws Exception {
        factory.setUsername("testUsername");
        factory.setPassword("testPassword");
        factory.setDomainName("testDomainName");
        factory.createClient();
    }


    /**
     * Tests tunnelling.
     */
    public void testTunnel() throws Exception {
        factory.setUsername("testUsername");
        factory.setPassword("testPassword");
        factory.setLivelinkCgi("/testLivelinkCgi/livelink"); 
        factory.createClient();
    }


    /**
     * Tests tunnelling with HTTP authentication.
     */
    public void testTunnelAndAuthenticate() throws Exception {
        factory.setUsername("testUsername");
        factory.setPassword("testPassword");
        factory.setLivelinkCgi("/testLivelinkCgi/livelink");
        factory.setHttpUsername("testHttpUsername"); 
        factory.setHttpPassword("testHttpPassword"); 
        factory.createClient();
    }


    /**
     * Tests disabling NTLM.
     */
    public void testDisableNtlm() throws Exception {
        factory.setUsername("testUsername");
        factory.setPassword("testPassword");
        factory.setEnableNtlm(false);
        factory.createClient();
    }


    /**
     * Tests enabling HTTPS.
     */
    public void testTunnelWithHttps() throws Exception {
        factory.setUsername("testUsername");
        factory.setPassword("testPassword");
        factory.setLivelinkCgi("/testLivelinkCgi/livelink");
        factory.setHttpUsername("testHttpUsername"); 
        factory.setHttpPassword("testHttpPassword"); 
        factory.setUseHttps(true); 
        factory.createClient();
    }


    /**
     * Tests VerifyServer.
     */
    public void testTunnelVerify() throws Exception {
        factory.setUsername("testUsername");
        factory.setPassword("testPassword");
        factory.setLivelinkCgi("/testLivelinkCgi/livelink");
        factory.setHttpUsername("testHttpUsername"); 
        factory.setHttpPassword("testHttpPassword"); 
        factory.setUseHttps(true);
        factory.setVerifyServer(true);
        ArrayList certs = new ArrayList();
        certs.add("test cert");
        factory.setCaRootCerts(certs); 
        factory.createClient();
    }


    /**
     * Tests the properties used by createClient(username,
     * password). Used to verify that the values in the method
     * arguments are used, not the previously-configured values.
     */
    public void testOverrideIdentity() throws Exception {
        factory.setUsername("*** ERROR ***");
        factory.setPassword("*** ERROR ***");
        factory.createClient("newUsername", "newPassword");
    }


    /**
     * Tests the properties used by createClient(username,
     * password). Used to verify that the values in the method
     * arguments are used, not the previously-configured values.
     */
    public void testOverrideIdentityHttp() throws Exception {
        factory.setUsername("*** ERROR ***");
        factory.setPassword("*** ERROR ***");
        factory.setHttpUsername("*** ERROR ***");
        factory.setHttpPassword("*** ERROR ***");
        factory.setUseUsernamePasswordWithWebServer(true); 
        factory.createClient("newUsername", "newPassword");
    }


    /**
     * Tests the properties used by createClient(username,
     * password). Used to verify that the values in the method
     * arguments are used, not the previously-configured values.
     */
    public void testRemoveIdentityHttp() throws Exception {
        factory.setUsername("*** ERROR ***");
        factory.setPassword("*** ERROR ***");
        factory.setHttpUsername("*** ERROR ***");
        factory.setHttpPassword("*** ERROR ***");
        factory.setUseUsernamePasswordWithWebServer(false); 
        factory.createClient("newUsername", "newPassword");
    }
}

