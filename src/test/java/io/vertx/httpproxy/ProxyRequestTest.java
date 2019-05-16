package io.vertx.httpproxy;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.streams.Pipe;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.WriteStream;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import org.junit.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class ProxyRequestTest extends ProxyTestBase {

  @Test
  public void testProxyRequestIllegalHttpVersion(TestContext ctx) {
    runHttpTest(ctx, req -> req.response().end("Hello World"), ctx.asyncAssertFailure());
    NetClient client = vertx.createNetClient();
    client.connect(8080, "localhost", ctx.asyncAssertSuccess(so -> {
      so.write("GET /somepath http/1.1\r\n\r\n");
    }));
  }

  @Test
  public void testBackendResponse(TestContext ctx) {
    runHttpTest(ctx, req -> req.response().end("Hello World"), ctx.asyncAssertSuccess());
    Async async = ctx.async();
    HttpClient httpClient = vertx.createHttpClient();
    httpClient.getNow(8080, "localhost", "/somepath", resp -> {
      resp.endHandler(v -> async.complete());
    });
  }

  @Test
  public void testChunkedBackendResponse(TestContext ctx) {
    testChunkedBackendResponse(ctx, HttpVersion.HTTP_1_1);
  }

  @Test
  public void testChunkedBackendResponseToHttp1_0Client(TestContext ctx) {
    testChunkedBackendResponse(ctx, HttpVersion.HTTP_1_0);
  }

  private void testChunkedBackendResponse(TestContext ctx, HttpVersion version) {
    runHttpTest(ctx, req -> req.response().setChunked(true).end("Hello World"), ctx.asyncAssertSuccess());
    Async async = ctx.async();
    HttpClient httpClient = vertx.createHttpClient(new HttpClientOptions().setProtocolVersion(version));
    httpClient.getNow(8080, "localhost", "/somepath", resp -> {
      resp.endHandler(v -> async.complete());
    });
  }

  @Test
  public void testIllegalTransferEncodingBackendResponse(TestContext ctx) {
    runNetTest(ctx, req -> req.write("" +
        "HTTP/1.1 200 OK\r\n" +
        "transfer-encoding: identity\r\n" +
        "connection: close\r\n" +
        "\r\n"), ctx.asyncAssertSuccess());
    Async async = ctx.async();
    HttpClient httpClient = vertx.createHttpClient();
    httpClient.getNow(8080, "localhost", "/somepath", resp -> {
      resp.endHandler(v -> async.complete());
    });
  }

  @Test
  public void testCloseBackendResponse(TestContext ctx) {
    testCloseBackendResponse(ctx, false);
  }

  @Test
  public void testCloseChunkedBackendResponse(TestContext ctx) {
    testCloseBackendResponse(ctx, true);
  }

  private void testCloseBackendResponse(TestContext ctx, boolean chunked) {
    CompletableFuture<Void> cont = new CompletableFuture<>();
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      HttpServerResponse resp = req.response();
      if (chunked) {
        resp.setChunked(true);
      } else {
        resp.putHeader("content-length", "10000");
      }
      resp.write("part");
      cont.thenAccept(v -> {
        resp.close();
      });
    });
    HttpClient backendClient = vertx.createHttpClient(new HttpClientOptions(clientOptions));
    HttpProxy proxy = HttpProxy.reverseProxy(backendClient);
    Async async = ctx.async();
    startHttpServer(ctx, proxyOptions, req -> {
      ProxyRequest proxyReq = proxy.proxy(req, backend);
      proxyReq.send(ctx.asyncAssertSuccess(resp -> resp.send(ctx.asyncAssertFailure(err -> async.complete()))));
    });
    HttpClient httpClient = vertx.createHttpClient();
    httpClient.getNow(8080, "localhost", "/somepath", resp -> {
      resp.handler(buff -> {
        cont.complete(null);
      });
    });
  }

  @Test
  public void testCloseFrontendResponse(TestContext ctx) {
    testCloseFrontendResponse(ctx, false);
  }

  @Test
  public void testCloseChunkedFrontendResponse(TestContext ctx) {
    testCloseFrontendResponse(ctx, true);
  }

  private void testCloseFrontendResponse(TestContext ctx, boolean chunked) {
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      HttpServerResponse resp = req.response();
      if (chunked) {
        resp.setChunked(true);
      } else {
        resp.putHeader("content-length", "10000");
      }
      resp.write("part");
    });
    HttpClient backendClient = vertx.createHttpClient(new HttpClientOptions(clientOptions));
    HttpProxy proxy = HttpProxy.reverseProxy(backendClient);
    Async async = ctx.async();
    startHttpServer(ctx, proxyOptions, req -> {
      ProxyRequest proxyReq = proxy.proxy(req, backend);
      proxyReq.send(ctx.asyncAssertSuccess(resp -> resp.send(ctx.asyncAssertFailure(err -> async.complete()))));
    });
    HttpClient httpClient = vertx.createHttpClient();
    httpClient.getNow(8080, "localhost", "/somepath", resp -> {
      resp.handler(buff -> {
        resp.request().connection().close();
      });
    });
  }

  @Test
  public void testCloseFrontendRequest(TestContext ctx) throws Exception {
    testCloseChunkedFrontendRequest(ctx, false);
  }

  @Test
  public void testCloseChunkedFrontendRequest(TestContext ctx) throws Exception {
    testCloseChunkedFrontendRequest(ctx, true);
  }

  private void testCloseChunkedFrontendRequest(TestContext ctx, boolean chunked) throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      req.handler(buff -> {
        ctx.assertEquals("part", buff.toString());
        latch.countDown();
      });
    });
    HttpClient backendClient = vertx.createHttpClient(new HttpClientOptions(clientOptions));
    HttpProxy proxy = HttpProxy.reverseProxy(backendClient);
    Async async = ctx.async();
    startHttpServer(ctx, proxyOptions, req -> {
      ProxyRequest proxyReq = proxy.proxy(req, backend);
      proxyReq.send(ctx.asyncAssertFailure(err -> {
        async.complete();
      }));
    });
    HttpClient httpClient = vertx.createHttpClient();
    HttpClientRequest req = httpClient.get(8080, "localhost", "/somepath", resp -> {
      ctx.fail();
    });
    if (chunked) {
      req.setChunked(true);
    } else {
      req.putHeader("content-length", "10000");
    }
    req.write("part");
    latch.await(10, TimeUnit.SECONDS);
    req.connection().close();
  }

  @Test
  public void testCloseBackendRequest(TestContext ctx) throws Exception {
    testCloseBackendRequest(ctx, false);
  }

  @Test
  public void testCloseChunkedBackendRequest(TestContext ctx) throws Exception {
    testCloseBackendRequest(ctx, true);
  }

  private void testCloseBackendRequest(TestContext ctx, boolean chunked) throws Exception {
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      req.handler(buff -> {
        ctx.assertEquals("part", buff.toString());
        req.connection().close();
      });
    });
    HttpClient backendClient = vertx.createHttpClient(new HttpClientOptions(clientOptions));
    HttpProxy proxy = HttpProxy.reverseProxy(backendClient);
    Async async = ctx.async();
    startHttpServer(ctx, proxyOptions, req -> {
      ProxyRequest proxyReq = proxy.proxy(req, backend);
      proxyReq.send(ctx.asyncAssertFailure(err -> {
        async.complete();
      }));
    });
    HttpClient httpClient = vertx.createHttpClient();
    Async async2 = ctx.async();
    HttpClientRequest req = httpClient.get(8080, "localhost", "/somepath", resp -> {
      ctx.assertEquals(502, resp.statusCode());
      async2.complete();
    });
    if (chunked) {
      req.setChunked(true);
    } else {
      req.putHeader("content-length", "10000");
    }
    req.write("part");
  }

  @Test
  public void testLatency(TestContext ctx) throws Exception {
    HttpClient client = vertx.createHttpClient();
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      HttpServerResponse resp = req.response();
      req.bodyHandler(resp::end);
    });
    HttpClient backendClient = vertx.createHttpClient(new HttpClientOptions(clientOptions));
    Async async = ctx.async(2);
    HttpProxy proxy = HttpProxy.reverseProxy(backendClient);
    startHttpServer(ctx, proxyOptions, req -> {
      req.pause();
      vertx.setTimer(500, id1 -> {
        ProxyRequest proxyReq = proxy.proxy(req, backend);
        proxyReq.send(ctx.asyncAssertSuccess(resp -> {
          vertx.setTimer(500, id2 -> {
            resp.send(ctx.asyncAssertSuccess(v -> async.countDown()));
          });
        }));
      });
    });
    Buffer sent = Buffer.buffer("Hello world");
    HttpClientRequest req = client.post(8080, "localhost", "/somepath", resp -> {
      resp.bodyHandler(received -> {
        ctx.assertEquals(sent, received);
        async.countDown();
      });
    });
    req.end(sent);
  }

  @Test
  public void testRequestFilter(TestContext ctx) throws Exception {
    Filter filter = new Filter();
    CompletableFuture<Integer> onResume = new CompletableFuture<>();
    HttpClient client = vertx.createHttpClient();
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      req.pause();
      onResume.thenAccept(num -> {
        req.bodyHandler(body -> {
          ctx.assertEquals(filter.expected, body);
          req.response().end();
        });
        req.resume();
      });
    });
    HttpClient backendClient = vertx.createHttpClient(new HttpClientOptions(clientOptions));
    HttpProxy proxy = HttpProxy.reverseProxy(backendClient);
    startHttpServer(ctx, proxyOptions, req -> {
      ProxyRequest proxyReq = proxy.proxy(req, backend);
      proxyReq.bodyFilter(filter::init);
      proxyReq.send(ctx.asyncAssertSuccess(resp -> resp.send(ctx.asyncAssertSuccess())));
    });
    Async async = ctx.async();
    HttpClientRequest req = client.post(8080, "localhost", "/somepath", resp -> {
      async.complete();
    }).setChunked(true);
    int num = 0;
    while (!filter.paused.get()) {
      req.write(CHUNK);
      Thread.sleep(1);
      num++;
    }
    req.end();
    onResume.complete(num);
  }

  @Test
  public void testResponseFilter(TestContext ctx) throws Exception {
    Filter filter = new Filter();
    CompletableFuture<Integer> onResume = new CompletableFuture<>();
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      HttpServerResponse resp = req.response().setChunked(true);
      AtomicInteger num = new AtomicInteger();
      vertx.setPeriodic(1, id -> {
        if (!filter.paused.get()) {
          resp.write(CHUNK);
          num.getAndIncrement();
        } else {
          vertx.cancelTimer(id);
          resp.end();
          onResume.complete(num.get());
        }
      });
    });
    HttpClient backendClient = vertx.createHttpClient(new HttpClientOptions(clientOptions));
    HttpProxy proxy = HttpProxy.reverseProxy(backendClient);
    startHttpServer(ctx, proxyOptions, req -> {
      ProxyRequest proxyReq = proxy.proxy(req, backend);
      proxyReq.send(ctx.asyncAssertSuccess(proxyResp -> {
        proxyResp.bodyFilter(filter::init);
        proxyResp.send(ctx.asyncAssertSuccess());
      }));
    });
    Async async = ctx.async();
    HttpClient client = vertx.createHttpClient();
    client.get(8080, "localhost", "/somepath", resp -> {
      resp.pause();
      onResume.thenAccept(num -> {
        resp.resume();
      });
      resp.endHandler(v -> {
        async.complete();
      });
    }).end();
  }

  @Test
  public void testUpdateRequestHeaders(TestContext ctx) throws Exception {
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      ctx.assertNull(req.getHeader("header"));
      ctx.assertEquals("proxy_header_value", req.getHeader("proxy_header"));
      req.response().putHeader("header", "header_value").end();
    });
    HttpClient backendClient = vertx.createHttpClient(new HttpClientOptions(clientOptions));
    HttpProxy proxy = HttpProxy.reverseProxy(backendClient);
    startHttpServer(ctx, proxyOptions, req -> {
      ProxyRequest proxyReq = proxy.proxy(req, backend);
      MultiMap clientHeaders = proxyReq.headers();
      clientHeaders.add("proxy_header", "proxy_header_value");
      ctx.assertEquals("header_value", clientHeaders.get("header"));
      clientHeaders.remove("header");
      proxyReq.send(ctx.asyncAssertSuccess(proxyResp -> {
        MultiMap targetHeaders = proxyResp.headers();
        targetHeaders.add("proxy_header", "proxy_header_value");
        ctx.assertEquals("header_value", targetHeaders.get("header"));
        targetHeaders.remove("header");
        proxyResp.send(ctx.asyncAssertSuccess());
      }));
    });
    HttpClient client = vertx.createHttpClient();
    Async async = ctx.async();
    client.get(8080, "localhost", "/somepath", resp -> {
      ctx.assertEquals("proxy_header_value", resp.getHeader("proxy_header"));
      ctx.assertNull(resp.getHeader("header"));
      resp.endHandler(v -> {
        async.complete();
      });
    }).putHeader("header", "header_value").end();
  }

  @Test
  public void testCancelResponse(TestContext ctx) throws Exception {
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      req.response().end("the-response");
    });
    HttpClient backendClient = vertx.createHttpClient(new HttpClientOptions(clientOptions));
    HttpProxy proxy = HttpProxy.reverseProxy(backendClient);
    startHttpServer(ctx, proxyOptions, req -> {
      ProxyRequest proxyReq = proxy.proxy(req, backend);
      proxyReq.send(ctx.asyncAssertSuccess(proxyResp -> {
        proxyResp.cancel();
        req.response().end("another-response");
      }));
    });
    HttpClient client = vertx.createHttpClient();
    Async async = ctx.async();
    client.getNow(8080, "localhost", "/somepath", resp -> {
      resp.bodyHandler(body -> {
        ctx.assertEquals("another-response", body.toString());
        async.complete();
      });
    });
  }

  private static Buffer CHUNK;

  static {
    byte[] bytes = new byte[1024];
    for (int i = 0;i < 1024;i++) {
      bytes[i] = (byte)('A' + (i % 26));
    }
    CHUNK = Buffer.buffer(bytes);
  }

  static class Filter implements ReadStream<Buffer> {

    private final AtomicBoolean paused = new AtomicBoolean();
    private ReadStream<Buffer> stream;
    private Buffer expected = Buffer.buffer();
    private Handler<Buffer> dataHandler;
    private Handler<Throwable> exceptionHandler;
    private Handler<Void> endHandler;

    ReadStream<Buffer> init(ReadStream<Buffer> s) {
      stream = s;
      stream.handler(buff -> {
        if (dataHandler != null) {
          byte[] bytes = new byte[buff.length()];
          for (int i = 0;i < bytes.length;i++) {
            bytes[i] = (byte)(('a' - 'A') + buff.getByte(i));
          }
          expected.appendBytes(bytes);
          dataHandler.handle(Buffer.buffer(bytes));
        }
      });
      stream.exceptionHandler(err -> {
        if (exceptionHandler != null) {
          exceptionHandler.handle(err);
        }
      });
      stream.endHandler(v -> {
        if (endHandler != null) {
          endHandler.handle(v);
        }
      });
      return this;
    }

    @Override
    public ReadStream<Buffer> pause() {
      paused.set(true);
      stream.pause();
      return this;
    }

    @Override
    public ReadStream<Buffer> resume() {
      stream.resume();
      return this;
    }

    @Override
    public ReadStream<Buffer> fetch(long amount) {
      stream.fetch(amount);
      return this;
    }

    @Override
    public ReadStream<Buffer> exceptionHandler(Handler<Throwable> handler) {
      exceptionHandler = handler;
      return this;
    }

    @Override
    public ReadStream<Buffer> handler(Handler<Buffer> handler) {
      dataHandler = handler;
      return this;
    }

    @Override
    public ReadStream<Buffer> endHandler(Handler<Void> handler) {
      endHandler = handler;
      return this;
    }

    @Override
    public Pipe<Buffer> pipe() {
      return stream.pipe();
    }

    @Override
    public void pipeTo(WriteStream<Buffer> dst) {
      stream.pipeTo(dst);
    }

    @Override
    public void pipeTo(WriteStream<Buffer> dst, Handler<AsyncResult<Void>> handler) {
      stream.pipeTo(dst, handler);
    }
  }

  private void runHttpTest(TestContext ctx,
                           Handler<HttpServerRequest> backendHandler,
                           Handler<AsyncResult<Void>> expect) {
    Async async = ctx.async();
    SocketAddress backend = startHttpBackend(ctx, 8081, backendHandler);
    HttpClient client = vertx.createHttpClient(new HttpClientOptions(clientOptions));
    HttpProxy proxy = HttpProxy.reverseProxy(client);
    startHttpServer(ctx, proxyOptions, req -> {
      ProxyRequest proxyReq = proxy.proxy(req, backend);
      proxyReq.proxy(ar -> {
        expect.handle(ar);
        async.complete();
      });
    });
  }

  private void runNetTest(TestContext ctx,
                       Handler<NetSocket> backendHandler,
                       Handler<AsyncResult<Void>> expect) {
    Async async = ctx.async();
    SocketAddress backend = startNetBackend(ctx, 8081, backendHandler);
    HttpClient client = vertx.createHttpClient(new HttpClientOptions(clientOptions));
    HttpProxy proxy = HttpProxy.reverseProxy(client);
    startHttpServer(ctx, proxyOptions, req -> {
      ProxyRequest proxyReq = proxy.proxy(req, backend);
      proxyReq.proxy(ar -> {
        expect.handle(ar);
        async.complete();
      });
    });
  }
}
