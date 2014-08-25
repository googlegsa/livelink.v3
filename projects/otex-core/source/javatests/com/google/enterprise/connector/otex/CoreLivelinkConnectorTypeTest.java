// Copyright (C) 2007-2009 Google Inc.
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

import com.google.enterprise.connector.spi.ConfigureResponse;
import com.google.enterprise.connector.util.XmlParseUtil;

import junit.framework.TestCase;

import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.net.URI;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.logging.Logger;

import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;

/**
 * Tests the LivelinkConnectorType implementation.
 */
public class CoreLivelinkConnectorTypeTest extends TestCase {
  /** The logger for this class. */
  private static final Logger LOGGER =
      Logger.getLogger(CoreLivelinkConnectorTypeTest.class.getName());

  /**
   * Processes the Livelink Connector configuration form and
   * makes the elements and their attributes and values
   * available for tests.
   */
  static class FormElementsCallback extends HTMLEditorKit.ParserCallback {
    // Use a list if we care about the element order on the form.
    Map<String, Map<String, Object>> properties =
        new HashMap<String, Map<String, Object>>();

    Map<String, Object> currentProperty;

    ArrayList<Map<String, Object>> currentOptions;

    StringBuilder currentText;

    FormElementsCallback() {
    }

    Map<String, Map<String, Object>> getProperties() {
      return properties;
    }

    void cacheCurrentProperty() {
      if (currentProperty == null)
        return;
      String name = (String) currentProperty.get("name");
      if (name == null)
        return;

      // If there are already any properties associated
      // with this input, merge them.  This allows us
      // to associate labels w/inputs.
      Map<String, Object> already = properties.get(name);
      if ( already != null ){
        already.putAll(currentProperty);
        currentProperty = already;
      }
      properties.put(name, currentProperty);
      currentProperty = null;
    }

    void startNewProperty() {
      cacheCurrentProperty();
      currentProperty = new HashMap<String, Object>();
    }

    void cacheAttrs(Map<String, Object> cache, MutableAttributeSet attrs) {
      Enumeration<?> attrNames = attrs.getAttributeNames();
      while (attrNames.hasMoreElements()) {
        Object attr = attrNames.nextElement();
        cache.put(attr.toString(), attrs.getAttribute(attr));
      }
    }

    /* Only handles the tags we support on the form. */
    void doTag(HTML.Tag tag, MutableAttributeSet attrs) {
      String tagname = tag.toString();

      if (tag == HTML.Tag.INPUT) {
        startNewProperty();
        currentProperty.put("tag", tagname);
        cacheAttrs(currentProperty, attrs);
        cacheCurrentProperty();
      } else if (tag == HTML.Tag.SELECT) {
        startNewProperty();
        currentProperty.put("tag", tagname);
        cacheAttrs(currentProperty, attrs);
        currentOptions = new ArrayList<Map<String, Object>>();
      } else if (tag == HTML.Tag.OPTION) {
        HashMap<String, Object> option = new HashMap<String, Object>();
        cacheAttrs(option, attrs);
        currentOptions.add(option);
      } else if (tag == HTML.Tag.TEXTAREA) {
        startNewProperty();
        currentProperty.put("tag", tagname);
        cacheAttrs(currentProperty, attrs);
        currentText = new StringBuilder();
      } else if (tag == HTML.Tag.HTML ||
          tag == HTML.Tag.HEAD ||
          tag == HTML.Tag.BODY ||
          tag == HTML.Tag.TABLE ||
          tag == HTML.Tag.TR ||
          tag == HTML.Tag.TD) {
        // skip
      } else if (tagname.equals("label")) {
        // If this is a label for an input, store the
        // label value as a property of the input.
        if (attrs.getAttribute(HTML.Attribute.ENDTAG) != null) {
          // If it's an end tag, preserve the label text.
          // This will get merged w/the actual input's attrs.
          // The name will have been set at the start tag (below)
          //   We have to do this here because "label" isn't
          // recognized by the default DTD as a start or end tag.
          currentProperty.put("label", currentText.toString());
        }
        startNewProperty();
        currentProperty.put("tag", tagname);
        String labelFor = (String) attrs.getAttribute("for");
        if (labelFor != null) { // only true for start tags.
          currentProperty.put("name", labelFor);
        }
        cacheAttrs(currentProperty, attrs);
        currentText = new StringBuilder();
      } else {
        LOGGER.fine("<" + tag + ">");
        //fail("Unexpected HTML tag " + tag);
      }
    }

    @Override
    public void handleText(char[] data, int pos) {
      if (currentText != null) {
        currentText.append(data);
        LOGGER.fine(new String(data));
      }
    }

