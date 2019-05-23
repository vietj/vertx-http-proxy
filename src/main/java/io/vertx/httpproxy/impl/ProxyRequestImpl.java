package io.vertx.httpproxy.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.streams.Pipe;
import io.vertx.core.streams.ReadStream;
import io.vertx.httpproxy.Body;
import io.vertx.httpproxy.ProxyRequest;
import io.vertx.httpproxy.ProxyResponse;

import java.util.function.Function;

public class ProxyRequestImpl implements ProxyRequest {

  private HttpMethod method;
  private HttpVersion version;
  private String uri;
  private String absoluteURI;
  private Body body;
  private MultiMap headers;
  HttpClientRequest backRequest;
  private HttpServerResponse frontResponse;

  public ProxyRequestImpl(HttpServerRequest frontRequest) {

    // Determine content length
    long contentLength = -1L;
    String contentLengthHeader = frontRequest.getHeader(HttpHeaders.CONTENT_LENGTH);
    if (contentLengthHeader != null) {
      try {
        contentLength = Long.parseLong(contentLengthHeader);
      } catch (NumberFormatException e) {
        // Ignore ???
      }
    }

    this.method = frontRequest.method();
    this.version = frontRequest.version();
    this.body = Body.body(frontRequest, contentLength);
    this.uri = frontRequest.uri();
    this.headers = MultiMap.caseInsensitiveMultiMap().addAll(frontRequest.headers());
    this.absoluteURI = frontRequest.absoluteURI();
    this.frontResponse = frontRequest.response();
  }

  @Override
  public HttpVersion version() {
    return version;
  }

  @Override
  public Body getBody() {
    return body;
  }

  @Override
  public ProxyRequestImpl setBody(Body body) {
    this.body = body;
    return this;
  }

  @Override
  public String absoluteURI() {
    return absoluteURI;
  }

  @Override
  public HttpMethod getMethod() {
    return method;
  }

  @Override
  public ProxyRequest setMethod(HttpMethod method) {
    this.method = method;
    return this;
  }

  @Override
  public ProxyRequest release() {
    body.stream().resume();
    headers.clear();
    body = null;
    return this;
  }

  @Override
  public ProxyResponse response() {
    return new ProxyResponseImpl(this, frontResponse);
  }

  void sendRequest(Handler<AsyncResult<ProxyResponse>> responseHandler) {

    backRequest.<ProxyResponse>map(r -> {
      r.pause(); // Pause it
      return new ProxyResponseImpl(this, frontResponse, r);
    }).onComplete(responseHandler);


    backRequest.setMethod(method);
    backRequest.setURI(uri);

    // Add all end-to-end headers
    headers.forEach(header -> {
      String name = header.getKey();
      String value = header.getValue();
      if (name.equalsIgnoreCase("host")) {
        // Skip
      } else {
        backRequest.headers().add(name, value);
      }
    });

    long len = body.length();
    if (len >= 0) {
      backRequest.putHeader(HttpHeaders.CONTENT_LENGTH, Long.toString(len));
    } else {
      backRequest.setChunked(true);
    }

    Pipe<Buffer> pipe = body.stream().pipe();
    pipe.endOnComplete(true);
    pipe.endOnFailure(false);
    pipe.to(backRequest, ar -> {
      if (ar.failed()) {
        backRequest.reset();
      }
    });
  }

  @Override
  public ProxyRequestImpl putHeader(CharSequence name, CharSequence value) {
    headers.set(name, value);
    return this;
  }

  @Override
  public MultiMap headers() {
    return headers;
  }

  @Override
  public ProxyRequest bodyFilter(Function<ReadStream<Buffer>, ReadStream<Buffer>> filter) {
    return this;
  }

  @Override
  public void send(HttpClientRequest request, Handler<AsyncResult<ProxyResponse>> completionHandler) {
    backRequest = request;
    sendRequest(completionHandler);
  }
}
