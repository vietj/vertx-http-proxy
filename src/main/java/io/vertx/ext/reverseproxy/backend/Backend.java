package io.vertx.ext.reverseproxy.backend;

import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.net.SocketAddress;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@VertxGen
public interface Backend {

  SocketAddress next();

}
