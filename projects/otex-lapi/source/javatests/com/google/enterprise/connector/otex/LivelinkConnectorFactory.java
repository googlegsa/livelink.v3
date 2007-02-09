// Copyright (C) 2007 Google Inc.

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
 *
 * TODO: Use this code in the Authn/z test classes when the
 * connectorInstance.xml supports them.
 */
class LivelinkConnectorFactory {
    /*
     * Gets a new <code>LivelinkConnector</code> instance.
     *
     * @param prefix the prefix on the names of the system properties
     * to be used to configure the bean, e.g., <code>"connector."</code>
     */
    public static LivelinkConnector getConnector(String prefix) {
        Resource res = new ClassPathResource("config/connectorInstance.xml");
        XmlBeanFactory factory = new XmlBeanFactory(res);

        Properties p = new Properties();
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

        return (LivelinkConnector) factory.getBean("livelink-connector");
    }
}