    @Override
    public void handleStartTag(HTML.Tag tag,
        MutableAttributeSet attrs, int pos) {
      doTag(tag, attrs);
    }

    @Override
    public void handleSimpleTag(HTML.Tag tag,
        MutableAttributeSet attrs, int pos) {
      doTag(tag, attrs);
    }

    @Override
    public void handleEndTag(HTML.Tag tag, int pos) {
      if (tag == HTML.Tag.SELECT) {
        currentProperty.put("options", currentOptions);
        cacheCurrentProperty();
      } else if (tag == HTML.Tag.TEXTAREA) {
        currentProperty.put("text", currentText.toString());
        cacheCurrentProperty();
      }
    }

    @Override
    public String toString() {
      return properties.toString();
    }
  };

  protected static final Map<String, String> emptyProperties =
      LivelinkConnectorFactory.emptyProperties;

  private final JdbcFixture jdbcFixture = new JdbcFixture();

  protected LivelinkConnectorType connectorType;

  @Override
  protected void setUp() throws SQLException {
    jdbcFixture.setUp();

    connectorType = new LivelinkConnectorType();
  }

  @Override
  protected void tearDown() throws SQLException {
    jdbcFixture.tearDown();
  }

  /** The default locale for use in creating config forms. */
  protected static final Locale defaultLocale = Locale.getDefault();

  /**
   * Tests the default empty config form.
   */
  public void testGetConfigForm() throws Exception {
    Map<String, Map<String, Object>> form =
        getForm(connectorType.getConfigForm(defaultLocale));
    assertNotNull("Missing server element", form.get("server"));
    assertBooleanIsFalse(form, "https");
    assertBooleanIsFalse(form, "useUsernamePasswordWithWebServer");
    assertBooleanIsFalse(form, "useSeparateAuthentication");
    assertIsHidden(form, "authenticationServer");
  }

  /**
   * Tests that the config form labels are responsive to the
   * locale parameter.  Testing that the form is ok is done in
   * testGetConfigForm(), so here we just check that the labels
   * change with the locale.
   */
  public void testGetConfigFormOtherLocale() throws Exception {
    Map<String, Map<String, Object>> form =
        getForm(connectorType.getConfigForm(defaultLocale));
    Locale locale = new Locale("test");
    Map<String, Map<String, Object>> testForm =
        getForm(connectorType.getConfigForm(locale));

    // We'll look at the server input since any one will do.
    Map<String, Object> element = form.get("server");
    assertNotNull("Missing server element (" + defaultLocale + ")", element);
    String defaultLabel = element.get("label").toString();
    assertNotNull("Missing server element label (" + defaultLocale + ")",
        defaultLabel);

    element = testForm.get("server");
    assertNotNull("Missing server element (" + locale + ")",  element);
    String testLabel = element.get("label").toString();
    assertNotNull("Missing server element label (" + locale + ")", testLabel);

    // make sure they're different
    assertFalse("The two labels are the same. (" + testLabel + "," +
        defaultLabel + ")", testLabel.equals(defaultLabel));
    // and that the server label is what it should be
    assertEquals("Did not find expected label.", testLabel, "Test:Host");
  }

  /**
   * Tests that untranslated labels use the English labels as the default.
   */
  public void testGetConfigFormUntranslated() throws Exception {
    Map<String, Map<String, Object>> form =
        getForm(connectorType.getConfigForm(defaultLocale));
    Locale locale = new Locale("test");
    Map<String, Map<String, Object>> testForm =
        getForm(connectorType.getConfigForm(locale));

    // We'll look at the traversalUsername input, which doesn't
    // appear in the test resource bundle.
    Map<String, Object> element = form.get("traversalUsername");
    assertNotNull("Missing server element (" + defaultLocale + ")", element);
    String defaultLabel = element.get("label").toString();
    assertNotNull("Missing server element label (" + defaultLocale + ")",
        defaultLabel);

    element = testForm.get("traversalUsername");
    assertNotNull("Missing server element (" + locale + ")",  element);
    String testLabel = element.get("label").toString();
    assertNotNull("Missing server element label (" + locale + ")", testLabel);

    // make sure they're the same
    assertEquals(defaultLabel, testLabel);

    // Now grab the test resource bundle and make sure the key is missing.
    InputStream in = ClassLoader.getSystemResourceAsStream(
        "config/OtexConnectorResources_test.properties");
    assertNotNull(in);
    Properties testProperties = new Properties();
    testProperties.load(in);
    assertFalse(testProperties.containsKey("traversalUsername"));
    assertNull(testProperties.getProperty("traversalUsername"));
  }

