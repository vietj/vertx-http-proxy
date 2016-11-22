package io.vertx.httpproxy;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.httpproxy.backend.BackendProvider;
import io.vertx.httpproxy.impl.HttpProxyImpl;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@VertxGen
public interface HttpProxy {

  static HttpProxy createProxy(Vertx vertx) {
    return new HttpProxyImpl(vertx, new HttpProxyOptions());
  }

  static HttpProxy createProxy(Vertx vertx, HttpProxyOptions options) {
    return new HttpProxyImpl(vertx, options);
  }

  @Fluent
  HttpProxy beginRequestHandler(Handler<HttpServerRequest> handler);

  @Fluent
  HttpProxy addBackend(BackendProvider client);

  @Fluent
  HttpProxy listen(Handler<AsyncResult<HttpProxy>> completionHandler);

}
