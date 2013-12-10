// Copyright 2007 Google Inc.
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

import com.google.enterprise.connector.pusher.FeedConnection;
import com.google.enterprise.connector.pusher.FeedData;
import com.google.enterprise.connector.pusher.XmlFeed;
import com.google.enterprise.connector.pusher.FeedException;

import java.io.IOException;
import java.io.OutputStream;

public class MockFileFeedConnection implements FeedConnection {
  private final OutputStream outputStream;

  public MockFileFeedConnection(OutputStream os) {
    outputStream = os;
  }

  @Override
  public String sendData(FeedData feedData)
    throws FeedException {
    try {
      ((XmlFeed)feedData).writeTo(outputStream);
    } catch (IOException e) {
      throw new FeedException("IO Error.", e);
    }
    return "Success";
  }

  @Override
  public boolean isBacklogged() {
    return false;
  }

  @Override
  public String getContentEncodings() {
    return "base64binary";
  }

  @Override
  public boolean supportsInheritedAcls() {
    return true;
  }
}