  /* Tests a empty config form for valid XHTML. */
  public void testXhtmlConfigForm() throws Exception {
    ConfigureResponse response = connectorType.getConfigForm(defaultLocale);

    Map<String, Map<String, Object>> form = getForm(response);
    assertIsHidden(form, "livelinkCgi");
    assertIsHidden(form, "authenticationServer");

    String formSnippet = response.getFormSnippet();
    XmlParseUtil.validateXhtml(formSnippet);
  }

  /* Tests an expanded empty config form for valid XHTML. */
  public void testXhtmlPopulatedConfigForm() throws Exception {
    Map<String, String> data = new HashMap<String, String>();
    data.put("useHttpTunneling", "true");
    data.put("useSeparateAuthentication", "true");
    ConfigureResponse response =
        connectorType.getPopulatedConfigForm(data, defaultLocale);

    Map<String, Map<String, Object>> form = getForm(response);
    assertIsNotHidden(form, "livelinkCgi");
    assertIsNotHidden(form, "authenticationServer");

    String formSnippet = response.getFormSnippet();
    XmlParseUtil.validateXhtml(formSnippet);
  }

  /**
   * Tests prepopulating a config form.
   */
  public void testGetPopulatedConfigForm() throws Exception {
    Map<String, String> data = new HashMap<String, String>();
    data.put("server", "myhostname");
    data.put("port", "myport");
    data.put("https", "false");
    Map<String, Map<String, Object>> form = getForm(
        connectorType.getPopulatedConfigForm(data, defaultLocale));
    assertValue(form, "server", "myhostname");
    assertIsHidden(form, "authenticationServer");
    assertBooleanIsFalse(form, "https");
  }

  /**
   * Tests prepopulating a config form with authentication
   * parameters displayed.
   */
  public void testGetPopulatedConfigFormWithAuthentication()
      throws Exception {
    Map<String, String> data = new HashMap<String, String>();
    data.put("server", "myhostname");
    data.put("port", "111");
    data.put("useSeparateAuthentication", "true");
    data.put("authenticationServer", "myauthhostname");
    data.put("authenticationPort", "222");
    Map<String, Map<String, Object>> form = getForm(
        connectorType.getPopulatedConfigForm(data, defaultLocale));
    assertValue(form, "server", "myhostname");
    assertValue(form, "port", "111");
    assertValue(form, "authenticationServer", "myauthhostname");
    assertValue(form, "authenticationPort", "222");
    assertIsNotHidden(form, "authenticationServer");
  }

  /**
   * Tests the validateConfig method with input which fails during
   * Spring instantiation. The server value is empty so validation
   * fails.
   */
  public void testValidateConfigBadInput() throws Exception {
    ConfigureResponse response =
        connectorType.validateConfig(emptyProperties, defaultLocale,
            LivelinkConnectorFactory.getInstance());
    assertNotNull("Missing ConfigureResponse", response);
    Map<String, Map<String, Object>> form = getForm(response);
    assertValue(form, "server", "");
    assertIsHidden(form, "authenticationServer");
    assertBooleanIsFalse(form, "https");
  }

  /**
   * Tests the validateConfig method with input which doesn't map to
   * a valid Livelink server. In this mock world, we don't detect
   * this as an error. In the LAPI world, we override this method
   * and expect an error.
   */
  public void testValidateConfigInvalidLivelinkInput() throws Exception {
    Map<String, String> props = new HashMap<String, String>(emptyProperties);
    props.put("server", "myhost");
    props.put("port", "123");
    props.put("username", "Admin");
    props.put("password", "pw");
    props.put("googleConnectorName", "llconnector");
    props.put("googleFeedHost", "localhost");
    ConfigureResponse response =
        connectorType.validateConfig(props, defaultLocale,
            LivelinkConnectorFactory.getInstance());
    assertValid(response);
  }

  /**
   * Tests the validateConfig method with input that maps to a valid
   * Livelink server.
   */
  public void testValidateConfigValidLivelinkInput() throws Exception {
    Map<String, String> props = getValidProperties();
    ConfigureResponse response =
        connectorType.validateConfig(props, defaultLocale,
            LivelinkConnectorFactory.getInstance());
    assertValid(response);
  }

  /**
   * Tests the validateConfig method with HTTP tunneling disabled
   * and an invalid Livelink CGI parameter.
   */
  public void testValidateConfigIgnoredLivelinkCgi() throws Exception {
    Map<String, String> props = getValidProperties();
    props.put("livelinkCgi", "/frog");
    props.put("https", "false");
    ConfigureResponse response =
        connectorType.validateConfig(props, defaultLocale,
            LivelinkConnectorFactory.getInstance());
    assertValid(response);
  }

