// Copyright (C) 2007-2008 Google Inc.
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

import com.google.enterprise.connector.spi.Document;
import com.google.enterprise.connector.spi.DocumentList;
import com.google.enterprise.connector.spi.Property;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.Session;
import com.google.enterprise.connector.spi.TraversalManager;
import com.google.enterprise.connector.spi.Value;
import com.google.enterprise.connector.spiimpl.BinaryValue;

import junit.framework.TestCase;

import java.io.IOException;
import java.io.InputStream;

public class NonDocumentTest extends TestCase {
  private LivelinkConnector conn;

  @Override
  protected void setUp() throws RepositoryException {
    conn = LivelinkConnectorFactory.getConnector("connector.");
  }

  public void testTraversal() throws RepositoryException {
    Session sess = conn.login();

    TraversalManager mgr = sess.getTraversalManager();
    mgr.setBatchHint(3000);

    String checkpoint = "2007-02-16 14:39:09,31257";

    DocumentList rs = mgr.resumeTraversal(checkpoint);
    processResultSet(rs);
  }

  private void processResultSet(DocumentList docList)
      throws RepositoryException {
    if (docList == null) {
      System.out.println("No results.");
      return;
    }

    Document doc;
    while ((doc = docList.nextDocument()) != null) {
      System.out.println();
      for (String name : doc.getPropertyNames()) {
        Property prop = doc.findProperty(name);
        Value value;
        while ((value = prop.nextValue()) != null) {
          String printableValue;
          if (value instanceof BinaryValue) {
            try {
              InputStream in = ((BinaryValue) value).getInputStream();
              byte[] buffer = new byte[32];
              int count = in.read(buffer);
              in.close();
              if (count == -1)
                printableValue = "";
              else
                printableValue = new String(buffer, 0, count);
            } catch (IOException e) {
              printableValue = e.toString();
            }
          } else
            printableValue = value.toString();
          System.out.println(name + " = " + printableValue);
        }
      }
    }
  }
}
