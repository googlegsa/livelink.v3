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
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.otex.client.Client;

/**
 * This content handler implementation uses <code>FetchVersion</code>
 * with a <code>PipedOutputStream</code> and returns the paired
 * instance of a <code>PipedInputStream</code> subclass that uses a
 * larger pipe buffer and that rethrows exceptions that were thrown by
 * <code>FetchVersion</code>.
 * <p>
 * Pipes must be used from two threads, a consumer and a producer. In
 * this case the consumer is the calling thread and the producer is a
 * thread that is created here for each instance. The two threads
 * share instances of this class, which presents a
 * <code>ContentHandler</code> interface to the calling thread, and a
 * <code>Runnable</code> interface to the created producer thread. The
 * producer thread runs in the background waiting for calls to (@link
 * #getInputStream} to queue requests.
 * 
 * @see ContentHandler
 */
/*
 * FIXME: This implementation deadlocks, especially when the producer
 * thread throws exceptions. One example occurs when the consumer
 * thread closes the input stream before EOF is reached.
 */
class PipedContentHandler implements ContentHandler, Runnable {
  /** The logger for this class. */
  private static final Logger LOGGER =
      Logger.getLogger(PipedContentHandler.class.getName());

  /** A larger pipe size for a tenfold increase in performance. */
  private static final int PIPE_SIZE = 32768;
    
  /** The connector contains configuration information. */
  private LivelinkConnector connector;

  /** The client provides access to the server. */
  private Client client;

  /** Is the producer thread running? */
  private volatile boolean isRunning = false;
    
  /** Queued requests. */
  /*
   * The queue is necessary because this content handler might
   * (probably erroneously) be invoked from multiple threads. If the
   * QueryTraversalManager were truly single-threaded, then
   * "queueing" a single request would suffice.
   */
  private final LinkedList<Request> queue = new LinkedList<Request>();
    
  /** Represents queue entries. */
  private static class Request {
    private final int volumeId;
    private final int objectId;
    private final int versionNumber;
    private final PipedOutputStream out;
    private final SafePipedInputStream in;
    private Request(int volumeId, int objectId, int versionNumber,
        PipedOutputStream out, SafePipedInputStream in) {
      this.volumeId = volumeId;
      this.objectId = objectId;
      this.versionNumber = versionNumber;
      this.out = out;
      this.in = in;
    }
  }
    
  PipedContentHandler() {
  }

  /**
   * {@inheritDoc}
   * <p>
   * This implementation starts the producer thread.
   */
  @Override
  public void initialize(LivelinkConnector connector, Client client)
      throws RepositoryException {
    this.connector = connector;
    this.client = client;

    Thread t = new Thread(this);
    t.setDaemon(true);
    t.start();
    if (LOGGER.isLoggable(Level.FINE))
      LOGGER.fine("PIPED CONTENT HANDLER THREAD STARTED: " + t);
  }

  /** {@inheritDoc} */
  @Override
  public InputStream getInputStream(int volumeId, int objectId,
      int versionNumber, int size) throws RepositoryException {
    if (!isRunning)
      throw new LivelinkException("No producer thread.", LOGGER);

    try {
      SafePipedInputStream in = new SafePipedInputStream(PIPE_SIZE);
      PipedOutputStream out = new PipedOutputStream(in);
      queueRequest(new Request(volumeId, objectId, versionNumber, out, in));
      return in;
    } catch (IOException e) {
      throw new LivelinkException(e, LOGGER);
    }
  }

  /** The producer thread processes requests for item content. */
  @Override
  public void run() {
    // TODO: We need some way of stopping this thread
    // gracefully, although at the moment we have no lifecycle
    // event to trigger stopping the thread.
    try {
      isRunning = true;
      while (true) {
        try {
          processRequest();
        } catch (InterruptedException e) {
          return;
        }
      }
    } finally {
      isRunning = false;
    }
  }

  /**
   * Called by the consumer thread, this method stores the
   * <code>FetchVersion</code> parameters and notifies the producer
   * thread that the request is ready.
   */
  private synchronized void queueRequest(Request request) {
    if (LOGGER.isLoggable(Level.FINE))
      LOGGER.fine("QUEUEING REQUEST: " + request.objectId);
    queue.addLast(request);
    notify();
  }

  private synchronized Request dequeueRequest() throws InterruptedException {
    while (queue.isEmpty()) {
      LOGGER.finer("WAITING FOR REQUEST");
      wait();
      LOGGER.fine("CHECKING FOR REQUEST");
    }
    return queue.removeFirst();
  }
    
  /**
   * Waits for a request and calls <code>FetchVersion</code> to fill
   * the pipe.
   */
  private void processRequest() throws InterruptedException {
    Request request = dequeueRequest();
    if (LOGGER.isLoggable(Level.FINE))
      LOGGER.fine("PROCESSING REQUEST: " + request.objectId);
    try {
      client.FetchVersion(request.volumeId, request.objectId,
          request.versionNumber, request.out);
    } catch (Throwable t) {
      LOGGER.log(Level.FINE, "CAUGHT EXCEPTION", t);
      try {
        request.in.setException(t, request.out);
      } catch (Throwable tt) {
        LOGGER.log(Level.WARNING,
            "CAUGHT EXCEPTION SETTING EXCEPTION", tt);
      }
    }
  }

  /**
   * Enables a larger pipe buffer for performance, and supports
   * rethrowing exceptions from the producer of the pipe content to
   * the consumer.
   * <p>
   * <strong>WARNING:</strong> This implementation depends heavily
   * on the implementation details of <code>PipedInputStream</code>.
   * The <code>buffer</code> field is protected, not final, and is
   * never reassigned to, and the <code>PIPE_SIZE<code> constant is
   * only used to construct the buffer. Also, we're assuming that
   * closing the output stream and calling notify on the input
   * stream will unblock a blocked call to read.
   */
  /*
   * FIXME: Do we need a stand-alone implementation to avoid
   * depending on implementation details?
   */
  private static class SafePipedInputStream extends PipedInputStream {
    private Throwable throwable = null;

    public SafePipedInputStream(int size) {
      buffer = new byte[size];
    }

    public synchronized int read() throws IOException {
      checkException();
      int b = super.read();
      if (b == -1)
        checkException();
      return b;
    }

    @Override
    public int read(byte[] buffer) throws IOException {
      return read(buffer, 0, buffer.length);
    }

    @Override
    public synchronized int read(byte[] buffer, int offset, int length)
        throws IOException {
      checkException();
      int count = super.read(buffer, offset, length);
      if (count == -1)
        checkException();
      return count;
    }

    public synchronized void setException(Throwable t,
        PipedOutputStream out) {
      if (throwable == null) {
        throwable = t;

        // If the reader is blocked, we need to unblock it.
        // XXX: This code depends on the implementation of
        // PipedInputStream.read. First we close the output
        // stream so that the unblocked reader doesn't
        // immediately block again, and then we notify the
        // reader to wake up.
        try {
          out.close();
        } catch (IOException e) {
          // This can't happen because
          // PipedOutputStream.close doesn't really throw
          // IOExceptions.
          throw new RuntimeException(e);
        } finally {
          notify(); // Only the one reader can be waiting.
        }
      }
    }

    private void checkException() throws IOException {
      if (throwable != null) {
        // IOException didn't have a constructor that takes a
        // cause until Java 6.
        IOException e = new IOException(throwable.getMessage());
        e.initCause(throwable);
        throwable = null;
        throw e;
      }
    }
  }
}