  /**
   * Tests the validateConfig method with HTTP tunneling disabled
   * and HTTP enabled without the Secure Connect module.
   */
  public void testValidateConfigIgnoredHttps() throws Exception {
    Map<String, String> props = getValidProperties();
    props.put("livelinkCgi", "/frog");
    props.put("https", "true");
    ConfigureResponse response =
        connectorType.validateConfig(props, defaultLocale,
            LivelinkConnectorFactory.getInstance());
    assertValid(response);
  }

  /*
   * Tests the validateConfig method with HTTP tunneling disabled
   * and NTLM authentication enabled.
   */
  public void testValidateConfigIgnoredEnableNtlm() throws Exception {
    Map<String, String> props = getValidProperties();
    props.put("enableNtlm", "true");
    ConfigureResponse response =
        connectorType.validateConfig(props, defaultLocale,
            LivelinkConnectorFactory.getInstance());
    assertValid(response);
  }

  /**
   * Tests the validateConfig method with HTTP tunneling disabled
   * and sending the credentials to the web server enabled.
   */
  public void testValidateConfigIgnoredCredentials() throws Exception {
    Map<String, String> props = getValidProperties();
    props.put("useUsernamePasswordWithWebServer", "true");
    ConfigureResponse response =
        connectorType.validateConfig(props, defaultLocale,
            LivelinkConnectorFactory.getInstance());
    assertValid(response);
  }

  /**
   * Tests URL validation. This test requires the
   * connector.displayUrl system property to be set. The URL can
   * point at anything valid; it doesn't have to a Livelink server.
   */
  public void testValidateUrl() throws Exception {
    ResourceBundle bundle = ResourceBundle.getBundle(
        "config.OtexConnectorResources", defaultLocale);
    URI uri = new URI(System.getProperty("connector.displayUrl"));

    // Test 1: Configured URL (presumably correct)
    int code = connectorType.validateUrl(uri.toString(), bundle);
    assertTrue(code == 200 || code == 401);

    // Test 2: Invalid path
    try {
      // This should usually fail, but it won't if the entire
      // web server requires authorization.
      URI testUri = new URI(uri.getScheme(), uri.getAuthority(), "/Xyggy",
        uri.getQuery(), uri.getFragment());
      code = connectorType.validateUrl(testUri.toString(), bundle);
      assertEquals(401, code);
    } catch (UrlConfigurationException e) {
    }

    // Test 3: Invalid CGI
    // Some web servers issue a different error for invalid CGI
    // scripts in an existing CGI directory. This test requires an URL
    // with a valid CGI directory, including an actual Livelink URL.
    if (uri.getPath() != null && uri.getPath().lastIndexOf('/') > 0) {
      try {
        // This should usually fail, but it won't if the entire
        // web server requires authorization.
        String path = uri.getPath().replaceFirst("[^/]+$", "xyggy.exe");
        URI testUri = new URI(uri.getScheme(), uri.getAuthority(), path,
          uri.getQuery(), uri.getFragment());
        code = connectorType.validateUrl(testUri.toString(), bundle);
        assertEquals(401, code);
      } catch (UrlConfigurationException e) {
      }
    }

    // Test 4: Invalid host
    try {
      String url = "http://xyggy/";
      code = connectorType.validateUrl(url, bundle);
      fail(url);
    } catch (UrlConfigurationException e) {
    }

    // Test 5: Invalid protocol
    try {
        URI testUri = new URI("xyggy", uri.getAuthority(), uri.getPath(),
            uri.getQuery(), uri.getFragment());
      code = connectorType.validateUrl(testUri.toString(), bundle);
      fail(testUri.toString());
    } catch (UrlConfigurationException e) {
    }

    // Test 6: Host not fully-qualified
    try {
      URI testUri = new URI(uri.getScheme(), uri.getUserInfo(), "localhost",
            uri.getPort(), uri.getPath(), uri.getQuery(), uri.getFragment());
      code = connectorType.validateUrl(testUri.toString(), bundle);
      fail(testUri.toString());
    } catch (UrlConfigurationException e) {
      assertTrue(e.toString(), e.getMessage().indexOf("412") != -1);
    }
  }

