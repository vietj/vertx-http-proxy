package io.vertx.httpproxy;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class ProxyClientNotPersistentTest extends HttpTest {

  public ProxyClientNotPersistentTest() {
    keepAlive = false;
    pipelining = false;
  }
}
