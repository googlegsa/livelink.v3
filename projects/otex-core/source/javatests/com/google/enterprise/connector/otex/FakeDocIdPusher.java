// Copyright 2014 Google Inc. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.enterprise.connector.otex;

import com.google.enterprise.adaptor.Acl;
import com.google.enterprise.adaptor.DocId;
import com.google.enterprise.adaptor.DocIdPusher;
import com.google.enterprise.adaptor.ExceptionHandler;
import com.google.enterprise.adaptor.GroupPrincipal;
import com.google.enterprise.adaptor.Principal;

import java.util.Collection;
import java.util.Map;

class FakeDocIdPusher implements DocIdPusher{
  private Map<GroupPrincipal, ? extends Collection<Principal>> groupDefinitions;

  @Override
  public GroupPrincipal pushGroupDefinitions(
      Map<GroupPrincipal, ? extends Collection<Principal>> groupDefinitions,
          boolean arg1)
      throws InterruptedException {
    this.groupDefinitions = groupDefinitions;
    return null;
  }

  public Map<GroupPrincipal, ? extends Collection<Principal>>
      getGroupDefinitions() {
    return groupDefinitions;
  }

  @Override
  public DocId pushDocIds(Iterable<DocId> arg0) throws InterruptedException {
    throw new UnsupportedOperationException();
  }

  @Override
  public DocId pushDocIds(Iterable<DocId> arg0, ExceptionHandler arg1)
      throws InterruptedException {
    throw new UnsupportedOperationException();
  }

  @Override
  public GroupPrincipal pushGroupDefinitions(
      Map<GroupPrincipal, ? extends Collection<Principal>> arg0, boolean arg1,
      ExceptionHandler arg2) throws InterruptedException {
    throw new UnsupportedOperationException();
  }

  @Override
  public DocId pushNamedResources(Map<DocId, Acl> arg0)
      throws InterruptedException {
    throw new UnsupportedOperationException();
  }

  @Override
  public DocId pushNamedResources(Map<DocId, Acl> arg0, ExceptionHandler arg1)
      throws InterruptedException {
    throw new UnsupportedOperationException();
  }

  @Override
  public Record pushRecords(Iterable<Record> arg0) throws InterruptedException {
    throw new UnsupportedOperationException();
  }

  @Override
  public Record pushRecords(Iterable<Record> arg0, ExceptionHandler arg1)
      throws InterruptedException {
    throw new UnsupportedOperationException();
  }
}
