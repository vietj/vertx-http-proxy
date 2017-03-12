package io.vertx.httpproxy.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.streams.Pump;
import io.vertx.core.streams.ReadStream;
import io.vertx.httpproxy.ProxyRequest;
import io.vertx.httpproxy.ProxyResponse;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class ProxyRequestImpl implements ProxyRequest {

  private SocketAddress target;

  private Function<HttpServerRequest, HttpClientRequest> backendRequestProvider;
  private Function<ReadStream<Buffer>, ReadStream<Buffer>> bodyFilter = Function.identity();

  private HttpServerRequest frontRequest;
  private HttpClientRequest backRequest;
  private Pump requestPump;
  private Pump responsePump;

  public ProxyRequestImpl(HttpClient client) {
    backendRequestProvider(req -> client.request(frontRequest.method(), target.port(), target.host(), frontRequest.uri()));
  }

  @Override
  public ProxyRequest backendRequestProvider(Function<HttpServerRequest, HttpClientRequest> provider) {
    backendRequestProvider = provider;
    return this;
  }

  @Override
  public ProxyRequest request(HttpServerRequest request) {
    frontRequest = request;
    return this;
  }

  @Override
  public ProxyRequest target(SocketAddress address) {
    target = address;
    return this;
  }

  @Override
  public void handle(Handler<AsyncResult<Void>> completionHandler) {
    send(ar -> {
      if (ar.succeeded()) {
        ProxyResponse resp = ar.result();
        resp.send(completionHandler);
      } else {
        completionHandler.handle(ar.mapEmpty());
      }
    });
  }

  @Override
  public ProxyRequest bodyFilter(Function<ReadStream<Buffer>, ReadStream<Buffer>> filter) {
    bodyFilter = filter;
    return this;
  }

  @Override
  public void send(Handler<AsyncResult<ProxyResponse>> completionHandler) {

    // Sanity check
    try {
      frontRequest.version();
    } catch (IllegalStateException e) {
      // Sends 501
      frontRequest.resume();
      completionHandler.handle(Future.failedFuture(e));
      return;
    }

    //
    backRequest = backendRequestProvider.apply(frontRequest);
    backRequest.handler(resp -> handle(resp, completionHandler));

    // Set headers, don't copy host, as HttpClient will set it
    for (Map.Entry<String, String> header : frontRequest.headers()) {
      if (header.getKey().equalsIgnoreCase("host")) {
        //
      } else if (header.getKey().equalsIgnoreCase("transfer-encoding")) {
        if (header.getValue().equals("chunked")) {
          backRequest.setChunked(true);
        } else {
          frontRequest.resume().response().setStatusCode(400).end();
          return;
        }
      } else {
        backRequest.putHeader(header.getKey(), header.getValue());
      }
    }

    // Apply body filter
    ReadStream<Buffer> bodyStream = bodyFilter.apply(frontRequest);

    bodyStream.endHandler(v -> {
      requestPump = null;
      backRequest.end();
    });
    requestPump = Pump.pump(bodyStream, backRequest);
    backRequest.exceptionHandler(err -> {
      resetClient();
      completionHandler.handle(Future.failedFuture(err));
    });
    this.frontRequest.response().endHandler(v -> {
      if (stop() != null) {
        backRequest.reset();
        completionHandler.handle(Future.failedFuture("no-msg"));
      }
    });
    bodyStream.resume();
    requestPump.start();
  }

  /**
   * Stop the proxy request
   *
   * @return the front request if stopped / {@code null} means nothing happened
   */
  private HttpServerRequest stop() {
    HttpServerRequest request = frontRequest;
    if (request != null) {
      // Abrupt close
      frontRequest = null;
      if (requestPump != null) {
        requestPump.stop();
        requestPump = null;
      }
      if (responsePump != null) {
        responsePump.stop();
        responsePump = null;
      }
      return request;
    }
    return null;
  }

  private void resetClient() {
    HttpServerRequest request = stop();
    if (request != null) {
      HttpConnection conn = request.connection();
      HttpServerResponse response = request.response();
      response.setStatusCode(502).end();
      if (conn != null) {
        conn.close();
      }
    }
  }

  private void handle(HttpClientResponse backResponse, Handler<AsyncResult<ProxyResponse>> completionHandler) {
    if (frontRequest == null) {
      return;
    }
    backResponse.pause();
    HttpServerResponse frontResponse = frontRequest.response();
    ProxyResponseImpl response = new ProxyResponseImpl(backResponse, frontResponse);
    response.prepare();
    completionHandler.handle(Future.succeededFuture(response));
  }

  private class ProxyResponseImpl implements ProxyResponse {

    private final HttpClientResponse backResponse;
    private final HttpServerResponse frontResponse;
    private Function<ReadStream<Buffer>, ReadStream<Buffer>> bodyFilter = Function.identity();

    public ProxyResponseImpl(HttpClientResponse backResponse, HttpServerResponse frontResponse) {
      this.backResponse = backResponse;
      this.frontResponse = frontResponse;
    }

    @Override
    public ProxyResponse bodyFilter(Function<ReadStream<Buffer>, ReadStream<Buffer>> filter) {
      bodyFilter = filter;
      return this;
    }

    @Override
    public HttpClientResponse backendResponse() {
      return backResponse;
    }

    void prepare() {

      frontResponse.setStatusCode(backResponse.statusCode());
      frontResponse.setStatusMessage(backResponse.statusMessage());

      // Date header
      String dateHeader = backResponse.headers().get("date");
      Date date = null;
      if (dateHeader == null) {
        List<String> warningHeaders = backResponse.headers().getAll("warning");
        if (warningHeaders.size() > 0) {
          for (String warningHeader : warningHeaders) {
            date = ParseUtils.parseWarningHeaderDate(warningHeader);
            if (date != null) {
              break;
            }
          }
        }
      } else {
        date = ParseUtils.parseWarningHeaderDate(dateHeader);
      }
      if (date == null) {
        date = new Date();
      }
      try {
        frontResponse.putHeader("date", ParseUtils.formatHttpDate(date));
      } catch (Exception e) {
        e.printStackTrace();
      }

      // Suppress incorrect warning header
      List<String> warningHeaders = backResponse.headers().getAll("warning");
      if (warningHeaders.size() > 0) {
        warningHeaders = new ArrayList<>(warningHeaders);
        Date dateInstant = ParseUtils.parseDateHeaderDate(dateHeader);
        Iterator<String> i = warningHeaders.iterator();
        while (i.hasNext()) {
          String warningHeader = i.next();
          Date warningInstant = ParseUtils.parseWarningHeaderDate(warningHeader);
          if (warningInstant != null && dateInstant != null && !warningInstant.equals(dateInstant)) {
            i.remove();
          }
        }
      }
      frontResponse.putHeader("warning", warningHeaders);

      // Handle other headers
      backResponse.headers().forEach(header -> {
        if (header.getKey().equalsIgnoreCase("date") || header.getKey().equalsIgnoreCase("warning") || header.getKey().equalsIgnoreCase("transfer-encoding")) {
          // Skip
        } else {
          frontResponse.headers().add(header.getKey(), header.getValue());
        }
      });
    }

    @Override
    public void send(Handler<AsyncResult<Void>> completionHandler) {

      // Determine chunked
      boolean chunked = false;
      for (String value : backResponse.headers().getAll("transfer-encoding")) {
        if (value.equals("chunked")) {
          chunked = true;
        } else {
          frontRequest = null;
          frontResponse.setStatusCode(501).end();
          completionHandler.handle(Future.succeededFuture());
          return;
        }
      }

      backResponse.exceptionHandler(err -> {
        HttpServerRequest request = stop();
        if (request != null) {
          request.response().close();
          completionHandler.handle(Future.failedFuture(err));
        }
      });

      // Apply body filter
      ReadStream<Buffer> bodyStream = bodyFilter.apply(backResponse);

      if (chunked && frontRequest.version() == HttpVersion.HTTP_1_1) {
        frontResponse.setChunked(true);
        responsePump = Pump.pump(bodyStream, frontResponse);
        responsePump.start();
        bodyStream.endHandler(v -> {
          frontRequest = null;
          frontResponse.end();
          completionHandler.handle(Future.succeededFuture());
        });
      } else {
        String contentLength = backResponse.getHeader("content-length");
        if (contentLength != null) {
          responsePump = Pump.pump(bodyStream, frontResponse);
          responsePump.start();
          bodyStream.endHandler(v -> {
            frontRequest = null;
            frontResponse.end();
            completionHandler.handle(Future.succeededFuture());
          });
        } else {
          Buffer body = Buffer.buffer();
          bodyStream.handler(body::appendBuffer);
          bodyStream.endHandler(v -> {
            frontRequest = null;
            frontResponse.end(body);
            completionHandler.handle(Future.succeededFuture());
          });
        }
      }

      backResponse.resume();
    }
  }

}
