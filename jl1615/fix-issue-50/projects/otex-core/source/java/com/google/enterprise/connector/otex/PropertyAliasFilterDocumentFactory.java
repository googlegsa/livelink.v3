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

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A {@link FilterDocumentFactory} that creates {@link Document} filters
 * designed to allow {@link Property Properties} to be retrieved using an alias.
 * The aliases also show up on the list of Property names.
 */
public class PropertyAliasFilterDocumentFactory implements FilterDocumentFactory {
  /** Map of aliases to actual property names */
  Map<String, String>aliasMap;

  /**
   * Constructs a new {@link PropertyAliasFilterDocumentFactory}.  The manufactured
   * filter {@link Document Documents} will allow {@link Property Properties} to
   * be referred to using aliases.  The supplied {@code aliasMap} maps an alias
   * to its actual property name.
   *
   * @param aliasMap an alias-to-propertyName {@link Map}
   * @throws NullPointerException if {@code aliasMap} is {@code null}
   */
  public PropertyAliasFilterDocumentFactory(Map<String,String> aliasMap) {
    Preconditions.checkNotNull(aliasMap);
    this.aliasMap = aliasMap;
  }

  /* @Override */
  public Document newFilterDocument(Document source) {
    return new PropertyAliasFilterDocument(source);
  }

  @Override
  public String toString() {
    return getClass().getName() + ":" + aliasMap;
  }

  /**
   * A {@link Document} implementation provides aliases to {@link Property}
   * names.
   */
  protected class PropertyAliasFilterDocument implements Document {
    private Document source;      // The input Document.

    /**
     * Constructs a {@link PropertyAliasFilterDocument} with the supplied
     * {@code source} Document.
     *
     * @param source the source {@link Document}
     */
    public PropertyAliasFilterDocument(Document source) {
      this.source = source;
    }

    /* @Override */
    public Property findProperty(String name) throws RepositoryException {
      String realName = aliasMap.get(name);
      return source.findProperty((realName == null) ? name : realName);
    }

    /* @Override */
    public Set<String> getPropertyNames() throws RepositoryException {
      Set<String> superSet = new HashSet<String>(source.getPropertyNames());
      superSet.addAll(aliasMap.keySet());
      return superSet;
    }
  }
}
