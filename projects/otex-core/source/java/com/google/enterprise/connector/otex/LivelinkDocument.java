// Copyright (C) 2007-2008 Google Inc.
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

import com.google.enterprise.connector.otex.client.ClientValue;
import com.google.enterprise.connector.spi.Document;
import com.google.enterprise.connector.spi.Property;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.SimpleProperty;
import com.google.enterprise.connector.spi.Value;

import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@inheritDoc}
 * <p>
 * This implementation accumulated properties from the the
 * underlying recarray, along with other properties derived from
 * the recarray.
 */
class LivelinkDocument implements Document {
  /** The logger for this class. */
  private static final Logger LOGGER =
      Logger.getLogger(LivelinkDocument.class.getName());

  /*
   * I'm using a LinkedHashMap just because it's got a more
   * predictable ordering when I'm looking at test output.
   */
  private Map<String, List<Value>> properties;

  /**
   * Creates a Document Property Map for the specified object.
   *
   * @param objectId the livelink object
   * @param initSize initial size of the map
   */
  LivelinkDocument(int objectId, int initSize) throws RepositoryException {
    if (LOGGER.isLoggable(Level.FINE))
      LOGGER.fine("PROPERTY MAP FOR ID = " + objectId);

    properties = new LinkedHashMap<String, List<Value>>(initSize);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Set<String> getPropertyNames() {
    return properties.keySet();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Property findProperty(String name) throws RepositoryException {
    List<Value> values = properties.get(name);
    if (values == null)
      return null;
    else {
      if (LOGGER.isLoggable(Level.FINEST))
        LOGGER.finest("PROPERTY: " + name + " = " + values);
      return new SimpleProperty(values);
    }
  }


  /**
   * Adds a property to the property map. If the property
   * already exists in the map, the given value is added to the
   * list of values in the property.
   *
   * @param name a property name
   * @param value a property value
   */
  public void addProperty(String name, Value value) {
    List<Value> values = properties.get(name);
    if (values == null) {
      LinkedList<Value> firstValues = new LinkedList<Value>();
      firstValues.add(value);
      properties.put(name, firstValues);
    } else
      values.add(value);
  }

  /**
   * Adds a property to the property map. If the property
   * already exists in the map, the given value is added to the
   * list of values in the property.
   *
   * @param name a property name
   * @param value a property value
   */
  public void addProperty(String name, ClientValue value) {
    addProperty(name, getValue(name, value));
  }

  /**
   * Adds a property to the property map. If the property
   * already exists in the map, the given value is added to the
   * list of values in the property.
   *
   * @param field a field definition, possibly including
   * multiple property names
   * @param value a <code>ClientValue</code>, which must be
   * wrapped as a <code>LivelinkValue</code>
   */
  public void addProperty(Field field, ClientValue value) {
    String[] names = field.propertyNames;
    for (int j = 0; j < names.length; j++)
      addProperty(names[j], getValue(names[j], value));
  }

  /**
   * Creates a <code>Value</code> (of the appropriate type) from
   * a <code>ClientValue</code>. Complex <code>ClientValue</code>
   * objects (RecArrays or Lists), must be decomposed into
   * individual atomic components before calling this method.
   *
   * @param name the property name (not the field name)
   * @param clientValue the value to be represented
   */
  private Value getValue(String name, ClientValue clientValue) {
    try {
      switch (clientValue.type()) {
        case ClientValue.BOOLEAN:
          return Value.getBooleanValue(clientValue.toBoolean());
        case ClientValue.DATE:
          Calendar c = Calendar.getInstance();
          c.setTime(clientValue.toDate());
          // Livelink only stores timestamps to the nearest second,
          // but LAPI 9.7 and earlier constructs a Date object that
          // includes milliseconds, which are taken from the current
          // time. So we need to avoid using the milliseconds in the
          // parameter.
          c.clear(Calendar.MILLISECOND);
          return Value.getDateValue(c);
        case ClientValue.DOUBLE:
          return Value.getDoubleValue(clientValue.toDouble());
        case ClientValue.INTEGER:
          return Value.getLongValue(clientValue.toInteger());
        case ClientValue.LONG:
          return Value.getLongValue(clientValue.toLong());
        case ClientValue.STRING:
          return Value.getStringValue(clientValue.toString2());
        default:
          throw new AssertionError("The value must have an atomic type. "
              + name + " has type " + clientValue.type());
      }
    } catch (RepositoryException e) {
      LOGGER.log(Level.WARNING, "LivelinkDocument caught exception "
          + "extracting value from " + name, e);
      return Value.getStringValue(e.getMessage());
    }
  }
}
