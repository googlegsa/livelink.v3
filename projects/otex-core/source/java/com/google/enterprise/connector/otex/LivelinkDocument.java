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

import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Map;
import java.util.Set;

import com.google.enterprise.connector.spi.Document;
import com.google.enterprise.connector.spi.Property;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.SpiConstants;
import com.google.enterprise.connector.spi.Value;
import com.google.enterprise.connector.otex.client.Client;
import com.google.enterprise.connector.otex.client.ClientValue;


/**
 * {@inheritDoc}
 * <p>
 * This implementation accumlated properties from the the
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
    private Map properties;

    /**
     * Creates a Document Property Map for the specified object.
     *
     * @param objectId the livelink object
     * @param initSize initial size of the map
     */
    LivelinkDocument(int objectId, int initSize) throws RepositoryException
    {
        if (LOGGER.isLoggable(Level.FINE))
            LOGGER.fine("PROPERTY MAP FOR ID = " + objectId);

        properties = new LinkedHashMap(initSize);
    }

    /**
     * {@inheritDoc}
     */
    public Set getPropertyNames() {
        return properties.keySet();
    }

    /**
     * {@inheritDoc}
     */
    public Property findProperty(String name) throws RepositoryException {
        Object values = properties.get(name);
        if (values == null) 
            return null;
        else {
            if (LOGGER.isLoggable(Level.FINEST))
                LOGGER.finest("PROPERTY: " + name + " = " + values);
            return new LivelinkProperty((LinkedList) values);
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
        Object values = properties.get(name);
        if (values == null) {
            LinkedList firstValues = new LinkedList();
            firstValues.add(value);
            properties.put(name, firstValues);
        } else
            ((LinkedList) values).add(value);
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
        addProperty(name, getValue(value));
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
            addProperty(names[j], getValue(value));
    }


    /**
     * {@inheritDoc}
     */
    /*
     * TODO: We could use SimpleProperty instead, except that the lazy
     * initialization of the iterator there bugs me.
     */
    private static class LivelinkProperty implements Property {
        private final Iterator iterator;
        
        LivelinkProperty(LinkedList values) {
            this.iterator = values.iterator();
        }

        /**
         * {@inheritDoc}
         */
        public Value nextValue() throws RepositoryException {
            return (iterator.hasNext()) ? (Value) iterator.next() : null;
        }
    }

    /**
     * Creates a <code>Value</code> (of the appropriate type) from
     * a <code>ClientValue</code>. Complex <code>ClientValue</code>
     * objects (RecArrays or Lists), must be * decomposed into
     * individual atomic components before calling this method.
     *
     * @param clientValue the value to be represented.
     */
    public Value getValue(ClientValue clientValue) {
        try {
            switch (clientValue.type()) {
            case ClientValue.BOOLEAN:
                return Value.getBooleanValue(clientValue.toBoolean());
            case ClientValue.DATE:
                Calendar c = Calendar.getInstance();
                c.setTime(clientValue.toDate());
                // Livelink only stores timestamps to the nearest second, 
                // but LAPI constructs a Date object that includes milli-
                // seconds, which are taken from the current time.  So we
                // need to avoid using the milliseconds in the parameter.
                c.clear(Calendar.MILLISECOND);
                return Value.getDateValue(c);
            case ClientValue.DOUBLE: 
                return Value.getDoubleValue(clientValue.toDouble());
            case ClientValue.INTEGER:
                return Value.getLongValue(clientValue.toInteger());
            case ClientValue.STRING:
                return Value.getStringValue(clientValue.toString2());
            default:
                throw new AssertionError(
                            "The clientValue must have an atomic type.");
            }
        } catch (RepositoryException e) {
            LOGGER.warning("LivelinkValue factory caught exception " +
                           "exctracting value from ClientValue: " +
                           e.getMessage());
            return Value.getStringValue(e.getMessage());
        }
    }
}
