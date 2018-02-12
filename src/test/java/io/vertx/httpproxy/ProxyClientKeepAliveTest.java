package io.vertx.httpproxy;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.streams.WriteStream;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import org.junit.Test;

import java.io.Closeable;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class ProxyClientKeepAliveTest extends ProxyTestBase {

  protected boolean keepAlive = true;
  protected boolean pipelining = false;

  @Override
  public void setUp() {
    super.setUp();
    clientOptions.setKeepAlive(keepAlive).setPipelining(pipelining);
  }

/*
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
*/

  @Test
  public void testGet(TestContext ctx) {
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      ctx.assertEquals("/somepath", req.uri());
      ctx.assertEquals("localhost:8081", req.host());
      req.response().end("Hello World");
    });
    startProxy(backend);
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
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      req.bodyHandler(buff -> {
        req.response().end();
        ctx.assertEquals(Buffer.buffer(body), buff);
        async.complete();
      });
    });
    startProxy(backend);
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
  public void testBackendClosesDuringUpload(TestContext ctx) {
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      AtomicInteger len = new AtomicInteger();
      req.handler(buff -> {
        if (len.addAndGet(buff.length()) == 1024) {
          req.connection().close();
        }
      });
    });
    startProxy(backend);
    HttpClient client = vertx.createHttpClient();
    AtomicBoolean responseReceived = new AtomicBoolean();
    HttpClientRequest req = client.post(8080, "localhost", "/", resp -> {
      ctx.assertEquals(502, resp.statusCode());
      responseReceived.set(true);
    });
    Async async = ctx.async();
    req.connectionHandler(conn -> {
      conn.closeHandler(v -> {
        ctx.assertTrue(responseReceived.get());
        async.complete();
      });
    });
    req.putHeader("Content-Length", "2048");
    req.write(Buffer.buffer(new byte[1024]));
  }

  @Test
  public void testClientClosesDuringUpload(TestContext ctx) {
    Async async = ctx.async();
    Async closeLatch = ctx.async();
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      req.response().closeHandler(v -> {
        async.complete();
      });
      req.handler(buff -> {
        if (!closeLatch.isCompleted()) {
          closeLatch.complete();
        }
      });
    });
    startProxy(backend);
    HttpClient client = vertx.createHttpClient();
    HttpClientRequest req = client.post(8080, "localhost", "/", resp -> ctx.fail());
    req.putHeader("Content-Length", "2048");
    req.write(Buffer.buffer(new byte[1024]));
    closeLatch.awaitSuccess(10000);
    req.connection().close();
  }

  @Test
  public void testClientClosesAfterUpload(TestContext ctx) {
    Async async = ctx.async();
    Async closeLatch = ctx.async();
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      req.endHandler(v -> {
        closeLatch.complete();
        vertx.setTimer(200, id -> {
          req.response().setChunked(true).write("partial response");
        });
      });
      req.response().closeHandler(v -> {
        async.complete();
      });
    });
    startProxy(backend);
    HttpClient client = vertx.createHttpClient();
    HttpClientRequest req = client.post(8080, "localhost", "/", resp -> ctx.fail());
    req.end(Buffer.buffer(new byte[1024]));
    closeLatch.awaitSuccess(10000);
    HttpConnection conn = req.connection();
    conn.close();
  }

  @Test
  public void testBackendCloseResponseWithOnGoingRequest(TestContext ctx) {
    SocketAddress backend = startNetBackend(ctx, 8081, so -> {
      Buffer body = Buffer.buffer();
      so.handler(buff -> {
        body.appendBuffer(buff);
        if (buff.toString().contains("\r\n\r\n")) {
          so.write(
              "HTTP/1.1 200 OK\r\n" +
              "content-length: 0\r\n" +
              "\r\n");
          so.close();
        }
      });
    });
    startProxy(backend);
    HttpClient client = vertx.createHttpClient();
    HttpClientRequest req = client.post(8080, "localhost", "/");
    Async async = ctx.async();
    req.handler(resp -> {
      ctx.assertEquals(200, resp.statusCode());
      async.complete();
    });
    req.putHeader("Content-Length", "2048");
    req.write(Buffer.buffer(new byte[1024]));
  }

  @Test
  public void testBackendCloseResponse(TestContext ctx) {
    testBackendCloseResponse(ctx, false);
  }

  @Test
  public void testBackendCloseChunkedResponse(TestContext ctx) {
    testBackendCloseResponse(ctx, true);
  }

  private void testBackendCloseResponse(TestContext ctx, boolean chunked) {
    CompletableFuture<Void> closeFuture = new CompletableFuture<>();
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      HttpServerResponse resp = req.response();
      if (chunked) {
        resp.setChunked(true);
      } else {
        resp.putHeader("content-length", "10000");
      }
      resp.write("part");
      closeFuture.thenAccept(v -> {
        resp.close();
      });
    });
    startProxy(backend);
    HttpClient client = vertx.createHttpClient();
    Async async = ctx.async();
    client.getNow(8080, "localhost", "/", resp -> {
      resp.handler(buff -> {
        closeFuture.complete(null);
      });
      resp.exceptionHandler(err -> {
        async.complete();
      });
    });
  }

  @Test
  public void testFrontendCloseResponse(TestContext ctx) {
    testFrontendCloseResponse(ctx, false);
  }

  @Test
  public void testFrontendCloseChunkedResponse(TestContext ctx) {
    testBackendCloseResponse(ctx, true);
  }

  private void testFrontendCloseResponse(TestContext ctx, boolean chunked) {
    Async async = ctx.async();
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      HttpServerResponse resp = req.response();
      if (chunked) {
        resp.setChunked(true);
      } else {
        resp.putHeader("content-length", "10000");
      }
      resp.write("part");
      resp.exceptionHandler(err -> {
        async.complete();
      });
    });
    startProxy(backend);
    HttpClient client = vertx.createHttpClient();
    client.getNow(8080, "localhost", "/", resp -> {
      resp.handler(buff -> {
        resp.request().connection().close();
        System.out.println("closing");
      });
    });
  }

  @Test
  public void testBackendRepliesIncorrectHttpVersion(TestContext ctx) {
    Async latch = ctx.async();
    SocketAddress backend = startNetBackend(ctx, 8081, so -> {
      so.write("HTTP/1.2 200 OK\r\n\r\n");
      so.close();
    });
    startProxy(backend);
    HttpClient client = vertx.createHttpClient();
    client.getNow(8080, "localhost", "/", resp -> {
      ctx.assertEquals(502, resp.statusCode());
      resp.endHandler(v -> {
        latch.complete();
      });
    });
  }

  @Test
  public void testSuppressIncorrectWarningHeaders(TestContext ctx) {
    Async latch = ctx.async();
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      req.response()
          .putHeader("date", "Tue, 15 Nov 1994 08:12:30 GMT")
          .putHeader("warning", "199 Miscellaneous warning \"Tue, 15 Nov 1994 08:12:31 GMT\"")
        .end();
    });
    startProxy(backend);
    HttpClient client = vertx.createHttpClient();
    client.getNow(8080, "localhost", "/", resp -> {
      ctx.assertNotNull(resp.getHeader("date"));
      ctx.assertNull(resp.getHeader("warning"));
      latch.complete();
    });
  }

  @Test
  public void testAddMissingHeaderDate(TestContext ctx) {
    Async latch = ctx.async();
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      req.response()
//          .putHeader("date", "Tue, 15 Nov 1994 08:12:30 GMT")
//          .putHeader("warning", "199 Miscellaneous warning \"Tue, 15 Nov 1994 08:12:31 GMT\"")
          .end();
    });
    startProxy(backend);
    HttpClient client = vertx.createHttpClient();
    client.getNow(8080, "localhost", "/", resp -> {
      ctx.assertNotNull(resp.getHeader("date"));
      latch.complete();
    });
  }

  @Test
  public void testAddMissingHeaderDateFromWarning(TestContext ctx) {
    String expected = "Tue, 15 Nov 1994 08:12:31 GMT";
    Async latch = ctx.async();
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      req.response()
          .putHeader("warning", "199 Miscellaneous warning \"" + expected + "\"")
          .end();
    });
    startProxy(backend);
    HttpClient client = vertx.createHttpClient();
    client.getNow(8080, "localhost", "/", resp -> {
      ctx.assertEquals(expected, resp.getHeader("date"));
      latch.complete();
    });
  }

  @Test
  public void testChunkedResponseToHttp1_1Client(TestContext ctx) {
    checkChunkedResponse(ctx, HttpVersion.HTTP_1_1);
  }

  @Test
  public void testChunkedResponseToHttp1_0Client(TestContext ctx) {
    checkChunkedResponse(ctx, HttpVersion.HTTP_1_0);
  }

  private void checkChunkedResponse(TestContext ctx, HttpVersion version) {
    int num = 50;
    Async latch = ctx.async();
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      HttpServerResponse resp = req.response();
      resp.setChunked(true);
      streamChunkedBody(resp, num);
    });
    startProxy(backend);
    HttpClient client = vertx.createHttpClient(new HttpClientOptions().setProtocolVersion(version));
    StringBuilder sb = new StringBuilder();
    for (int i = 0;i < num;i++) {
      sb.append("chunk-").append(i);
    }
    client.getNow(8080, "localhost", "/", resp -> {
      if (version == HttpVersion.HTTP_1_1) {
        ctx.assertEquals("chunked", resp.getHeader("transfer-encoding"));
        ctx.assertEquals(null, resp.getHeader("content-length"));
      } else {
        ctx.assertEquals(null, resp.getHeader("transfer-encoding"));
        ctx.assertEquals("" + sb.length(), resp.getHeader("content-length"));
      }
      resp.handler(buff -> {
        String part = buff.toString();
        if (sb.indexOf(part) == 0) {
          sb.delete(0, part.length());
        } else {
          ctx.fail();
        }
      });
      resp.endHandler(v -> {
        ctx.assertEquals("", sb.toString());
        latch.complete();
      });
    });
  }

  @Test
  public void testChunkedTransferEncodingRequest(TestContext ctx) {
    int num = 50;
    Async latch = ctx.async();
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      StringBuilder sb = new StringBuilder();
      for (int i = 0;i < num;i++) {
        sb.append("chunk-").append(i);
      }
      ctx.assertEquals("chunked", req.getHeader("transfer-encoding"));
      req.handler(buff -> {
        String part = buff.toString();
        if (sb.indexOf(part) == 0) {
          sb.delete(0, part.length());
        } else {
          ctx.fail();
        }
      });
      req.endHandler(v -> {
        ctx.assertEquals("", sb.toString());
        latch.complete();
      });
      req.response().end();
    });
    startProxy(backend);
    HttpClient client = vertx.createHttpClient();
    HttpClientRequest req = client.get(8080, "localhost", "/", resp -> {
    });
    req.setChunked(true);
    streamChunkedBody(req, num);
  }

  @Test
  public void testIllegalClientHttpVersion(TestContext ctx) {
    Async latch = ctx.async();
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      ctx.fail();
    });
    startProxy(backend);
    NetClient client = vertx.createNetClient();
    client.connect(8080, "localhost", ctx.asyncAssertSuccess(so -> {
      Buffer resp = Buffer.buffer();
      so.handler(resp::appendBuffer);
      so.closeHandler(v -> {
        ctx.assertTrue(resp.toString().startsWith("HTTP/1.1 501 Not Implemented\r\n"));
        latch.complete();
      });
      so.write("GET /somepath http/1.1\r\n\r\n");
    }));
  }

  @Test
  public void testHandleLongInitialLength(TestContext ctx) {
    proxyOptions.setMaxInitialLineLength(10000);
    Async latch = ctx.async();
    String uri = "/" + randomAlphaString(5999);
    SocketAddress backend = startHttpBackend(ctx, new HttpServerOptions().setPort(8081).setMaxInitialLineLength(10000), req -> {
      ctx.assertEquals(uri, req.uri());
      req.response().end();
    });
    startProxy(backend);
    HttpClient client = vertx.createHttpClient();
    client.getNow(8080, "localhost", "" + uri, resp -> {
      ctx.assertEquals(200, resp.statusCode());
      latch.complete();
    });
  }

  @Test
  public void testLargeChunkExtValue(TestContext ctx) {
    String s = "" + randomAlphaString(4096);
    Async latch = ctx.async();
    SocketAddress backend = startNetBackend(ctx, 8081, so -> {
      Buffer body = Buffer.buffer();
      so.handler(buff -> {
        body.appendBuffer(buff);
        if (body.toString().endsWith("\r\n\r\n")) {
          so.write("" +
              "HTTP/1.1 200 OK\r\n" +
              "Transfer-Encoding: chunked\r\n" +
              "connection: close\r\n" +
              "\r\n" +
              "A; name=\"" + s + "\"\r\n" +
              "0123456789\r\n" +
              "0\r\n" +
              "\r\n"
          );
        }
      });
    });
    clientOptions.setMaxInitialLineLength(5000);
    startProxy(backend);
    HttpClient client = vertx.createHttpClient(/*new HttpClientOptions().setProtocolVersion(HttpVersion.HTTP_1_0)*/);
    client.getNow(8080, "localhost", "/somepath", resp -> {
      ctx.assertEquals(200, resp.statusCode());
      resp.bodyHandler(body -> {
        ctx.assertEquals("0123456789", body.toString());
        latch.complete();
      });
    });
  }

  @Test
  public void testRequestIllegalTransferEncoding1(TestContext ctx) throws Exception {
    checkBadRequest(ctx,
        "POST /somepath HTTP/1.1\r\n" +
        "transfer-encoding: identity\r\n" +
        "connection: close\r\n" +
        "\r\n",
        "POST /somepath HTTP/1.1\r\n" +
            "transfer-encoding: chunked, identity\r\n" +
            "connection: close\r\n" +
            "\r\n",
        "POST /somepath HTTP/1.1\r\n" +
            "transfer-encoding: identity, chunked\r\n" +
            "connection: close\r\n" +
            "\r\n",
        "POST /somepath HTTP/1.1\r\n" +
            "transfer-encoding: identity\r\n" +
            "transfer-encoding: chunked\r\n" +
            "connection: close\r\n" +
            "\r\n",
        "POST /somepath HTTP/1.1\r\n" +
            "transfer-encoding: chunked\r\n" +
            "transfer-encoding: identity\r\n" +
            "connection: close\r\n" +
            "\r\n",
        "POST /somepath HTTP/1.1\r\n" +
            "transfer-encoding: other, chunked\r\n" +
            "connection: close\r\n" +
            "\r\n",
        "POST /somepath HTTP/1.1\r\n" +
            "transfer-encoding: other\r\n" +
            "transfer-encoding: chunked\r\n" +
            "connection: close\r\n" +
            "\r\n");
  }

  private void checkBadRequest(TestContext ctx, String... requests) throws Exception {
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      ctx.fail();
    });
    startProxy(backend);
    for (String request : requests) {
      Async latch = ctx.async();
      NetClient client = vertx.createNetClient();
      try {
        client.connect(8080, "localhost", ctx.asyncAssertSuccess(so -> {
          Buffer resp = Buffer.buffer();
          so.handler(buff -> {
            resp.appendBuffer(buff);
            if (resp.toString().startsWith("HTTP/1.1 400 Bad Request\r\n")) {
              latch.complete();
            }
          });
          so.write(request);
        }));
        latch.awaitSuccess(10000);
      } finally {
        client.close();
      }
    }
  }

  @Test
  public void testResponseIllegalTransferEncoding(TestContext ctx) throws Exception {
    checkBadResponse(ctx, "" +
        "HTTP/1.1 200 OK\r\n" +
        "Transfer-Encoding: other, chunked\r\n" +
        "connection: close\r\n" +
        "\r\n" +
        "A\r\n" +
        "0123456789\r\n" +
        "0\r\n" +
        "\r\n", "" +
        "HTTP/1.1 200 OK\r\n" +
        "Transfer-Encoding: other\r\n" +
        "Transfer-Encoding: chunked\r\n" +
        "connection: close\r\n" +
        "\r\n" +
        "A\r\n" +
        "0123456789\r\n" +
        "0\r\n" +
        "\r\n");
  }

  @Test
  public void testRawMethod(TestContext ctx) throws Exception {
    Async latch = ctx.async();
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      ctx.assertEquals(HttpMethod.OTHER, req.method());
      ctx.assertEquals("FOO", req.rawMethod());
      req.response().end();
    });
    startProxy(backend);
    HttpClient client = vertx.createHttpClient();
    client.request(HttpMethod.OTHER, 8080, "localhost", "/", resp -> latch.complete()).setRawMethod("FOO").end();
  }

  @Test
  public void testHead(TestContext ctx) throws Exception {
    Async latch = ctx.async();
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      ctx.assertEquals(HttpMethod.HEAD, req.method());
      req.response().putHeader("content-length", "" + "content".length()).end();
    });
    startProxy(backend);
    HttpClient client = vertx.createHttpClient();
    client.request(HttpMethod.HEAD, 8080, "localhost", "/", resp -> {
      ctx.assertEquals("" + ("content".length()), resp.getHeader("content-length"));
      resp.bodyHandler(buff -> {
        ctx.assertEquals("", buff.toString());
        latch.complete();
      });
    }).end();
  }

  private void checkBadResponse(TestContext ctx, String... responses) throws Exception {
    AtomicReference<String> responseBody = new AtomicReference<>();
    SocketAddress backend = startNetBackend(ctx, 8081, so -> {
      Buffer body = Buffer.buffer();
      so.handler(buff -> {
        body.appendBuffer(buff);
        if (body.toString().endsWith("\r\n\r\n")) {
          System.out.println(body.toString());
          so.write(responseBody.get());
        }
      });
    });
    for (String response : responses) {
      responseBody.set(response);
      Async latch = ctx.async();
      HttpClient client = vertx.createHttpClient();
      try (Closeable proxy = startProxy(backend)) {
        client.get(8080, "localhost", "/somepath", resp -> {
          ctx.assertEquals(501, resp.statusCode());
          resp.bodyHandler(body -> {
            ctx.assertEquals("", body.toString());
            latch.complete();
          });
        }).exceptionHandler(ctx::fail).end();
        latch.awaitSuccess(10000);
      } finally {
        client.close();
      }
    }
  }

  private void streamChunkedBody(WriteStream<Buffer> stream, int num) {
    AtomicInteger count = new AtomicInteger(0);
    vertx.setPeriodic(10, id -> {
      int val = count.getAndIncrement();
      if (val < num) {
        stream.write(Buffer.buffer("chunk-" + val));
      } else {
        vertx.cancelTimer(id);
        stream.end();
      }
    });
  }

  private StringBuilder randomAlphaString(int len) {
    Random random = new Random();
    StringBuilder uri = new StringBuilder();
    for (int i = 0;i < len;i++) {
      uri.append((char)('A' + random.nextInt(26)));
    }
    return uri;
  }
}
