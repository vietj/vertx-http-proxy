package io.vertx.httpproxy;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class ProxyClientKeepAliveTest extends HttpTest {

  public ProxyClientKeepAliveTest() {
    keepAlive = true;
    pipelining = false;
  }
}
