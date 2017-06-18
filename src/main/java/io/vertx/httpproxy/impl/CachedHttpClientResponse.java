package io.vertx.httpproxy.impl;

import io.vertx.core.http.HttpClientResponse;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface CachedHttpClientResponse extends HttpClientResponse {

  void send();

}
