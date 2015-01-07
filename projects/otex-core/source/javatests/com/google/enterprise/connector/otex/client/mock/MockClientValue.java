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

package com.google.enterprise.connector.otex.client.mock;

import com.google.enterprise.connector.otex.client.ClientValue;
import com.google.enterprise.connector.spi.RepositoryException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Vector;

/**
 * A partial mock implementation of a <code>ClientValue</code>. Many
 * of the methods are not implemented and throw an
 * <code>IllegalArgumentException</code>.
 */
public final class MockClientValue implements ClientValue {
  private final int type;
  private final List<String> fieldNames;
  private final Vector<List<Object>> tableValues;
  private final Vector<Object> assocValues;
  private final Vector<Object> listValues;
  private final Object atomicValue;

  /** Constructs a Recarray. */
  public MockClientValue(String[] fieldNames, Object[][] values) {
    if (fieldNames == null || values == null ||
        values.length > 0 && (fieldNames.length != values[0].length)) {
      throw new IllegalArgumentException();
    }
    this.type = TABLE;
    this.fieldNames = new ArrayList<String>(Arrays.asList(fieldNames));
    this.tableValues = new Vector<List<Object>>(values.length);
    for (int i = 0; i < values.length; i++) {
      tableValues.add(Arrays.asList(values[i]));
    }
    this.assocValues = null;
    this.listValues = null;
    this.atomicValue = null;
  }

  /** Constructs an Assoc. */
  MockClientValue(String[] fieldNames, Object[] values) {
    if (fieldNames == null || values == null ||
        fieldNames.length != values.length) {
      throw new IllegalArgumentException();
    }
    this.type = ASSOC;
    this.fieldNames = new ArrayList<String>(Arrays.asList(fieldNames));
    this.tableValues = null;
    this.assocValues = new Vector<Object>(Arrays.asList(values));
    this.listValues = null;
    this.atomicValue = null;
  }

  /** Constructs a List. */
  MockClientValue(Object[] values) {
    if (values == null)
      throw new IllegalArgumentException();
    this.type = LIST;
    this.fieldNames = null;
    this.tableValues = null;
    this.assocValues = null;
    this.listValues = new Vector<Object>(Arrays.asList(values));
    this.atomicValue = null;
  }

  /** Constructs an undefined value. */
  public MockClientValue() {
    this.type = UNDEFINED;
    this.fieldNames = null;
    this.tableValues = null;
    this.assocValues = null;
    this.listValues = null;
    this.atomicValue = null;
  }

  /** Constructs an atomic value. */
  public MockClientValue(Object value) {
    if (value == null) {
      this.type = UNDEFINED;
    } else if (value instanceof Date) {
      this.type = DATE;
    } else if (value instanceof Integer) {
      this.type = INTEGER;
    } else if (value instanceof Long) {
      this.type = LONG;
    } else {
      this.type = STRING;
    }
    this.fieldNames = null;
    this.tableValues = null;
    this.assocValues = null;
    this.listValues = null;
    this.atomicValue = value;
  }

  private Object getField(List<Object> values, String field)
      throws RepositoryException {
    for (int i = 0; i < fieldNames.size(); i++) {
      if (fieldNames.get(i).equals(field))
        return values.get(i);
    }
    throw new RepositoryException("LLValue unknown field name: " + field);
  }

  private Object getValue(int row, String field)
      throws RepositoryException {
    if (type != TABLE)
      throw new IllegalArgumentException("ClientValue is not a table.");
    if (row < 0 || row > tableValues.size())
      throw new IllegalArgumentException(String.valueOf(row));
    return getField(tableValues.get(row), field);
  }

  private Object getValue(String field)
      throws RepositoryException {
    if (type != ASSOC)
      throw new IllegalArgumentException("ClientValue is not an assoc.");
    return getField(assocValues, field);
  }

  private Object getValue(int index) {
    if (type != LIST)
      throw new IllegalArgumentException("ClientValue is not a list.");
    return listValues.get(index);
  }

  @Override
  public int size() {
    switch (type) {
      case TABLE:
        return tableValues.size();
      case ASSOC:
        return assocValues.size();
      case LIST:
        return listValues.size();
      case STRING:
        // FIXME: The value may not be a string.
        if (atomicValue instanceof String)
          return atomicValue.toString().length();
        else
          throw new IllegalArgumentException("ClientValue has no size");
      default:
        throw new IllegalArgumentException("ClientValue has no size");
    }
  }

