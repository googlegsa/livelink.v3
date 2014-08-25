// Copyright 2014 Google Inc. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.enterprise.connector.otex;

import com.google.enterprise.adaptor.Application;
import com.google.enterprise.connector.logging.NDC;
import com.google.enterprise.connector.spi.DocumentAcceptor;
import com.google.enterprise.connector.spi.Lister;
import com.google.enterprise.connector.spi.RepositoryException;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementation of {@link Lister} that feeds Livelink groups information 
 * from the repository. This Lister instantiates and starts an adaptor to feed
 * Livelink groups information.
 */
public class GroupLister implements Lister {
  private static final Logger LOGGER =
      Logger.getLogger(GroupLister.class.getName());

  private final LivelinkConnector connector;

  private final GroupAdaptor groupAdaptor;

  private volatile Application adaptorApplication;

  private final Semaphore shutdownSemaphore = new Semaphore(0);

  GroupLister(LivelinkConnector connector, GroupAdaptor groupAdaptor) {
    this.connector = connector;
    this.groupAdaptor = groupAdaptor;
  }

  @Override
  public void setDocumentAcceptor(DocumentAcceptor documentAcceptor) {
  }

  @Override
  public void start() throws RepositoryException {
    String feedHost = connector.getGoogleFeedHost();
    String connectorName = connector.getGoogleConnectorName();
    String logfile = System.getProperty("java.util.logging.config.file");
    String[] groupFeederArgs = {
            "-Dgsa.hostname=" + feedHost,
            "-Dserver.port=" + 0,
            "-Dserver.dashboardPort=" + 0,
            "-Dfeed.name=" + connectorName,
            "-Djava.util.logging.config.file=" + logfile,
            "-Dadaptor.fullListingSchedule="
                + connector.getGroupFeedSchedule()};

    NDC.push("GroupFeed " + connectorName);
    try {
      LOGGER.info("Starting group feed adaptor for " + connectorName);
      adaptorApplication =
          groupAdaptor.invokeAdaptor(groupFeederArgs);

      // This start method needs to be blocked, so that the lister shutdown
      // method can be invoked.
      shutdownSemaphore.acquire();
      LOGGER.log(Level.FINEST, "Acquired semaphore {0}", connectorName);
    } catch (InterruptedException e) {
      LOGGER.log(Level.INFO, "Interrupted acquiring semaphore: {0}",
          e.toString());
      adaptorApplication.stop(1000L, TimeUnit.MILLISECONDS);
    } finally {
      NDC.remove();
    }
  }

  @Override
  public void shutdown() throws RepositoryException {
    LOGGER.info("Shutting down Livelink group lister "
        + connector.getGoogleConnectorName());
    try {
      adaptorApplication.stop(1000L, TimeUnit.MILLISECONDS);
    } finally {
      shutdownSemaphore.release();
    }
  }
}
