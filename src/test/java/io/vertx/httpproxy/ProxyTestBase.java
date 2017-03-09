package io.vertx.httpproxy;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@RunWith(VertxUnitRunner.class)
public class ProxyTestBase {

  protected HttpServerOptions proxyOptions;
  protected HttpClientOptions clientOptions;


  protected Vertx vertx;

  @Before
  public void setUp() {
    proxyOptions = new HttpServerOptions().setPort(8080).setHost("localhost");
    clientOptions = new HttpClientOptions();
    vertx = Vertx.vertx();
  }

  @After
  public void tearDown(TestContext context) {
    vertx.close(context.asyncAssertSuccess());
  }


}
