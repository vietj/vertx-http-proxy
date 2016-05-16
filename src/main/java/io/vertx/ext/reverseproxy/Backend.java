package io.vertx.ext.reverseproxy;

import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.ext.reverseproxy.impl.DockerBackend;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@VertxGen
public interface Backend {

  static Backend create(Vertx vertx) {
    return new DockerBackend(vertx);
  }

  default void start(Handler<AsyncResult<Void>> doneHandler) {
    doneHandler.handle(Future.succeededFuture());
  }

  default void stop(Handler<AsyncResult<Void>> doneHandler) {
  }

  void handle(ProxyRequest request);

}
