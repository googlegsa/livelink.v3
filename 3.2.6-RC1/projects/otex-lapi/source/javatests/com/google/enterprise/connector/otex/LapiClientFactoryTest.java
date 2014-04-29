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

import com.google.enterprise.connector.otex.client.lapi.LapiClientFactory;

import junit.framework.TestCase;

import java.util.ArrayList;

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
  @Override
  protected void setUp() throws Exception {
    factory = new LapiClientFactory(); 
    factory.setServer(getName()); 
    factory.setPort(1111);
    factory.setConnection("testConnection");
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
    factory.setHttps(true); 
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
    factory.setHttps(true);
    factory.setVerifyServer(true);
    ArrayList<String> certs = new ArrayList<String>();
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

