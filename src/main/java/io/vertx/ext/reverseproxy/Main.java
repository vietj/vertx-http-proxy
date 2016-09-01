package io.vertx.ext.reverseproxy;

import io.vertx.core.Vertx;
import io.vertx.ext.reverseproxy.backend.BackendProvider;
import io.vertx.ext.reverseproxy.config.BackendType;
import io.vertx.ext.reverseproxy.config.ConfiguredBackend;
import io.vertx.ext.reverseproxy.config.HttpProxyOptions;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Main {

  @Parameter(names = "--port")
  public int port = 8080;
  @Parameter(names = "--config")
  public String config;

  public static void main(String[] args) {
    Main main = new Main();
    JCommander jc = new JCommander(main);
    jc.parse(args);
    main.run();
  }

  public void run() {
    final Vertx vertx = Vertx.vertx();
    
    final HttpProxyOptions options = getOptions();
    options.getServerOptions().setPort(port);
    
    final HttpProxy proxy = HttpProxy.createProxy(vertx, options);
    final List<ConfiguredBackend> backends = options.getBackends();
    proxy.listen(ar -> {
      if (ar.succeeded()) {
        System.out.println("Proxy server started on " + port);

        for (ConfiguredBackend configuredBackend : backends) {
            BackendProvider backend = configuredBackend.create(vertx);
            backend.start(h -> {});
            proxy.addBackend(backend);
        }

      } else {
        ar.cause().printStackTrace();
      }
    });
  }

  private HttpProxyOptions getOptions() {
    HttpProxyOptions options = new HttpProxyOptions();
    if (config != null) {
        ObjectMapper mapper = new ObjectMapper();
        File configFile = new File(config);
        if (configFile.isFile()) {
            try {
                options = mapper.readerFor(HttpProxyOptions.class).readValue(configFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    } else {
        options.setBackends(new ArrayList<>());
        options.getBackends().add(new ConfiguredBackend(BackendType.proxyTo));
        options.getBackends().add(new ConfiguredBackend(BackendType.docker));
    }
    return options;
  }
}
