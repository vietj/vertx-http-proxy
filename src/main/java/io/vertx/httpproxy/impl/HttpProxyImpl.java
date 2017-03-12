package io.vertx.httpproxy.impl;

import io.vertx.core.AsyncResult;
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
  private final SocketAddress target;
  private Function<HttpServerRequest, BodyFilter> requestBodyFilter = req -> s -> s;
  private Function<HttpClientResponse, BodyFilter> responseBodyFilter = resp -> s -> s;

  public HttpProxyImpl(HttpClient client, SocketAddress target) {
    this.client = client;
    this.target = target;
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
  public void handle(HttpServerRequest request) {
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
  }
}
