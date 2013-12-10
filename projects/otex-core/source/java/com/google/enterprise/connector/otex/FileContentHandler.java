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

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.otex.client.Client;

/**
 * This content handler implementation uses <code>FetchVersion</code>
 * with a temporary file path and returns a
 * <code>FileInputStream</code> subclass that deletes the the
 * temporary file when the stream is closed.
 * 
 * @see ContentHandler
 */
class FileContentHandler implements ContentHandler {
  /** The logger for this class. */
  private static final Logger LOGGER =
      Logger.getLogger(FileContentHandler.class.getName());

  /** The connector contains configuration information. */
  private LivelinkConnector connector;

  /** The client provides access to the server. */
  private Client client;

  FileContentHandler() {
  }

  /** {@inheritDoc} */
  @Override
  public void initialize(LivelinkConnector connector, Client client)
      throws RepositoryException {
    this.connector = connector;
    this.client = client;
  }

  /** {@inheritDoc} */
  @Override
  public InputStream getInputStream(int volumeId, int objectId,
      int versionNumber, int size) throws RepositoryException {
    try {
      // TODO: Does the new delete before throwing clear up debris?
      // TODO: Use connector working dir here?
      final File temporaryFile =
          File.createTempFile("gsa-otex-", null);
      if (LOGGER.isLoggable(Level.FINER))
        LOGGER.finer("CREATE TEMP FILE: " + temporaryFile);
      try {
        client.FetchVersion(volumeId, objectId, versionNumber,
            temporaryFile);
      } catch (RepositoryException e) {
        try {
          if (LOGGER.isLoggable(Level.FINER))
            LOGGER.finer("DELETE TEMP FILE: " + temporaryFile);
          temporaryFile.delete();
        } catch (Throwable t) {
          LOGGER.log(Level.FINEST,
              "Ignored exception deleting temp file", t);
        }
        throw e;
      }
      return new FileInputStream(temporaryFile) {
        public void close() throws IOException {
          try {
            super.close();
          } finally {
            // close is called again from the finalizer,
            // so check whether the file still exists.
            // No synchronization is needed, because the
            // finalizer can't be running while another
            // thread is using this object.
            if (temporaryFile.exists()) {
              if (LOGGER.isLoggable(Level.FINER)) {
                LOGGER.finer("DELETE TEMP FILE: " +
                    temporaryFile);
              }
              temporaryFile.delete();
            }
          }
        }
      };
    } catch (IOException e) {
      throw new LivelinkException(e, LOGGER);
    }
  }
}
