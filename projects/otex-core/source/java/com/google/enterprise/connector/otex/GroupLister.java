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

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.annotations.VisibleForTesting;
import com.google.enterprise.adaptor.Application;
import com.google.enterprise.connector.logging.NDC;
import com.google.enterprise.connector.spi.DocumentAcceptor;
import com.google.enterprise.connector.spi.Lister;
import com.google.enterprise.connector.spi.RepositoryException;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
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

  private final int dashboardPort;

  private Application adaptorApplication = null;

  private final Lock lock = new ReentrantLock();

  private final Condition isStarted = lock.newCondition();

  private final Condition isStopped = lock.newCondition();

  GroupLister(LivelinkConnector connector, GroupAdaptor groupAdaptor) {
    this(connector, groupAdaptor, 0);
  }

  @VisibleForTesting
  GroupLister(LivelinkConnector connector, GroupAdaptor groupAdaptor,
      int dashboardPort) {
    this.connector = connector;
    this.groupAdaptor = groupAdaptor;
    this.dashboardPort = dashboardPort;
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
            "-Dserver.hostname=localhost",
            "-Dserver.port=0",
            "-Dserver.dashboardPort=" + dashboardPort,
            "-Dfeed.name=" + connectorName,
            "-Djava.util.logging.config.file=" + logfile,
            "-Dadaptor.fullListingSchedule="
                + connector.getGroupFeedSchedule()};

    NDC.push("GroupFeed " + connectorName);
    try {
      LOGGER.log(Level.FINEST, "Waiting to start group feed adaptor for {0}",
          connectorName);
      lock.lock();
      try {
        while (adaptorApplication != null) {
          if (!isStopped.await(1L, MINUTES)) {
            throw new RepositoryException(
                "Timed out waiting for previous lister "
                + connectorName + " to stop");
          }
        }
        LOGGER.info("Starting group feed adaptor for " + connectorName);
        adaptorApplication =
            groupAdaptor.invokeAdaptor(groupFeederArgs);
        isStarted.signal();

        // This start method needs to be blocked, so that the lister shutdown
        // method can be invoked.
        LOGGER.log(Level.FINEST, "Blocking lister {0} until shutdown",
            connectorName);
        while (adaptorApplication != null) {
          isStopped.awaitUninterruptibly();
        }
        LOGGER.log(Level.FINEST, "Exiting lister {0}", connectorName);
      } catch (InterruptedException e) {
        LOGGER.log(Level.INFO, "Interrupted waiting for previous lister "
            + connectorName + " to stop", e);
        Thread.currentThread().interrupt();
      } finally {
        lock.unlock();
      }
    } finally {
      NDC.remove();
    }
  }

  @Override
  public void shutdown() throws RepositoryException {
    String connectorName = connector.getGoogleConnectorName();

    LOGGER.log(Level.FINEST, "Waiting to shutdown group feed adaptor for {0}",
        connectorName);
    lock.lock();
    try {
      while (adaptorApplication == null) {
        if (!isStarted.await(1L, MINUTES)) {
          throw new RepositoryException(
              "Timed out waiting for lister " + connectorName + " to start");
        }
      }
      LOGGER.info("Shutting down group feed adaptor for "
          + connector.getGoogleConnectorName());
      try {
        adaptorApplication.stop(1L, SECONDS);
      } finally {
        adaptorApplication = null;
        LOGGER.log(Level.FINEST, "Shutting down lister {0}",
            connector.getGoogleConnectorName());
        isStopped.signal();
      }
    } catch (InterruptedException e) {
      LOGGER.log(Level.INFO, "Interrupted waiting for lister "
          + connectorName + " to start", e);
      Thread.currentThread().interrupt();
    } finally {
      lock.unlock();
    }
  }
}
