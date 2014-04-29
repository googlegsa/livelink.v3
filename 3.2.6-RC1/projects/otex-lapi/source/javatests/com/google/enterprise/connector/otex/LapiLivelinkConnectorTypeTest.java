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

import com.google.enterprise.connector.spi.ConfigureResponse;

import java.util.HashMap;
import java.util.Map;

/**
 * Tests the LivelinkConnectorType implementation.
 */
public class LapiLivelinkConnectorTypeTest
    extends CoreLivelinkConnectorTypeTest {

  public void testUrlValidation() throws Exception {
    Map<String, String> props = getValidProperties();
    props.put("displayUrl", System.getProperty("validateurl.goodUrl"));
    ConfigureResponse response =
        connectorType.validateConfig(props, defaultLocale,
            LivelinkConnectorFactory.getInstance());
    assertValid(response);

    for (int i = 0; i < 5; i++) {
      String url = System.getProperty("validateurl.badUrl." + i);
      if (url == null)
        break;
      props.put("displayUrl", url);
      response = connectorType.validateConfig(props, defaultLocale,
          LivelinkConnectorFactory.getInstance());
      assertNotNull("Invalid URL succeeded:" + url, response);
      Map<String, Map<String, Object>> form = getForm(response);
      assertIsNotHidden(form, "ignoreDisplayUrlErrors");
      assertValue(form, "ignoreDisplayUrlErrors", "false");
    }

    props.put("displayUrl", System.getProperty("validateurl.badUrl.0"));
    props.put("ignoreDisplayUrlErrors", "true");
    response = connectorType.validateConfig(props, defaultLocale,
        LivelinkConnectorFactory.getInstance());
    assertValid(response);
  }

  /**
   * Tests the validateConfig method with input which doesn't map to
   * a valid Livelink server. Overrides the same method from
   * LivelinkConnectorType.
   */
  public void testValidateConfigInvalidLivelinkInput() throws Exception {
    HashMap<String, String> props =
        new HashMap<String, String>(emptyProperties);
    props.put("server", "myhost");
    props.put("port", "123");
    props.put("username", "me");
    props.put("Password", "pw");
    ConfigureResponse response =
        connectorType.validateConfig(props, defaultLocale,
            LivelinkConnectorFactory.getInstance());
    assertNotNull("Missing ConfigureResponse", response);
    Map<String, Map<String, Object>> form = getForm(response);
    assertValue(form, "server", "myhost");
    assertValue(form, "port", "123");
    assertValue(form, "username", "me");
    assertValue(form, "Password", "pw");
    assertBooleanIsFalse(form, "https");
    assertIsHidden(form, "authenticationServer");
  }

  /**
   * Tests the validateConfig method with HTTP tunneling enabled
   * and an invalid Livelink CGI parameter.
   */
  public void testValidateConfigInvalidLivelinkCgi() throws Exception {
    Map<String, String> props = getValidProperties();
    props.put("enableHttpTunneling", "true");
    props.put("useHttpTunneling", "true");
    props.put("livelinkCgi", "/frog");
    props.put("https", "false");
    ConfigureResponse response =
        connectorType.validateConfig(props, defaultLocale,
            LivelinkConnectorFactory.getInstance());
    assertInvalid(response);
  }

  /**
   * Tests the validateConfig method with HTTP tunneling enabled
   * and HTTPS enabled without the Secure Connect module.
   */
  public void testValidateConfigInvalidHttps() throws Exception {
    Map<String, String> props = getValidProperties();
    props.put("enableHttpTunneling", "true");
    props.put("useHttpTunneling", "true");
    props.put("livelinkCgi", "/frog");
    props.put("https", "true");
    ConfigureResponse response =
        connectorType.validateConfig(props, defaultLocale,
            LivelinkConnectorFactory.getInstance());
    assertInvalid(response);
  }

  /**
   * Tests the validateConfig method with HTTP tunneling enabled
   * and HTTPS enabled without the Secure Connect module, but no
   * Livelink CGI parameter.
   */
  public void testValidateConfigIgnoredHttps() throws Exception {
    Map<String, String> props = getValidProperties();
    props.put("enableHttpTunneling", "true");
    props.put("useHttpTunneling", "true");
    props.put("https", "true");
    ConfigureResponse response =
        connectorType.validateConfig(props, defaultLocale,
            LivelinkConnectorFactory.getInstance());
    assertValid(response);
  }
}
