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


import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Locale;
import java.util.logging.Logger;
import java.util.MissingResourceException;
import java.util.Properties;
import java.util.ResourceBundle;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;
import javax.swing.text.MutableAttributeSet;
import com.google.enterprise.connector.spi.ConfigureResponse;
import junit.framework.TestCase;

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
        HashMap properties = new HashMap();

        HashMap currentProperty;

        ArrayList currentOptions;

        StringBuffer currentText;

        FormElementsCallback() {
        }

        HashMap getProperties() {
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
            HashMap already = (HashMap) properties.get(name);
            if ( already != null ){
                already.putAll(currentProperty);
                currentProperty = already;
            }
            properties.put(name, currentProperty);
            currentProperty = null;
        }

        void startNewProperty() {
            cacheCurrentProperty();
            currentProperty = new HashMap();
        }

        void cacheAttrs(HashMap cache, MutableAttributeSet attrs) {
            Enumeration attrNames = attrs.getAttributeNames();
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
            }
            else if (tag == HTML.Tag.SELECT) {
                startNewProperty();
                currentProperty.put("tag", tagname);
                cacheAttrs(currentProperty, attrs);
                currentOptions = new ArrayList();
            }
            else if (tag == HTML.Tag.OPTION) {
                HashMap option = new HashMap();
                cacheAttrs(option, attrs);
                currentOptions.add(option);
            }
            else if (tag == HTML.Tag.TEXTAREA) {
                startNewProperty();
                currentProperty.put("tag", tagname);
                cacheAttrs(currentProperty, attrs);
                currentText = new StringBuffer();
            }
            else if (tag == HTML.Tag.HTML ||
                tag == HTML.Tag.HEAD ||
                tag == HTML.Tag.BODY ||
                tag == HTML.Tag.TABLE ||
                tag == HTML.Tag.TR ||
                tag == HTML.Tag.TD) {
                // skip
            }
            else if (tagname.equals("label")) {
                // If this is a label for an input, store the
                // label value as a property of the input.
                if (attrs.getAttribute(HTML.Attribute.ENDTAG) != null) {
                    // If it's an end tag, preserve the label text.
                    // This will get merged w/the actual input's attrs.
                    // The name will have been set at the start tag (below)
                    //   We have to do this here because "label" isn't
                    // recognized by the default DTD as a start or end tag.
                    currentProperty.put("label", currentText);
                }
                startNewProperty();
                currentProperty.put("tag", tagname);
                String labelFor = (String) attrs.getAttribute("for");
                if (labelFor != null) { // only true for start tags.
                    currentProperty.put("name", labelFor);
                }
                cacheAttrs(currentProperty, attrs);
                currentText = new StringBuffer();
            }
            else {
                LOGGER.fine("<" + tag + ">");
                //fail("Unexpected HTML tag " + tag);
            }
        }

        public void handleText(char[] data, int pos) {
            if (currentText != null) {
                currentText.append(data);
                LOGGER.fine(new String(data));
            }
        }

        public void handleStartTag(HTML.Tag tag,
            MutableAttributeSet attrs, int pos) {
            doTag(tag, attrs);
        }

        public void handleSimpleTag(HTML.Tag tag,
            MutableAttributeSet attrs, int pos) {
            doTag(tag, attrs);
        }

        public void handleEndTag(HTML.Tag tag, int pos) {
            if (tag == HTML.Tag.SELECT) {
                currentProperty.put("options", currentOptions);
                cacheCurrentProperty();
            }
            else if (tag == HTML.Tag.TEXTAREA) {
                currentProperty.put("text", currentText.toString());
                cacheCurrentProperty();
            }
        }

        public String toString() {
            return properties.toString();
        }
    };

    protected static final Properties emptyProperties =
        LivelinkConnectorFactory.emptyProperties;

    protected LivelinkConnectorType connectorType;

    protected void setUp() {
        connectorType = new LivelinkConnectorType();
    }


    /** The default locale for use in creating config forms. */
    protected static final Locale defaultLocale = Locale.getDefault();


    /**
     * Tests the default empty config form.
     */
    public void testGetConfigForm() throws Exception {
        HashMap form = getForm(connectorType.getConfigForm(defaultLocale));
        assertNotNull("Missing server element", form.get("server"));
        assertBooleanIsTrue(form, "https");
        assertBooleanIsTrue(form, "enableNtlm");
        assertBooleanIsTrue(form, "verifyServer");
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
        HashMap form = getForm(connectorType.getConfigForm(defaultLocale));
        Locale locale = new Locale("test");
        HashMap testForm = getForm(connectorType.getConfigForm(locale));

        // We'll look at the server input since any one will do.
        HashMap element = (HashMap) form.get("server");
        assertNotNull("Missing server element (" + defaultLocale + ")",
            element);
        String defaultLabel = element.get("label").toString();
        assertNotNull("Missing server element label (" + defaultLocale + ")",
            defaultLabel);

        element = (HashMap) testForm.get("server");
        assertNotNull("Missing server element (" + locale + ")",  element);
        String testLabel = element.get("label").toString();
        assertNotNull("Missing server element label (" + locale + ")",
            testLabel);

        // make sure they're different
        assertFalse("The two labels are the same. (" + testLabel + "," +
            defaultLabel + ")", testLabel.equals(defaultLabel));
        // and that the server label is what it should be
        assertEquals("Did not find expected label.", testLabel, "Test:Host");
    }


    /**
     * Tests prepopulating a config form.
     */
    public void testGetPopulatedConfigForm() throws Exception {
        HashMap data = new HashMap();
        data.put("server", "myhostname");
        data.put("port", "myport");
        data.put("https", "false");
        HashMap form = getForm(
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
        HashMap data = new HashMap();
        data.put("server", "myhostname");
        data.put("port", "111");
        data.put("useSeparateAuthentication", "true");
        data.put("authenticationServer", "myauthhostname");
        data.put("authenticationPort", "222");
        HashMap form = getForm(
            connectorType.getPopulatedConfigForm(data, defaultLocale));
        assertValue(form, "server", "myhostname");
        assertValue(form, "port", "111");
        assertValue(form, "authenticationServer", "myauthhostname");
        assertValue(form, "authenticationPort", "222");
        assertIsNotHidden(form, "authenticationServer");
    }

    /**
     * Tests the validateConfig method with input which fails
     * during Spring instantiation.
     */
    /* The port value is empty so bean instantiation fails
     * because it can't be converted to a number.
     */
    public void testValidateConfigBadInput() throws Exception {
        ConfigureResponse response =
            connectorType.validateConfig(emptyProperties, defaultLocale, null);
        assertNotNull("Missing ConfigureResponse", response);
        HashMap form = getForm(response);
        assertValue(form, "server", "");
        assertIsHidden(form, "authenticationServer");
        assertBooleanIsTrue(form, "https");
    }

    /**
     * Tests the validateConfig method with input which doesn't map to
     * a valid Livelink server. In this mock world, we don't detect
     * this as an error. In the LAPI world, we override this method
     * and expect an error.
     */
    public void testValidateConfigInvalidLivelinkInput() throws Exception {
        HashMap props = new HashMap(emptyProperties);
        props.put("server", "myhost");
        props.put("port", "123");
        props.put("username", "me");
        props.put("password", "pw");
        ConfigureResponse response =
            connectorType.validateConfig(props, defaultLocale, null);
        assertNull("Missing ConfigureResponse", response);
    }

    /**
     * Tests the validateConfig method with input that maps to a valid
     * Livelink server.
     */
    public void testValidateConfigValidLivelinkInput() throws Exception {
        Properties props = getValidProperties();
        ConfigureResponse response =
            connectorType.validateConfig(props, defaultLocale, null);
        assertValid(response);
    }

    /**
     * Tests the validateConfig method with HTTP tunneling disabled
     * and an invalid Livelink CGI parameter.
     */
    public void testValidateConfigIgnoredLivelinkCgi() throws Exception {
        Properties props = getValidProperties();
        props.setProperty("livelinkCgi", "/frog");
        props.setProperty("https", "false");
        ConfigureResponse response =
            connectorType.validateConfig(props, defaultLocale, null);
        assertValid(response);
    }

    /**
     * Tests the validateConfig method with HTTP tunneling disabled
     * and HTTP enabled without the Secure Connect module.
     */
    public void testValidateConfigIgnoredHttps() throws Exception {
        Properties props = getValidProperties();
        props.setProperty("livelinkCgi", "/frog");
        props.setProperty("https", "true");
        ConfigureResponse response =
            connectorType.validateConfig(props, defaultLocale, null);
        assertValid(response);
    }

    /**
     * Tests the validateConfig method with HTTP tunneling disabled
     * and NTLM authentication enabled.
     */
    public void testValidateConfigIgnoredEnableNtlm() throws Exception {
        Properties props = getValidProperties();
        props.setProperty("enableNtlm", "true");
        ConfigureResponse response =
            connectorType.validateConfig(props, defaultLocale, null);
        assertValid(response);
    }

    /**
     * Tests the validateConfig method with HTTP tunneling disabled
     * and sending the credentials to the web server enabled.
     */
    public void testValidateConfigIgnoredCredentials() throws Exception {
        Properties props = getValidProperties();
        props.setProperty("useUsernamePasswordWithWebServer", "true");
        ConfigureResponse response =
            connectorType.validateConfig(props, defaultLocale, null);
        assertValid(response);
    }


    /** Helper method to get valid direct connection properties. */
    protected Properties getValidProperties() {
        Properties props = new Properties(emptyProperties);
        props.setProperty("server", System.getProperty("connector.server"));
        props.setProperty("port", System.getProperty("connector.port"));
        props.setProperty("username",
            System.getProperty("connector.username"));
        props.setProperty("password",
            System.getProperty("connector.password"));
        return props;
    }
    
    protected HashMap getForm(ConfigureResponse response) throws Exception {
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
        if (response != null) {
            String message = "Got ConfigureResponse with valid input";
            if (response.getMessage() != null)
                message += ": " + response.getMessage();
            fail(message);
        }
    }
    
    protected void assertInvalid(ConfigureResponse response) {
        assertNotNull("Got no ConfigureResponse with invalid input", response);
    }
    
    protected void assertValue(HashMap form, String name,
            String expectedValue) {
        HashMap element = (HashMap) form.get(name);
        assertNotNull("Missing " + name, element);
        String value = (String) element.get("value");
        assertNotNull("No value for " + name, value);
        assertEquals("Unexpected value for " + name, expectedValue, value);
    }

    protected void assertIsHidden(HashMap form, String name) {
        HashMap element = (HashMap) form.get(name);
        assertNotNull("Missing " + name, element);
        assertEquals("Type not hidden for " + name, "hidden",
            element.get("type"));
    }

    protected void assertIsNotHidden(HashMap form, String name) {
        HashMap element = (HashMap) form.get(name);
        assertNotNull("Missing " + name, element);
        assertFalse("Type is hidden for " + name,
            "hidden".equals(element.get("type")));
    }

    protected void assertBooleanIsTrue(HashMap form, String name) {
        booleanIs(form, name, true);
    }

    protected void assertBooleanIsFalse(HashMap form, String name) {
        booleanIs(form, name, false);
    }

    /* Verify that the given input has the given boolean value.  How
     * to do that will depend on the type of input.
     */
    protected void booleanIs(HashMap form, String name, boolean expected) {
        HashMap element = (HashMap) form.get(name);
        assertNotNull("Missing " + name, element);

        String inputType = (String) element.get("type");
        assertNotNull("No input type found for " + name, inputType);

        // Look for the value according to the input type
        if ( inputType.equals("select") ) {
            // If it's a select, figure out which option is selected
            ArrayList options = (ArrayList) element.get("options");
            assertNotNull("Missing options for " + name, options);
            assertEquals("Number of options", 2, options.size());
            String selectedValue = null;
            for (int i = 0; i < 2; i++) {
                HashMap option = (HashMap) options.get(i);
                String value = (String) option.get("value");
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
