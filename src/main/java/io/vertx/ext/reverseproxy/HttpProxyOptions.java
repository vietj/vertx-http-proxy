package io.vertx.ext.reverseproxy;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@DataObject
public class HttpProxyOptions {

  private HttpServerOptions serverOptions = new HttpServerOptions();

  public HttpProxyOptions() {
  }

  public HttpProxyOptions(JsonObject json) {
  }

  public HttpProxyOptions(HttpProxyOptions that) {
    serverOptions = new HttpServerOptions(that.serverOptions);
  }

  public HttpServerOptions getServerOptions() {
    return serverOptions;
  }

  public HttpProxyOptions setServerOptions(HttpServerOptions serverOptions) {
    this.serverOptions = serverOptions;
    return this;
  }
}
