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

/**
 * The options for including the domain name with the username for
 * authentication and authorization.
 *
 * @since 2.8
 */
enum DomainAndName {
  /** Do not use the identity domain. */
  FALSE,

  /**
   * Do not use the identity domain but allow DNS-style domains in
   * the username for authentication. This matches the behavior in
   * versions prior to 2.8.
   */
  LEGACY,

  /**
   * Use the identity domain (or a DNS-style domain in the username)
   * only for authentication. This is useful when using HTTP tunneling
   * and the web server requires the domain for authentication.
   */
  AUTHENTICATION,

  /** Use the identity domain for authentication and authorization. */
  TRUE;
}
