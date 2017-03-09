package io.vertx.httpproxy;

import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.net.SocketAddress;
import io.vertx.httpproxy.impl.HttpProxyImpl;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@VertxGen
public interface HttpProxy extends Handler<HttpServerRequest> {

  static HttpProxy reverseProxy(HttpClient client, SocketAddress address) {
    return new HttpProxyImpl(client, address);
  }

  void handle(HttpServerRequest request);

}
