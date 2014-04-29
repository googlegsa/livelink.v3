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

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import java.util.logging.Logger;

/**
 * Formats dates multiple ways: ISO 8601 GMT time, SQL local time
 * (with or without milliseconds), and RFC 822 local time.
 *
 * This class is thread-safe. Access to the underlying
 * <code>Calendar</code> and <code>SimpleDateFormat</code> objects is
 * synchronized.
 */
/*
 * TODO(jlacey): The code here was designed to have one instance per
 * batch, to balance object creation, not frequent at just once per
 * DocumentList, and synchronization, which is fast in the absence of
 * contention. The code was extracted from what is now
 * LivelinkDocumentList and turned into a singleton. So we have
 * essentially no object creation, but all connector instances use the
 * singleton.
 *
 * So far that hasn't been a problem, but I noticed when writing
 * LivelinkDateFormatTest that testEverything runs in 6 milliseconds,
 * but testParse, which has only a single call to parse instead of
 * four calls to parse mixed with four calls to the toXxxString
 * methods, takes 100 milliseconds. That's about 140 times slower,
 * presumably due to thread contention.
 */
class LivelinkDateFormat {
    /** The logger for this class. */
    private static final Logger LOGGER =
        Logger.getLogger(LivelinkDateFormat.class.getName());

    /** A GMT calendar for converting timestamps to UTC. */
    private final Calendar gmtCalendar =
        Calendar.getInstance(TimeZone.getTimeZone("GMT+0"));

    /** The ISO 8601 date format returned in property values. */
    private final SimpleDateFormat iso8601 =
        new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

    /** The ISO SQL date format used in database queries. */
    private final SimpleDateFormat sql =
        new SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ss");

    /** The ISO SQL date format used in database queries, with milliseconds. */
    private final SimpleDateFormat sqlMillis =
        new SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ss.SSS");

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
     * 9.7 and earlier constructs a Date object that includes milli-
     * seconds, which are taken from the current time. So we need to
     * avoid using the milliseconds in the parameter.
     *
     * @param value a Livelink date
     * @return an ISO 8601 formatted string representation,
     *     using the pattern "yyyy-MM-dd'T'HH:mm:ss'Z'"
     * @see toSqlString
     */
    /*
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
     * Converts a local time date to an ISO SQL local time string,
     * with milliseconds.
     *
     * @param value a timestamp where local time is database local time
     * @return an ISO SQL string using the pattern
     * "yyyy-MM-dd' 'HH:mm:ss.SSS"
     * @see #toSqlString
     * @since 1.3.1
     */
    public synchronized String toSqlMillisString(Date value) {
        return sqlMillis.format(value);
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

    /**
     * Parses a string representation of a Date according to the
     * known underlying DateFormats.
     *
     * @param dateStr a String representation of a Date.
     * @return a Date parsed from the string, or null, if error.
     */
    public synchronized Date parse(String dateStr) {
        ParsePosition ppos = new ParsePosition(0);
        Date date = null;
        if (dateStr.length() > 10) {
            char c = dateStr.charAt(10);
            if (c == ' ') {
                if (dateStr.length() > 19)
                    date = sqlMillis.parse(dateStr, ppos);
                else
                    date = sql.parse(dateStr, ppos);
            } else if (c == 'T')
                date = iso8601.parse(dateStr, ppos);
            else
                date = rfc822.parse(dateStr, ppos);
        }

        if (date == null)
            LOGGER.warning("Unable to parse date: " + dateStr);

        return date;
    }
}
