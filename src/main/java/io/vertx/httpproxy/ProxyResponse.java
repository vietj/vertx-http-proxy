package io.vertx.httpproxy;

import io.vertx.codegen.annotations.CacheReturn;
import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.streams.ReadStream;

import java.util.function.Function;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@VertxGen
public interface ProxyResponse {

  int statusCode();

  String statusMessage();

  boolean publicCacheControl();

  long maxAge();

  String etag();

  /**
   * @return the headers that will be sent to the client, the returned headers can be modified
   */
  MultiMap headers();

  @Fluent
  ProxyResponse bodyFilter(Function<ReadStream<Buffer>, ReadStream<Buffer>> filter);

  /**
   * Set the proxy response to use the {@code response}, this will update the values returned by {@link #statusCode()},
   * {@link #statusMessage()}, {@link #headers()}, {@link #publicCacheControl()}, {@link #maxAge()}.
   *
   * @param response the response to use
   */
  @Fluent
  ProxyResponse set(HttpClientResponse response);

  /**
   * Send the proxy response to the client.
   *
   * @param completionHandler the handler to be called when the response has been sent
   */
  void send(Handler<AsyncResult<Void>> completionHandler);

  /**
   * Cancels the proxy request, this will release the resources and clear the headers of the
   * wrapped {@link io.vertx.core.http.HttpServerResponse}.
   */
  void cancel();

}
