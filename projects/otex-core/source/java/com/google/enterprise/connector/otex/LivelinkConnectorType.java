// Copyright (C) 2006-2007 Google Inc.

package com.google.enterprise.connector.otex;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Map;
import java.util.Properties;

import com.google.enterprise.connector.spi.ConfigureResponse;
import com.google.enterprise.connector.spi.ConnectorType;
import org.springframework.beans.factory.config.PropertyPlaceholderConfigurer;
import org.springframework.beans.factory.xml.XmlBeanFactory;
import org.springframework.beans.PropertyAccessException;
import org.springframework.beans.PropertyAccessExceptionsException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;


/**
 * Supports the configuration properties used by the Livelink Connector.
 */
/*
 * TODO: I18N
 * TODO: Messages in ConfigureResponse. Are they ever used?
 */
public class LivelinkConnectorType implements ConnectorType {

    /** The logger. */
    private static final Logger LOGGER =
        Logger.getLogger(LivelinkConnectorType.class.getName());

    /**
     * Holds information (name, label, default value) about a
     * configuration property. Handles displaying that property
     * in the format used by the GSA.
     */
    private static abstract class ConnectorProperty {
        String name;
        String label;
        String defaultValue;
        
        ConnectorProperty(String name, String label) {
            this(name, label, null); 
        }

        ConnectorProperty(String name, String label, String defaultValue) {
            this.name = name;
            this.label = label;
            this.defaultValue = defaultValue;
        }

        public String toString() {
            StringBuffer buffer = new StringBuffer();
            addToBuffer(buffer); 
            return buffer.toString();
        }

        abstract void addFormControl(StringBuffer buffer, String value);

        void addToBuffer(StringBuffer buffer) {
            addToBuffer(buffer, null); 
        }

        void addToBuffer(StringBuffer buffer, String value) {
            buffer.append("<tr>\r\n").
                append("<td style='white-space: nowrap'>").
                append(this.label).
                append("</td>\r\n"). 
                append("<td>");
            addFormControl(buffer, value);
            buffer.append("</td>\r\n</tr>\r\n");
        }

        void appendOption(StringBuffer buffer, String value, String text,
                boolean isSelected) {
            buffer.append("<option ");
            appendAttribute(buffer, "value", value);
            if (isSelected)
                buffer.append("selected");
            buffer.append(">");
            escapeAndAppend(buffer, text);
            buffer.append("</option>"); 
        }

        /* We need to use double quotes around the attribute
         * value because the ConnectorManager uses a string match
         * to find 'name=\"' and add a prefix to the
         * configuration parameters coming from this connector
         * type. See
         * ConnectionManagerGetServlet.writeConfigureResponse and
         * ServletUtil.prependCmPrefix.
         */
        void appendAttribute(StringBuffer buffer, String name, 
                String value) {
            buffer.append(name).append("=\"");
            escapeAndAppend(buffer, value);
            buffer.append("\" "); 
        }

        void escapeAndAppend(StringBuffer buffer, String data) {
            for (int i = 0; i < data.length(); i++) {
                switch (data.charAt(i)) {
                case '\'': buffer.append("&apos;"); break;
                case '"': buffer.append("&quot;"); break;
                case '&': buffer.append("&amp;"); break;
                case '<': buffer.append("&lt;"); break;
                case '>': buffer.append("&gt;"); break;
                default: buffer.append(data.charAt(i)); 
                }
            }
        }
    }

    /**
     * Holder for a property which should be rendered as a text
     * input element.
     */
    private static class TextInputProperty extends ConnectorProperty {
        String type = "text"; 

        TextInputProperty(String name, String label) {
            this(name, label, null); 
        }

        TextInputProperty(String name, String label, String defaultValue) {
            super(name, label, defaultValue); 
        }

