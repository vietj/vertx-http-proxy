package io.vertx.httpproxy;

import io.vertx.ext.unit.TestContext;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class ProxyClientKeepAliveTest extends HttpTest {

  public ProxyClientKeepAliveTest() {
    keepAlive = true;
    pipelining = false;
  }
}
