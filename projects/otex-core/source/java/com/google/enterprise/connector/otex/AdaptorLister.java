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

import com.google.enterprise.adaptor.AbstractAdaptor;
import com.google.enterprise.adaptor.Adaptor;
import com.google.enterprise.adaptor.Application;
import com.google.enterprise.connector.spi.DocumentAcceptor;
import com.google.enterprise.connector.spi.Lister;
import com.google.enterprise.connector.spi.RepositoryException;

import java.util.Arrays;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementation of {@link Lister} that runs an {@code Adaptor}.
 */
public class AdaptorLister implements Lister {
  private static final Logger LOGGER =
      Logger.getLogger(AdaptorLister.class.getName());

  private final Adaptor adaptor;

  private final String[] adaptorArgs;

  private Application adaptorApplication = null;

  private final Lock lock = new ReentrantLock();

  private final Condition isStarted = lock.newCondition();

  private final Condition isStopped = lock.newCondition();

  public AdaptorLister(Adaptor adaptor, String[] adaptorArgs) {
    this.adaptor = adaptor;
    this.adaptorArgs = adaptorArgs;
  }

  @Override
  public void setDocumentAcceptor(DocumentAcceptor documentAcceptor) {
  }

  private Application startAdaptor() {
    LOGGER.log(Level.CONFIG, "Arguments to Adaptor: {0}",
        Arrays.asList(adaptorArgs));
    return AbstractAdaptor.main(adaptor, adaptorArgs);
  }

  @Override
  public void start() throws RepositoryException {
    LOGGER.log(Level.FINEST, "Waiting to start adaptor");
    lock.lock();
    try {
      while (adaptorApplication != null) {
        if (!isStopped.await(1L, MINUTES)) {
          throw new RepositoryException(
              "Timed out waiting for previous lister to stop");
        }
      }
      LOGGER.info("Starting adaptor");
      adaptorApplication = startAdaptor();
      isStarted.signal();

      // This start method needs to be blocked, so that the lister shutdown
      // method can be invoked.
      LOGGER.log(Level.FINEST, "Blocking lister until shutdown");
      while (adaptorApplication != null) {
        isStopped.awaitUninterruptibly();
      }
      LOGGER.log(Level.FINEST, "Exiting lister");
    } catch (InterruptedException e) {
      LOGGER.log(Level.INFO, "Interrupted waiting for previous lister to stop",
          e);
      Thread.currentThread().interrupt();
    } finally {
      lock.unlock();
    }
  }

  @Override
  public void shutdown() throws RepositoryException {
    LOGGER.log(Level.FINEST, "Waiting to shutdown adaptor");
    lock.lock();
    try {
      while (adaptorApplication == null) {
        if (!isStarted.await(1L, MINUTES)) {
          throw new RepositoryException(
              "Timed out waiting for lister to start");
        }
      }
      LOGGER.info("Shutting down adaptor");
      try {
        adaptorApplication.stop(1L, SECONDS);
      } finally {
        adaptorApplication = null;
        LOGGER.log(Level.FINEST, "Shutting down lister");
        isStopped.signal();
      }
    } catch (InterruptedException e) {
      LOGGER.log(Level.INFO, "Interrupted waiting for lister to start", e);
      Thread.currentThread().interrupt();
    } finally {
      lock.unlock();
    }
  }
}
