package io.vertx.httpproxy;

import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.httpproxy.impl.ParseUtils;
import org.junit.Test;

import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class CacheExpiresTest extends ProxyTestBase {

  private AtomicInteger hits = new AtomicInteger();
  private HttpClient client;

  @Override
  public void setUp() {
    super.setUp();
    hits.set(0);
    client = vertx.createHttpClient();
  }

  protected void setCacheControl(MultiMap headers, long now, long delaySeconds) {
    Date tomorrow = new Date();
    tomorrow.setTime(now + delaySeconds * 1000);
    headers.set(HttpHeaders.CACHE_CONTROL, "public");
    headers.set(HttpHeaders.EXPIRES, ParseUtils.formatHttpDate(tomorrow));
  }

  @Test
  public void testPublicGet(TestContext ctx) throws Exception {
    testPublic(ctx, HttpMethod.GET);
  }

  @Test
  public void testPublicHead(TestContext ctx) throws Exception {
    testPublic(ctx, HttpMethod.HEAD);
  }

  private void testPublic(TestContext ctx, HttpMethod method) throws Exception {
    Async latch = ctx.async();
    testPublic(ctx, resp1 -> {
      vertx.setTimer(2000, id -> {
        client.request(method, 8080, "localhost", "/", resp2 -> {
          ctx.assertEquals(200, resp2.statusCode());
          resp2.bodyHandler(buff2 -> {
            if (method == HttpMethod.HEAD) {
              ctx.assertEquals("", buff2.toString());
            } else {
              ctx.assertEquals("content", buff2.toString());
            }
            ctx.assertEquals(resp1.getHeader(HttpHeaders.DATE), resp2.getHeader(HttpHeaders.DATE));
            ctx.assertEquals(1, hits.get());
            latch.complete();
          });
        }).end();
      });
    });
  }

  @Test
  public void testPublicExpiration(TestContext ctx) throws Exception {
    Async latch = ctx.async();
    testPublic(ctx, resp1 -> {
      vertx.setTimer(6000, id -> {
        client.getNow(8080, "localhost", "/", resp2 -> {
          ctx.assertEquals(200, resp2.statusCode());
          resp2.bodyHandler(buff2 -> {
            ctx.assertEquals("content", buff2.toString());
            ctx.assertEquals(2, hits.get());
            ctx.assertNotEquals(resp1.getHeader(HttpHeaders.DATE), resp2.getHeader(HttpHeaders.DATE));
            latch.complete();
          });
        });
      });
    });
  }

  @Test
  public void testPublicValidClientMaxAge(TestContext ctx) throws Exception {
    Async latch = ctx.async();
    testPublic(ctx, resp1 -> {
      vertx.setTimer(1000, id -> {
        client.get(8080, "localhost", "/", resp2 -> {
          ctx.assertEquals(200, resp2.statusCode());
          resp2.bodyHandler(buff2 -> {
            ctx.assertEquals("content", buff2.toString());
            ctx.assertEquals(1, hits.get());
            ctx.assertEquals(resp1.getHeader(HttpHeaders.DATE), resp2.getHeader(HttpHeaders.DATE));
            latch.complete();
          });
        }).putHeader(HttpHeaders.CACHE_CONTROL, "max-age=2").end();
      });
    });
  }

  @Test
  public void testPublicInvalidClientMaxAge(TestContext ctx) throws Exception {
    Async latch = ctx.async();
    testPublic(ctx, resp1 -> {
      vertx.setTimer(1000, id -> {
        client.get(8080, "localhost", "/", resp2 -> {
          ctx.assertEquals(200, resp2.statusCode());
          resp2.bodyHandler(buff2 -> {
            ctx.assertEquals("content", buff2.toString());
            ctx.assertEquals(2, hits.get());
            ctx.assertNotEquals(resp1.getHeader(HttpHeaders.DATE), resp2.getHeader(HttpHeaders.DATE));
            latch.complete();
          });
        }).putHeader(HttpHeaders.CACHE_CONTROL, "max-age=1").end();
      });
    });
  }

  private void testPublic(TestContext ctx, Handler<HttpClientResponse> respHandler) throws Exception {
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      hits.incrementAndGet();
      ctx.assertEquals(HttpMethod.GET, req.method());
      Date now = new Date();
      setCacheControl(req.response().headers(), now.getTime(), 5);
      req.response()
          .putHeader(HttpHeaders.LAST_MODIFIED, ParseUtils.formatHttpDate(now))
          .putHeader(HttpHeaders.DATE, ParseUtils.formatHttpDate(now))
          .end("content");
    });
    startProxy(backend);
    client.getNow(8080, "localhost", "/", resp -> {
      ctx.assertEquals(200, resp.statusCode());
      resp.bodyHandler(buff -> {
        ctx.assertEquals("content", buff.toString());
        respHandler.handle(resp);
      });
    });
  }

  @Test
  public void testPublicInvalidClientMaxAgeRevalidation(TestContext ctx) throws Exception {
    testPublicInvalidClientMaxAge(ctx, 5);
  }

/*
  @Test
  public void testPublicInvalidClientMaxAgeOverwrite(TestContext ctx) throws Exception {
    testPublicInvalidClientMaxAge(ctx, 3);
  }
*/

  private void testPublicInvalidClientMaxAge(TestContext ctx, long maxAge) throws Exception {
    Async latch = ctx.async();
    long now = System.currentTimeMillis();
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      ctx.assertEquals(HttpMethod.GET, req.method());
      setCacheControl(req.response().headers(), now, 5);
      switch (hits.getAndIncrement()) {
        case 0:
          ctx.assertEquals(null, req.getHeader(HttpHeaders.ETAG));
          req.response()
              .putHeader(HttpHeaders.LAST_MODIFIED, ParseUtils.formatHttpDate(new Date(now)))
              .putHeader(HttpHeaders.DATE, ParseUtils.formatHttpDate(new Date(now)))
              .putHeader(HttpHeaders.ETAG, "" + now)
              .end("content");
          break;
        case 1:
          ctx.assertEquals("" + now, req.getHeader(HttpHeaders.IF_NONE_MATCH));
          if (System.currentTimeMillis() < now + maxAge * 1000) {
            req.response()
                .setStatusCode(304)
                .putHeader(HttpHeaders.DATE, ParseUtils.formatHttpDate(new Date(System.currentTimeMillis())))
                .putHeader(HttpHeaders.ETAG, "" + now)
                .end();
          } else {
            req.response()
                .putHeader(HttpHeaders.DATE, ParseUtils.formatHttpDate(new Date(System.currentTimeMillis())))
                .putHeader(HttpHeaders.ETAG, "" + now + "2")
                .end("content2");
          }
          break;
        default:
          ctx.fail();
      }
    });
    startProxy(backend);
    client.getNow(8080, "localhost", "/", resp1 -> {
      ctx.assertEquals(200, resp1.statusCode());
      resp1.bodyHandler(buff -> {
        ctx.assertEquals("content", buff.toString());
        vertx.setTimer(3000, id -> {
          client.get(8080, "localhost", "/", resp2 -> {
            ctx.assertEquals(200, resp2.statusCode());
            resp2.bodyHandler(buff2 -> {
              ctx.assertEquals("content", buff2.toString());
              ctx.assertEquals(2, hits.get());
//              ctx.assertNotEquals(resp1.getHeader(HttpHeaders.DATE), resp2.getHeader(HttpHeaders.DATE));
              latch.complete();
            });
          }).putHeader(HttpHeaders.CACHE_CONTROL, "max-age=1").end();
        });
      });
    });
  }

}
