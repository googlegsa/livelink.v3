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

import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;


/**
 * Dump the Version info from the Manifest for the Connector's JAR file.
 * This is set as the default main() for the JAR if running the jar 
 * stand-alone.  This makes it easy to dump the Connector's JAR Manifest
 * (including version and build info) simply by running the command:
 *   java -jar /path/to/connector-otex.jar
 */

public class LivelinkMain {

    public static void main(String[] args) throws Exception
    {
        // From our class, traipse through the class loader,
        // the URL to this class file, the URL to the jar
        // file containing this class, and make our way to
        // the Manifest file located in that jar file.
        Class thisClass = LivelinkMain.class;
        ClassLoader loader = thisClass.getClassLoader();
        String jarPath = thisClass.getName().replace('.', '/') + ".class";
        URL bootstrapJarUrl = loader.getSystemResource(jarPath);

        // We're always in a jar file.
        JarURLConnection connection =
            (JarURLConnection) bootstrapJarUrl.openConnection();
        URL jarFileUrl = connection.getJarFileURL();
        String configFilePath = jarFileUrl.toString() +
            "!/META-INF/MANIFEST.MF";

        // I don't know for sure that getJarFileURL always
        // returns "file" rather than "jar".
        if ("file".equalsIgnoreCase(jarFileUrl.getProtocol()))
            configFilePath = "jar:" + configFilePath;
        URL resource = new URL(configFilePath);
        
        // Read the Manifest file and write it to stdout.
        InputStream is = resource.openStream();
        byte[] buffer = new byte[4096];
        int bytes;
        while ((bytes = is.read(buffer)) > 0)
            System.out.write(buffer, 0, bytes);
    }
}
