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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Properties;
import java.util.ResourceBundle;

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
    /** The logger for this class. */
    private static final Logger LOGGER =
        Logger.getLogger(LivelinkConnectorType.class.getName());

    /**
     * Holds information (name, label, default value) about a
     * configuration property. Handles displaying that property
     * in the format used by the GSA.
     */
    private static abstract class ConnectorProperty {
        /** HTML form labels for the configuration properties. */
        private static final ResourceBundle labels;
    
        static {
            // TODO: Use the language parameters to create the locale
            // to use here. That means building the resource bundles
            // more dynamically, instead of a single static bundle.
            ResourceBundle bundle;
            try {
                bundle = ResourceBundle.getBundle(
                    LivelinkConnectorType.class.getName(), Locale.US);
            } catch (MissingResourceException e) {
                LivelinkConnectorType.LOGGER.log(Level.SEVERE,
                    e.getMessage(), e);
                bundle = null;
            }
            labels = bundle;
        }

        /**
         * Gets the display label for the given name.
         *
         * @param name a form element name or some other unique key
         * @return the display label, or if one cannot be found, the name
         */
        protected static String getLabel(String name) {
            if (labels == null)
                return name;
            else {
                try {
                    return labels.getString(name);
                } catch (MissingResourceException e) {
                    return name;
                }
            }
        }
        
        String name;
        String label;
        String defaultValue;
        
        protected ConnectorProperty(String name) {
            this(name, null); 
        }

        protected ConnectorProperty(String name, String defaultValue) {
            this.name = name;
            this.label = getLabel(name);
            this.defaultValue = defaultValue;
        }

        public final String toString() {
            StringBuffer buffer = new StringBuffer();
            addToBuffer(buffer, "", "", null); 
            return buffer.toString();
        }

        abstract protected void addFormControl(StringBuffer buffer,
            String value);

        public void addToBuffer(StringBuffer buffer, String labelPrefix,
                String labelSuffix, String value) {
            // TODO: Use CSS here. Better handling of section labels.
            if (START_TAG.equals(labelPrefix))
                buffer.append("<tr><td>&nbsp;</td><td>&nbsp;</td></tr>\r\n");
            buffer.append("<tr valign='top'>\r\n").
                append("<td style='white-space: nowrap'>").
                append(labelPrefix).
                append(label).
                append(labelSuffix).
                append("</td>\r\n"). 
                append("<td>");
            addFormControl(buffer, value);
            buffer.append("</td>\r\n</tr>\r\n");
        }

        protected void appendOption(StringBuffer buffer, String value,
                String text, boolean isSelected) {
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
        protected void appendAttribute(StringBuffer buffer, String name, 
                String value) {
            buffer.append(name).append("=\"");
            escapeAndAppend(buffer, value);
            buffer.append("\" "); 
        }

        protected void escapeAndAppend(StringBuffer buffer, String data) {
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

        TextInputProperty(String name) {
            this(name, null); 
        }

        TextInputProperty(String name, String defaultValue) {
            super(name, defaultValue); 
        }

        protected void addFormControl(StringBuffer buffer, String value) {
            buffer.append("<input ");
            appendAttribute(buffer, "type", type);
            appendAttribute(buffer, "name", name); 

            // TODO: What size should this be? I made it larger to
            // reasonably handle displayUrl, but arguably it should be
            // larger than this. One inconsistency is that the
            // Connector Name field on the form has no size, so it
            // will have the smaller default size text box. I picked
            // 50 because the New Connector Manager form has elements
            // that size.
            appendAttribute(buffer, "size", "50");

            if (value == null) {
                if (defaultValue != null)
                    value = defaultValue;
                else
                    value = "";
            }
            appendAttribute(buffer, "value", value); 
            buffer.append(" />"); 
        }
    }

    /**
     * Holder for a property which should be rendered as a password
     * input element.
     */
    private static class PasswordInputProperty extends TextInputProperty {

        PasswordInputProperty(String name) {
            super(name);
            this.type = "password";
        }
    }

    /**
     * Holder for a property which should be rendered as a hidden
     * input element.
     */
    private static class HiddenInputProperty extends TextInputProperty {
        HiddenInputProperty(ConnectorProperty prop) {
            super(prop.name, prop.defaultValue); 
            this.type = "hidden";
        }

        public void addToBuffer(StringBuffer buffer, String labelPrefix,
                String labelSuffix, String value) {
            buffer.append("<tr style='display:none'>\r\n").
                append("<td></td>\r\n<td>");
            addFormControl(buffer, value);
            buffer.append("</td>\r\n</tr>\r\n");
        }
    }

    /**
     * Holder for a property which should be rendered as a textarea
     * input element.
     */
    private static class TextareaProperty extends ConnectorProperty {
        TextareaProperty(String name) {
            super(name); 
        }

        protected void addFormControl(StringBuffer buffer, String value) {
            buffer.append("<textarea ");
            appendAttribute(buffer, "rows", "5");
            appendAttribute(buffer, "cols", "40");
            appendAttribute(buffer, "name", name);
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
        BooleanSelectProperty(String name, String defaultValue) {
            super(name, defaultValue); 
        }

        protected void addFormControl(StringBuffer buffer, String value) {
            if (value == null && defaultValue != null)
                value = defaultValue;
            boolean isTrue = new Boolean(value).booleanValue();

            // FIXME: Uh, maybe we don't want to depend on the
            // property name here, eh?
            if (name.startsWith("enable") && !name.equals("enableNtlm")) {
                // TODO: Split out a CheckboxProperty class for this.
                buffer.append("<input ");
                appendAttribute(buffer, "type", "checkbox");
                appendAttribute(buffer, "name", name); 
                appendAttribute(buffer, "id", name + "true"); 
                appendAttribute(buffer, "value", "true");
                if (isTrue)
                    buffer.append("checked");
                buffer.append("> ");
                appendLabel(buffer, name + "true", getLabel("enable"));
            } else {
                // XXX: Surely this could be cleaner.
                buffer.append("<input ");
                appendAttribute(buffer, "type", "radio");
                appendAttribute(buffer, "name", name); 
                appendAttribute(buffer, "id", name + "true"); 
                appendAttribute(buffer, "value", "true");
                if (isTrue)
                    buffer.append("checked");
                buffer.append("> ");
                appendLabel(buffer, name + "true", getLabel("true"));
                buffer.append("<br>");
                buffer.append("<input ");
                appendAttribute(buffer, "type", "radio");
                appendAttribute(buffer, "name", name); 
                appendAttribute(buffer, "id", name + "false"); 
                appendAttribute(buffer, "value", "false");
                if (!isTrue)
                    buffer.append("checked");
                buffer.append("> ");
                appendLabel(buffer, name + "false", getLabel("false"));
            }
        }

        protected void appendLabel(StringBuffer buffer, String element,
                String label) {
            buffer.append("<label ");
            appendAttribute(buffer, "for", element);
            buffer.append(">");
            buffer.append(label);
            buffer.append("</label>");
        }
    }

    /** Configuration properties which are always displayed. */
    private static final ArrayList baseEntries;

    /** Configuration properties which are never displayed. */
    private static final ArrayList hiddenEntries;

    /** Flag property for enabling the HTTP tunneling properties. */
    private static final ConnectorProperty tunnelingEnabler;
    
    /**
     * Configuration properties for HTTP tunneling; displayed when the
     * useHttpTunneling property is set to "true".
     */
    private static final ArrayList tunnelingEntries;

    /** Flag property for enabling the separate authentication properties. */
    private static final ConnectorProperty authenticationEnabler;
    
    /**
     * Configuration properties which are used for authentication;
     * displayed when the useSeparateAuthentication property was set
     * to "true".
     */
    private static final ArrayList authenticationEntries;

    /**
     * Start tag wrapper for the nested HTTP tunneling and separate
     * authentication enabler labels.
     */
    private static final String START_TAG = "<b>";
    
    /**
     * End tag wrapper for the nested HTTP tunneling and separate
     * authentication enabler labels.
     */
    private static final String END_TAG = "</b>";
    
    /**
     * Form indentation for the nested HTTP tunneling and separate
     * authentication property labels.
     */
    private static final String INDENTATION =
        "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;";
    
    static {
        baseEntries = new ArrayList();
        baseEntries.add(new TextInputProperty("hostname")); 
        baseEntries.add(new TextInputProperty("port", "2099")); 
        baseEntries.add(new TextInputProperty("database")); 
        baseEntries.add(new TextInputProperty("username")); 
        baseEntries.add(new PasswordInputProperty("password")); 
        baseEntries.add(new TextInputProperty("domainName"));
        baseEntries.add(new TextInputProperty("displayUrl")); 

        // These record the state of the enablers. They are a little
        // different from each other, because the useHttpTunneling
        // property is only used by LivelinkConnectorType and its
        // helpers. The useSeparateAuthentication property, on the
        // other hand, is passed along through the
        // connectorInstance.xml file to the LivelinkConnector class,
        // which uses it to determine whether any of the
        // authentication parameters should be used.
        hiddenEntries = new ArrayList();
        hiddenEntries.add(
            new BooleanSelectProperty("useHttpTunneling", "false"));
        hiddenEntries.add(
            new BooleanSelectProperty("useSeparateAuthentication", "false"));

        tunnelingEnabler =
            new BooleanSelectProperty("enableHttpTunneling", "false");
        tunnelingEntries = new ArrayList();
        tunnelingEntries.add(new TextInputProperty("livelinkCgi"));
        tunnelingEntries.add(new TextInputProperty("httpUsername")); 
        tunnelingEntries.add(new PasswordInputProperty("httpPassword")); 
        tunnelingEntries.add(new BooleanSelectProperty("enableNtlm", "true")); 
        tunnelingEntries.add(new BooleanSelectProperty("useHttps", "true")); 
        tunnelingEntries.add(
            new BooleanSelectProperty("verifyServer", "true" ));
        tunnelingEntries.add(new TextareaProperty("caRootCert")); 
        tunnelingEntries.add(
            new BooleanSelectProperty("useUsernamePasswordWithWebServer",
                "false"));
        
        authenticationEnabler =
            new BooleanSelectProperty("enableSeparateAuthentication", "false");
        authenticationEntries = new ArrayList();
        authenticationEntries.add(
            new TextInputProperty("authenticationHostname")); 
        authenticationEntries.add(
            new TextInputProperty("authenticationPort", "443")); 
        authenticationEntries.add(
            new TextInputProperty("authenticationDatabase")); 
        authenticationEntries.add(
            new TextInputProperty("authenticationDomainName"));
        authenticationEntries.add(
            new TextInputProperty("authenticationLivelinkCgi")); 
        authenticationEntries.add(
            new BooleanSelectProperty("authenticationEnableNtlm", "true")); 
        authenticationEntries.add(
            new BooleanSelectProperty("authenticationUseHttps", "true")); 
        authenticationEntries.add(
            new BooleanSelectProperty("authenticationVerifyServer", "true")); 
        authenticationEntries.add(
            new TextareaProperty("authenticationCaRootCert")); 
        authenticationEntries.add(
            new BooleanSelectProperty(
                "authenticationUseUsernamePasswordWithWebServer", "false"));
    }

    /**
     * The Livelink Connector configuration form.
     */
    private static class Form {
        private String message;
        private Map data; 

        Form() {
            this(null, new HashMap()); 
        }

        Form(String message, Map data) {
            this.message = message;
            this.data = data;
        }

        private String getProperty(String name) {
            return (String) data.get(name);
        }
        
        private void addEntries(StringBuffer buffer, ArrayList entries,
                boolean hide, String labelPrefix, String labelSuffix) {
            for (Iterator i = entries.iterator(); i.hasNext(); ) {
                ConnectorProperty prop = (ConnectorProperty) i.next();
                addEntry(buffer, prop, hide, labelPrefix, labelSuffix);
            }
        }

        private void addEntry(StringBuffer buffer, ConnectorProperty prop,
                boolean hide, String labelPrefix, String labelSuffix) {
            if (hide)
                prop = new HiddenInputProperty(prop);
            prop.addToBuffer(buffer, labelPrefix, labelSuffix,
                getProperty(prop.name));
        }
        
        private boolean hide(String name) {
            String value = getProperty(name);
            return value == null || ! new Boolean(value).booleanValue(); 
        }

        String getForm() {
            StringBuffer buffer = new StringBuffer(4096);
            if (message != null) {
                buffer.append("<tr valign='top'>\r\n").
                    append("<td colspan='2' style='color: red'>").
                    append(message).
                    append("</td>\r\n</tr>\r\n");
            }
            addEntries(buffer, baseEntries, false, "", "");
            addEntries(buffer, hiddenEntries, true, null, null);
            addEntry(buffer, tunnelingEnabler, false, START_TAG, END_TAG);
            addEntries(buffer, tunnelingEntries, hide("useHttpTunneling"),
                INDENTATION, "");
            addEntry(buffer, authenticationEnabler, false, START_TAG, END_TAG);
            addEntries(buffer, authenticationEntries,
                hide("useSeparateAuthentication"), INDENTATION, "");
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
            LOGGER.config("getConfigForm language: " + language); 
        return new ConfigureResponse(null, new Form().getForm()); 
    }

    /**
     * {@inheritDoc}
     */
    public ConfigureResponse getPopulatedConfigForm(Map configData,
            String language) {
        if (LOGGER.isLoggable(Level.CONFIG)) {
            LOGGER.config("getPopulatedConfigForm data: " + configData);
            LOGGER.config("getPopulatedConfigForm language: " + language);
        }
        return getResponse(null, configData, language);
    }

    /**
     * Helper method for <code>getPopulatedConfigForm</code> and
     * <code>validateConfig</code>.
     *
     * @param message A message to be included to the user along with the form
     * @param configData A map of name, value pairs (String, String)
     * of configuration data
     * @param language A locale string, such as "en" or "fr_CA" which
     * the implementation may use to produce appropriate descriptions
     * and messages
     */
    private ConfigureResponse getResponse(String message, Map configData,
            String language) {
        if (LOGGER.isLoggable(Level.CONFIG)) 
            LOGGER.config("Response data: " + configData); 
        Form form = new Form(message, configData);
        return new ConfigureResponse(message, form.getForm()); 
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
        if (LOGGER.isLoggable(Level.CONFIG)) {
            LOGGER.config("validateConfig data: " + configData); 
            LOGGER.config("validateConfig language: " + language);
        }

        // The configData object is actually a Properties
        // object, but since they don't tell us that
        // officially, we'll copy the data.
        Properties p = new Properties();
        p.putAll(configData); 

        // Update the properties to copy the enabler properties to
        // the uses.
        boolean changeHttp = changeFormDisplay(p, "useHttpTunneling",
            "enableHttpTunneling");
        boolean changeAuth = changeFormDisplay(p,
            "useSeparateAuthentication", "enableSeparateAuthentication");
            
        // Instantiate a LivelinkConnector to check connectivity. 
        LivelinkConnector conn = null; 
        try {
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
        } catch (Throwable t) {
            LOGGER.log(Level.WARNING, "Failed to create connector", t);
            t = t.getCause();
            while (t != null) {
                if (t instanceof PropertyAccessExceptionsException) {
                    PropertyAccessException[] pae = 
                        ((PropertyAccessExceptionsException) t).
                        getPropertyAccessExceptions();
                    for (int i = 0; i < pae.length; i++)
                        LOGGER.warning(pae[i].getMessage());
                } else
                    LOGGER.warning(t.toString());
                t = t.getCause();
            }
            return getResponse("Failed to instantiate connector",
                p, language); 
        }

        try {
            conn.login(); 
        } catch (Throwable t) {
            LOGGER.log(Level.WARNING, "Error in configuration", t);
            return getResponse("Error in configuration: " + t.getMessage(),
                p, language); 
        }

        if (changeHttp || changeAuth)
            return getResponse(null, p, language); 
        else
            return null; 
    }

    /**
     * Checks whether a property for showing and hiding other
     * parameters on the form has changed. If it has changed, the
     * current state property value is changed to match the requested
     * state.
     *
     * @param p the form properties
     * @param useName the name of the current state property
     * @param enableName the name of the requested state property
     * @return <code>true</code> if the requested state has changed
     * from the current state, or <code>false</code> if it has not
     * changed
     */
    private boolean changeFormDisplay(Properties p, String useName,
            String enableName) {
        if (LOGGER.isLoggable(Level.FINE))
            LOGGER.fine("ENABLED: " + p.getProperty(enableName));
        boolean enable = new Boolean(p.getProperty(enableName)).booleanValue();
        boolean use = new Boolean(p.getProperty(useName)).booleanValue();
        if (enable != use) {
            p.setProperty(useName, enable ? "true" : "false");
            return true;
        } else
            return false;
    }            
}
