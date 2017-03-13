package io.vertx.httpproxy.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.net.SocketAddress;
import io.vertx.httpproxy.BodyFilter;
import io.vertx.httpproxy.HttpProxy;
import io.vertx.httpproxy.ProxyResponse;

import java.util.function.Function;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class HttpProxyImpl implements HttpProxy {

  private final HttpClient client;
  private Function<HttpServerRequest, BodyFilter> requestBodyFilter = req -> s -> s;
  private Function<HttpClientResponse, BodyFilter> responseBodyFilter = resp -> s -> s;
  private Function<HttpServerRequest, Future<SocketAddress>> targetSelector = req -> Future.failedFuture("No target available");

  public HttpProxyImpl(HttpClient client) {
    this.client = client;
  }

  @Override
  public HttpProxy requestBodyFilter(Function<HttpServerRequest, BodyFilter> filter) {
    requestBodyFilter = filter;
    return this;
  }

  @Override
  public HttpProxy responseBodyFilter(Function<HttpClientResponse, BodyFilter> filter) {
    responseBodyFilter = filter;
    return this;
  }

  @Override
  public HttpProxy target(SocketAddress target) {
    targetSelector = req -> Future.succeededFuture(target);
    return this;
  }

  @Override
  public HttpProxy targetSelector(Function<HttpServerRequest, Future<SocketAddress>> selector) {
    targetSelector = selector;
    return this;
  }

  @Override
  public void handle(HttpServerRequest request) {
    request.pause();
    Future<SocketAddress> fut = targetSelector.apply(request);
    fut.setHandler(ar -> {
      if (ar.succeeded()) {
        SocketAddress target = ar.result();
        ProxyRequestImpl proxyReq = new ProxyRequestImpl(client);
        proxyReq.bodyFilter(requestBodyFilter.apply(request));
        proxyReq.request(request);
        proxyReq.target(target);
        proxyReq.send(ar1 -> {
          if (ar1.succeeded()) {
            ProxyResponse proxyResp = ar1.result();
            proxyResp.bodyFilter(responseBodyFilter.apply(proxyResp.backendResponse()));
            proxyResp.send(ar2 -> {
              // Done
            });
          }
        });
      } else {
        request.resume();
        request.response().setStatusCode(404).end();
      }
    });
  }
}
