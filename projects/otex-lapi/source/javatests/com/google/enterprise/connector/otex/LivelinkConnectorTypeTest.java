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
import java.util.Properties;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;
import javax.swing.text.MutableAttributeSet;
import com.google.enterprise.connector.spi.ConfigureResponse;
import junit.framework.TestCase;

/**
 * Tests the LivelinkConnectorType implementation.
 */
public class LivelinkConnectorTypeTest extends TestCase {
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
            else
                fail("Unexpected HTML tag " + tag); 
        }

        public void handleText(char[] data, int pos) {
            if (currentText != null)
                currentText.append(data); 
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

    private static final Properties emptyProperties =
        LivelinkConnectorFactory.emptyProperties;

    private LivelinkConnectorType connectorType; 

    protected void setUp() {
        connectorType = new LivelinkConnectorType(); 
    }

    /**
     * Tests the default empty config form.
     */
    public void testGetConfigForm() throws Exception {
        HashMap form = getForm(connectorType.getConfigForm(null)); 
        assertNotNull("Missing hostname element", form.get("hostname")); 
        assertBooleanIsTrue(form, "useHttps"); 
        assertBooleanIsTrue(form, "enableNtlm"); 
        assertBooleanIsTrue(form, "verifyServer"); 
        assertBooleanIsFalse(form, "useUsernamePasswordWithWebServer"); 
        assertBooleanIsFalse(form, "useSeparateAuthentication"); 
        assertIsHidden(form, "authenticationHostname"); 
    }

    /**
     * Tests prepopulating a config form.
     */
    public void testGetPopulatedConfigForm() throws Exception {
        HashMap data = new HashMap();
        data.put("hostname", "myhostname");
        data.put("port", "myport");
        data.put("useHttps", "false"); 
        HashMap form = getForm(
            connectorType.getPopulatedConfigForm(data, null));
        assertValue(form, "hostname", "myhostname"); 
        assertIsHidden(form, "authenticationHostname"); 
        assertBooleanIsFalse(form, "useHttps"); 
    }

    /**
     * Tests prepopulating a config form with authentication
     * parameters displayed.
     */
    public void testGetPopulatedConfigFormWithAuthentication()
            throws Exception {
        HashMap data = new HashMap();
        data.put("hostname", "myhostname");
        data.put("port", "111"); 
        data.put("useSeparateAuthentication", "true"); 
        data.put("authenticationHostname", "myauthhostname");
        data.put("authenticationPort", "222"); 
        HashMap form = getForm(
            connectorType.getPopulatedConfigForm(data, null));
        assertValue(form, "hostname", "myhostname"); 
        assertValue(form, "port", "111"); 
        assertValue(form, "authenticationHostname", "myauthhostname"); 
        assertValue(form, "authenticationPort", "222"); 
        assertIsNotHidden(form, "authenticationHostname"); 
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
            connectorType.validateConfig(emptyProperties, null);
        assertNotNull("Missing ConfigureResponse", response); 
        HashMap form = getForm(response);
        assertValue(form, "hostname", ""); 
        assertIsHidden(form, "authenticationHostname"); 
        assertBooleanIsTrue(form, "useHttps"); 
    }

    /**
     * Tests the validateConfig method with input which doesn't
     * map to a valid Livelink server.
     */
    public void testValidateConfigInvalidLivelinkInput() throws Exception {
        HashMap props = new HashMap(emptyProperties);
        props.put("hostname", "myhost"); 
        props.put("port", "123");
        props.put("username", "me");
        props.put("password", "pw"); 
        ConfigureResponse response = 
            connectorType.validateConfig(props, null);
        assertNotNull("Missing ConfigureResponse", response); 
        HashMap form = getForm(response);
        assertValue(form, "hostname", "myhost"); 
        assertValue(form, "port", "123"); 
        assertValue(form, "username", "me");
        assertValue(form, "password", "pw"); 
        assertBooleanIsTrue(form, "useHttps");
        assertIsHidden(form, "authenticationHostname"); 
    }

    /**
     * Tests the validateConfig method with input which doesn't
     * map to a valid Livelink server.
     */
    public void testValidateConfigValidLivelinkInput() throws Exception {
        HashMap props = new HashMap(emptyProperties);
        props.put("hostname", System.getProperty("connector.hostname")); 
        props.put("port", System.getProperty("connector.port"));
        props.put("username", System.getProperty("connector.username"));
        props.put("password", System.getProperty("connector.password")); 
        ConfigureResponse response = 
            connectorType.validateConfig(props, null);
        assertNull("Got ConfigureResponse with valid input", response); 
    }       
    
    private HashMap getForm(ConfigureResponse response) throws Exception {
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

    private void assertValue(HashMap form, String name, 
            String expectedValue) {
        HashMap element = (HashMap) form.get(name);
        assertNotNull("Missing " + name, element);
        String value = (String) element.get("value");
        assertNotNull("No value for " + name, value);
        assertEquals("Unexpected value for " + name, expectedValue, value); 
    }

    private void assertIsHidden(HashMap form, String name) {
        HashMap element = (HashMap) form.get(name);
        assertNotNull("Missing " + name, element);
        assertEquals("Type not hidden for " + name, "hidden",
            element.get("type")); 
    }

    private void assertIsNotHidden(HashMap form, String name) {
        HashMap element = (HashMap) form.get(name);
        assertNotNull("Missing " + name, element);
        assertFalse("Type is hidden for " + name, 
            "hidden".equals(element.get("type"))); 
    }

    private void assertBooleanIsTrue(HashMap form, String name) {
        booleanIs(form, name, true);
    }
    
    private void assertBooleanIsFalse(HashMap form, String name) {
        booleanIs(form, name, false);
    }
    
    private void booleanIs(HashMap form, String name, boolean expected) {
        HashMap element = (HashMap) form.get(name);
        assertNotNull("Missing " + name, element);
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
    }
}
