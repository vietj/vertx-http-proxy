package io.vertx.httpproxy;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.net.impl.SocketAddressImpl;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.httpproxy.impl.ParseUtils;
import org.junit.Rule;
import org.junit.Test;

import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class CacheExpires2Test extends ProxyTestBase {

  private AtomicInteger hits = new AtomicInteger();
  private HttpClient client;

  @Rule
  public WireMockRule wireMockRule = new WireMockRule(wireMockConfig().port(8081));

  @Override
  public void setUp() {
    super.setUp();
    hits.set(0);
    client = vertx.createHttpClient(new HttpClientOptions().setKeepAlive(true));
  }

  protected void setCacheControl(MultiMap headers, long now, long delaySeconds) {
    Date tomorrow = new Date();
    tomorrow.setTime(now + delaySeconds * 1000);
    headers.set(HttpHeaders.CACHE_CONTROL, "public");
    headers.set(HttpHeaders.EXPIRES, ParseUtils.formatHttpDate(tomorrow));
  }

  @Test
  public void testPublicInvalidClientMaxAgeRevalidation(TestContext ctx) throws Exception {
    stubFor(get(urlEqualTo("/img.jpg")).inScenario("s").whenScenarioStateIs(STARTED)
        .willReturn(
            aResponse()
                .withStatus(200)
                .withHeader("Cache-Control", "public")
                .withHeader("ETag", "tag0")
                .withHeader("Date", ParseUtils.formatHttpDate(new Date(System.currentTimeMillis())))
                .withHeader("Expires", ParseUtils.formatHttpDate(new Date(System.currentTimeMillis() + 5000)))
                .withBody("content")));
    stubFor(get(urlEqualTo("/img.jpg")).withHeader("If-None-Match", equalTo("tag0")).inScenario("s").whenScenarioStateIs(STARTED)
        .willReturn(
            aResponse()
                .withStatus(304)
                .withHeader("Cache-Control", "public")
                .withHeader("etag", "tag1")
                .withHeader("Date", ParseUtils.formatHttpDate(new Date(System.currentTimeMillis())))
                .withHeader("Expires", ParseUtils.formatHttpDate(new Date(System.currentTimeMillis() + 5000))))
        .willSetStateTo("abc"));
    stubFor(get(urlEqualTo("/img.jpg")).inScenario("s").whenScenarioStateIs("abc")
        .willReturn(
            aResponse()
                .withStatus(200)
                .withHeader("Cache-Control", "public")
                .withHeader("ETag", "tag1")
                .withHeader("Date", ParseUtils.formatHttpDate(new Date(System.currentTimeMillis())))
                .withHeader("Expires", ParseUtils.formatHttpDate(new Date(System.currentTimeMillis() + 5000)))
                .withBody("content2")));
    startProxy(new SocketAddressImpl(8081, "localhost"));
    Async latch = ctx.async();
    client.getNow(8080, "localhost", "/img.jpg", resp1 -> {
      ctx.assertEquals(200, resp1.statusCode());
      resp1.bodyHandler(buff -> {
        ctx.assertEquals("content", buff.toString());
        vertx.setTimer(3000, id -> {
          client.get(8080, "localhost", "/img.jpg", resp2 -> {
            ctx.assertEquals(200, resp2.statusCode());
            resp2.bodyHandler(buff2 -> {
              ctx.assertEquals("content2", buff2.toString());
//              ctx.assertNotEquals(resp1.getHeader(HttpHeaders.DATE), resp2.getHeader(HttpHeaders.DATE));
              latch.complete();
            });
          }).putHeader(HttpHeaders.CACHE_CONTROL, "max-age=1")
              .putHeader(HttpHeaders.CONTENT_ENCODING, "identity")
              .end();
        });
      });
    });
    latch.awaitSuccess(10000);
/*
    ServeEvent event1 = getAllServeEvents().get(1);
    assertNull(event1.getRequest().getHeader("If-None-Match"));
    assertEquals(200, event1.getResponse().getStatus());
    ServeEvent event0 = getAllServeEvents().get(0);
    assertEquals("tag0", event0.getRequest().getHeader("If-None-Match"));
    assertEquals(304, event0.getResponse().getStatus());
*/
  }

  @Test
  public void testUncacheableGetInvalidatesEntryOnOk(TestContext ctx) throws Exception {
    testUncacheableRequestInvalidatesEntryOnOk(ctx, HttpMethod.GET);
  }

  @Test
  public void testUncacheableHeadInvalidatesEntryOnOk(TestContext ctx) throws Exception {
    testUncacheableRequestInvalidatesEntryOnOk(ctx, HttpMethod.HEAD);
  }

  private void testUncacheableRequestInvalidatesEntryOnOk(TestContext ctx, HttpMethod method) throws Exception {
    stubFor(get(urlEqualTo("/img.jpg")).inScenario("s").whenScenarioStateIs(STARTED)
        .willReturn(
            aResponse()
                .withStatus(200)
                .withHeader("Cache-Control", "public")
                .withHeader("ETag", "tag0")
                .withHeader("Date", ParseUtils.formatHttpDate(new Date(System.currentTimeMillis())))
                .withHeader("Expires", ParseUtils.formatHttpDate(new Date(System.currentTimeMillis() + 5000)))
                .withBody("content"))
        .willSetStateTo("abc"));
    stubFor(get(urlEqualTo("/img.jpg")).inScenario("s").whenScenarioStateIs("abc")
        .willReturn(
            aResponse()
                .withStatus(200)
                .withHeader("Cache-Control", "public")
                .withHeader("etag", "tag1")
                .withHeader("Date", ParseUtils.formatHttpDate(new Date(System.currentTimeMillis())))
                .withHeader("Expires", ParseUtils.formatHttpDate(new Date(System.currentTimeMillis() + 5000)))
                .withBody("content2")));
    stubFor(head(urlEqualTo("/img.jpg")).inScenario("s").whenScenarioStateIs("abc")
        .willReturn(
            aResponse()
                .withStatus(200)
                .withHeader("Cache-Control", "public")
                .withHeader("etag", "tag1")
                .withHeader("Date", ParseUtils.formatHttpDate(new Date(System.currentTimeMillis())))
                .withHeader("Expires", ParseUtils.formatHttpDate(new Date(System.currentTimeMillis() + 5000)))));
    startProxy(new SocketAddressImpl(8081, "localhost"));
    Async latch = ctx.async();
    client.getNow(8080, "localhost", "/img.jpg", resp1 -> {
      ctx.assertEquals(200, resp1.statusCode());
      resp1.bodyHandler(buff -> {
        ctx.assertEquals("content", buff.toString());
        vertx.setTimer(3000, id -> {
          client.request(method, 8080, "localhost", "/img.jpg", resp2 -> {
            ctx.assertEquals(200, resp2.statusCode());
            resp2.bodyHandler(buff2 -> {
              ctx.assertEquals(method == HttpMethod.GET ? "content2" : "", buff2.toString());
              client.getNow(8080, "localhost", "/img.jpg", resp3 -> {
                ctx.assertEquals(200, resp3.statusCode());
                resp3.bodyHandler(buff3 -> {
                  ctx.assertEquals("content2", buff3.toString());
                  latch.complete();
                });
              });
            });
          }).putHeader(HttpHeaders.CACHE_CONTROL, "max-age=1").end();
        });
      });
    });
    latch.awaitSuccess(10000);
/*
    ServeEvent event1 = getAllServeEvents().get(1);
    assertNull(event1.getRequest().getHeader("If-None-Match"));
    assertEquals(200, event1.getResponse().getStatus());
    ServeEvent event0 = getAllServeEvents().get(0);
    assertEquals("tag0", event0.getRequest().getHeader("If-None-Match"));
    assertEquals(304, event0.getResponse().getStatus());
*/
  }

  @Test
  public void testUncacheableHeadRevalidatesEntryOnOk(TestContext ctx) throws Exception {
    testUncacheableHeadRevalidatesEntry(ctx, 200);
  }

  @Test
  public void testUncacheableHeadRevalidatesEntryOnNotModified(TestContext ctx) throws Exception {
    testUncacheableHeadRevalidatesEntry(ctx, 304);
  }

  private void testUncacheableHeadRevalidatesEntry(TestContext ctx, int status) throws Exception {
    stubFor(get(urlEqualTo("/img.jpg"))
        .willReturn(
            aResponse()
                .withStatus(200)
                .withHeader("Cache-Control", "public")
                .withHeader("ETag", "tag0")
                .withHeader("Date", ParseUtils.formatHttpDate(new Date(System.currentTimeMillis())))
                .withHeader("Expires", ParseUtils.formatHttpDate(new Date(System.currentTimeMillis() + 5000)))
                .withBody("content")));
    stubFor(head(urlEqualTo("/img.jpg"))
        .willReturn(
            aResponse()
                .withStatus(status)
                .withHeader("Cache-Control", "public")
                .withHeader("ETag", "tag0")
                .withHeader("Date", ParseUtils.formatHttpDate(new Date(System.currentTimeMillis())))
                .withHeader("Expires", ParseUtils.formatHttpDate(new Date(System.currentTimeMillis() + 5000)))));
    startProxy(new SocketAddressImpl(8081, "localhost"));
    Async latch = ctx.async();
    client.getNow(8080, "localhost", "/img.jpg", resp1 -> {
      ctx.assertEquals(200, resp1.statusCode());
      resp1.bodyHandler(buff -> {
        ctx.assertEquals("content", buff.toString());
        vertx.setTimer(3000, id -> {
          client.head(8080, "localhost", "/img.jpg", resp2 -> {
            ctx.assertEquals(200, resp2.statusCode());
            resp2.bodyHandler(buff2 -> {
              ctx.assertEquals("", buff2.toString());
              latch.complete();
            });
          }).putHeader(HttpHeaders.CACHE_CONTROL, "max-age=1").end();
        });
      });
    });
    latch.awaitSuccess(10000);
    ServeEvent event1 = getAllServeEvents().get(1);
    assertNull(event1.getRequest().getHeader("If-None-Match"));
    assertEquals(200, event1.getResponse().getStatus());
    ServeEvent event0 = getAllServeEvents().get(0);
    assertEquals("tag0", event0.getRequest().getHeader("If-None-Match"));
    assertEquals(status, event0.getResponse().getStatus());
  }

  @Test
  public void testHeadDoesNotPopulateCache(TestContext ctx) throws Exception {
    stubFor(get(urlEqualTo("/img.jpg"))
        .willReturn(
            aResponse()
                .withStatus(200)
                .withHeader("Cache-Control", "public")
                .withHeader("ETag", "tag0")
                .withHeader("Date", ParseUtils.formatHttpDate(new Date(System.currentTimeMillis())))
                .withHeader("Expires", ParseUtils.formatHttpDate(new Date(System.currentTimeMillis() + 5000)))
                .withBody("content")));
    stubFor(head(urlEqualTo("/img.jpg"))
        .willReturn(
            aResponse()
                .withStatus(200)
                .withHeader("Cache-Control", "public")
                .withHeader("ETag", "tag0")
                .withHeader("Date", ParseUtils.formatHttpDate(new Date(System.currentTimeMillis())))
                .withHeader("Expires", ParseUtils.formatHttpDate(new Date(System.currentTimeMillis() + 5000)))));
    startProxy(new SocketAddressImpl(8081, "localhost"));
    Async latch = ctx.async();
    client.headNow(8080, "localhost", "/img.jpg", resp1 -> {
      ctx.assertEquals(200, resp1.statusCode());
      resp1.bodyHandler(buff -> {
        ctx.assertEquals("", buff.toString());
        client.getNow(8080, "localhost", "/img.jpg", resp2 -> {
          ctx.assertEquals(200, resp2.statusCode());
          resp2.bodyHandler(buff2 -> {
            ctx.assertEquals("content", buff2.toString());
            latch.complete();
          });
        });
      });
    });
    latch.awaitSuccess(10000);
    ServeEvent event1 = getAllServeEvents().get(1);
    assertNull(event1.getRequest().getHeader("If-None-Match"));
    assertEquals(200, event1.getResponse().getStatus());
    ServeEvent event0 = getAllServeEvents().get(0);
    assertEquals(null, event0.getRequest().getHeader("If-None-Match"));
    assertEquals(200, event0.getResponse().getStatus());
  }
}
