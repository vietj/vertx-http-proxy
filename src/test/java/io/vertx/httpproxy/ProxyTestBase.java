package io.vertx.httpproxy;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.net.impl.SocketAddressImpl;
import io.vertx.ext.unit.Async;
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

  protected void startProxy(TestContext ctx, SocketAddress backend) {
    HttpClient proxyClient = vertx.createHttpClient(new HttpClientOptions(clientOptions));
    HttpServer proxyServer = vertx.createHttpServer(new HttpServerOptions(proxyOptions));
    HttpProxy proxy = HttpProxy.reverseProxy(proxyClient, backend);
    proxyServer.requestHandler(proxy);
    Async async1 = ctx.async();
    proxyServer.listen(ctx.asyncAssertSuccess(p -> async1.complete()));
    async1.awaitSuccess();
  }

  protected void startHttpServer(TestContext ctx, HttpServerOptions options, Handler<HttpServerRequest> handler) {
    HttpServer proxyServer = vertx.createHttpServer(options);
    proxyServer.requestHandler(handler);
    Async async1 = ctx.async();
    proxyServer.listen(ctx.asyncAssertSuccess(p -> async1.complete()));
    async1.awaitSuccess();
  }

  protected SocketAddress startHttpBackend(TestContext ctx, int port, Handler<HttpServerRequest> handler) {
    return startHttpBackend(ctx, new HttpServerOptions().setPort(port).setHost("localhost"), handler);
  }

  protected SocketAddress startHttpBackend(TestContext ctx, HttpServerOptions options, Handler<HttpServerRequest> handler) {
    HttpServer backendServer = vertx.createHttpServer(options);
    backendServer.requestHandler(handler);
    Async async = ctx.async();
    backendServer.listen(ctx.asyncAssertSuccess(s -> async.complete()));
    async.awaitSuccess();
    return new SocketAddressImpl(options.getPort(), "localhost");
  }

  protected SocketAddress startNetBackend(TestContext ctx, int port, Handler<NetSocket> handler) {
    NetServer backendServer = vertx.createNetServer(new HttpServerOptions().setPort(port).setHost("localhost"));
    backendServer.connectHandler(handler);
    Async async = ctx.async();
    backendServer.listen(ctx.asyncAssertSuccess(s -> async.complete()));
    async.awaitSuccess();
    return new SocketAddressImpl(port, "localhost");
  }

}
