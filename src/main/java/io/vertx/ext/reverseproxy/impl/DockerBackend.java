package io.vertx.ext.reverseproxy.impl;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.ContainerPort;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.ext.reverseproxy.Backend;
import io.vertx.ext.reverseproxy.ProxyRequest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class DockerBackend implements Backend {

  private final Vertx vertx;
  private List<Server> servers = new ArrayList<>();
  private Map<String, Server> serverMap = new HashMap<>();
  private DockerClient client;
  private boolean started;

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

  public DockerBackend(Vertx vertx) {
    this.vertx = vertx;
  }

  @Override
  public synchronized void start(Handler<AsyncResult<Void>> doneHandler) {
    if (started) {
      throw new IllegalStateException("Already started");
    }
    DockerClientConfig.DockerClientConfigBuilder builder = DockerClientConfig.createDefaultConfigBuilder();
    builder.withDockerTlsVerify(false);
    DockerClientConfig config = builder.build();
    client = DockerClientBuilder.getInstance(config).build();
    started = true;
    vertx.runOnContext(v1 -> {
      refresh(v2 -> doneHandler.handle(Future.succeededFuture()));
    });
  }

  @Override
  public synchronized void stop(Handler<AsyncResult<Void>> doneHandler) {
    if (started) {
      started = false;
      DockerClient c = client;
      client = null;
      vertx.executeBlocking(fut -> {
        try {
          c.close();
          fut.complete();
        } catch (IOException ignore) {
          fut.fail(ignore);
        }
      }, ar -> doneHandler.handle(Future.succeededFuture()));
    } else {
      doneHandler.handle(Future.succeededFuture());
    }
  }

  private void refresh(Handler<Void> doneHandler) {
    vertx.<List<Container>>executeBlocking(
        future -> {
          try {
            future.complete(client.listContainersCmd().withStatusFilter("running").exec());
          } catch (Exception e) {
            future.fail(e);
          }
        }, ar -> {
          if (ar.succeeded()) {
            List<Container> running = ar.result();
            Map<String, Server> newServerMap = new HashMap<>();
            running.stream()
                .forEach(container -> {
                  Map<String, String> labels = container.getLabels();
                  if ("http.endpoint".equals(labels.get("service.type"))) {
                    String route = labels.get("service.route");
                    String id = container.getId();
                    if (route != null) {
                      Server server = serverMap.get(id);
                      if  (server == null) {
                        ContainerPort port = container.getPorts()[0];
                        server = new Server(id, route, port.getIp(), port.getPublicPort());
                        System.out.println("Discovery backend server " + server.id + " " + server.address + ":" + server.port);
                      }
                      newServerMap.put(id, server);
                    }
                  }
                });
            serverMap.values().stream().
                filter(server -> !newServerMap.containsKey(server.id)).
                forEach(server -> {
                  System.out.println("Closing backend server " + server.id + " " + server.address + ":" + server.port);
                  server.client.close();
                });
            serverMap.clear();
            serverMap.putAll(newServerMap);
            synchronized (DockerBackend.this) {
              this.servers = new ArrayList<>(serverMap.values());
              if (started) {
                vertx.runOnContext(v1 -> refresh(v2 -> {}));
              }
            }
          }
          doneHandler.handle(null);
        }
    );
  }

  @Override
  public void handle(ProxyRequest request) {
    synchronized (this) {
      for (Server server : servers) {
        if (request.frontRequest().path().startsWith(server.route)) {
          HttpClientOptions options = new HttpClientOptions();
          options.setDefaultHost(server.address);
          options.setDefaultPort(server.port);
          HttpClient client = vertx.createHttpClient(options);
          request.pass(client);
          return;
        }
      }
    }
    request.next();
  }
}
