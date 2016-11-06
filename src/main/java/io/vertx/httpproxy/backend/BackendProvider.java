package io.vertx.httpproxy.backend;

import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.httpproxy.ProxyRequest;
import io.vertx.httpproxy.backend.docker.DockerProvider;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@VertxGen
public interface BackendProvider {

  static BackendProvider create(Vertx vertx) {
    return new DockerProvider(vertx);
  }

  default void start(Handler<AsyncResult<Void>> doneHandler) {
    doneHandler.handle(Future.succeededFuture());
  }

  default void stop(Handler<AsyncResult<Void>> doneHandler) {
  }

  void handle(ProxyRequest request);

}
