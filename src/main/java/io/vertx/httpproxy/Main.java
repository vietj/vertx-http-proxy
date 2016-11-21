package io.vertx.httpproxy;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import io.netty.channel.unix.Socket;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.net.impl.SocketAddressImpl;
import io.vertx.httpproxy.backend.Backend;
import io.vertx.httpproxy.backend.BackendProvider;
import io.vertx.httpproxy.backend.docker.DockerProvider;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Main {

  @Parameter(names = "--port")
  public int port = 8080;

  @Parameter(names = "--address")
  public String address = "0.0.0.0";

  public static void main(String[] args) {
    Main main = new Main();
    JCommander jc = new JCommander(main);
    jc.parse(args);
    main.run();
  }

  public void run() {
    Vertx vertx = Vertx.vertx();
//    DockerProvider backend = new DockerProvider(vertx);
    Backend backend = new Backend() {
      @Override
      public SocketAddress next() {
        return new SocketAddressImpl(8081, "96.126.115.136");
      }
    };
    HttpProxyOptions options = new HttpProxyOptions();
    options.getServerOptions().setPort(port);
    options.getServerOptions().setMaxInitialLineLength(10000);
    HttpProxy proxy = HttpProxy.createProxy(vertx, options);
    proxy.addBackend(request -> request.handle(backend));
//    backend.start(ar -> {}); // Should be done by proxy listen
    proxy.listen(ar -> {
      if (ar.succeeded()) {
        System.out.println("Proxy server started on " + port);
      } else {
        ar.cause().printStackTrace();
      }
    });
  }
}
