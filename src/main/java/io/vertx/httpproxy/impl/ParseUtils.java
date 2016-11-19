package io.vertx.httpproxy.impl;

import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.TimeZone;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class ParseUtils {

  public static Instant parseDateHeaderDate(String value) {
    try {
      return parseHttpDate(value);
    } catch (Exception e) {
      return null;
    }
  }

  public static Instant parseWarningHeaderDate(String value) {
    // warn-code
    int index = value.indexOf(' ');
    if (index > 0) {
      // warn-agent
      index = value.indexOf(' ', index + 1);
      if (index > 0) {
        // warn-text
        index = value.indexOf(' ', index + 1);
        if (index > 0) {
          // warn-date
          int len = value.length();
          if (index + 2 < len && value.charAt(index + 1) == '"' && value.charAt(len - 1) == '"') {
            // Space for 2 double quotes
            String date = value.substring(index + 2, len - 1);
            try {
              return parseHttpDate(date);
            } catch (Exception ignore) {
            }
          }
        }
      }
    }
    return null;
  }

  private static final SimpleDateFormat RFC_850_DATE_TIME = new SimpleDateFormat("EEEEEEEEE, dd-MMM-yy HH:mm:ss zzz", Locale.US);
  private static final SimpleDateFormat ASC_TIME = new SimpleDateFormat("EEE MMM dd HH:mm:ss yyyy", Locale.US);

  static {
    RFC_850_DATE_TIME.setTimeZone(TimeZone.getTimeZone("GMT"));
    ASC_TIME.setTimeZone(TimeZone.getTimeZone("GMT"));
  }

  // http://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.3.1
  public static Instant parseHttpDate(String value) throws Exception {
    int sep = 0;
    while (true) {
      if (sep < value.length()) {
        char c = value.charAt(sep);
        if (c == ',') {
          String s = value.substring(0, sep);
          if (parseWkday(s) != null) {
            // rfc1123-date
            return Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(value));
          } else if (parseWeekday(s) != null) {
            // rfc850-date
            return RFC_850_DATE_TIME.parse(value).toInstant();
          }
          return null;
        }  else if (c == ' ') {
          String s = value.substring(0, sep);
          if (parseWkday(s) != null) {
            // asctime-date
            return ASC_TIME.parse(value).toInstant();
          }
          return null;
        }
        sep++;
      } else {
        return null;
      }
    }
  }

  private static DayOfWeek parseWkday(String value) {
    switch (value) {
      case "Mon":
        return DayOfWeek.MONDAY;
      case "Tue":
        return DayOfWeek.TUESDAY;
      case "Wed":
        return DayOfWeek.WEDNESDAY;
      case "Thu":
        return DayOfWeek.THURSDAY;
      case "Fri":
        return DayOfWeek.FRIDAY;
      case "Sat":
        return DayOfWeek.SATURDAY;
      case "Sun":
        return DayOfWeek.SUNDAY;
      default:
        return null;
    }
  }

  private static DayOfWeek parseWeekday(String value) {
    switch (value) {
      case "Monday":
        return DayOfWeek.MONDAY;
      case "Tuesday":
        return DayOfWeek.TUESDAY;
      case "Wednesday":
        return DayOfWeek.WEDNESDAY;
      case "Thursday":
        return DayOfWeek.THURSDAY;
      case "Friday":
        return DayOfWeek.FRIDAY;
      case "Saturday":
        return DayOfWeek.SATURDAY;
      case "Sunday":
        return DayOfWeek.SUNDAY;
      default:
        return null;
    }
  }
}
