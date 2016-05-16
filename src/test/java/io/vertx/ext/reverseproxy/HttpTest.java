package io.vertx.ext.reverseproxy;

import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.net.impl.SocketAddressImpl;
import io.vertx.ext.reverseproxy.backend.BackendProvider;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import org.junit.Test;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class HttpTest extends ProxyTestBase {

  private HttpProxy startProxy(TestContext ctx, BackendProvider... backends) {
    HttpProxy proxy = HttpProxy.createProxy(vertx, options);
    for (BackendProvider backend : backends) {
      proxy.addBackend(backend);
    }
    Async async1 = ctx.async();
    proxy.listen(ctx.asyncAssertSuccess(p -> async1.complete()));
    async1.awaitSuccess();
    return proxy;
  }

  private BackendProvider startBackend(TestContext ctx, int port, Handler<HttpServerRequest> handler) {
    HttpServer backendServer = vertx.createHttpServer(new HttpServerOptions().setPort(port).setHost("localhost"));
    backendServer.requestHandler(handler);
    Async async = ctx.async();
    backendServer.listen(ctx.asyncAssertSuccess(s -> async.complete()));
    async.awaitSuccess();
    return new BackendProvider() {
      @Override
      public void handle(ProxyRequest request) {
        request.handle(() -> new SocketAddressImpl(port, "localhost"));
      }
    };
  }

  @Test
  public void testNotfound(TestContext ctx) {
    startProxy(ctx);
    HttpClient client = vertx.createHttpClient();
    Async async = ctx.async();
    client.getNow(8080, "localhost", "/", resp -> {
      ctx.assertEquals(404, resp.statusCode());
      async.complete();
    });
  }

  @Test
  public void testGet(TestContext ctx) {
    BackendProvider backend = startBackend(ctx, 8081, req -> {
      ctx.assertEquals("/somepath", req.uri());
      ctx.assertEquals("localhost:8081", req.host());
      req.response().end("Hello World");
    });
    startProxy(ctx, backend);
    HttpClient client = vertx.createHttpClient();
    Async async = ctx.async();
    client.getNow(8080, "localhost", "/somepath", resp -> {
      ctx.assertEquals(200, resp.statusCode());
      resp.bodyHandler(buff -> {
        ctx.assertEquals("Hello World", buff.toString());
        async.complete();
      });
    });
  }

  @Test
  public void testPost(TestContext ctx) {
    byte[] body = new byte[1024];
    Random random = new Random();
    random.nextBytes(body);
    Async async = ctx.async(2);
    BackendProvider backend = startBackend(ctx, 8081, req -> {
      req.bodyHandler(buff -> {
        req.response().end();
        ctx.assertEquals(Buffer.buffer(body), buff);
        async.complete();
      });
    });
    startProxy(ctx, backend);
    HttpClient client = vertx.createHttpClient();
    HttpClientRequest req = client.get(8080, "localhost", "/", resp -> {
      ctx.assertEquals(200, resp.statusCode());
      resp.endHandler(v -> {
        async.complete();
      });
    });
    req.end(Buffer.buffer(body));
  }

  @Test
  public void testProxyCloseDuringUpload(TestContext ctx) {
    BackendProvider backend = startBackend(ctx, 8081, req -> {
      AtomicInteger len = new AtomicInteger();
      req.handler(buff -> {
        if (len.addAndGet(buff.length()) == 1024) {
          req.connection().close();
        }
      });
    });
    startProxy(ctx, backend);
    HttpClient client = vertx.createHttpClient();
    HttpClientRequest req = client.get(8080, "localhost", "/", resp -> {
      ctx.assertEquals(200, resp.statusCode());
    });
    Async async = ctx.async();
    req.connectionHandler(conn -> {
      conn.closeHandler(v -> {
        async.complete();
      });
    });
    req.putHeader("Content-Length", "2048");
    req.write(Buffer.buffer(new byte[1024]));
  }
}
