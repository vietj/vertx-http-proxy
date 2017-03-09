package io.vertx.httpproxy.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.net.SocketAddress;
import io.vertx.httpproxy.HttpProxy;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class HttpProxyImpl implements HttpProxy {

  private static final Handler<AsyncResult<Void>> NOOP = ar -> {};
  private final HttpClient client;
  private final SocketAddress target;

  public HttpProxyImpl(HttpClient client, SocketAddress target) {
    this.client = client;
    this.target = target;
  }

  @Override
  public void handle(HttpServerRequest request) {
    ProxyRequestImpl proxyRequest = new ProxyRequestImpl(client);
    proxyRequest.request(request);
    proxyRequest.target(target);
    proxyRequest.handle(NOOP);
  }
}
