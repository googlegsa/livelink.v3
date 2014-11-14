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

import java.util.HashMap;
import java.util.Map;
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

  private final String[] adaptorArgs;

  private Application adaptorApplication = null;

  private final Lock lock = new ReentrantLock();

  private final Condition isStarted = lock.newCondition();

  private final Condition isStopped = lock.newCondition();

  GroupLister(LivelinkConnector connector, GroupAdaptor groupAdaptor) {
    this(connector, groupAdaptor, new HashMap<String, String>());
  }

  /**
   * @param extraArgs a map of additional adaptor properties. These
   *     will overwrite the default values if there are duplicate keys.
   */
  @VisibleForTesting
  GroupLister(LivelinkConnector connector, GroupAdaptor groupAdaptor,
      Map<String, String> extraArgs) {
    this.connector = connector;
    this.groupAdaptor = groupAdaptor;

    Map<String, String> argsMap = new HashMap<String, String>();
    argsMap.put("gsa.hostname", connector.getGoogleFeedHost());
    argsMap.put("server.hostname", "localhost");
    argsMap.put("server.port", "0");
    argsMap.put("server.dashboardPort", "0");
    argsMap.put("feed.name", connector.getGoogleConnectorName());
    argsMap.put("adaptor.fullListingSchedule",
        connector.getGroupFeedSchedule());
    argsMap.putAll(extraArgs);
    this.adaptorArgs = new String[argsMap.size()];
    int i = 0;
    for (Map.Entry<String, String> entry : argsMap.entrySet()) {
      this.adaptorArgs[i++] = "-D" + entry.getKey() + "=" + entry.getValue();
    }
  }

  @Override
  public void setDocumentAcceptor(DocumentAcceptor documentAcceptor) {
  }

  @Override
  public void start() throws RepositoryException {
    String connectorName = connector.getGoogleConnectorName();

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
        adaptorApplication = groupAdaptor.invokeAdaptor(adaptorArgs);
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
