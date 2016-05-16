package io.vertx.ext.reverseproxy.impl;

import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.streams.Pump;
import io.vertx.ext.reverseproxy.Backend;

import java.util.List;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class Router {

  final HttpServer server;
  final List<Backend> clients;

  public Router(HttpServer server, List<Backend> clients) {
    this.server = server;
    this.clients = clients;
  }

  public void handle(HttpServerRequest request) {
    handle(request, 0);
  }

  public void handle(HttpServerRequest request, int index) {
    request.pause();
    HttpServerResponse response = request.response();
    if (index < clients.size()) {
      Backend backend = clients.get(index);
      backend.getClient(request.host(), request.path(), client -> {
        if (client != null) {
          HttpClientRequest proxyRequest = client.request(request.method(), request.absoluteURI());
          proxyRequest.handler(proxyResponse -> {
            response.setStatusCode(proxyResponse.statusCode());
            response.setStatusMessage(proxyResponse.statusMessage());
            response.headers().addAll(proxyResponse.headers());
            Pump responsePump = Pump.pump(proxyResponse, response);
            responsePump.start();
            proxyResponse.endHandler(v -> {
              response.end();
            });
          });
          proxyRequest.headers().setAll(request.headers());
          request.resume();
          request.endHandler(v -> proxyRequest.end());
          Pump requestPump = Pump.pump(request, proxyRequest);
          requestPump.start();
        } else {
          handle(request, index + 1);
        }
      });
    } else {
      request.resume();
      response.setStatusCode(404).end();
    }
  }
}
