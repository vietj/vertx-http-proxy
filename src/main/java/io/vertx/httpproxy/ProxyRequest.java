package io.vertx.httpproxy;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.net.SocketAddress;
import io.vertx.httpproxy.impl.ProxyRequestImpl;

import java.util.function.Function;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@VertxGen
public interface ProxyRequest {

  static ProxyRequest reverse(HttpClient client) {
    return new ProxyRequestImpl(client);
  }

  @Fluent
  ProxyRequest request(HttpServerRequest request);

  @Fluent
  ProxyRequest target(SocketAddress target);

  void handle(Handler<AsyncResult<Void>> completionHandler);

  void send(Handler<AsyncResult<ProxyResponse>> completionHandler);

  /**
   * Set a function that returns a {@link HttpClientRequest} to send for a given {@link HttpServerRequest}.
   *
   * @param provider the function
   * @return this proxy request instance
   */
  @Fluent
  ProxyRequest backendRequestProvider(Function<HttpServerRequest, HttpClientRequest> provider);

}
