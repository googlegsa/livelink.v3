// Copyright 2007 Google Inc.
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

import com.google.common.annotations.VisibleForTesting;
import com.google.enterprise.connector.spi.ConfigureResponse;
import com.google.enterprise.connector.spi.ConnectorFactory;
import com.google.enterprise.connector.spi.ConnectorType;
import com.google.enterprise.connector.util.UrlValidator;
import com.google.enterprise.connector.util.UrlValidatorException;

import org.springframework.beans.PropertyAccessException;
import org.springframework.beans.PropertyBatchUpdateException;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Supports the configuration properties used by the Livelink Connector.
 */
public class LivelinkConnectorType implements ConnectorType {
  /** The logger for this class. */
  private static final Logger LOGGER =
      Logger.getLogger(LivelinkConnectorType.class.getName());

  /** The connector properties version property name. */
  public static final String VERSION_PROPERTY =
      "LivelinkConnectorPropertyVersion";

  public static final String VERSION_NUMBER = "1";

  /**
   * Holds information (name, label, default value) about a
   * configuration property. Handles displaying that property
   * in the format used by the GSA.
   */
  private static abstract class FormProperty {
    /* Property for space betwen sections. */
    protected static final String PADDING = "padding-top: 3ex; ";

    /* Style attribute for space betwen sections. */
    protected static final String PADDING_STYLE =
        " style='" + PADDING + '\'';

