package io.vertx.ext.reverseproxy.backend;

import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.ext.reverseproxy.ProxyRequest;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@VertxGen
public interface BackendProvider {

  default void start(Handler<AsyncResult<Void>> doneHandler) {
    doneHandler.handle(Future.succeededFuture());
  }

  default void stop(Handler<AsyncResult<Void>> doneHandler) {
  }

  void handle(ProxyRequest request);

}
