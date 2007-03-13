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

package com.google.enterprise.connector.otex.client;

import java.util.Date;

import com.google.enterprise.connector.spi.RepositoryException;

/**
 * A table-like interface for the database results.
 */
public interface RecArray {
    /**
     * Gets the number of records in this recarray.
     *
     * @return the number of records
     */
    int size();

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
     * <code>RecArray</code> value.
     *
     * @param row a zero-based row index
     * @param field a field name
     * @return a <code>RecArray</code> field value
     */
    RecArray toValue(int row, String field) throws RepositoryException;

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
     * Gets the named field as a <code>RecArray</code> value.
     *
     * @param field a field name
     * @return a <code>RecArray</code> field value
     */
    RecArray toValue(String field) throws RepositoryException;

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
     * Gets the named field as a string value.
     *
     * @param field a field name
     * @return a string field value
     */
    String toString(String field) throws RepositoryException;
    
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
     * Gets the value as a string value.
     *
     * @return a string field value
     */
    /*
     * FIXME: This can't be called toString because it needs to throw
     * RepositoryExceptions.
     */
    String toString2() throws RepositoryException;
}
