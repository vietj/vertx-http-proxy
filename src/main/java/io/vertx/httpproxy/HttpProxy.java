package io.vertx.httpproxy;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.streams.ReadStream;
import io.vertx.httpproxy.impl.HttpProxyImpl;

import java.util.function.Function;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@VertxGen
public interface HttpProxy extends Handler<HttpServerRequest> {

  static HttpProxy reverseProxy(HttpClient client, SocketAddress address) {
    return new HttpProxyImpl(client, address);
  }

  @Fluent
  HttpProxy requestBodyFilter(Function<HttpServerRequest, BodyFilter> filter);

  @Fluent
  HttpProxy responseBodyFilter(Function<HttpClientResponse, BodyFilter> filter);

  void handle(HttpServerRequest request);

}