  /** Helper method to get valid direct connection properties. */
  protected Map<String, String> getValidProperties() {
    Map<String, String> props = new HashMap<String, String>();
    props.putAll(emptyProperties);
    props.put("server", System.getProperty("connector.server"));
    props.put("port", System.getProperty("connector.port"));
    props.put("username", System.getProperty("connector.username"));
    props.put("Password", System.getProperty("connector.Password"));
    props.put("googleConnectorName",
        System.getProperty("connector.googleConnectorName"));
    props.put("googleFeedHost", System.getProperty("connector.googleFeedHost"));
    return props;
  }

  protected Map<String, Map<String, Object>> getForm(
      ConfigureResponse response) throws Exception {
    String snippet = response.getFormSnippet();
    if (Boolean.getBoolean("printform")) {
      System.out.println(snippet);
      System.out.println("Form length: " + snippet.length());
    }
    Reader reader = new StringReader("<table>" +
        response.getFormSnippet() + "</table>");
    FormElementsCallback callback = new FormElementsCallback();
    new ParserDelegator().parse(reader, callback, false);
    return callback.getProperties();
  }

  protected void assertValid(ConfigureResponse response) {
    // A valid response is either a null response or a
    // response with no message or form snippet.
    if (response != null) {
      String resMessage = response.getMessage();
      if (response.getFormSnippet() != null || resMessage != null) {
        String message = "Got ConfigureResponse with valid input";
        if (resMessage != null)
          message += ": " + resMessage;
        fail(message);
      }
    }
  }

  protected void assertInvalid(ConfigureResponse response) {
    // A valid response is either a null response or a
    // response with no message or form snippet.
    if (response == null) {
      fail("Got no ConfigureResponse with invalid input");
    } else if (response.getMessage() == null &&
        response.getFormSnippet() == null) {
      fail("Got error-free ConfigureResponse with invalid input");
    }
  }

  protected void assertValue(Map<String, Map<String, Object>> form,
      String name, String expectedValue) {
    Map<String, Object> element = form.get(name);
    assertNotNull("Missing " + name, element);
    String value = (String) element.get("value");
    assertNotNull("No value for " + name, value);
    assertEquals("Unexpected value for " + name, expectedValue, value);
  }

  protected void assertIsHidden(Map<String, Map<String, Object>> form,
      String name) {
    Map<String, Object> element = form.get(name);
    assertNotNull("Missing " + name, element);
    assertEquals("Type not hidden for " + name, "hidden",
        element.get("type"));
  }

  protected void assertIsNotHidden(Map<String, Map<String, Object>> form,
      String name) {
    Map<String, Object> element = form.get(name);
    assertNotNull("Missing " + name, element);
    assertFalse("Type is hidden for " + name,
        "hidden".equals(element.get("type")));
  }

  protected void assertBooleanIsTrue(Map<String, Map<String, Object>> form,
      String name) {
    booleanIs(form, name, true);
  }

  protected void assertBooleanIsFalse(Map<String, Map<String, Object>> form,
      String name) {
    booleanIs(form, name, false);
  }

  /* Verify that the given input has the given boolean value.  How
   * to do that will depend on the type of input.
   */
  protected void booleanIs(Map<String, Map<String, Object>> form, String name,
      boolean expected) {
    Map<String, Object> element = form.get(name);
    assertNotNull("Missing " + name, element);

    String inputType = (String) element.get("type");
    assertNotNull("No input type found for " + name, inputType);

    // Look for the value according to the input type
    if ( inputType.equals("select") ) {
      // If it's a select, figure out which option is selected.
      // XXX: It's not obvious to me how to recover the parameterized
      // types here, so we're using raw types.
      @SuppressWarnings("unchecked") ArrayList<Map<String, String>> options =
          (ArrayList<Map<String, String>>) element.get("options");
      assertNotNull("Missing options for " + name, options);
      assertEquals("Number of options", 2, options.size());
      String selectedValue = null;
      for (int i = 0; i < 2; i++) {
        Map<String, String> option = options.get(i);
        String value = option.get("value");
        assertNotNull("Missing value for option in " + name, value);
        if (option.get("selected") != null) {
          if (selectedValue != null)
            fail("Two selected values for " + name);
          selectedValue = value;
          break;
        }
      }
      assertNotNull("No selected value for " + name, selectedValue);
      assertEquals("Expected option not selected for " + name, expected,
          new Boolean(selectedValue).booleanValue());
    } else if ( inputType.equals("hidden") ) {
      // If it's hidden, just look at the value
      String value = (String) element.get("value");
      assertNotNull("Missing value for " + name, value);
      assertEquals("Expected value not found for " + name, expected,
          new Boolean(value).booleanValue());
    } else {
      // other types can be added, but for know we don't know.
      fail("Don't know how to extract value from input type \"" +
          inputType + "\"");
    }
  }
}
