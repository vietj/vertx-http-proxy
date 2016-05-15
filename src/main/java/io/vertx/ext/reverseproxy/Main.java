package io.vertx.ext.reverseproxy;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import io.vertx.core.Vertx;
import io.vertx.ext.reverseproxy.impl.DockerBackend;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Main {

  @Parameter(names = "--port")
  public int port = 8080;

  public static void main(String[] args) {
    Main main = new Main();
    JCommander jc = new JCommander(main);
    jc.parse(args);
    main.run();
  }

  public void run() {
    Vertx vertx = Vertx.vertx();
    DockerBackend backend = new DockerBackend(vertx);
    HttpProxyOptions options = new HttpProxyOptions();
    options.getServerOptions().setPort(port);
    HttpProxy proxy = HttpProxy.createProxy(vertx, options);
    proxy.addBackend(backend);
    backend.start(ar -> {}); // Should be done by proxy listen
    proxy.listen(ar -> {
      if (ar.succeeded()) {
        System.out.println("Proxy server started on " + port);
      } else {
        ar.cause().printStackTrace();
      }
    });
  }
}
