package io.vertx.httpproxy;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.net.SocketAddress;
import io.vertx.httpproxy.impl.ProxyRequestImpl;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@VertxGen
public interface ProxyRequest {

  static ProxyRequest create(HttpClient client) {
    return new ProxyRequestImpl(client);
  }

  @Fluent
  ProxyRequest request(HttpServerRequest request);

  @Fluent
  ProxyRequest target(SocketAddress target);

  void handle();

  void handle(Handler<AsyncResult<Void>> completionHandler);


}
