package io.vertx.ext.reverseproxy;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.ext.reverseproxy.backend.BackendProvider;
import io.vertx.ext.reverseproxy.impl.HttpProxyImpl;

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
  HttpProxy addBackend(BackendProvider client);

  @Fluent
  HttpProxy listen(Handler<AsyncResult<HttpProxy>> completionHandler);

}
