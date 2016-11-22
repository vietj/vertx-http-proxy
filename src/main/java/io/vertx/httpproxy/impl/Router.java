package io.vertx.httpproxy.impl;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.httpproxy.backend.BackendProvider;

import java.util.List;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class Router {

  final HttpClient client;
  final HttpServer server;
  final Handler<HttpServerRequest> beginRequestHandler;
  final List<BackendProvider> backends;

  public Router(HttpClient client, HttpServer server, Handler<HttpServerRequest> beginRequestHandler, List<BackendProvider> backends) {
    this.server = server;
    this.beginRequestHandler = beginRequestHandler;
    this.backends = backends;
    this.client = client;
  }

  public void handle(HttpServerRequest req) {
    if (beginRequestHandler != null) {
      beginRequestHandler.handle(req);
    }
    RequestContext request = new RequestContext(this, req, backends);
    request.next();
  }

}
