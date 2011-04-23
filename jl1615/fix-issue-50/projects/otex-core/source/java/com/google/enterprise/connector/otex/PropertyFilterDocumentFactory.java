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
import com.google.common.base.Strings;
import com.google.enterprise.connector.spi.Document;
import com.google.enterprise.connector.spi.Property;
import com.google.enterprise.connector.spi.Value;
import com.google.enterprise.connector.spi.RepositoryException;

import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * A {@link FilterDocumentFactory} that creates {@link Document} filters
 * designed to alter the values of a specified {@link Property}.
 */
public class PropertyFilterDocumentFactory implements FilterDocumentFactory {
  /** The field name to look for. */
  protected String propertyName;

  /** The regex pattern to match in the property {@link Value Values}. */
  protected Pattern pattern;

  /** The regex pattern to match in the property {@link Value Values}. */
  protected String replacement;

  /**
   * Constructs a new {@link PropertyFilterDocumentFactory}.  The manufactured
   * filter {@link Document Documents} will scrutinize the {@link Value Values}
   * returned by the named {@link Property}.  If the value (as a string) matches
   * the regular expression {@code pattern}, then all matching regions of the
   * value will be replace with the {@code replacement} string.
   * <p/>
   * The supplied pattern must conform to the syntax defined in
   * {@link java.util.regex.Pattern}.
   * <p/>
   * This implementation actually returns both the original and the modified
   * values as if this were a multi-valued Property.
   *
   * @param propertyName the name of the {@link Property} to filter
   * @param pattern a regular expression to match in the values
   * @param replacement the replacement string for matching regions in the values
   * @throws IllegalArgumentException if {@code propertyName} is {@code null} or empty
   * @throws IllegalArgumentException if {@code pattern} is {@code null} or empty
   * @throws PatternSyntaxException if {@code pattern}'s syntax is invalid
   */
  public PropertyFilterDocumentFactory(String propertyName, String pattern,
       String replacement) throws PatternSyntaxException {
    Preconditions.checkArgument(!Strings.isNullOrEmpty(propertyName),
                                "propertyName may not be null or empty");
    Preconditions.checkArgument(!Strings.isNullOrEmpty(pattern),
                                "pattern may not be null or empty");
    this.propertyName = propertyName;
    this.replacement = Strings.nullToEmpty(replacement);
    this.pattern = Pattern.compile(pattern);
  }

  /* @Override */
  public Document newFilterDocument(Document source) {
    return new PropertyFilterDocument(source);
  }

  /**
   * A {@link Document} implementation that filters all {@link Value Values}
   * of the target {@link Property}.  All other Properties pass through
   * unchanged.
   */
  protected class PropertyFilterDocument implements Document {
    private Document source;      // The input Document.

    /**
     * Constructs a {@link PropertyFilterDocument} with the supplied
     * {@code source} Document.
     *
     * @param source the source {@link Document}
     */
    public PropertyFilterDocument(Document source) {
      this.source = source;
    }

    /* @Override */
    public Property findProperty(String name) throws RepositoryException {
      Property prop = source.findProperty(name);
      if (prop != null && propertyName.equalsIgnoreCase(name)) {
        return new PropertyFilter(prop);
      } else {
        return prop;
      }
    }

    /* @Override */
    public Set<String> getPropertyNames() throws RepositoryException {
      return source.getPropertyNames();
    }
  }

  /**
   * A {@link Property} implementation that filters all {@link Value Values},
   * replacing any matching occurances of the {@code pattern} regular expression
   * with the designated {@code replacement}.  This implementation actually
   * returns both the original and the modified values as if this were a
   * multi-valued Property.
   *
   * @param property the source {@link Property}
   */
  /* TODO (bmj): Try to restrict this to only StringValue instances? */
  protected class PropertyFilter implements Property {
    private final Property source;
    private Value cachedValue = null;

    /**
     * Constructs a {@link Propertyfilter} that may modify the
     * {@link Value Values} of the {@code source} Property.
     *
     * @param source the {@link Property} to filter
     */
    public PropertyFilter(Property source) {
      this.source = source;
    }

    /* @Override */
    public Value nextValue() throws RepositoryException {
      // If we have cached the modified version the previous Value, return it.
      if (cachedValue != null) {
        try {
          return cachedValue;
        } finally {
          cachedValue = null;
        }
      }

      // Retrieve the Property's Value.  Replace any regions matching
      // the regular expression pattern with the replacement string.
      Value value = source.nextValue();
      if (value != null) {
        String original = value.toString();
        if (!Strings.isNullOrEmpty(original)) {
          String modified = pattern.matcher(original).replaceAll(replacement);
          if (!original.equals(modified)) {
            // Next time around, return the modified value.
            cachedValue = Value.getStringValue(modified);
          }
        }
      }
      return value;
    }
  }
}
