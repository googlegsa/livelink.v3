// Copyright (C) 2010 Google Inc.
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

package com.opentext.api;

/**
 * Provides public access to LAPI methods with package access. This
 * class is in the <code>com.opentext.api</code> package so that we
 * can call inaccessible methods.
 */
public class GoogleThunk {
    /** Wraps the <code>LLSession.unMarshall</code> method. */
    public static void unMarshall(LLSession session) {
        session.unMarshall();
    }

    /** Do not instantiate this class. */
    private GoogleThunk() {
        throw new AssertionError();
    }
}
