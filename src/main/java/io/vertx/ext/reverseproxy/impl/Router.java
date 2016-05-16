package io.vertx.ext.reverseproxy.impl;

import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.streams.Pump;
import io.vertx.ext.reverseproxy.Backend;
import io.vertx.ext.reverseproxy.ProxyRequest;

import java.util.List;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class Router {

  final HttpServer server;
  final List<Backend> backends;

  public Router(HttpServer server, List<Backend> backends) {
    this.server = server;
    this.backends = backends;
  }

  public void handle(HttpServerRequest req) {
    Request request = new Request(req, backends);
    request.next();
  }

  private static class Request implements ProxyRequest {

    private final List<Backend> backends;
    private final HttpServerRequest frontRequest;
    private final HttpServerResponse frontResponse;
    private HttpClientRequest backRequest;
    private HttpClientResponse backResponse;
    private Pump requestPump;
    private Pump responsePump;
    private boolean closed;
    private int index;

    public Request(HttpServerRequest frontRequest, List<Backend> backends) {
      this.frontRequest = frontRequest;
      this.frontResponse = frontRequest.response();
      this.index = 0;
      this.backends = backends;
    }

    private void reset() {
      if (!closed) {
        closed = true;
        requestPump.stop();
        if (responsePump != null) {
          responsePump.stop();
        }
        frontRequest.connection().close();
      }
    }

    void handle(HttpClientResponse response) {
      backResponse = response;
      frontResponse.setStatusCode(response.statusCode());
      frontResponse.setStatusMessage(response.statusMessage());
      frontResponse.headers().addAll(response.headers());
      responsePump = Pump.pump(response, frontResponse);
      responsePump.start();
      response.endHandler(v -> {
        frontResponse.end();
      });
    }

    @Override
    public HttpServerRequest frontRequest() {
      return frontRequest;
    }

    @Override
    public void pass(HttpClient client) {
      backRequest = client.request(frontRequest.method(), frontRequest.uri());
      backRequest.handler(this::handle);

      // Set headers, don't copy host, as HttpClient will set it
      frontRequest.headers().forEach(header -> {
        if (!header.getKey().equalsIgnoreCase("host")) {
          backRequest.putHeader(header.getKey(), header.getValue());
        }
      });
      frontRequest.endHandler(v -> backRequest.end());
      requestPump = Pump.pump(frontRequest, backRequest);
      backRequest.exceptionHandler(err -> {
        reset();
      });
      frontRequest.resume();
      requestPump.start();
    }

    @Override
    public void next() {
      if (index < backends.size()) {
        Backend backend = backends.get(index++);
        backend.handle(this);
      } else {
        frontRequest.resume();
        frontResponse.setStatusCode(404).end();
      }
    }
  }
}
