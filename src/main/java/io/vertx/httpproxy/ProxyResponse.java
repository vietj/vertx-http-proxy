package io.vertx.httpproxy;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.streams.ReadStream;

import java.util.function.Function;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@VertxGen
public interface ProxyResponse {

  HttpClientResponse backendResponse();

  @Fluent
  ProxyResponse bodyFilter(Function<ReadStream<Buffer>, ReadStream<Buffer>> filter);

  void send(Handler<AsyncResult<Void>> completionHandler);

}
