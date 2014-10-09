// Copyright (C) 2007-2009 Google Inc.
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

package com.google.enterprise.connector.otex.client;

import com.google.enterprise.connector.spi.RepositoryException;

import java.util.Date;
import java.util.Enumeration;

/**
 * A generic value interface for primitive types, lists, associative
 * arrays, and record arrays.
 */
public interface ClientValue {

    /** Associative array of key-value pairs. */
    int ASSOC = -18;

    /** Boolean value. */
    int BOOLEAN = 5;

    /** Date value. */
    int DATE = 	-7;

    /** Floating-point value. */
    int DOUBLE = -4;

    /** Error value. */
    int ERROR = 1;

    /** Integer value. */
    int INTEGER = 2;

    /** Long value. */
    int LONG = -8;

    /** Array of values. */
    int LIST = -2;

    /** Value has not been set. */
    int NOTSET = 7;

    /** Record of key-value pairs; behaves like an assoc */
    int RECORD = -109;

    /** String value. */
    int STRING = -1;

    /** Array of records. */
    int TABLE = -110;

    /** Undefined value (string value "?"). */
    int UNDEFINED = 0;


    /**
     * Gets the number of elements in this value.
     *
     * @return the number of characters in a string, the number of
     * elements in a list, the number of key-value pairs in an assoc,
     * or the number of records in a recarray
     */
    int size();


    /**
     * Sets the number of records in this recarray/list/assoc.
     */
    void setSize(int size) throws RepositoryException;


    /**
     * Sets the value of a specific item in recarray/list/assoc
     */
    void setInteger(int index, int value) throws RepositoryException;


    /**
     * Tests whether the ClientValue encapsulates a valid repository value.
     *
     * @return <code>true</code> if the value is a valid value.
     * <code>false</code> if the repository value is non-null object, 
     * is an error, is undefined, or is not set.
     */
    boolean hasValue();
    

    /**
     * Gets the type of this value.
     *
     * @return an integer type constant
     */
    int type();

    /**
     * Gets the list of field names for an assoc, record, or recarray value.
     *
     * @return an enumeration of strings
     */
    Enumeration<String> enumerateNames();

    /**
     * Gets a <code>ClientValue</code> from a stringified representation.
     * This is like the OScript <code>Str.StringToValue</code> method.
     * Internally, it uses <code>LLValue.crack</code>.
     */
    ClientValue stringToValue() throws RepositoryException;
    
    /**
     * Gets whether the named field from the given row has a defined value.
     *
     * @param row a zero-based row index
     * @param field a field name
     * @return <code>true</code> if the value is defined, or
     * <code>false</code> if the value is <code>Undefined</code>.
     */
    boolean isDefined(int row, String field) throws RepositoryException;
    
    /**
     * Gets the named field from the given row as a
     * <code>ClientValue</code> value.
     *
     * @param row a zero-based row index
     * @param field a field name
     * @return a <code>ClientValue</code> field value
     */
    ClientValue toValue(int row, String field) throws RepositoryException;

    /**
     * Gets the ordinal item from this ordered container as a 
     * <code>ClientValue</code> value.
     *
     * @param index a zero-based item index
     * @return a <code>ClientValue</code> field value
     */
    ClientValue toValue(int index) throws RepositoryException;

    /**
     * Gets the ordinal item from this ordered container as a 
     * <code>String</code> value.
     *
     * @param index a zero-based item index
     * @return a <code>String</code> field value
     */
    String toString(int index) throws RepositoryException;

    /**
     * Gets the ordinal item from this ordered container as a 
     * <code>int</code> value.
     *
     * @param index a zero-based item index
     * @return a <code>int</code> field value
     */
    int toInteger(int index) throws RepositoryException;

    /**
     * Gets the ordinal item from this ordered container as a
     * <code>long</code> value.
     *
     * @param index a zero-based item index
     * @return a <code>long</code> field value
     * @since 3.2.10
     */
    long toLong(int index) throws RepositoryException;

    /**
     * Gets the named field from the given row as a boolean value.
     *
     * @param row a zero-based row index
     * @param field a field name
     * @return a boolean field value
     */
    boolean toBoolean(int row, String field) throws RepositoryException;

    /**
     * Gets the named field from the given row as a
     * <code>java.util.Date</code> value.
     *
     * @param row a zero-based row index
     * @param field a field name
     * @return a <code>Date</code> field value
     */
    Date toDate(int row, String field) throws RepositoryException;

    /**
     * Gets the named field from the given row as a double value.
     *
     * @param row a zero-based row index
     * @param field a field name
     * @return a double field value
     */
    double toDouble(int row, String field) throws RepositoryException;

    /**
     * Gets the named field from the given row as an integer value.
     *
     * @param row a zero-based row index
     * @param field a field name
     * @return an integer field value
     */
    int toInteger(int row, String field) throws RepositoryException;

    /**
     * Gets the named field from the given row as a long value.
     *
     * @param row a zero-based row index
     * @param field a field name
     * @return a long field value
     * @since 3.2.10
     */
    long toLong(int row, String field) throws RepositoryException;

    /**
     * Gets the named field from the given row as a string value.
     *
     * @param row a zero-based row index
     * @param field a field name
     * @return a string field value
     */
    String toString(int row, String field) throws RepositoryException;
    
