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

import com.google.enterprise.connector.common.StringUtils;
import com.google.enterprise.connector.pusher.FeedConnection;
import com.google.enterprise.connector.pusher.FeedData;
import com.google.enterprise.connector.pusher.GsaFeedData;
import com.google.enterprise.connector.pusher.FeedException;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;

public class MockFileFeedConnection implements FeedConnection {

    StringBuffer buf = null;

    private final PrintStream printStream;

    public String getFeed() {
        String result;
        if (buf == null) {
            result = "";
        }
        result = buf.toString();
        buf = new StringBuffer(2048);
        return result;
    }

    public MockFileFeedConnection(PrintStream ps) {
        buf = new StringBuffer(2048);
        printStream = ps;
    }

    public String sendData(String dataSource, FeedData feedData)
        throws FeedException {
        try {
            InputStream data = ((GsaFeedData)feedData).getData();
            //    String dataStr = StringUtils.streamToString(data);
            //    buf.append(dataStr);
            //    printStream.println(dataStr);
            long before = System.currentTimeMillis();
            if (true) {
                byte[] buffer = new byte[4096];
                int count = 0;
                while ((count = data.read(buffer)) != -1)
                    printStream.write(buffer, 0, count);
                printStream.println();
            } else {
                int ch;
                while ((ch = data.read()) != -1)
                    printStream.write(ch);
                printStream.println();
            }
            long after = System.currentTimeMillis();
            System.out.println("Elapsed time = " + (after - before));
        } catch (IOException e) {
            throw new FeedException("IO Error.", e);
        }
        return "Success";
    }

}
