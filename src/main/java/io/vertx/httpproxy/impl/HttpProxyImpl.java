package io.vertx.httpproxy.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpServer;
import io.vertx.httpproxy.backend.BackendProvider;
import io.vertx.httpproxy.HttpProxy;
import io.vertx.httpproxy.HttpProxyOptions;

import java.util.ArrayList;
import java.util.List;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class HttpProxyImpl implements HttpProxy {

  private final Vertx vertx;
  private final HttpProxyOptions options;
  private Router router;
  private List<BackendProvider> clients = new ArrayList<>();

  public HttpProxyImpl(Vertx vertx, HttpProxyOptions options) {
    this.options = new HttpProxyOptions(options);
    this.vertx = vertx;
  }

  @Override
  public synchronized HttpProxy addBackend(BackendProvider client) {
    clients.add(client);
    return this;
  }

  @Override
  public synchronized HttpProxy listen(Handler<AsyncResult<HttpProxy>> completionHandler) {
    if (router != null) {
      throw new IllegalStateException();
    }
    HttpClient client = vertx.createHttpClient(options.getClientOptions());
    HttpServer server = vertx.createHttpServer(options.getServerOptions());
    router = new Router(client, server, new ArrayList<>(clients));
    server.requestHandler(router::handle);
    server.listen(ar -> {
      if (ar.succeeded()) {
        completionHandler.handle(Future.succeededFuture(this));
      } else {
        completionHandler.handle(Future.failedFuture(ar.cause()));
      }
    });
    return this;
  }
}
