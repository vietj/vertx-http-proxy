package io.vertx.httpproxy;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.GenIgnore;
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

  ProxyRequest request();

  int getStatusCode();

  @Fluent
  ProxyResponse setStatusCode(int sc);

  Body getBody();

  @Fluent
  ProxyResponse setBody(Body body);

  String statusMessage();

  boolean publicCacheControl();

  long maxAge();

  String etag();

  /**
   * @return the headers that will be sent to the client, the returned headers can be modified
   */
  MultiMap headers();

  @GenIgnore
  @Fluent
  ProxyResponse putHeader(CharSequence name, CharSequence value);

  @Fluent
  ProxyResponse bodyFilter(Function<ReadStream<Buffer>, ReadStream<Buffer>> filter);

  /**
   * Send the proxy response to the client.
   *
   * @param completionHandler the handler to be called when the response has been sent
   */
  void send(Handler<AsyncResult<Void>> completionHandler);

  /**
   * Release the proxy response.
   *
   * <p> The HTTP client response is resumed, no HTTP server response is sent.
   */
  @Fluent
  ProxyResponse release();

}
