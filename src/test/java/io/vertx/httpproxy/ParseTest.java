package io.vertx.httpproxy;

import io.vertx.httpproxy.impl.ParseUtils;
import org.junit.Test;

import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class ParseTest {

  @Test
  public void testParseWarningDates() {
    assertNull(ParseUtils.parseWarningHeaderDate(""));
    assertNull(ParseUtils.parseWarningHeaderDate("code"));
    assertNull(ParseUtils.parseWarningHeaderDate("code agent"));
    assertNull(ParseUtils.parseWarningHeaderDate("code agent text"));
    assertNull(ParseUtils.parseWarningHeaderDate("code agent text date"));
    assertNotNull(ParseUtils.parseWarningHeaderDate("code agent text \"Sat, 19 Nov 2016 21:50:20 GMT\""));
  }

}
