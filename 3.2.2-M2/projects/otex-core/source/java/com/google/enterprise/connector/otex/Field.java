// Copyright 2007 Google Inc.
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

/**
 * Describes the properties returned to the caller and the fields in
 * Livelink needed to implement them. A field with no property names
 * indicates a field that is required from the database but which is
 * not returned as a property to the caller.
 */
final class Field {
    /** The WebNodes select expression. */
    public final String selectExpression;

    /** The WebNodes recarray column name. */
    public final String fieldName;

    /**
     * The property names to publish the value as. May be an empty
     * array, but it must not be <code>null</code>.
     */
    public final String[] propertyNames;


    /**
     * Creates a field with a select list expression and corresponding
     * property names.
     *
     * @param selectExpression a select list expression
     * @param fieldName a recarray field name
     * @param propertyNames a possibly empty array of property names
     */
    public static Field fromExpression(String selectExpression,
            String fieldName, String... propertyNames) {
        return new Field(selectExpression, fieldName, propertyNames);
    }

    /**
     * Creates a field with no corresponding property names.
     *
     * @param fieldName the recarray field name
     */
    public Field(String fieldName) {
        this(fieldName, fieldName, new String[0]);
    }

    /**
     * Creates a field with one corresponding property name.
     *
     * @param fieldName the recarray field name
     * @param propertyName the output property name
     */
    public Field(String fieldName, String propertyName) {
        this(fieldName, fieldName, new String[] { propertyName });
    }

    /**
     * Creates a field with two corresponding property names. The
     * multiple property names are not ordered in any way.
     *
     * @param fieldName the recarray field name
     * @param propertyName1 one output property name
     * @param propertyName2 another output property name
     */
    /* Close enough to varargs for our purposes. */
    public Field(String fieldName, String propertyName1, String propertyName2) {
        this(fieldName, fieldName,
            new String[] { propertyName1, propertyName2 });
    }

    /**
     * Creates a field with multiple corresponding property names. The
     * multiple property names are not ordered in any way.
     *
     * @param fieldName the recarray field name
     * @param propertyNames the output property names, which may be
     * an empty array but not <code>null</code>
     */
    private Field(String selectExpression, String fieldName,
            String[] propertyNames) {
        assert selectExpression != null;
        assert fieldName != null;
        assert selectExpression.endsWith(fieldName) : fieldName;

        // This is obvious by inspection here, but code elsewhere
        // depends on this.
        assert propertyNames != null : fieldName;

        this.selectExpression = selectExpression;
        this.fieldName = fieldName;
        this.propertyNames = propertyNames;
    }
}
