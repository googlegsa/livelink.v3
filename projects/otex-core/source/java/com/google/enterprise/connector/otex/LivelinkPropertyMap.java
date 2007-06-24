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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;


import com.google.enterprise.connector.spi.Property;
import com.google.enterprise.connector.spi.PropertyMap;
import com.google.enterprise.connector.spi.PropertyMapList;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.SimpleValue;
import com.google.enterprise.connector.spi.SpiConstants;
import com.google.enterprise.connector.spi.Value;
import com.google.enterprise.connector.spi.ValueType;
import com.google.enterprise.connector.otex.client.Client;
import com.google.enterprise.connector.otex.client.ClientValue;

/**
 * {@inheritDoc}
 * <p>
 * This implementation accumlated properties from the the
 * underlying recarray, along with other properties derived from
 * the recarray.
 */
class LivelinkPropertyMap implements PropertyMap {

    /** The logger for this class. */
    private static final Logger LOGGER =
        Logger.getLogger(LivelinkResultSet.class.getName());

    /** The checkpoint string for this item */
    private String checkpoint;

    /*
     * I'm using a LinkedHashMap just because it's got a more
     * predictable ordering when I'm looking at test output.
     */
    private Map properties;


    /**
     * Creates a PropertyMap for the specified object.
     *
     * @param objectId the livelink object
     * @param initSize initial size of the map
     */
    LivelinkPropertyMap(int objectId, int initSize) throws RepositoryException
    {
        if (LOGGER.isLoggable(Level.FINE))
            LOGGER.fine("PROPERTY MAP FOR ID = " + objectId);

        properties = new LinkedHashMap(initSize);
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
        addProperty(name, new LivelinkValue(value));
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
        for (int j = 0; j < names.length; j++) {
            addProperty(names[j],
                        new LivelinkValue(field.fieldType, value));
        }
    }


    /**
     * Returns an Iterator over the Property objects in this 
     * PropertyMap.
     *
     * @returns Iterator over the PropertyMap entries.
     */
    public Iterator getProperties() {
        return new LivelinkPropertyMapIterator(properties);
    }


    /**
     * Returns a Property (or LinkedList of Properties) associated
     * with the supplied <code>name</code>, or <code>null</code> if
     * no such Properties exist.
     *
     * @param name a property name
     * @returns a Property value (or List of values)
     */
    public Property getProperty(String name) {
        Object values = properties.get(name);
        if (values == null)
            return null;
        else
            return new LivelinkProperty(name, (LinkedList) values);
    }


    /**
     * Gets a string of the form "yyyy-MM-dd HH:mm:ss,nnnnnn" where
     * nnnnnn is the object ID of the item represented by this property
     * map.
     *
     * @return a checkpoint string for this property map
     * @throws RepositoryException if an error occurs retrieving
     * the field data
     */
    public String checkpoint() throws RepositoryException {
        return checkpoint;
    }


    /**
     * Gets a string of the form "yyyy-MM-dd HH:mm:ss,nnnnnn" where
     * nnnnnn is the object ID of the item represented by this property
     * map.
     *
     * @param the checkpoint string for this property map
     * @throws RepositoryException if an error occurs retrieving
     * the field data
     */
    public void setCheckpoint(String checkpoint) {
        this.checkpoint = checkpoint;
    }


    /**
     * Iterates over a <code>PropertyMap</code>, returning each
     * <code>Property</code> it contains.
     */
    private static class LivelinkPropertyMapIterator implements Iterator {
        private final Iterator properties;

        LivelinkPropertyMapIterator(Map properties) {
            this.properties = properties.entrySet().iterator();
        }

        public boolean hasNext() {
            return properties.hasNext();
        }

        public Object next() {
            Map.Entry property = (Map.Entry) properties.next();
            return new LivelinkProperty((String) property.getKey(),
                                        (LinkedList) property.getValue());
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
    }


    /**
     * {@inheritDoc}
     * <p>
     * This implementation represents a property that always has one
     * or more values.
     */
    /*
     * We could just use SimpleProperty instead, and move the logging
     * to LivelinkPropertyMapIterator.
     */
    private static class LivelinkProperty implements Property {
        private final String name;
        private final LinkedList values;

        LivelinkProperty(String name, LinkedList values) {
            this.name = name;
            this.values = values;

            if (LOGGER.isLoggable(Level.FINEST))
                LOGGER.finest("PROPERTY: " + name + " = " + values);
        }

        public String getName() {
            return name;
        }

        public Value getValue() throws RepositoryException {
            return (Value) values.getFirst();
        }

        public Iterator getValues() throws RepositoryException {
            return values.iterator();
        }
    }
}
