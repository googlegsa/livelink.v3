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

import java.util.Enumeration;
import java.util.Properties;
import org.springframework.beans.factory.config.PropertyPlaceholderConfigurer;
import org.springframework.beans.factory.xml.XmlBeanFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

/**
 * Instatiates a <code>LivelinkConnector</code> using Spring together
 * with the connectorInstance.xml in the classpath and the system
 * properties with a given name prefix.
 */
/*
 * TODO: Merge this with the similar code used in the
 * LivelinkConnectorType if that class continues to explicitly do
 * Spring instantiation.
 */
class LivelinkConnectorFactory {
    static final Properties emptyProperties = new Properties();

    static {
        emptyProperties.put("server", ""); 
        emptyProperties.put("port", ""); 
        emptyProperties.put("connection", ""); 
        emptyProperties.put("username", ""); 
        emptyProperties.put("password", ""); 
        emptyProperties.put("domainName", ""); 
        emptyProperties.put("displayUrl", ""); 
        emptyProperties.put("includedLocationNodes", ""); 
        emptyProperties.put("https", "true"); 
        emptyProperties.put("livelinkCgi", ""); 
        emptyProperties.put("enableNtlm", "false"); 
        emptyProperties.put("httpUsername", ""); 
        emptyProperties.put("httpPassword", ""); 
        emptyProperties.put("verifyServer", "true"); 
        emptyProperties.put("caRootCert", ""); 
        emptyProperties.put("useUsernamePasswordWithWebServer", "false"); 
        emptyProperties.put("useSeparateAuthentication", "false"); 
        emptyProperties.put("authenticationServer", ""); 
        emptyProperties.put("authenticationPort", "0"); 
        emptyProperties.put("authenticationConnection", ""); 
        emptyProperties.put("authenticationDomainName", ""); 
        emptyProperties.put("authenticationHttps", "true"); 
        emptyProperties.put("authenticationLivelinkCgi", ""); 
        emptyProperties.put("authenticationEnableNtlm", "false"); 
        emptyProperties.put("authenticationVerifyServer", "true"); 
        emptyProperties.put("authenticationCaRootCert", ""); 
        emptyProperties.put("authenticationUseUsernamePasswordWithWebServer", 
            "false"); 
    }

    /*
     * Gets a new <code>LivelinkConnector</code> instance.
     *
     * @param prefix the prefix on the names of the system properties
     * to be used to configure the bean, e.g., <code>"connector."</code>
     */
    public static LivelinkConnector getConnector(String prefix) {
        Resource res = new ClassPathResource("config/connectorInstance.xml");
        XmlBeanFactory factory = new XmlBeanFactory(res);

        Properties p = new Properties(emptyProperties);
        Properties system = System.getProperties();
        Enumeration names = system.propertyNames();
        while (names.hasMoreElements()) {
            String name = (String) names.nextElement();
            if (name.startsWith(prefix)) {
                System.out.println("PROPERTY: " + name);
                p.setProperty(name.substring(prefix.length()),
                    system.getProperty(name));
            }
        }
       
        PropertyPlaceholderConfigurer cfg =
            new PropertyPlaceholderConfigurer();
        cfg.setProperties(p);
        cfg.postProcessBeanFactory(factory);

        return (LivelinkConnector) factory.getBean("Livelink");
    }
}
