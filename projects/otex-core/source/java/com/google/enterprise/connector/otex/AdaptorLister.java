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

import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.enterprise.adaptor.AbstractAdaptor;
import com.google.enterprise.adaptor.Adaptor;
import com.google.enterprise.adaptor.Application;
import com.google.enterprise.connector.spi.DocumentAcceptor;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.TraversalSchedule;
import com.google.enterprise.connector.spi.TraversalScheduleAware;

import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementation of {@link AbstractLister} that runs an {@code Adaptor}.
 */
public class AdaptorLister extends AbstractLister
    implements TraversalScheduleAware {
  private static final Logger LOGGER =
      Logger.getLogger(AdaptorLister.class.getName());

  private final Adaptor adaptor;

  private final String[] adaptorArgs;

  private Application adaptorApplication = null;

  public AdaptorLister(Adaptor adaptor, String[] adaptorArgs) {
    this.adaptor = adaptor;
    this.adaptorArgs = adaptorArgs;
  }

  /**
   * Note: when the traversal is disabled in the admin console, this
   * method is called once with the old, enabled schedule, then the
   * lister is started, and then this method is called again with the
   * new, disabled schedule. Since the adaptor library calls getDocIds
   * at startup by default, it will be called once, and that seems to
   * happen before the disabled schedule is passed in, so the groups
   * are fed one more time before the feeds are actually disabled.
   */
  @Override
  public void setTraversalSchedule(TraversalSchedule schedule) {
    if (adaptor instanceof TraversalScheduleAware) {
      ((TraversalScheduleAware) adaptor).setTraversalSchedule(schedule);
    }
  }

  @Override
  public void setDocumentAcceptor(DocumentAcceptor documentAcceptor) {
  }

  @Override
  public void init() {
    LOGGER.log(Level.CONFIG, "Arguments to Adaptor: {0}",
        Arrays.asList(adaptorArgs));
    adaptorApplication = AbstractAdaptor.main(adaptor, adaptorArgs);
  }

  @Override
  public void run() throws RepositoryException {
    // This method needs to be blocked, so that the lister shutdown
    // method can be invoked.
    waitForShutdown();
  }

  @Override
  public void destroy() throws RepositoryException {
    adaptorApplication.stop(1L, SECONDS);
  }
}