    /**
     * Gets whether the named field has a defined value.
     *
     * @param field a field name
     * @return <code>true</code> if the value is defined, or
     * <code>false</code> if the value is <code>Undefined</code>.
     */
    boolean isDefined(String field) throws RepositoryException;
    
    /**
     * Gets the named field as a <code>ClientValue</code> value.
     *
     * @param field a field name
     * @return a <code>ClientValue</code> field value
     */
    ClientValue toValue(String field) throws RepositoryException;

    /**
     * Gets the named field as a boolean value.
     *
     * @param field a field name
     * @return a boolean field value
     */
    boolean toBoolean(String field) throws RepositoryException;

    /**
     * Gets the named field as a <code>java.util.Date</code> value.
     *
     * @param field a field name
     * @return a <code>Date</code> field value
     */
    Date toDate(String field) throws RepositoryException;

    /**
     * Gets the named field as a double value.
     *
     * @param field a field name
     * @return a double field value
     */
    double toDouble(String field) throws RepositoryException;

    /**
     * Gets the named field as an integer value.
     *
     * @param field a field name
     * @return an integer field value
     */
    int toInteger(String field) throws RepositoryException;

    /**
     * Gets the named field as a long value.
     *
     * @param field a field name
     * @return a long field value
     * @since 3.2.10
     */
    long toLong(String field) throws RepositoryException;

    /**
     * Gets the named field as a string value.
     *
     * @param field a field name
     * @return a string field value
     */
    String toString(String field) throws RepositoryException;
    
    /**
     * Gets whether the named field has a defined value.
     *
     * @param index a 0-based array index
     * @return <code>true</code> if the value is defined, or
     * <code>false</code> if the value is <code>Undefined</code>.
     */
    boolean isDefined(int index) throws RepositoryException;
    
    /**
     * Gets the named field as a boolean value.
     *
     * @param index a 0-based array index
     * @return a boolean field value
     */
    boolean toBoolean(int index) throws RepositoryException;

    /**
     * Gets the named field as a <code>java.util.Date</code> value.
     *
     * @param index a 0-based array index
     * @return a <code>Date</code> field value
     */
    Date toDate(int index) throws RepositoryException;

    /**
     * Gets the named field as a double value.
     *
     * @param index a 0-based array index
     * @return a double field value
     */
    double toDouble(int index) throws RepositoryException;

    /**
     * Gets whether the value has a defined value.
     *
     * @return <code>true</code> if the value is defined, or
     * <code>false</code> if the value is <code>Undefined</code>.
     */
    boolean isDefined() throws RepositoryException;
    
    /**
     * Gets the value as a boolean value.
     *
     * @return a boolean value
     */
    boolean toBoolean() throws RepositoryException;

    /**
     * Gets the value as a
     * <code>java.util.Date</code> value.
     *
     * @return a <code>Date</code> value
     */
    Date toDate() throws RepositoryException;

    /**
     * Gets the value as a double value.
     *
     * @return a double field value
     */
    double toDouble() throws RepositoryException;

    /**
     * Gets the value as an integer value.
     *
     * @return an integer field value
     */
    int toInteger() throws RepositoryException;

    /**
     * Gets the value as a long value.
     *
     * @return a long field value
     * @since 1.3.1
     */
    long toLong() throws RepositoryException;

    /**
     * Gets the value as a string value.
     *
     * @return a string field value
     */
    /*
     * FIXME: This can't be called toString because it needs to throw
     * RepositoryExceptions.
     */
    String toString2() throws RepositoryException;

    
    /**
     * Add and item to a Assoc ClientValue.  This method is
     * overloaded to allow adding many different types.
     *
     * @param key the key associated with the following value
     * @param obj the object to add to the Assoc
     * @returns the new count of items in the Assoc
     */
    int add(String key, boolean obj) throws RepositoryException;
    int add(String key, char obj) throws RepositoryException;
    int add(String key, int obj) throws RepositoryException;
    int add(String key, long obj) throws RepositoryException;
    int add(String key, float obj) throws RepositoryException;
    int add(String key, double obj) throws RepositoryException;
    int add(String key, Boolean obj) throws RepositoryException;
    int add(String key, Double obj) throws RepositoryException;
    int add(String key, Float obj) throws RepositoryException;
    int add(String key, Integer obj) throws RepositoryException;
    int add(String key, Long obj) throws RepositoryException;
    int add(String key, String obj) throws RepositoryException;
    int add(String key, java.util.Date obj) throws RepositoryException;

    /**
     * Add and item to a List ClientValue.  This method is
     * overloaded to allow adding many different types.
     *
     * @param obj the object to add to the List
     * @returns the new count of items in the List
     */
    int add(Boolean obj) throws RepositoryException;
    int add(Double obj) throws RepositoryException;
    int add(Float obj) throws RepositoryException;
    int add(Integer obj) throws RepositoryException;
    int add(Long obj) throws RepositoryException;
    int add(String obj) throws RepositoryException;
    int add(java.util.Date obj) throws RepositoryException;
    int add(boolean obj) throws RepositoryException;
    int add(char obj) throws RepositoryException;
    int add(int obj) throws RepositoryException;
    int add(long obj) throws RepositoryException;
    int add(float obj) throws RepositoryException;
    int add(double obj) throws RepositoryException;

}
