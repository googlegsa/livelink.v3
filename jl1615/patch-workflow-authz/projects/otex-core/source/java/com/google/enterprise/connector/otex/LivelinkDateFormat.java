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

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

/**
 * This Date Formatter knows how to format dates two
 * different ways: ISO8601 GMT time and SQL local time.
 */
class LivelinkDateFormat {

    /** A GMT calendar for converting timestamps to UTC. */
    private final Calendar gmtCalendar =
        Calendar.getInstance(TimeZone.getTimeZone("GMT+0"));

    /** The ISO 8601 date format returned in property values. */
    private final SimpleDateFormat iso8601 =
        new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

    /** The ISO SQL date format used in database queries. */
    private final SimpleDateFormat sql =
        new SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ss");

    /** The RFC 822 date format for the SPI */
    private final SimpleDateFormat rfc822 =
        new SimpleDateFormat("EEE', 'dd' 'MMM' 'yyyy' 'HH:mm:ss z");

    /** The Singleton LivelinkDateFormatter */ 
    private static final LivelinkDateFormat singleton =
        new LivelinkDateFormat();

    private LivelinkDateFormat()
    {
        iso8601.setCalendar(gmtCalendar);
        rfc822.setCalendar(gmtCalendar);
        rfc822.setTimeZone(TimeZone.getTimeZone("GMT"));
    }
       
    /** Return the singleton instance of the Livelink Date Formatter.
     *
     * @returns the LivelinkDateFormat instance.
     */
    public static LivelinkDateFormat getInstance()
    {
        return singleton;
    }


    /**
     * Livelink only stores timestamps to the nearest second, but LAPI
     * constructs a Date object that includes milliseconds, which are
     * taken from the current time. So we need to avoid using the
     * milliseconds in the parameter.
     *
     * @param value a Livelink date
     * @return an ISO 8601 formatted string representation,
     *     using the pattern "yyyy-MM-dd'T'HH:mm:ss'Z'"
     * @see toSqlString
     */
    /*
     * This method is synchronized to emphasize safety over speed.
     * <code>Calendar</code> and <code>SimpleDateFormat</code> objects
     * are not thread-safe. We've put these calendar and date format
     * objects in instance fields to balance object creation, not
     * frequent at just once per result set, and synchronization,
     * which is fast in the absence of contention. It is extremely
     * unlikely that <code>DocumentList</code> instances or their
     * children will be called from multiple threads, but there's no
     * need to cut corners here.
     * 
     * TODO: LAPI converts the database local time to UTC using the
     * default Java time zone, so if the database time zone is different
     * from the Java time zone, we need to adjust the given Date
     * accordingly. In order to account for Daylight Savings, we
     * probably need to subtract the offsets for the date under both
     * time zones and apply the resulting adjustment (in milliseconds)
     * to the Date object.
     */
    public synchronized String toIso8601String(Date value) {
        return iso8601.format(value);
    }


    /**
     * Converts a local time date to an ISO SQL local time string.
     *
     * @param value a timestamp where local time is database local time
     * @return an ISO SQL string using the pattern
     * "yyyy-MM-dd' 'HH:mm:ss"
     * @see #toIso8601String
     */
    public synchronized String toSqlString(Date value) {
        return sql.format(value);
    }


    /**
     * Converts a local time date to an RFC 822 format time string.
     *
     * @param value a timestamp where local time is database local time
     * @return an RFC 822 string 
     * @see #toIso8601String
     */
    public synchronized String toRfc822String(Date value) {
        return rfc822.format(value);
    }

}