        void addFormControl(StringBuffer buffer, String value) {
            buffer.append("<input ");
            appendAttribute(buffer, "type", this.type);
            appendAttribute(buffer, "name", this.name); 

            // TODO: What size should this be? I made it larger to
            // reasonably handle displayUrl, but arguably it should be
            // larger than this. One inconsistency is that the
            // Connector Name field on the form has no size, so it
            // will have the smaller default size text box. I picked
            // 50 because the New Connector Manager form has elements
            // that size.
            appendAttribute(buffer, "size", "50");

            if (value != null)
                appendAttribute(buffer, "value", value); 
            buffer.append(" />"); 
        }
    }

    /**
     * Holder for a property which should be rendered as a password
     * input element.
     */
    private static class PasswordInputProperty extends TextInputProperty {

        PasswordInputProperty(String name, String label) {
            super(name, label); 
            this.type="password";
        }
    }

    /**
     * Holder for a property which should be rendered as a hidden
     * input element.
     */
    private static class HiddenInputProperty extends TextInputProperty {

        HiddenInputProperty(ConnectorProperty prop) {
            super(prop.name, prop.label, prop.defaultValue); 
            this.type="hidden";
        }

        void addToBuffer(StringBuffer buffer) {
            addToBuffer(buffer, null); 
        }

        void addToBuffer(StringBuffer buffer, String value) {
            buffer.append("<tr style='display:none'>\r\n").
                append("<td></td>\r\n<td>");
            addFormControl(buffer, value);
            buffer.append("</td>\r\n</tr>\r\n");
        }

        void addFormControl(StringBuffer buffer, String value) {
            if (value == null) {
                if (this.defaultValue != null)
                    value = this.defaultValue;
                else
                    value = "";
            }
            super.addFormControl(buffer, value); 
        }
    }

    /**
     * Holder for a property which should be rendered as a textarea
     * input element.
     */
    private static class TextareaProperty extends ConnectorProperty {

        TextareaProperty(String name, String label) {
            super(name, label); 
        }

        void addFormControl(StringBuffer buffer, String value) {
            buffer.append("<textarea ");
            appendAttribute(buffer, "rows", "5");
            appendAttribute(buffer, "cols", "40");
            appendAttribute(buffer, "name", this.name);
            buffer.append(">");
            if (value != null)
                escapeAndAppend(buffer, value); 
            buffer.append("</textarea>");
        }
    }

    /**
     * Holder for a property which should be rendered as a select 
     * list with two values, "true" and "false".
     */
    /*
     * We need to use select lists (radio buttons would probably
     * be an option too) to ensure that a value is always present
     * for boolean properties. If we let the value be empty,
     * Spring can't convert the value to boolean when
     * instantiating the bean.
     */
    private static class BooleanSelectProperty extends ConnectorProperty {

        BooleanSelectProperty(String name, String label, String defaultValue) {
            super(name, label, defaultValue); 
        }

        void addFormControl(StringBuffer buffer, String value) {
            boolean isTrue = false;
            if (value == null && this.defaultValue != null) 
                isTrue = new Boolean(this.defaultValue).booleanValue();
            else 
                isTrue = new Boolean(value).booleanValue();
            buffer.append("<select ");
            appendAttribute(buffer, "name", this.name);
            buffer.append(">"); 
            appendOption(buffer, "false", "False", !isTrue); 
            appendOption(buffer, "true", "True", isTrue); 
            buffer.append("</select>"); 
        }
    }

    /** Configuration properties which are always displayed. */
    private static final ArrayList baseEntries;

    /** Configuration properties which are used for
     * authentication; displayed when the
     * useSeparateAuthentication property was set to "true".
     */
    private static final ArrayList authenticationEntries;

