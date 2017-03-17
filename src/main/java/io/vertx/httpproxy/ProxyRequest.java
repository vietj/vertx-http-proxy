package io.vertx.httpproxy;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.streams.ReadStream;
import io.vertx.httpproxy.impl.ProxyRequestImpl;

import java.util.function.Function;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@VertxGen
public interface ProxyRequest {

  /**
   * @return the headers that will be sent to the target, the returned headers can be modified
   */
  MultiMap headers();

  @Fluent
  ProxyRequest bodyFilter(Function<ReadStream<Buffer>, ReadStream<Buffer>> filter);

  void proxy(SocketAddress target, Handler<AsyncResult<Void>> completionHandler);

  void send(SocketAddress target, Handler<AsyncResult<ProxyResponse>> completionHandler);

  /**
   * Set a function that returns a {@link HttpClientRequest} to send for a given {@link HttpServerRequest}.
   *
   * @param provider the function
   * @return this client request instance
   */
  @Fluent
  ProxyRequest requestProvider(Function<HttpServerRequest, HttpClientRequest> provider);

}
