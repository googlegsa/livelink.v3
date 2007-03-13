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

import com.google.enterprise.connector.spi.ValueType;

/**
 * Describes the properties returned to the caller and the fields
 * in Livelink needed to implement them. A null
 * <code>fieldName</code> indicates a property that is in not in
 * the recarray selected from the database. These properties are
 * implemented separately. A null <code>propertyName</code>
 * indicates a field that is required from the database but which
 * is not returned as a property to the caller.
 */
final class Field {
    public final String fieldName;
    public final ValueType fieldType;
    public final String propertyName;

    public Field(String fieldName, ValueType fieldType,
        String propertyName) {
        this.fieldName = fieldName;
        this.fieldType = fieldType;
        this.propertyName = propertyName;
    }
}
    
