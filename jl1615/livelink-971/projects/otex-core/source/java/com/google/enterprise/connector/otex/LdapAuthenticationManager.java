// Copyright (C) 2007-2008 Google Inc.
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

import java.text.MessageFormat;
import java.util.Hashtable;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.naming.Context;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.DirContext;
import javax.naming.NamingException;

import com.google.enterprise.connector.spi.AuthenticationIdentity;
import com.google.enterprise.connector.spi.AuthenticationManager;
import com.google.enterprise.connector.spi.AuthenticationResponse;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.RepositoryLoginException;

/**
 * Implements an AuthenticationManager that authenticates against
 * an LDAP repository.
 */
/*
 * http://forum.java.sun.com/thread.jspa?threadID=726601&tstart=0
 * Support FAST_BIND for ActiveDirectory 2003?
 *
 * Type of server :http://forum.java.sun.com/thread.jspa?threadID=5141427&tstart=225
 *
 * Once you connected to RootDSE, just peruse the values in
 * either the supportedCapabilities or supportedControl and use
 * these to differentiate between different LDAP directories.
 *
 * In addition on AD, you have some other "interesting"
 * attributes in the RootDSE such as supportedLDAPPolicies,
 * highestCommittedUSN, forestFunctionality which I don't think
 * are present on other LDAP directories.
 * ...
 * Most servers have implemented RFC 3045: Storing Vendor
 * Information in the LDAP root DSE (at least Sun Directory
 * Server does, and so does OpenLDAP, Fedora Directory Server...)

 * Reading the rootDSE you can find the VendorName and
 * VendorVersion strings.  These are informational and must not
 * be used (according to the RFC) as a advertisement for
 * supported features and extensioons.
 */
class LdapAuthenticationManager implements AuthenticationManager {

    /** The logger for this class. */
    private static final Logger LOGGER =
        Logger.getLogger(LdapAuthenticationManager.class.getName());

    /** The LDAP provider URL. */
    private String providerUrl;

    /** The security principal string; the identity username will
     * be substituted using MessageFormat.
     */
    private String securityPrincipalPattern;


    /**
     * Default constructor for bean instantiation.
     */
    LdapAuthenticationManager() {
        super();
    }

    /**
     * The LDAP provider URL.
     *
     * @param providerUrl the provider URL
     */
    public void setProviderUrl(String providerUrl) {
        if (LOGGER.isLoggable(Level.CONFIG))
            LOGGER.config("PROVIDER URL: " + providerUrl);
        this.providerUrl = providerUrl;
    }

    /**
     * The LDAP security principal string for
     * authentication. This should contain a MessageFormat
     * placeholder for the username.
     *
     * @param securityPrincipalPattern the LDAP security principal string
     * @throws IllegalArgumentException if the pattern can't be
     * used with MessageFormat
     */
    public void setSecurityPrincipalPattern(String securityPrincipalPattern) {
        if (LOGGER.isLoggable(Level.CONFIG))
            LOGGER.config("SECURITY PRINCIPAL PATTERN: " + securityPrincipalPattern);
        // Test the format string.
        MessageFormat.format(securityPrincipalPattern, new Object[] { "foo" });
        this.securityPrincipalPattern = securityPrincipalPattern;
    }


    /** {@inheritDoc} */
    /* With Java 1.4.2 and up, you can use SSL by specifying the
     * URL using the "ldaps" protocol instead of "ldap". No code
     * changes are needed.
     *
     * TODO: accept a map of environment properties for greater
     * configurability.
     */
    public AuthenticationResponse authenticate(AuthenticationIdentity identity)
            throws RepositoryLoginException, RepositoryException {
        if (LOGGER.isLoggable(Level.FINE))
            LOGGER.fine("AUTHENTICATE (LDAP): " + identity.getUsername());

        Hashtable env = new Hashtable();
        env.put(Context.INITIAL_CONTEXT_FACTORY,
            "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.PROVIDER_URL, providerUrl);
        env.put(Context.SECURITY_AUTHENTICATION, "simple");

        String dn = MessageFormat.format(securityPrincipalPattern,
            new Object[] { escapeUsername(identity.getUsername()) });
        if (LOGGER.isLoggable(Level.FINER))
            LOGGER.finer("DN: " + dn);
        env.put(Context.SECURITY_PRINCIPAL, dn);
        env.put(Context.SECURITY_CREDENTIALS, identity.getPassword());

        try {

            // Create the initial directory context
            DirContext ctx = new InitialDirContext(env);
            // Ask for attributes to ensure that the server is
            // contacted. JNDI allows lazy initialization of
            // contexts, so we have to use it, not just create
            // it.
            ctx.getAttributes("");
            return new AuthenticationResponse(true, null);
        }
        catch (NamingException e)
        {
            LOGGER.warning("Authentication failed for " +
                identity.getUsername() + "; " + e.toString());
            return new AuthenticationResponse(false, null);
        }
    }

    /**
     * Escapes the username before putting it into the LDAP URL.
     *
     * @param username the username
     * @return the escaped username; see RFC 4514
     */
    /* Package access for testing. */
    String escapeUsername(String username) {

        StringBuffer buffer = new StringBuffer();
        int start = 0;
        if (username.startsWith(" ")) {
            buffer.append("\\ ");
            ++start;
        }
        if (username.startsWith("#")) {
            buffer.append("\\#");
            ++start;
        }
        for (int i = start; i < username.length(); i++) {
            char c = username.charAt(i);
            switch (c) {
            case '"': buffer.append("\\\""); break;
            case '\\': buffer.append("\\\\"); break;
            case '+': buffer.append("\\+"); break;
            case ',': buffer.append("\\,"); break;
            case ';': buffer.append("\\;"); break;
            case '<': buffer.append("\\<"); break;
            case '>': buffer.append("\\>"); break;
            default: buffer.append(c); break;
            }
        }
        if (username.endsWith(" "))
            buffer.insert(buffer.length() - 1, "\\");
        return buffer.toString();
    }
}
