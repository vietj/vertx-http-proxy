package io.vertx.httpproxy;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.streams.ReadStream;
import io.vertx.httpproxy.impl.ProxyRequestImpl;

import java.util.function.Function;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@VertxGen
public interface ProxyRequest {

  static ProxyRequest reverseProxy(HttpServerRequest request) {
    request.pause();
    ProxyRequestImpl proxyRequest = new ProxyRequestImpl(request);
    return proxyRequest;
  }

  HttpVersion version();

  Body getBody();

  @Fluent
  ProxyRequest setBody(Body body);

  /**
   * @return the headers that will be sent to the target, the returned headers can be modified
   */
  MultiMap headers();

  @GenIgnore
  @Fluent
  ProxyRequest putHeader(CharSequence name, CharSequence value);

  HttpMethod getMethod();

  @Fluent
  ProxyRequest setMethod(HttpMethod method);

  String absoluteURI();

  @Fluent
  ProxyRequest bodyFilter(Function<ReadStream<Buffer>, ReadStream<Buffer>> filter);

  default void proxy(HttpClientRequest request, Handler<AsyncResult<Void>> completionHandler) {
    send(request, ar -> {
      if (ar.succeeded()) {
        ProxyResponse resp = ar.result();
        resp.send(completionHandler);
      } else {
        completionHandler.handle(ar.mapEmpty());
      }
    });
  }

  /**
   * Release the proxy request.
   *
   * <p> The HTTP server request is resumed, no HTTP server response is sent.
   *
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  ProxyRequest release();

  /**
   * Create and return a default proxy response.
   *
   * @return a default proxy response
   */
  ProxyResponse response();

  void send(HttpClientRequest request, Handler<AsyncResult<ProxyResponse>> completionHandler);

}
