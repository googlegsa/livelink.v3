// Copyright 2011 Google Inc.
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

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.enterprise.connector.spi.Document;
import com.google.enterprise.connector.spi.Property;
import com.google.enterprise.connector.spi.SkippedDocumentException;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * {@link FilterDocumentChain} constructs a chain of {@link Document}
 * filters.  The filters are constructed from a {@link List} of
 * {@link FilterDocumentFactory FilterDocumentFactories}, and linked
 * together like pop-beads, each using the previous as its source Document.
 * <p/>
 * The {@link FilterDocumentChain} is configured by via the {@code
 * FilterDocumentChain} bean in Spring, supplying a list of
 * FilterDocumentFactories to the constructor.
 */
/* TODO (bmj): This should really be in the Connctor Manager. */
public class FilterDocumentChain implements FilterDocumentFactory {

  // The list of factories used to construct the filter chain.
  private final List<FilterDocumentFactory> factories;

  /**
   * Constructs an empty {@link FilterDocumentChain}. Documents will
   * will pass through unchanged.
   */
  public FilterDocumentChain() {
    this.factories = Collections.emptyList();
  }

  /**
   * Constructs a {@link FilterDocumentChain} that uses the supplied
   * List of {@link FilterDocumentFactory FilterDocumentFactories}
   * assemble a document filter chain.
   *
   * @param factories a List of {@link FilterDocumentFactory}
   */
  public FilterDocumentChain(List<FilterDocumentFactory> factories) {
    Preconditions.checkNotNull(factories);
    this.factories = factories;
  }

  /**
   * Constructs a document procssing pipeline, assembled from filters fetched
   * from each of the {@link FilterDocumentFactory FilterDocumentFactories}
   * in the list.  Returns the head of the chain.  The supplied {@code source}
   * Document will be the input for the tail of the chain.
   *
   * @param source the input {@link Document} for the filters
   * @return the head of the chain of filters
   */
  /* @Override */
  public Document newFilterDocument(Document source) {
    Preconditions.checkNotNull(source);
    for (FilterDocumentFactory factory : factories) {
      source = factory.newFilterDocument(source);
    }
    return source;
  }
}
