package io.vertx.ext.reverseproxy;

import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpServerRequest;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@VertxGen
public interface ProxyRequest {

  /**
   * @return the front http request
   */
  HttpServerRequest frontRequest();

  /**
   * Pass the proxy request to the client.
   *
   * @param client the client to use
   */
  void pass(HttpClient client);

  void next();

}