    static {
        baseEntries = new ArrayList();
        baseEntries.add(new TextInputProperty("hostname", "Hostname")); 
        baseEntries.add(new TextInputProperty("port", "Port")); 
        baseEntries.add(new TextInputProperty("database", "Database")); 
        baseEntries.add(new TextInputProperty("username", "Username")); 
        baseEntries.add(new PasswordInputProperty("password", "Password")); 
        baseEntries.add(new BooleanSelectProperty("useHttps", "Use HTTPS", 
                            "true")); 
        baseEntries.add(new BooleanSelectProperty("enableNtlm", "Enable NTLM",
                            "true")); 
        baseEntries.add(new TextInputProperty("livelinkCgi", "Livelink CGI")); 
        baseEntries.add(new TextInputProperty("httpUsername", 
                            "HTTP Username")); 
        baseEntries.add(new PasswordInputProperty("httpPassword",
                            "HTTP Password")); 
        baseEntries.add(new BooleanSelectProperty("verifyServer",
                            "Verify Server", "true" ));
        baseEntries.add(new TextareaProperty("caRootCerts",
                            "CA Root Certificate")); 
        baseEntries.add(new TextInputProperty("domainName", "Livelink Domain"));
        baseEntries.add(new TextInputProperty("displayUrl", "Display URL")); 
        baseEntries.add(new BooleanSelectProperty(
                            "useUsernamePasswordWithWebServer",
                            "Use Credentials With Web Server", "false"));  
        //baseEntries.add(new TextInputProperty("servtype", "Server Type")); 
        baseEntries.add(
            new BooleanSelectProperty("useSeparateAuthentication", 
                "Use Different Authentication Configuration", "false"));  
            
        authenticationEntries = new ArrayList();
        authenticationEntries.add(
            new TextInputProperty("authenticationHostname", 
                "[Authentication] Hostname")); 
        authenticationEntries.add(
            new TextInputProperty("authenticationPort", 
                "[Authentication] Port", "0")); 
        authenticationEntries.add(
            new TextInputProperty("authenticationDatabase",
                "[Authentication] Database")); 
        authenticationEntries.add(
            new BooleanSelectProperty("authenticationUseHttps", 
                "[Authentication] Use HTTPS", "true")); 
        authenticationEntries.add(
            new BooleanSelectProperty("authenticationEnableNtlm", 
                "[Authentication] Enable NTLM", "true")); 
        authenticationEntries.add(
            new TextInputProperty("authenticationLivelinkCgi", 
                "[Authentication] Livelink CGI")); 
        authenticationEntries.add(
            new BooleanSelectProperty("authenticationVerifyServer", 
                "[Authentication] Verify Server", "true")); 
        authenticationEntries.add(
            new TextareaProperty("authenticationCaRootCerts",
                "[Authentication] CA Root Certificate")); 
        authenticationEntries.add(
            new TextInputProperty("authenticationDomainName", 
                "[Authentication] Livelink Domain"));
        authenticationEntries.add(
            new BooleanSelectProperty(
                "authenticationUseUsernamePasswordWithWebServer",
                "[Authentication] Use Credentials With Web Server", "false"));  
    }

    /**
     * The Livelink Connector configuration form.
     */
    private static class Form {

        private Map data; 

        Form() {
            this(new HashMap()); 
        }

        Form(Map data) {
            this.data = data;
        }
        
        private void addEntries(StringBuffer buffer,
                ArrayList entries, boolean hide) {
            for (Iterator i = entries.iterator(); i.hasNext(); ) {
                ConnectorProperty prop = (ConnectorProperty) i.next();
                if (hide) {
                    new HiddenInputProperty(prop).addToBuffer(buffer, 
                        (String) data.get(prop.name));
                }
                else
                    prop.addToBuffer(buffer, (String) data.get(prop.name));
            }
        }

        private boolean hideAuthentication() {
            String value = (String) data.get("useSeparateAuthentication");
            return value == null || ! new Boolean(value).booleanValue(); 
        }

        String getForm() {
            StringBuffer buffer = new StringBuffer(4096); 
            addEntries(buffer, baseEntries, false);
            addEntries(buffer, authenticationEntries, hideAuthentication());
            return buffer.toString(); 
        }
    }