  /** This implementation only works on empty values. */
  @Override
  public void setSize(int size) {
    switch (type) {
      case TABLE:
        int originalSize = tableValues.size();
        tableValues.setSize(size);
        for (int i = originalSize + 1; i < size; i++) {
          tableValues.add(new ArrayList<Object>());
        }
        break;
      case ASSOC:
        assocValues.setSize(size);
        break;
      case LIST:
        listValues.setSize(size);
        break;
      default:
        throw new IllegalArgumentException("ClientValue has no size");
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setInteger(int index, int value) {
    throw new IllegalArgumentException();
  }

  @Override
  public int type() {
    return type;
  }

  @Override
  public Enumeration<String> enumerateNames() {
    return new Enumeration<String>() {
      private int i = 0;
      @Override public boolean hasMoreElements() {
        return i < fieldNames.size();
      }
      @Override public String nextElement() {
        return fieldNames.get(i++);
      }
    };
  }

  @Override
  public ClientValue stringToValue() {
    return this;
  }

  @Override
  public boolean isDefined(int row, String field) throws RepositoryException {
    return getValue(row, field) != null;
  }

  @Override
  public boolean hasValue() {
    return type != UNDEFINED;
  }

  @Override
  public ClientValue toValue(int row, String field) throws RepositoryException {
    return new MockClientValue(getValue(row, field));
  }

  @Override
  public boolean toBoolean(int row, String field) {
    throw new IllegalArgumentException();
  }

  @Override
  public Date toDate(int row, String field) throws RepositoryException {
    Object v = getValue(row, field);
    if (v instanceof Date)
      return (Date) v;
    else
      throw new IllegalArgumentException();
  }

  @Override
  public double toDouble(int row, String field) {
    throw new IllegalArgumentException();
  }

  @Override
  public int toInteger(int row, String field) throws RepositoryException {
    Object v = getValue(row, field);
    if (v instanceof Number)
      return ((Number) v).intValue();
    else
      return Integer.parseInt(v.toString());
  }

  @Override
  public long toLong(int row, String field) throws RepositoryException {
    Object v = getValue(row, field);
    if (v instanceof Number)
      return ((Number) v).longValue();
    else
      return Integer.parseInt(v.toString());
  }

  @Override
  public String toString(int row, String field) throws RepositoryException {
    return getValue(row, field).toString();
  }

  @Override
  public boolean isDefined(String field) {
    throw new IllegalArgumentException();
  }

  @Override
  public ClientValue toValue(String field) {
    throw new IllegalArgumentException();
  }

  @Override
  public boolean toBoolean(String field) throws RepositoryException {
    Object v = getValue(field);
    if (v instanceof Boolean)
      return ((Boolean) v).booleanValue();
    else
      return Boolean.parseBoolean(v.toString());
  }

  @Override
  public Date toDate(String field) {
    throw new IllegalArgumentException();
  }

  @Override
  public double toDouble(String field) {
    throw new IllegalArgumentException();
  }

  @Override
  public int toInteger(String field) throws RepositoryException {
    Object v = getValue(field);
    if (v instanceof Number)
      return ((Number) v).intValue();
    else
      return Integer.parseInt(v.toString());
  }

  @Override
  public long toLong(String field) throws RepositoryException {
    Object v = getValue(field);
    if (v instanceof Number)
      return ((Number) v).longValue();
    else
      return Integer.parseInt(v.toString());
  }

  @Override
  public String toString(String field) throws RepositoryException {
    return getValue(field).toString();
  }

  @Override
  public boolean isDefined(int index) {
    throw new IllegalArgumentException();
  }

  @Override
  public ClientValue toValue(int index) throws RepositoryException {
    Object value = getValue(index);
    if (value instanceof ClientValue) {
      return (ClientValue) value;
    } else {
      return new MockClientValue(value);
    }
  }

  @Override
  public boolean toBoolean(int index) {
    throw new IllegalArgumentException();
  }

  @Override
  public Date toDate(int index) {
    throw new IllegalArgumentException();
  }

  @Override
  public double toDouble(int index) {
    throw new IllegalArgumentException();
  }

  @Override
  public int toInteger(int index) {
    throw new IllegalArgumentException();
  }

  @Override
  public long toLong(int index) {
    throw new IllegalArgumentException();
  }

  @Override
  public String toString(int index) {
    throw new IllegalArgumentException();
  }

  @Override
  public boolean isDefined() {
    return type != UNDEFINED;
  }

  @Override
  public boolean toBoolean() {
    throw new IllegalArgumentException();
  }

  @Override
  public Date toDate() {
    if (atomicValue != null && atomicValue instanceof Date) {
      return (Date) atomicValue;
    } else {
      throw new IllegalArgumentException("ClientValue is not a date.");
    }
  }

  @Override
  public double toDouble() {
    throw new IllegalArgumentException();
  }

  @Override
  public int toInteger() {
    if (atomicValue != null)
      if (atomicValue instanceof Number) {
        return ((Number) atomicValue).intValue();
      } else {
        return Integer.parseInt(atomicValue.toString());
      }
    else {
      throw new IllegalArgumentException("ClientValue is not an integer.");
    }
  }

  @Override
  public long toLong() {
    if (atomicValue != null)
      if (atomicValue instanceof Number) {
        return ((Number) atomicValue).longValue();
      } else {
        return Long.parseLong(atomicValue.toString());
      }
    else {
      throw new IllegalArgumentException("ClientValue is not a long.");
    }
  }

  @Override
  public String toString2() {
    return (type == UNDEFINED) ? "?" : atomicValue.toString();
  }

  private int addField(String key, Object obj) {
    for (int i = 0; i < fieldNames.size(); i++) {
      if (fieldNames.get(i).equals(key)) {
        assocValues.set(i, obj);
        return assocValues.size();
      }
    }

    fieldNames.add(key);
    assocValues.add(obj);
    return assocValues.size();
  }

  @Override
  public int add(String key, boolean obj) {
    return addField(key, Boolean.valueOf(obj));
  }

  @Override
  public int add(String key, char obj) {
    throw new IllegalArgumentException();
  }

  @Override
  public int add(String key, int obj) {
    return addField(key, new Integer(obj));
  }

  @Override
  public int add(String key, long obj) {
    throw new IllegalArgumentException();
  }

  @Override
  public int add(String key, float obj) {
    throw new IllegalArgumentException();
  }

  @Override
  public int add(String key, double obj) {
    throw new IllegalArgumentException();
  }

  @Override
  public int add(String key, Boolean obj) {
    throw new IllegalArgumentException();
  }

  @Override
  public int add(String key, Double obj) {
    throw new IllegalArgumentException();
  }

  @Override
  public int add(String key, Float obj) {
    throw new IllegalArgumentException();
  }

  @Override
  public int add(String key, Integer obj) {
    return addField(key, obj);
  }

  @Override
  public int add(String key, Long obj) {
    throw new IllegalArgumentException();
  }

  @Override
  public int add(String key, String obj) {
    return addField(key, obj);
  }

  @Override
  public int add(String key, java.util.Date obj) {
    return addField(key, obj);
  }

  @Override
  public int add(boolean obj) {
    throw new IllegalArgumentException();
  }

  @Override
  public int add(char obj) {
    throw new IllegalArgumentException();
  }

  @Override
  public int add(int obj) {
    throw new IllegalArgumentException();
  }

  @Override
  public int add(long obj) {
    throw new IllegalArgumentException();
  }

  @Override
  public int add(float obj) {
    throw new IllegalArgumentException();
  }

  @Override
  public int add(double obj) {
    throw new IllegalArgumentException();
  }

  @Override
  public int add(Boolean obj) {
    throw new IllegalArgumentException();
  }

  @Override
  public int add(Double obj) {
    throw new IllegalArgumentException();
  }

  @Override
  public int add(Float obj) {
    throw new IllegalArgumentException();
  }

  @Override
  public int add(Integer obj) {
    throw new IllegalArgumentException();
  }

  @Override
  public int add(Long obj) {
    throw new IllegalArgumentException();
  }

  @Override
  public int add(String obj) {
    throw new IllegalArgumentException();
  }

  @Override
  public int add(java.util.Date obj) {
    throw new IllegalArgumentException();
  }
}
