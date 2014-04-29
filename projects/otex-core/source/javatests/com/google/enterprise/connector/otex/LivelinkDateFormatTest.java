// Copyright 2013 Google Inc.
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

import com.google.common.base.Throwables;

import junit.framework.TestCase;

import java.util.Date;
import java.util.Random;

/** Tests the thread-safety of the LivelinkDateFormat class. */
public class LivelinkDateFormatTest extends TestCase {
  private final LivelinkDateFormat dateFormat =
      LivelinkDateFormat.getInstance();

  private interface Testable {
    void test(Date millis, Date seconds, String sqlMillis);
  }

  /** Smoke test for using a single instance from multiple threads. */
  public void testThreads(final Testable body) throws InterruptedException {
    // Without synchronization, even 2 threads and 1 iteration would
    // throw an exception. These values are still very quick to test
    // (6 milliseconds in my testing).
    final int threadCount = 10;
    final int iterations = 100;

    // We're going to generate dates randomly distributed around the
    // current date (e.g., in 2013, between 1970 and 2056).
    Random rnd = new Random();
    long now = new Date().getTime();
    long scale = 2 * now / Integer.MAX_VALUE;

    Thread[] threads = new Thread[threadCount];
    final Throwable[] errors = new Throwable[threadCount];

    // Start the threads.
    for (int t = 0; t < threadCount; t++) {
      long time = rnd.nextInt(Integer.MAX_VALUE) * scale;

      // Inputs to the tests: the random time with and without
      // milliseconds, and a SQL string with milliseconds.
      final Date millis = new Date(time);
      final Date seconds = new Date(time / 1000 * 1000);
      final String sqlMillis = dateFormat.toSqlMillisString(millis);

      // This is a little messy because we need to collect errors in
      // the threads to throw them from the test thread or the test
      // won't fail.
      final int tt = t;
      threads[t] = new Thread() {
          @Override public void run() {
            try {
              for (int i = 0; i < iterations; i++) {
                body.test(millis, seconds, sqlMillis);
              }
            } catch (Throwable e) {
              errors[tt] = e;
              Throwables.propagateIfPossible(e);
            }
          }
        };
      threads[t].start();
    }

    // Wait for each thread and check for errors.
    for (int t = 0; t < threadCount; t++) {
      threads[t].join();
      if (errors[t] != null) {
        Throwables.propagateIfPossible(errors[t]);
      }
    }
  }

  /**
   * Tests that the date survives a round trip to a string in each
   * format and back again.
   */
  public void testEverything() throws InterruptedException {
    testThreads(new Testable() {
        public void test(Date millis, Date seconds, String sqlMillis) {
          assertEquals(seconds,
              dateFormat.parse(dateFormat.toIso8601String(seconds)));
          assertEquals(seconds,
              dateFormat.parse(dateFormat.toSqlString(seconds)));
          assertEquals(millis,
              dateFormat.parse(dateFormat.toSqlMillisString(millis)));
          assertEquals(seconds,
              dateFormat.parse(dateFormat.toRfc822String(seconds)));
        }
      });
  }

  /** Tests only calls to parse with different date strings. */
  public void testParse() throws InterruptedException {
    testThreads(new Testable() {
        @Override
        public void test(Date millis, Date seconds, String sqlMillis) {
          assertEquals(millis, dateFormat.parse(sqlMillis));
        }
      });
  }
}
