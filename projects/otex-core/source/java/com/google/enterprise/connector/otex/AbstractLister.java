// Copyright 2015 Google Inc. All Rights Reserved.
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

import com.google.enterprise.connector.spi.Lister;
import com.google.enterprise.connector.spi.RepositoryException;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A thread-safe implementation of {@link Lister}. The Connector Manager
 * has a race condition when calling {@link Lister#start start} and
 * {@link Lister#shutdown shutdown}. It is also designed for
 * implementations of {@code start} that run indefinitely.
 * <p>
 * This implementation exposes a different interface of three methods:
 * {@link #init}, {@link #run}, and {@link #destroy}. Calls to these
 * methods are serialized so that {@code init} is always called before
 * {@code destroy}. The {@code run} method may not be called if the
 * {@code Lister} is shutdown while it is starting up. In that case,
 * {@code destroy} may be called immediately after {@code init}. If
 * {@code run} exits then {@code destroy} may not be called. Like the
 * {@code Lister} interface, these methods may be called multiple times
 * (in sequence) on the same object.
 * <p>
 * This class also provides a helper method, {@link #waitForShutdown},
 * that can be called from {@code run} to block and wait for
 * {@code shutdown} if the work is being done in other threads or if a
 * call to {@code destroy} is desired to cleanup allocated resources.
 */
/*
 * TODO(wiarlawd): Always call destroy, in start if run exits before
 * shutdown is called, or in shutdown if that is called first. Just
 * calling it from start would work, but would depend on run always
 * exiting cleanly when shutdown.
 *
 * TODO(wiarlawd): Before moving this class to Connector Manager, port
 * the FS connector's FileLister class to extend this class.
 *
 * TODO(BrettMichaelJohnson): Extract the generic traversal logic from
 * FileLister either as optional features here, or as a
 * TraversalLister that extends this class.
 */
public abstract class AbstractLister implements Lister {
  private static final Logger LOGGER =
      Logger.getLogger(AbstractLister.class.getName());

  private boolean isRunning = false;

  private final Lock lock = new ReentrantLock();

  private final Condition isStarted = lock.newCondition();

  private final Condition isStopped = lock.newCondition();

  /** Creates a new, unstarted {@link Lister}. */
  public AbstractLister() {
  }

  /**
   * Initializes any long-lived resources needed for {@link #run},
   * including threads.
   *
   * @throws RepositoryException if an error occurs; the {@code Lister}
   *     will not be retried.
   */
  public abstract void init() throws RepositoryException;

  /**
   * Runs for the life of the {@code Lister}. When this method exits, the
   * {@code Lister} is considered shutdown.
   *
   * @throws RepositoryException if an error occurs; the {@code Lister}
   *     will be treated as having shutdown.
   */
  public abstract void run() throws RepositoryException;

  /**
   * Cleans up any resources allocated by {@link #init} or {@link #run}.
   * This method may not be called if {@code run} has exited normally.
   *
   * @throws RepositoryException if an error occurs; the {@code Lister}
   *     will be treated as having shutdown.
   */
  public abstract void destroy() throws RepositoryException;

  @Override
  public final void start() throws RepositoryException {
    LOGGER.log(Level.FINEST, "Waiting to start lister");
    lock.lock();
    try {
      while (isRunning) {
        if (!isStopped.await(1L, MINUTES)) {
          throw new RepositoryException(
              "Timed out waiting for previous lister to stop");
        }
      }
      LOGGER.info("Starting lister");
      init();
      isRunning = true;
      isStarted.signal();

      run();
      LOGGER.log(Level.FINEST, "Exiting lister");
    } catch (InterruptedException e) {
      LOGGER.log(Level.INFO, "Interrupted waiting for previous lister to stop",
          e);
      Thread.currentThread().interrupt();
    } finally {
      lock.unlock();
    }
  }

  /** Helper method for subclasses that want to block in {@link #run}. */
  protected final void waitForShutdown() {
    LOGGER.log(Level.FINEST, "Blocking lister until shutdown");
    while (isRunning) {
      isStopped.awaitUninterruptibly();
    }
    LOGGER.log(Level.FINEST, "Unblocking lister");
  }

  @Override
  public final void shutdown() throws RepositoryException {
    LOGGER.log(Level.FINEST, "Waiting to shutdown lister");
    lock.lock();
    try {
      while (!isRunning) {
        if (!isStarted.await(1L, MINUTES)) {
          throw new RepositoryException(
              "Timed out waiting for lister to start");
        }
      }
      LOGGER.info("Shutting down lister");
      try {
        destroy();
      } finally {
        isRunning = false;
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