    /**
     * Gets the display label for the given name.
     *
     * @param name a form element name or some other unique key
     * @return the display label, or if one cannot be found, the name
     */
    protected static String getLabel(String name, ResourceBundle labels) {
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

    protected final String name;
    protected final boolean required;
    protected final String defaultValue;

    protected FormProperty(String name) {
      this(name, null);
    }

    protected FormProperty(String name, String defaultValue) {
      this(name, false, defaultValue);
    }

    protected FormProperty(String name, boolean required,
        String defaultValue) {
      this.name = name;
      this.required = required;
      this.defaultValue = defaultValue;
    }

    @Override
    public final String toString() {
      StringBuilder buffer = new StringBuilder();
      addToBuffer(buffer, "", "", null, null);
      return buffer.toString();
    }

    abstract protected void addFormControl(StringBuilder buffer,
        String value, ResourceBundle labels);

    /*
     * XXX: Make the valign dependent on the control type? We want
     * it for radio buttons and text areas, but maybe not for
     * simple text and password fields.
     */
    public void addToBuffer(StringBuilder buffer, String labelPrefix,
        String labelSuffix, String value, ResourceBundle labels) {
      String label = getLabel(name, labels);

      // TODO: Better handling of section labels.
      String padding;
      String style;
      if (FormBuilder.START_TAG.equals(labelPrefix)) {
        padding = PADDING;
        style = PADDING_STYLE;
      } else {
        padding = "";
        style = "";
      }
      buffer.append("<tr valign='top'>\r\n").append("<td style='").
          append(padding).append("white-space: nowrap'>");
      if (required)
        buffer.append("<div style='float: left;'>");
      buffer.append(labelPrefix);
      appendTextLabel(buffer, name, label);
      buffer.append(labelSuffix);
      if (required) {
        buffer.append("</div><div style='text-align: right; ").
            append("color: red; font-weight: bold; ").
            append("margin-right: 0.3em;\'>*</div>");
      }
      buffer.append("</td>\r\n").
          append("<td").append(style).append(">");
      addFormControl(buffer, value, labels);
      buffer.append("</td>\r\n</tr>\r\n");
    }

    protected void appendOption(StringBuilder buffer, String value,
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
    protected void appendAttribute(StringBuilder buffer, String name,
        String value) {
      buffer.append(name).append("=\"");
      escapeAndAppendAttributeValue(buffer, value);
      buffer.append("\" ");
    }

    /**
     * Escapes the given attribute value and appends it.
     *
     * @see #escapeAndAppend
     * @see <a href="http://www.w3.org/TR/REC-xml/#syntax"
     * >http://www.w3.org/TR/REC-xml/#syntax</a>
     */
    /* TODO: Replace with XmlUtils or its successor. */
    protected final void escapeAndAppendAttributeValue(StringBuilder buffer,
        String data) {
      for (int i = 0; i < data.length(); i++) {
        char c = data.charAt(i);
        switch (c) {
          case '\'':
            // Preferred over &apos; see http://www.w3.org/TR/xhtml1/#C_16
            buffer.append("&#39;");
            break;
          case '"':
            buffer.append("&quot;");
            break;
          case '&':
            buffer.append("&amp;");
            break;
          case '<':
            buffer.append("&lt;");
            break;
          case '\t':
          case '\n':
          case '\r':
            buffer.append(c);
            break;
          default:
            if (c >= 0x20 && c <= 0xFFFD) {
              buffer.append(c);
            }
            break;
        }
      }
    }

    /**
     * Escapes the given character data and appends it.
     *
     * @see #escapeAndAppendAttributeValue
     * @see <a href="http://www.w3.org/TR/REC-xml/#syntax"
     * >http://www.w3.org/TR/REC-xml/#syntax</a>
     */
    /* TODO: Replace with XmlUtils (new method) or its successor. */
    protected final void escapeAndAppend(StringBuilder buffer, String data) {
      for (int i = 0; i < data.length(); i++) {
        char c = data.charAt(i);
        switch (c) {
          case '&':
            buffer.append("&amp;");
            break;
          case '<':
            buffer.append("&lt;");
            break;
          case '\t':
          case '\n':
          case '\r':
            buffer.append(c);
            break;
          default:
            if (c >= 0x20 && c <= 0xFFFD) {
              buffer.append(c);
            }
            break;
        }
      }
    }

    protected void appendControlLabel(StringBuilder buffer, String element,
        String label) {
      buffer.append("<label ");
      appendAttribute(buffer, "for", element);
      buffer.append(">");
      buffer.append(label);
      buffer.append("</label>");
    }

    protected void appendTextLabel(StringBuilder buffer, String element,
        String label) {
      appendControlLabel(buffer, element, label);
    }
  }

  /**
   * Holder for a header property that does not use the HTML label
   * element on the label text.
   */
  private static abstract class HeaderProperty extends FormProperty {
    HeaderProperty(String name) {
      super(name);
    }

    HeaderProperty(String name, String defaultValue) {
      super(name, false, defaultValue);
    }

    protected void appendTextLabel(StringBuilder buffer, String element,
        String label) {
      buffer.append(label);
    }
  }

  /** A form label with no associated form control. */
  private static class LabelProperty extends HeaderProperty {
    LabelProperty(String name) {
      super(name);
    }

    /** Writes a table row with a two-column label and no form control. */
    @Override
    public void addToBuffer(StringBuilder buffer, String labelPrefix,
        String labelSuffix, String value, ResourceBundle labels) {
      String label = getLabel(name, labels);

      // TODO: Handle duplication between this and
      // FormProperty.addToBuffer.
      // We know that labelPrefix = START_TAG here.
      buffer.append("<tr valign='top'>\r\n").append("<td style='").
          append(PADDING).append("white-space: nowrap' colspan='2'>");
      buffer.append(labelPrefix);
      appendTextLabel(buffer, name, label);
      buffer.append(labelSuffix);
      buffer.append("</td>\r\n</tr>\r\n");
    }

    /* This is never called, but it is abstract in our superclass. */
    @Override
    protected void addFormControl(StringBuilder buffer, String value,
        ResourceBundle labels) {
    }
  }

  /** Holder for a property containing an optional message for each column. */
  private static class MessageProperty extends LabelProperty {
    private final String[] messages;

    MessageProperty(String name, String left, String right) {
      super(name);
      this.messages = new String[] { left, right };
    }

    @Override
    public void addToBuffer(StringBuilder buffer, String labelPrefix,
        String labelSuffix, String value, ResourceBundle labels) {
      buffer.append("<tr>\r\n");
      for (String message : messages) {
        if (message == null || message.length() == 0) {
            buffer.append("<td></td>\r\n");
        } else {
          buffer.append("<td><span style='font-size:smaller'>");
          buffer.append(getLabel(message, labels));
          buffer.append("</span></td>\r\n");
        }
      }
      buffer.append("</tr>\r\n");
    }
  }

  /**
   * Holder for a property which should be rendered as a text
   * input element.
   */
  private static class TextInputProperty extends FormProperty {
    private final String type;

    TextInputProperty(String name) {
      this(name, false);
    }

    TextInputProperty(String name, boolean required) {
      this(name, required, null);
    }

    TextInputProperty(String name, String defaultValue) {
      this(name, false, defaultValue);
    }

    TextInputProperty(String name, boolean required, String defaultValue) {
      this(name, required, defaultValue, "text");
    }

    protected TextInputProperty(String name, String defaultValue,
        String type) {
      this(name, false, defaultValue, type);
    }

    protected TextInputProperty(String name, boolean required,
        String defaultValue, String type) {
      super(name, required, defaultValue);
      this.type = type;
    }

    @Override
    protected void addFormControl(StringBuilder buffer, String value,
        ResourceBundle labels) {

      buffer.append("<input ");
      appendAttribute(buffer, "type", type);
      appendAttribute(buffer, "id", name);
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
      this(name, false);
    }

    PasswordInputProperty(String name, boolean required) {
      super(name, required, null, "password");
    }
  }

  /**
   * Holder for a property which should be rendered as a hidden
   * input element.
   */
  private static class HiddenInputProperty extends TextInputProperty {
    HiddenInputProperty(FormProperty prop) {
      super(prop.name, prop.defaultValue, "hidden");
    }

    @Override
    public void addToBuffer(StringBuilder buffer, String labelPrefix,
        String labelSuffix, String value, ResourceBundle labels) {
      buffer.append("<tr style='display:none'>\r\n").
          append("<td></td>\r\n<td>");
      addFormControl(buffer, value, labels);
      buffer.append("</td>\r\n</tr>\r\n");
    }
  }

  /**
   * Holder for a property which should be rendered as a textarea
   * input element.
   */
  private static class TextareaProperty extends FormProperty {
    TextareaProperty(String name) {
      super(name);
    }

    @Override
    protected void addFormControl(StringBuilder buffer, String value,
        ResourceBundle labels) {
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
   * Holder for a property which should be rendered as a set of
   * radio buttons.
   */
  private static class RadioSelectProperty extends HeaderProperty {
    private final String[] buttonNames;

    RadioSelectProperty(String name, String[] buttonNames,
        String defaultValue) {
      super(name, defaultValue);
      this.buttonNames = buttonNames;
    }

    @Override
    protected void addFormControl(StringBuilder buffer, String value,
        ResourceBundle labels) {
      if (value == null)
        value = defaultValue;

      for (int i = 0; i < buttonNames.length; i++) {
        if (i > 0)  // arrange radio buttons vertically
          buffer.append("<br />");
        String buttonName = buttonNames[i];
        buffer.append("<input ");
        appendAttribute(buffer, "type", "radio");
        appendAttribute(buffer, "name", name);
        appendAttribute(buffer, "id", name + buttonName);
        appendAttribute(buffer, "value", buttonName);
        if (buttonName.equalsIgnoreCase(value))
          appendAttribute(buffer, "checked", "checked");
        buffer.append(" /> ");
        appendControlLabel(buffer, name + buttonName,
            getLabel(buttonName, labels));
      }
    }
  }

  /**
   * Holder for a property which should be rendered as a radio button
   * selection with two values, "true" and "false".
   */
  private static class BooleanSelectProperty extends RadioSelectProperty {
    static final String[] boolstr = { "true", "false" };

    BooleanSelectProperty(String name, String defaultValue) {
      super(name, boolstr, defaultValue);
    }
  }

  /**
   * Holder for a property which should be rendered as a checkbox
   * whose values are "true"[checked] or "false"[unchecked].
   * Selecting an Enabler property usually results in a redisplay
   * that exposes additional properties.
   */
  private static class EnablerProperty extends HeaderProperty {
    EnablerProperty(String name, String defaultValue) {
      super(name, defaultValue);
    }

    @Override
    protected void addFormControl(StringBuilder buffer, String value,
        ResourceBundle labels) {
      if (value == null)
        value = defaultValue;

      buffer.append("<input ");
      appendAttribute(buffer, "type", "checkbox");
      appendAttribute(buffer, "name", name);
      appendAttribute(buffer, "id", name);
      appendAttribute(buffer, "value", "true");
      if ("true".equalsIgnoreCase(value))
        appendAttribute(buffer, "checked", "checked");
      buffer.append(" /> ");
      appendControlLabel(buffer, name, getLabel("enable", labels));
    }
  }

  /**
   * Encapsulates form display properties.
   */
  private static class FormContext {
    private HashMap<String, Boolean> hiddenProperties;

    FormContext(Map<String, String> configData) {
      hiddenProperties = new HashMap<String, Boolean>();
      hiddenProperties.put("ignoreDisplayUrlErrors",
          ignoreDisplayUrlErrors(configData) ? Boolean.FALSE : Boolean.TRUE);
    }

    void setHidden(String propertyName, boolean hide) {
      hiddenProperties.put(propertyName,
          hide ? Boolean.TRUE : Boolean.FALSE);
    }

    boolean isHidden(String propertyName) {
      Boolean b = hiddenProperties.get(propertyName);
      return b != null && b.booleanValue();
    }

    void setHideIgnoreDisplayUrlErrors(boolean hide) {
      setHidden("ignoreDisplayUrlErrors", hide);
    }

    boolean getHideIgnoreDisplayUrlErrors() {
      return isHidden("ignoreDisplayUrlErrors");
    }
  }

  /**
   * The Livelink Connector configuration form builder.
   */
  private static class FormBuilder {
    /** Configuration properties which are always displayed. */
    private static final ArrayList<FormProperty> baseEntries;

    /** Label for the Livelink System Administrator properties. */
    private static final FormProperty adminLabel;

    /**
     * Configuration properties for the Livelink System
     * Administrator, which are always displayed.
     */
    private static final ArrayList<FormProperty> adminEntries;

    /** Configuration properties which are never displayed. */
    private static final ArrayList<FormProperty> hiddenEntries;

    /** Flag property for enabling the HTTP tunneling properties. */
    private static final FormProperty tunnelingEnabler;

    /**
     * Configuration properties for HTTP tunneling; displayed when the
     * useHttpTunneling property is set to "true".
     */
    private static final ArrayList<FormProperty> tunnelingEntries;

    /**
     * Flag property for enabling the separate authentication
     * properties.
     */
    private static final FormProperty authenticationEnabler;

    /**
     * Configuration properties which are used for authentication;
     * displayed when the useSeparateAuthentication property was set
     * to "true".
     */
    private static final ArrayList<FormProperty> authenticationEntries;

    /** Label for the Indexing Traversal properties. */
    private static final FormProperty indexingLabel;

    /** Configuration properties for Indexing Traversal properties. */
    private static final ArrayList<FormProperty> indexingEntries;

    /** Start tag wrapper for the group labels. */
    /* Public because FormProperty needs it to detect the group headers. */
    public static final String START_TAG = "<b>";

    /** End tag wrapper for the group labels. */
    private static final String END_TAG = "</b>";

    /** Form indentation for the groups. */
    private static final String INDENTATION = "";

    static {
        baseEntries = new ArrayList<FormProperty>();
        baseEntries.add(new TextInputProperty("server", true));
        baseEntries.add(new TextInputProperty("port", true, "2099"));
        /* Story 2888
        String tunnels[] = { "noTunneling", "httpTunneling",
            "httpsTunneling" };
        baseEntries.add(new RadioSelectProperty("enableHttpTunneling",
                                                tunnels, tunnels[0]));
        */
        baseEntries.add(new TextInputProperty("displayUrl", true));
        baseEntries.add(
            new BooleanSelectProperty("ignoreDisplayUrlErrors", "false"));

        /* Story 2888
        String auths[] = { "livelinkAuthentication",
                           "httpBasicAuthentication",
                           "integratedWindowsAuthentication" };
        baseEntries.add(new RadioSelectProperty("userAuthentication",
                                                auths, auths[0]));
        */

        adminLabel = new LabelProperty("livelinkAdmin");
        adminEntries = new ArrayList<FormProperty>();
        adminEntries.add(new TextInputProperty("username", true));
        adminEntries.add(new PasswordInputProperty("Password", true));
        adminEntries.add(new TextInputProperty("domainName"));

        // These record the state of the enablers. They are a little
        // different from each other, because the useHttpTunneling
        // property is only used by LivelinkConnectorType and its
        // helpers. The useSeparateAuthentication property, on the
        // other hand, is passed along through the
        // connectorInstance.xml file to the LivelinkConnector class,
        // which uses it to determine whether any of the
        // authentication parameters should be used.
        hiddenEntries = new ArrayList<FormProperty>();
        hiddenEntries.add(
            new BooleanSelectProperty("useHttpTunneling", "false"));
        hiddenEntries.add(
            new BooleanSelectProperty("useSeparateAuthentication", "false"));

        tunnelingEnabler =
            new EnablerProperty("enableHttpTunneling", "false");
        tunnelingEntries = new ArrayList<FormProperty>();
        tunnelingEntries.add(new TextInputProperty("livelinkCgi", true));
        tunnelingEntries.add(new TextInputProperty("httpUsername"));
        tunnelingEntries.add(new PasswordInputProperty("httpPassword"));
        tunnelingEntries.add(
            new BooleanSelectProperty("enableNtlm", "false"));
        tunnelingEntries.add(new BooleanSelectProperty("https", "false"));
        tunnelingEntries.add(
            new BooleanSelectProperty("useUsernamePasswordWithWebServer",
                "false"));

        authenticationEnabler =
            new EnablerProperty("enableSeparateAuthentication", "false");

        authenticationEntries = new ArrayList<FormProperty>();
        authenticationEntries.add(
            new MessageProperty("authenticationMessage", null,
                "deprecateSeparateAuthentication"));
        authenticationEntries.add(
            new TextInputProperty("authenticationServer", true));
        authenticationEntries.add(
            new TextInputProperty("authenticationPort", true, "80"));
        authenticationEntries.add(
            new TextInputProperty("authenticationLivelinkCgi", true));
        authenticationEntries.add(
            new BooleanSelectProperty("authenticationEnableNtlm", "false"));
        authenticationEntries.add(
            new BooleanSelectProperty("authenticationHttps", "false"));
        authenticationEntries.add(
            new TextInputProperty("authenticationDomainName"));
        authenticationEntries.add(
            new BooleanSelectProperty(
                "authenticationUseUsernamePasswordWithWebServer", "false"));

        // Indexing Traversal configuration
        indexingLabel = new LabelProperty("indexing");
        indexingEntries = new ArrayList<FormProperty>();
        indexingEntries.add(
            new TextInputProperty("traversalUsername", false));
        indexingEntries.add(new TextInputProperty("includedLocationNodes"));
    }

    private final Map<String, String> data;
    private final ResourceBundle labels;
    private FormContext formContext;

    FormBuilder(ResourceBundle labels) {
      this(labels, Collections.<String, String>emptyMap(),
          new FormContext(Collections.<String, String>emptyMap()));
    }

    FormBuilder(ResourceBundle labels, Map<String, String> data,
        FormContext formContext) {
      this.labels = labels;
      this.data = data;
      this.formContext = formContext;
    }

    private String getProperty(String name) {
      return data.get(name);
    }

    private void addEntries(StringBuilder buffer,
        ArrayList<FormProperty> entries, boolean hide, String labelPrefix,
        String labelSuffix) {
      for (FormProperty prop : entries) {
        addEntry(buffer, prop, hide, labelPrefix, labelSuffix);
      }
    }

    private void addEntry(StringBuilder buffer, FormProperty prop,
        boolean hide, String labelPrefix, String labelSuffix) {
      if (hide)
        prop = new HiddenInputProperty(prop);
      if (formContext.isHidden(prop.name))
        prop = new HiddenInputProperty(prop);
      prop.addToBuffer(buffer, labelPrefix, labelSuffix,
          getProperty(prop.name), labels);
    }

    private boolean hide(String name) {
      String value = getProperty(name);
      return value == null || ! new Boolean(value).booleanValue();
    }

    String getFormSnippet() {
      StringBuilder buffer = new StringBuilder(4096);
      addEntries(buffer, baseEntries, false, "", "");
      addEntry(buffer, adminLabel, false, START_TAG, END_TAG);
      addEntries(buffer, adminEntries, false, INDENTATION, "");
      addEntries(buffer, hiddenEntries, true, null, null);
      addEntry(buffer, tunnelingEnabler, false, START_TAG, END_TAG);
      addEntries(buffer, tunnelingEntries, hide("useHttpTunneling"),
          INDENTATION, "");
      addEntry(buffer, authenticationEnabler, false, START_TAG, END_TAG);
      addEntries(buffer, authenticationEntries,
          hide("useSeparateAuthentication"), INDENTATION, "");
      addEntry(buffer, indexingLabel, false, START_TAG, END_TAG);
      addEntries(buffer, indexingEntries, false, INDENTATION, "");
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
   * Localizes the resource bundle name.
   *
   * @param locale the locale to look up
   * @return the ResourceBundle
   * @throws MissingResourceException if the bundle can't be found
   */
  private ResourceBundle getResources(Locale locale)
      throws MissingResourceException {
    return ResourceBundle.getBundle(
        "config.OtexConnectorResources", locale);
  }

  /**
   * Returns a form snippet containing the given string as an error message.
   *
   * @param error the error message to include
   * @return a ConfigureResponse consisting of a form snippet
   * with just an error message
   */
  private ConfigureResponse getErrorResponse(String error) {
    StringBuilder buffer = new StringBuilder(
        "<tr><td colspan=\"2\"><font color=\"red\">");
    buffer.append(error); // FIXME: HTML escaping?
    buffer.append("</font></td></tr>");
    return new ConfigureResponse(null, buffer.toString());
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ConfigureResponse getConfigForm(Locale locale) {
    if (LOGGER.isLoggable(Level.CONFIG))
      LOGGER.config("getConfigForm locale: " + locale);
    try {
      return new ConfigureResponse(null,
          new FormBuilder(getResources(locale)).getFormSnippet());
    } catch (Throwable t) {
      LOGGER.log(Level.SEVERE, "Failed to create config form", t);
      return getErrorResponse(getExceptionMessages(null, t));
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ConfigureResponse getPopulatedConfigForm(
      Map<String, String> configData, Locale locale) {
    if (LOGGER.isLoggable(Level.CONFIG)) {
      LOGGER.config("getPopulatedConfigForm data: " +
          getMaskedMap(configData));
      LOGGER.config("getPopulatedConfigForm locale: " + locale);
    }
    try {
      return getResponse(null, getResources(locale), configData,
          new FormContext(configData));
    } catch (Throwable t) {
      LOGGER.log(Level.SEVERE, "Failed to create config form", t);
      return getErrorResponse(getExceptionMessages(null, t));
    }
  }

  /**
   * Helper method for <code>getPopulatedConfigForm</code> and
   * <code>validateConfig</code>.
   *
   * @param message A message to be included to the user along with the form
   * @param configData A map of name, value pairs (String, String)
   * of configuration data
   * @param formContext A context which may be configured by
   * the caller to affect the form generation
   */
  private ConfigureResponse getResponse(String message, ResourceBundle bundle,
      Map<String, String> configData, FormContext formContext) {
    if (LOGGER.isLoggable(Level.CONFIG))
      LOGGER.config("Response data: " + getMaskedMap(configData));
    if (message != null || formContext != null) {
      FormBuilder form = new FormBuilder(bundle, configData, formContext);
      return new ConfigureResponse(message, form.getFormSnippet());
    } else if (configData != null) {
      return new ConfigureResponse(null, null, configData);
    } else {
      return null;
    }
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
   * TODO: Add init method and parameter validation to
   * LivelinkConnector.
   */
  @Override
  public ConfigureResponse validateConfig(Map<String, String> configData,
      Locale locale, ConnectorFactory connectorFactory) {
    if (LOGGER.isLoggable(Level.CONFIG)) {
      LOGGER.config("validateConfig data: " + getMaskedMap(configData));
      LOGGER.config("validateConfig locale: " + locale);
    }

    try {
      ResourceBundle bundle = getResources(locale);
      FormContext formContext = new FormContext(configData);

      // We want to change the passed in properties, but avoid
      // changing the original configData parameter.
      HashMap<String, String> config = new HashMap<String, String>(configData);
      config.put(VERSION_PROPERTY, VERSION_NUMBER);

      // If the user supplied a URL as the server, pull out the host name.
      smartConfig(config);

      // Update the properties to copy the enabler properties to
      // the uses.
      Boolean changeHttp = changeFormDisplay(config,
          "useHttpTunneling", "enableHttpTunneling");
      Boolean changeAuth = changeFormDisplay(config,
          "useSeparateAuthentication", "enableSeparateAuthentication");

      // Skip validation if one of the groups has been enabled.
      // The configuration is probably incomplete in this case,
      // and at least one more call to validateConfig will be
      // made, so we will eventually validate any of the other
      // changes that have been made this time.
      if (changeHttp == Boolean.TRUE || changeAuth == Boolean.TRUE) {
        if (LOGGER.isLoggable(Level.FINEST)) {
          LOGGER.finest("SKIPPING VALIDATION: changeHttp = " +
              changeHttp + "; changeAuth = " + changeAuth);
        }
        return getResponse(null, bundle, config, formContext);
      }

      // Instantiate a LivelinkConnector to check connectivity.
      LivelinkConnector conn = null;
      try {
        conn = (LivelinkConnector)
            connectorFactory.makeConnector(config);
      } catch (Throwable t) {
        LOGGER.log(Level.WARNING, "Failed to create connector", t);
        t = t.getCause();
        while (t != null) {
          if (t instanceof PropertyBatchUpdateException) {
            PropertyAccessException[] pae =
                ((PropertyBatchUpdateException) t).
                getPropertyAccessExceptions();
            for (int i = 0; i < pae.length; i++)
              LOGGER.warning(pae[i].getMessage());
          } else
            LOGGER.warning(t.toString());
          t = t.getCause();
        }
        return getResponse(failedInstantiation(bundle), bundle,
            config, formContext);
      }

      if (!ignoreDisplayUrlErrors(config)) {
        try {
          String url = config.get("displayUrl");
          LOGGER.finer("Validating display URL " + url);
          validateUrl(url, bundle);
          url = conn.getPublicContentDisplayUrl();
          LOGGER.finer("Validating public content display URL " + url);
          validateUrl(url, bundle);
        } catch (UrlConfigurationException e) {
          LOGGER.log(Level.WARNING, "Error in configuration", e);
          formContext.setHideIgnoreDisplayUrlErrors(false);
          return getResponse(
              errorInConfiguration(bundle, e.getLocalizedMessage()),
              bundle, config, formContext);
        }
      }

      try {
        conn.login();
      } catch (LivelinkException e) {
        // XXX: Should this be an errorInConfiguration error?
        return getResponse(e.getLocalizedMessage(bundle), bundle,
            config, formContext);
      } catch (ConfigurationException c) {
        LOGGER.log(Level.WARNING, "Error in configuration", c);
        return getResponse(
            errorInConfiguration(bundle, c.getLocalizedMessage(bundle)),
            bundle, config, formContext);
      } catch (Throwable t) {
        LOGGER.log(Level.WARNING, "Error in configuration", t);
        return getResponse(
            errorInConfiguration(bundle, t.getLocalizedMessage()),
            bundle, config, formContext);
      }

      // Return the OK configuration.
      return getResponse(null, null, config, null);
    } catch (Throwable t) {
      // One last catch to be sure we return a message.
      LOGGER.log(Level.SEVERE, "Failed to create config form", t);
      return getErrorResponse(getExceptionMessages(null, t));
    }
  }

  /**
   * Try to be a bit intelligent with the configuration.
   * Try to handle common user errors.  Even devine some
   * missing properties based upon others that are supplied.
   */
  private void smartConfig(Map<String, String> config) {
    URL url;

    // If user tried to specify a URL for the host name,
    // try to extract the host name and port from the URL.
    String server = config.get("server");
    if (server != null && server.indexOf("://") > 0) {
      try {
        url = new URL(server);
        config.put("server", url.getHost());
        if (url.getPort() != -1) {
          String port = config.get("port");
          if (port == null || port.trim().length() == 0) {
            config.put("port", Integer.toString(url.getPort()));
          }
        }
      } catch (MalformedURLException e) {
        // Try to be accomodating ... up to a point.
        LOGGER.warning("Invalid host name supplied: " + server);
      }
    }

    // TODO: Use the displayUrl property to set the tunneling properties.
  }


  /**
   * @param key key name to test
   * @return true if the given key name describes a sensitive field
   */
  /* Copied from com.google.enterprise.connector.common.SecurityUtils. */
  private boolean isKeySensitive(String key) {
    return key.toLowerCase().indexOf("password") != -1;
  }

  /**
   * Gets a copy of the map with password property values masked.
   *
   * @param original a property map
   * @return a copy of the map with password property values
   * replaced by the string "[...]"
   */
  /* Copied to com.google.enterprise.connector.dctm.DctmConnectorType. */
  private Map<String, String> getMaskedMap(Map<String, String> original) {
    HashMap<String, String> copy = new HashMap<String, String>();
    for (Map.Entry<String, String> entry : original.entrySet()) {
      String key = entry.getKey();
      if (isKeySensitive(key))
        copy.put(key, "[...]");
      else
        copy.put(key, entry.getValue());
    }
    return copy;
  }

  /**
   * Checks whether a property for showing and hiding other
   * parameters on the form has changed. If it has changed, the
   * current state property value is changed to match the requested
   * state.
   *
   * @param config the form properties
   * @param useName the name of the current state property
   * @param enableName the name of the requested state property
   * @return <code>Boolean.TRUE</code> if the property should be enabled,
   * <code>Boolean.FALSE</code> if the property should be disabled, or
   * <code>null</code> if it has not changed
   */
  private Boolean changeFormDisplay(Map<String, String> config, String useName,
      String enableName) {
    if (LOGGER.isLoggable(Level.FINE)) {
      LOGGER.fine("ENABLED " + enableName + ": " + config.get(enableName));
    }
    boolean enable =
        new Boolean(config.get(enableName)).booleanValue();
    boolean use = new Boolean(config.get(useName)).booleanValue();
    if (enable != use) {
      config.put(useName, enable ? "true" : "false");
      if (LOGGER.isLoggable(Level.FINE))
        LOGGER.fine("SETTING " + useName + ": " + enable);
      return enable ? Boolean.TRUE : Boolean.FALSE;
    } else
      return null;
  }

  /**
   * Formats and returns an errorInConfiguration message from
   * the given bundle.
   *
   * @param bundle the resource bundle
   * @param message the error message
   * @return the formatted error message
   * @throws MissingResourceException if the message isn't found
   */
  private String errorInConfiguration(ResourceBundle bundle,
      String message) {
    return MessageFormat.format(bundle.getString("errorInConfiguration"),
        new Object[] { message });
  }

  /**
   * Formats and returns a failedInstatiation message from
   * the given bundle.
   *
   * @param bundle the resource bundle
   * @param message the error message
   * @return the formatted error message
   * @throws MissingResourceException if the message isn't found
   */
  private String failedInstantiation(ResourceBundle bundle) {
    return bundle.getString("failedInstantiation");
  }

  /**
   * Formats and returns a httpNotFound message from the given
   * bundle.
   *
   * @param bundle the resource bundle
   * @param urlString the missing URL
   * @return the formatted error message
   * @throws MissingResourceException if the message isn't found
   */
  private String httpNotFound(ResourceBundle bundle, String urlString,
      int httpResponseCode, String httpResponseMessage) {
    return MessageFormat.format(bundle.getString("httpNotFound"),
        new Object[] { urlString, new Integer(httpResponseCode),
                       httpResponseMessage });
  }

  /**
   * Attempts to validate a URL.
   *
   * @param urlString the URL to test
   * @see UrlValidator#validate
   */
  /*
   * This method has package access and returns the HTTP response
   * code so that it can be unit tested. A return value of 0 is
   * arbitrary and unused by the tests.
   */
  @VisibleForTesting
  int validateUrl(String urlString, ResourceBundle bundle)
      throws UrlConfigurationException {
    try {
      UrlValidator validator = new UrlValidator();
      validator.setRequireFullyQualifiedHostNames(true);
      return validator.validate(urlString);
    } catch (UrlValidatorException e) {
      // FIXME: The Not found message is confusing with an error about
      // fully-qualified host names. I think the validator should
      // throw a different exception in that case, and we should
      // provide a different message.
      throw new UrlConfigurationException(
          httpNotFound(bundle, urlString, e.getStatusCode(),
              e.getMessage()));
    } catch (Throwable t) {
      LOGGER.log(Level.WARNING, "Error in Livelink URL validation", t);
      String text = getExceptionMessages(urlString, t);
      throw new UrlConfigurationException(text, t);
    }
  }

  /**
   * Returns the exception's message, or the exception class
   * name if no message is present.
   *
   * @param t the exception
   * @return a message
   */
  private String getExceptionMessage(Throwable t) {
    String message = t.getLocalizedMessage();
    if (message != null)
      return message;
    return t.getClass().getName();
  }

  /**
   * Returns a message containing the description text, the
   * message from the given exception, and any chained
   * exception messages.
   *
   * @param description the description text
   * @param t the exception
   * @return a message
   */
  private String getExceptionMessages(String description, Throwable t) {
    StringBuilder buffer = new StringBuilder();
    if (description != null)
      buffer.append(description).append(" ");
    buffer.append(getExceptionMessage(t)).append(" ");
    Throwable next = t;
    while ((next = next.getCause()) != null)
      buffer.append(getExceptionMessage(next));
    return buffer.toString();
  }

  /**
   * Return a boolean representing the state of the
   * ignoreDisplayUrlErrors configuration property.
   */
  private static boolean ignoreDisplayUrlErrors(Map<String, String> config) {
    String value = config.get("ignoreDisplayUrlErrors");
    if (value != null) {
      return new Boolean(value).booleanValue();
    }
    return false;
  }
}
