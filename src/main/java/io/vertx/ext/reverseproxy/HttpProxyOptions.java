package io.vertx.ext.reverseproxy;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@DataObject
public class HttpProxyOptions {

  private HttpClientOptions clientOptions = new HttpClientOptions();
  private HttpServerOptions serverOptions = new HttpServerOptions();

  public HttpProxyOptions() {
  }

  public HttpProxyOptions(JsonObject json) {
  }

  public HttpProxyOptions(HttpProxyOptions that) {
    clientOptions = new HttpClientOptions(that.clientOptions);
    serverOptions = new HttpServerOptions(that.serverOptions);
  }

  public HttpClientOptions getClientOptions() {
    return clientOptions;
  }

  public void setClientOptions(HttpClientOptions clientOptions) {
    this.clientOptions = clientOptions;
  }

  public HttpServerOptions getServerOptions() {
    return serverOptions;
  }

  public HttpProxyOptions setServerOptions(HttpServerOptions serverOptions) {
    this.serverOptions = serverOptions;
    return this;
  }
}
