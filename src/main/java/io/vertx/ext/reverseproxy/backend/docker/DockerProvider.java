package io.vertx.ext.reverseproxy.backend.docker;

import com.github.dockerjava.core.DockerClientConfig;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.impl.SocketAddressImpl;
import io.vertx.ext.reverseproxy.backend.BackendProvider;
import io.vertx.ext.reverseproxy.ProxyRequest;
import io.vertx.servicediscovery.Record;
import io.vertx.servicediscovery.docker.DockerDiscoveryBridge;
import io.vertx.servicediscovery.spi.ServiceDiscovery;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class DockerProvider implements BackendProvider, ServiceDiscovery {

  private final Vertx vertx;
  private List<Server> servers = new ArrayList<>();
  private Map<String, Server> serverMap = new HashMap<>();
  private boolean started;
  private DockerDiscoveryBridge bridge;

  private class Server {

    final String id;
    final String route;
    final String address;
    final int port;
    final HttpClient client;

    public Server(String id, String route, String address, int port) {
      this.id = id;
      this.route = route;
      this.address = address;
      this.port = port;
      this.client = vertx.createHttpClient(new HttpClientOptions().setDefaultHost(address).setDefaultPort(port));
    }
  }

  public DockerProvider(Vertx vertx) {
    this.vertx = vertx;
  }

  @Override
  public synchronized void start(Handler<AsyncResult<Void>> doneHandler) {
    if (started) {
      throw new IllegalStateException("Already started");
    }
    DockerClientConfig.DockerClientConfigBuilder builder = DockerClientConfig.createDefaultConfigBuilder();
    builder.withDockerTlsVerify(false);
    started = true;
    bridge = new DockerDiscoveryBridge();
    bridge.start(vertx, new ServiceDiscovery() {
      @Override
      public void publish(Record record, Handler<AsyncResult<Record>> resultHandler) {
        String id = record.getMetadata().getString("docker.id");
        if ("http.endpoint".equals(record.getMetadata().getString("service.type"))) {
          String route = record.getMetadata().getString("service.route");
          if (route != null) {
            Server server = serverMap.get(id);
            if  (server == null) {
              int port = record.getLocation().getInteger("port");
              String ip = record.getLocation().getString("ip");;
              server = new Server(id, route, ip, port);
              System.out.println("Discovery backend server " + server.id + " " + server.address + ":" + server.port);
              serverMap.put(id, server);
              synchronized (DockerProvider.this) {
                servers = new ArrayList<>(serverMap.values());
              }
            }
          }
        }
        record.setRegistration(id);
        resultHandler.handle(Future.succeededFuture(record));
      }

      @Override
      public void unpublish(String id, Handler<AsyncResult<Void>> resultHandler) {
        Server server = serverMap.remove(id);
        if (server != null) {
          System.out.println("unpublished " + id);
          server.client.close();
          synchronized (DockerProvider.this) {
            servers = new ArrayList<>(serverMap.values());
          }
        }
        resultHandler.handle(Future.succeededFuture());
      }
    }, new JsonObject().put("docker-tls-verify", false), Future.future());
  }

  @Override
  public synchronized void stop(Handler<AsyncResult<Void>> doneHandler) {
    if (started) {
      started = false;
    } else {
      doneHandler.handle(Future.succeededFuture());
    }
  }

  @Override
  public void handle(ProxyRequest request) {
    synchronized (this) {
      for (Server server : servers) {
        if (request.frontRequest().path().startsWith(server.route)) {
          request.handle(() -> new SocketAddressImpl(server.port, server.address));
          return;
        }
      }
    }
    request.next();
  }

  @Override
  public void publish(Record record, Handler<AsyncResult<Record>> resultHandler) {

  }

  @Override
  public void unpublish(String id, Handler<AsyncResult<Void>> resultHandler) {

  }
}