    /**
     * No-args constructor for bean instantiation.
     */
    public LivelinkConnectorType() {
        super();
    }
    
    /**
     * {@inheritDoc}
     */
    public ConfigureResponse getConfigForm(String language) {
        if (LOGGER.isLoggable(Level.CONFIG)) 
            LOGGER.config("method entry"); 
        return new ConfigureResponse(null, new Form().getForm()); 
    }

    /**
     * {@inheritDoc}
     */
    public ConfigureResponse getPopulatedConfigForm(Map configData,
            String language) {
        if (LOGGER.isLoggable(Level.CONFIG)) 
            LOGGER.config("getPopulatedConfigForm data: " + configData); 
        Form form = new Form(configData);
        return new ConfigureResponse(null, form.getForm()); 
    }

    /**
     * {@inheritDoc}
     * <p>
     * This method will use the provided configuration
     * information to attempt to instantiate a LivelinkConnector
     * and call its <code>login</code> method. This allows this
     * method to determine whether the configuration information
     * provided is sufficient to connect to Livelink for
     * traversal. It does not attempt to validate the separate
     * authentication configuration, if any, since no separate
     * user information can be provided, and it's possible that
     * the traversal username isn't one that can be authenticated
     * using the authentication properties. For example,
     * traversal might be done as a Livelink admin user using the
     * Livelink server port, while authentication is done using a
     * web server. The Livelink admin user may not be a defined
     * user in the web server's authentication scheme.
     */
    /*
     * We'd like to be able to invoke ConnectorManager to get a
     * connector instance, to be sure that the properties being
     * validated are being validated in the same way in which
     * they'll be used.
     *
     * For now, use Spring to mimic the instantiation of
     * LivelinkConnector here.
     *
     * TODO: Add init method and parameter validation to
     * LivelinkConnector.
     *
     * TODO: How do we/can we return error messages?
     *
     * TODO: do we get any values in the map that aren't part of
     * our parameter set? Should we preserve any other fields as
     * hidden data?
     */
    public ConfigureResponse validateConfig(Map configData,
            String language) {
        if (LOGGER.isLoggable(Level.CONFIG)) 
            LOGGER.config("validateConfig data: " + configData); 

        // Instantiate a LivelinkConnector to check connectivity. 
        LivelinkConnector conn = null; 
        try {
            // The configData object is actually a Properties
            // object, but since they don't tell us that
            // officially, we'll copy the data.
            Properties p = new Properties();
            p.putAll(configData); 
            // TODO: be able to locate connectorInstance.xml
            // elsewhere outside the jar file to support
            // hand-edits.
            Resource res = 
                new ClassPathResource("config/connectorInstance.xml");
            XmlBeanFactory factory = new XmlBeanFactory(res);
            PropertyPlaceholderConfigurer cfg =
                new PropertyPlaceholderConfigurer();
            cfg.setProperties(p);
            cfg.postProcessBeanFactory(factory);
            conn = (LivelinkConnector) factory.getBean("livelink-connector");
        }
        catch (Throwable t) {
            LOGGER.warning("Failed to create connector");
            t = t.getCause();
            while (t != null) {
                if (t instanceof PropertyAccessExceptionsException) {
                    PropertyAccessException[] pae = 
                        ((PropertyAccessExceptionsException) t).
                        getPropertyAccessExceptions();
                    for (int i = 0; i < pae.length; i++)
                        LOGGER.warning(pae[i].getMessage());
                }
                else
                    LOGGER.warning(t.toString());
                t = t.getCause();
            }
            return getPopulatedConfigForm(configData, language); 
        }

        try {
            conn.login(); 
        }
        catch (Throwable t) {
            LOGGER.warning("Failed to connect: " + t); 
            return getPopulatedConfigForm(configData, language); 
        }
        return null; 
    }
}
