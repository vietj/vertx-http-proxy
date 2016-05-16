package io.vertx.ext.reverseproxy;

import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.reverseproxy.backend.Backend;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@VertxGen
public interface ProxyRequest {

  /**
   * @return the front http request
   */
  HttpServerRequest frontRequest();

  void handle(Backend backend);

  void next();

}
