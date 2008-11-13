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

import java.util.Set;

import junit.framework.TestCase;

import com.google.enterprise.connector.spi.AuthenticationIdentity;
import com.google.enterprise.connector.spi.RepositoryException;

public class LdapAuthenticationManagerTest extends TestCase {

    private LdapAuthenticationManager manager;

    protected void setUp() {
        String name = getName().substring(4).toLowerCase();
        //System.out.println(name);
        manager = new LdapAuthenticationManager();
        manager.setProviderUrl(System.getProperty(name + ".providerUrl"));
        manager.setSecurityPrincipalPattern(
            System.getProperty(name + ".securityPrincipalPattern"));
    }

    public void testLdapEscaping() throws Exception {
        assertEquals("foo", manager.escapeUsername("foo"));
        assertEquals("\\#foo", manager.escapeUsername("#foo"));
        assertEquals("\\ bar", manager.escapeUsername(" bar"));
        assertEquals("bar\\ ", manager.escapeUsername("bar "));
        assertEquals("\\\"hello\\\"", manager.escapeUsername("\"hello\""));
        assertEquals("embedd\\\"ed", manager.escapeUsername("embedd\"ed"));
        assertEquals("\\<foo\\>", manager.escapeUsername("<foo>"));
        assertEquals("hi \\<there\\> you", manager.escapeUsername("hi <there> you"));
        assertEquals((Object) "domain\\\\username",
            manager.escapeUsername("domain\\username"));
    }

    public void testLdap() throws Exception {
        assertTrue("valid user", authenticate("gemerson", "test"));
        assertTrue("valid user with escape char",
            authenticate("Emerson, Gennis", "test"));
        assertFalse("invalid user", authenticate("foo", "test"));
    }

    public void testLdaps() throws Exception {
        System.setProperty("javax.net.ssl.trustStore",
            System.getProperty("ldaps.trustStore"));
        assertTrue("valid user", authenticate("gemerson", "test"));
        assertFalse("invalid user", authenticate("foo", "test"));
    }

    private boolean authenticate(final String username,
            final String password) {
        try {
            AuthenticationIdentity identity = new AuthenticationIdentity() {
                    public String getUsername() { return username; }
                    public String getPassword() { return password; }
                    public String getCookie(String c) { return null; }
                    public String setCookie(String c, String v) { return null; }
                    public Set getCookieNames() { return null; }
                };
            return manager.authenticate(identity).isValid();
        }
        catch (RepositoryException e) {
            return false;
        }
    }

}
