package io.vertx.httpproxy.impl;

import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.streams.Pump;
import io.vertx.httpproxy.backend.Backend;
import io.vertx.httpproxy.backend.BackendProvider;
import io.vertx.httpproxy.ProxyRequest;

import java.util.List;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class Router {

  final HttpClient client;
  final HttpServer server;
  final List<BackendProvider> backends;

  public Router(HttpClient client, HttpServer server, List<BackendProvider> backends) {
    this.server = server;
    this.backends = backends;
    this.client = client;
  }

  public void handle(HttpServerRequest req) {
    Request request = new Request(req, backends);
    request.next();
  }

  private class Request implements ProxyRequest {

    private final List<BackendProvider> backends;
    private final HttpServerRequest frontRequest;
    private final HttpServerResponse frontResponse;
    private HttpClientRequest backRequest;
    private HttpClientResponse backResponse;
    private Pump requestPump;
    private Pump responsePump;
    private boolean closed;
    private int index;

    public Request(HttpServerRequest frontRequest, List<BackendProvider> backends) {
      this.frontRequest = frontRequest;
      this.frontResponse = frontRequest.response();
      this.index = 0;
      this.backends = backends;
    }

    private void resetClient() {
      if (!closed) {
        closed = true;
        requestPump.stop();
        if (responsePump != null) {
          responsePump.stop();
        }
        frontRequest.response().setStatusCode(502).end();
        frontRequest.connection().close();
      }
    }

    private void resetBackend() {
      if (!closed) {
        closed = true;
        requestPump.stop();
        if (responsePump != null) {
          responsePump.start();
        }
        backRequest.reset();
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
    public HttpServerRequest clientRequest() {
      return frontRequest;
    }

    @Override
    public void handle(Backend backend) {
      SocketAddress address = backend.next();
      backRequest = client.request(frontRequest.method(), address.port(), address.host(), frontRequest.uri());
      backRequest.handler(this::handle);

      // Set headers, don't copy host, as HttpClient will set it
      frontRequest.headers().forEach(header -> {
        if (!header.getKey().equalsIgnoreCase("host")) {
          backRequest.putHeader(header.getKey(), header.getValue());
        }
      });
      frontRequest.endHandler(v ->
          backRequest.end()
      );
      requestPump = Pump.pump(frontRequest, backRequest);
      backRequest.exceptionHandler(err -> {
        resetClient();
      });
      frontRequest.response().closeHandler(err -> {
        resetBackend();
      });
      frontRequest.resume();
      requestPump.start();
    }

    @Override
    public void next() {
      if (index < backends.size()) {
        BackendProvider backend = backends.get(index++);
        backend.handle(this);
      } else {
        frontRequest.resume();
        frontResponse.setStatusCode(404).end();
      }
    }
  }
}
