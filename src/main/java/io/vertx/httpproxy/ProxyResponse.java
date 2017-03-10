package io.vertx.httpproxy;

import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpClientResponse;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@VertxGen
public interface ProxyResponse {

  HttpClientResponse backendResponse();

  void send(Handler<AsyncResult<Void>> completionHandler);

}
