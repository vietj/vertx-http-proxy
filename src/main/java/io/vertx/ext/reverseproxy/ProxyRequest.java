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
   * @return the client http request
   */
  HttpServerRequest clientRequest();

  void handle(Backend backend);

  void next();

}
