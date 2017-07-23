package io.vertx.httpproxy.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
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

  private HttpServerRequest frontRequest;
  private HttpServerResponse frontResponse;

  private Function<HttpServerRequest, HttpClientRequest> provider;
  private Function<ReadStream<Buffer>, ReadStream<Buffer>> bodyFilter = Function.identity();

  private MultiMap headers;

  private HttpClientRequest backRequest;
  private Pump requestPump;
  private Pump responsePump;

  public ProxyRequestImpl(HttpClient client, SocketAddress target, HttpServerRequest request) {
    this(req -> {
      HttpMethod method = req.method();
      HttpClientRequest backRequest = client.request(method, target.port(), target.host(), req.uri());
      if (method == HttpMethod.OTHER) {
        backRequest.setRawMethod(req.rawMethod());
      }
      return backRequest;
    }, request);
  }

  public ProxyRequestImpl(Function<HttpServerRequest, HttpClientRequest> provider, HttpServerRequest request) {
    if (request == null) {
      throw new NullPointerException();
    }
    this.provider = provider;
    this.frontRequest = request;
  }

  @Override
  public void proxy(Handler<AsyncResult<Void>> completionHandler) {
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
  public MultiMap headers() {
    if (headers == null) {
      headers = MultiMap.caseInsensitiveMultiMap();
      copyHeaders(headers);
    }
    return headers;
  }

  private void copyHeaders(MultiMap to) {
    // Set headers, don't copy host, as HttpClient will set it
    for (Map.Entry<String, String> header : frontRequest.headers()) {
      if (header.getKey().equalsIgnoreCase("host")) {
        //
      } else {
        to.add(header.getKey(), header.getValue());
      }
    }
  }

  @Override
  public void send(Handler<AsyncResult<ProxyResponse>> completionHandler) {

    // Sanity check 1
    try {
      frontRequest.version();
    } catch (IllegalStateException e) {
      // Sends 501
      frontRequest.resume();
      completionHandler.handle(Future.failedFuture(e));
      return;
    }

    // Create back request
    backRequest = provider.apply(frontRequest);

    // Encoding check
    List<String> te = frontRequest.headers().getAll("transfer-encoding");
    if (te != null) {
      for (String val : te) {
        if (val.equals("chunked")) {
          backRequest.setChunked(true);
        } else {
          frontRequest.resume().response().setStatusCode(400).end();
          // I think we should make a call to completion handler at this point - it does not seem to be tested
          return;
        }
      }
    }

    //
    backRequest.handler(resp -> handle(resp, completionHandler));

    // Set headers
    if (headers != null) {
      // Handler specially the host header
      String host = headers.get("host");
      if (host != null) {
        headers.remove("host");
        backRequest.setHost(host);
      }
      backRequest.headers().setAll(headers);
    } else {
      copyHeaders(backRequest.headers());
    }

    // Apply body filter
    ReadStream<Buffer> bodyStream = bodyFilter.apply(frontRequest);

    bodyStream.endHandler(v -> {
      requestPump = null;
      backRequest.end();
      if (frontResponse == null) {
        frontRequest.response().exceptionHandler(err -> {
          if (stop() != null) {
            backRequest.reset();
            completionHandler.handle(Future.failedFuture(err));
          }
        });
      }
    });
    requestPump = Pump.pump(bodyStream, backRequest);
    backRequest.exceptionHandler(err -> {
      if (resetClient()) {
        completionHandler.handle(Future.failedFuture(err));
      }
    });
    frontRequest.exceptionHandler(err -> {
      if (stop() != null) {
        backRequest.reset();
        completionHandler.handle(Future.failedFuture(err));
      }
    });

    requestPump.start();
    bodyStream.resume();
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

  private boolean resetClient() {
    HttpServerRequest request = stop();
    if (request != null) {
      HttpConnection conn = request.connection();
      HttpServerResponse response = request.response();
      response.setStatusCode(502).end();
      if (conn != null) {
        conn.close();
      }
      return true;
    }
    return false;
  }

  private void handle(HttpClientResponse backResponse, Handler<AsyncResult<ProxyResponse>> completionHandler) {
    if (frontRequest == null) {
      return;
    }
    backResponse.pause();
    frontResponse = frontRequest.response();
    frontResponse.exceptionHandler(null); // Might have been set previously
    ProxyResponseImpl response = new ProxyResponseImpl();
    response.set(backResponse);
    completionHandler.handle(Future.succeededFuture(response));
  }

  private class ProxyResponseImpl implements ProxyResponse {

    private HttpClientResponse backResponse;
    private Function<ReadStream<Buffer>, ReadStream<Buffer>> bodyFilter = Function.identity();
    private long maxAge;
    private String etag;
    private boolean publicCacheControl;
    private boolean sent;

    public ProxyResponseImpl() {
    }

    @Override
    public ProxyResponse bodyFilter(Function<ReadStream<Buffer>, ReadStream<Buffer>> filter) {
      checkSent();
      bodyFilter = filter;
      return this;
    }

    @Override
    public int statusCode() {
      return frontResponse.getStatusCode();
    }

    @Override
    public String statusMessage() {
      return frontResponse.getStatusMessage();
    }

    @Override
    public boolean publicCacheControl() {
      return publicCacheControl;
    }

    @Override
    public long maxAge() {
      return maxAge;
    }

    @Override
    public String etag() {
      return etag;
    }

    @Override
    public MultiMap headers() {
      return frontResponse.headers();
    }

    private void checkSent() {
      if (sent) {
        throw new IllegalStateException();
      }
    }

    public ProxyResponse set(HttpClientResponse backResponse) {
      checkSent();

      frontResponse.headers().clear();
      this.backResponse = backResponse;

      long maxAge = -1;
      boolean publicCacheControl = false;
      String cacheControlHeader = backResponse.getHeader(HttpHeaders.CACHE_CONTROL);
      if (cacheControlHeader != null) {
        CacheControl cacheControl = new CacheControl().parse(cacheControlHeader);
        if (cacheControl.isPublic()) {
          publicCacheControl = true;
          if (cacheControl.maxAge() > 0) {
            maxAge = (long)cacheControl.maxAge() * 1000;
          } else {
            String dateHeader = backResponse.getHeader(HttpHeaders.DATE);
            String expiresHeader = backResponse.getHeader(HttpHeaders.EXPIRES);
            if (dateHeader != null && expiresHeader != null) {
              maxAge = ParseUtils.parseHeaderDate(expiresHeader).getTime() - ParseUtils.parseHeaderDate(dateHeader).getTime();
            }
          }
        }
      }
      this.maxAge = maxAge;
      this.publicCacheControl = publicCacheControl;
      this.etag = backResponse.getHeader(HttpHeaders.ETAG);

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
        date = ParseUtils.parseHeaderDate(dateHeader);
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
        Date dateInstant = ParseUtils.parseHeaderDate(dateHeader);
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
        String name = header.getKey();
        String value = header.getValue();
        if (name.equalsIgnoreCase("date") || name.equalsIgnoreCase("warning") || name.equalsIgnoreCase("transfer-encoding")) {
          // Skip
        } else {
          frontResponse.headers().add(name, value);
        }
      });

      return this;
    }

    @Override
    public void cancel() {
      checkSent();
      sent = true;
      frontResponse.headers().clear();
      frontResponse.endHandler(null);
      backResponse.resume();
    }

    @Override
    public void send(Handler<AsyncResult<Void>> completionHandler) {
      checkSent();
      sent = true;

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

      frontResponse.exceptionHandler(err -> {
        HttpServerRequest request = stop();
        if (request != null) {
          backRequest.reset();
          completionHandler.handle(Future.failedFuture(err));
        }
      });

      backResponse.exceptionHandler(err -> {
        HttpServerRequest request = stop();
        if (request != null) {
          request.response().close(); // Should reset instead ????
          completionHandler.handle(Future.failedFuture(err));
        }
      });

      // Apply body filter
      ReadStream<Buffer> bodyStream = bodyFilter.apply(backResponse);

      if (frontRequest.method() == HttpMethod.HEAD) {
        frontRequest = null;
        frontResponse.end();
        completionHandler.handle(Future.succeededFuture());
      } else {
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
      }

      backResponse.resume();
    }
  }

}
