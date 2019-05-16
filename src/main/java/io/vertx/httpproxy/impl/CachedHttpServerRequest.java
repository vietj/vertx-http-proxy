package io.vertx.httpproxy.impl;

import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.SocketAddress;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.security.cert.X509Certificate;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class CachedHttpServerRequest implements HttpServerRequest {

  private final HttpServerRequest request;
  private Handler<Void> endHandler;
  private boolean paused = true;

  public CachedHttpServerRequest(HttpServerRequest request) {
    this.request = request;
  }

  @Override
  public HttpServerRequest exceptionHandler(Handler<Throwable> handler) {
    return this;
  }

  @Override
  public HttpServerRequest handler(Handler<Buffer> handler) {
    return this;
  }

  @Override
  public HttpServerRequest pause() {
    return this;
  }

  @Override
  public HttpServerRequest resume() {
    if (paused) {
      paused = false;
      if (endHandler != null) {
        endHandler.handle(null);
      }
    }
    return this;
  }

  @Override
  public HttpServerRequest fetch(long amount) {
    throw new UnsupportedOperationException();
  }

  @Override
  public HttpServerRequest endHandler(Handler<Void> handler) {
    endHandler = handler;
    return this;
  }

  @Override
  public HttpVersion version() {
    return request.version();
  }

  @Override
  public HttpMethod method() {
    return request.method();
  }

  @Override
  public String rawMethod() {
    return request.rawMethod();
  }

  @Override
  public boolean isSSL() {
    return request.isSSL();
  }

  @Override
  public SSLSession sslSession() {
    return request.sslSession();
  }

  @Override
  public String scheme() {
    return request.scheme();
  }

  @Override
  public String uri() {
    return request.uri();
  }

  @Override
  public String path() {
    return request.path();
  }

  @Override
  public String query() {
    return request.query();
  }

  @Override
  public String host() {
    return request.host();
  }

  @Override
  public long bytesRead() {
    throw new UnsupportedOperationException();
  }

  @Override
  public HttpServerResponse response() {
    return request.response();
  }

  @Override
  public MultiMap headers() {
    return request.headers();
  }

  @Override
  public String getHeader(String headerName) {
    return request.getHeader(headerName);
  }

  @Override
  public String getHeader(CharSequence headerName) {
    return request.getHeader(headerName);
  }

  @Override
  public MultiMap params() {
    return request.params();
  }

  @Override
  public String getParam(String paramName) {
    return request.getParam(paramName);
  }

  @Override
  public SocketAddress remoteAddress() {
    return request.remoteAddress();
  }

  @Override
  public SocketAddress localAddress() {
    return request.localAddress();
  }

  @Override
  public X509Certificate[] peerCertificateChain() throws SSLPeerUnverifiedException {
    return request.peerCertificateChain();
  }

  @Override
  public String absoluteURI() {
    return request.absoluteURI();
  }

  @Override
  public NetSocket netSocket() {
    throw new UnsupportedOperationException();
  }

  @Override
  public HttpServerRequest setExpectMultipart(boolean expect) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isExpectMultipart() {
    throw new UnsupportedOperationException();
  }

  @Override
  public HttpServerRequest uploadHandler(Handler<HttpServerFileUpload> uploadHandler) {
    throw new UnsupportedOperationException();
  }

  @Override
  public MultiMap formAttributes() {
    return request.formAttributes();
  }

  @Override
  public String getFormAttribute(String attributeName) {
    return request.getFormAttribute(attributeName);
  }

  @Override
  public ServerWebSocket upgrade() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isEnded() {
    return false;
  }

  @Override
  public HttpServerRequest customFrameHandler(Handler<HttpFrame> handler) {
    throw new UnsupportedOperationException();
  }

  @Override
  public HttpConnection connection() {
    throw new UnsupportedOperationException();
  }

  @Override
  public HttpServerRequest streamPriorityHandler(Handler<StreamPriority> handler) {
    throw new UnsupportedOperationException();
  }
}
