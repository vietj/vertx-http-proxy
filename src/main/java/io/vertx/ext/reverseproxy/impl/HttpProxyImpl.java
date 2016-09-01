package io.vertx.ext.reverseproxy.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.reverseproxy.backend.BackendProvider;
import io.vertx.ext.reverseproxy.config.HttpProxyOptions;
import io.vertx.ext.reverseproxy.HttpProxy;

import java.util.ArrayList;
import java.util.List;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class HttpProxyImpl implements HttpProxy {

  private final Vertx vertx;
  private final HttpProxyOptions options;
  private Router router;
  private List<BackendProvider> backends = new ArrayList<>();

  public HttpProxyImpl(Vertx vertx, HttpProxyOptions options) {
    this.options = new HttpProxyOptions(options);
    this.vertx = vertx;
  }

  @Override
  public synchronized HttpProxy addBackend(BackendProvider backend) {
    backends.add(backend);
    return this;
  }

  @Override
  public synchronized HttpProxy listen(Handler<AsyncResult<HttpProxy>> completionHandler) {
    if (router != null) {
      throw new IllegalStateException();
    }
    HttpClient client = vertx.createHttpClient(options.getClientOptions());
    HttpServer server = vertx.createHttpServer(options.getServerOptions());
    router = new Router(client, server, backends);
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
