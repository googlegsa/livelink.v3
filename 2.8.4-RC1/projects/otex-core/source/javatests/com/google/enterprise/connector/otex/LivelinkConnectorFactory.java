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

import java.util.Enumeration;
import java.util.HashMap;
import java.util.logging.Logger;
import java.util.Map;
import java.util.Properties;

import org.springframework.beans.factory.config.PropertyPlaceholderConfigurer;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.beans.factory.xml.XmlBeanFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import com.google.enterprise.connector.spi.Connector;
import com.google.enterprise.connector.spi.ConnectorFactory;
import com.google.enterprise.connector.spi.RepositoryException;


/**
 * Instantiates a <code>LivelinkConnector</code> using Spring together
 * with the connectorInstance.xml in the classpath and the system
 * properties with a given name prefix.
 */
/*
 * TODO: Merge this with the similar code used in the
 * LivelinkConnectorType if that class continues to explicitly do
 * Spring instantiation.
 */
class LivelinkConnectorFactory implements ConnectorFactory {
  /** The logger for this class. */
  private static final Logger LOGGER =
      Logger.getLogger(LivelinkConnectorFactory.class.getName());

  static final Map<String, String> emptyProperties =
      new HashMap<String, String>();

  static final LivelinkConnectorFactory instance =
      new LivelinkConnectorFactory();

  static {
    emptyProperties.put("server", "");
    emptyProperties.put("port", "");
    emptyProperties.put("displayUrl", "");
    emptyProperties.put("username", "");
    emptyProperties.put("Password", "");
    emptyProperties.put("domainName", "");
    emptyProperties.put("useHttpTunneling", "false");
    emptyProperties.put("livelinkCgi", "");
    emptyProperties.put("httpUsername", "");
    emptyProperties.put("httpPassword", "");
    emptyProperties.put("enableNtlm", "false");
    emptyProperties.put("https", "false");
    emptyProperties.put("useUsernamePasswordWithWebServer", "false");
    emptyProperties.put("useSeparateAuthentication", "false");
    emptyProperties.put("authenticationServer", "");
    emptyProperties.put("authenticationPort", "0");
    emptyProperties.put("authenticationLivelinkCgi", "");
    emptyProperties.put("authenticationEnableNtlm", "false");
    emptyProperties.put("authenticationHttps", "false");
    emptyProperties.put("authenticationDomainName", "");
    emptyProperties.put("authenticationUseUsernamePasswordWithWebServer",
        "false");
    emptyProperties.put("traversalUsername", "");
    emptyProperties.put("includedLocationNodes", "");
  }

  // Prevent public instantiation
  private LivelinkConnectorFactory() {}

  /**
   * Return Singleton instance.
   */
  public static LivelinkConnectorFactory getInstance() {
    return instance;
  }

  /*
   * Gets a new <code>LivelinkConnector</code> instance.
   *
   * @param prefix the prefix on the names of the system properties
   * to be used to configure the bean, e.g., <code>"connector."</code>
   */
  public static LivelinkConnector getConnector(String prefix)
      throws RepositoryException {
    Map<String, String> p = new HashMap<String, String>();
    p.putAll(emptyProperties);

    Properties system = System.getProperties();
    Enumeration names = system.propertyNames();
    boolean prefixFound = false;

    while (names.hasMoreElements()) {
      String name = (String) names.nextElement();
      if (name.startsWith(prefix)) {
        prefixFound = true;
        LOGGER.config("PROPERTY: " + name);
        p.put(name.substring(prefix.length()), system.getProperty(name));
      }
    }

    // If there is no connector configured by this name, bail early.
    if (!prefixFound) {
      throw new RepositoryException("No javatest." + prefix +
          "* properties specified for connector.");
    }

    return (LivelinkConnector) instance.makeConnector(p);
  }

  /**
   * Create a connector instance.
   * This conforms to com.google.enterprise.connector.spi.ConnectorFactory
   * interface for use by validateConfig().
   *
   * @param config Map of configuration properties
   */
  public Connector makeConnector(Map<String, String> config)
      throws RepositoryException {
    try {
      DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
      XmlBeanDefinitionReader beanReader = new XmlBeanDefinitionReader(factory);

      Resource prototype =
          new ClassPathResource("config/connectorInstance.xml");
      beanReader.loadBeanDefinitions(prototype);

      // Seems non-intuitive to load these in this order, but we want newer
      // versions of the connectors to override any default bean definitions
      // specified in old-style monolithic connectorInstance.xml files.
      Resource defaults = new ClassPathResource("config/connectorDefaults.xml");
      if (defaults != null) {
        beanReader.loadBeanDefinitions(defaults);
      }

      // This code should be using setLocation rather than setProperties, but
      // that requires the machinery of InstanceInfo.getPropertiesResource.
      PropertyPlaceholderConfigurer cfg = new PropertyPlaceholderConfigurer();
      Properties props = new Properties();
      props.putAll(config);
      cfg.setProperties(props);
      cfg.postProcessBeanFactory(factory);

      return (Connector) factory.getBean("Livelink_Enterprise_Server");
    } catch (Throwable t) {
      throw new RepositoryException(t);
    }
  }
}
