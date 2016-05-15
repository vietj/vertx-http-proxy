package io.vertx.ext.reverseproxy;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import org.junit.Test;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class SimpleTest extends ProxyTestBase {

  @Test
  public void testNotfound(TestContext ctx) {
    HttpProxy proxy = HttpProxy.createProxy(vertx, options);
    Async async1 = ctx.async();
    proxy.listen(ctx.asyncAssertSuccess(p -> async1.complete()));
    async1.awaitSuccess();
    HttpClient client = vertx.createHttpClient();
    Async async2 = ctx.async();
    client.getNow(8080, "localhost", "/", resp -> {
      ctx.assertEquals(404, resp.statusCode());
      async2.complete();
    });
  }

  @Test
  public void testGet(TestContext ctx) {
    HttpProxy proxy = HttpProxy.createProxy(vertx, options);
    Async async1 = ctx.async();
    HttpServer server2 = vertx.createHttpServer(new HttpServerOptions().setPort(8081).setHost("localhost"));
    server2.requestHandler(req -> {
      req.response().end("Hello World");
    });
    server2.listen(ctx.asyncAssertSuccess(s -> async1.complete()));
    proxy.addBackend(new Backend() {
      @Override
      public void getClient(String host, String path, Handler<HttpClient> handler) {
        handler.handle(vertx.createHttpClient(new HttpClientOptions().setDefaultPort(8081).setDefaultHost("localhost")));
      }
    });
    Async async2 = ctx.async();
    proxy.listen(ctx.asyncAssertSuccess(p -> async2.complete()));
    async2.awaitSuccess();
    HttpClient client = vertx.createHttpClient();
    Async async3 = ctx.async();
    client.getNow(8080, "localhost", "/", resp -> {
      ctx.assertEquals(200, resp.statusCode());
      resp.bodyHandler(buff -> {
        ctx.assertEquals("Hello World", buff.toString());
        async3.complete();
      });
    });
  }
}
