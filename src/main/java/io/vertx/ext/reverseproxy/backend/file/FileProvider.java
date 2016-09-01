/*
 * Copyright 2016 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.vertx.ext.reverseproxy.backend.file;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileProps;
import io.vertx.core.net.impl.SocketAddressImpl;
import io.vertx.ext.reverseproxy.ProxyRequest;
import io.vertx.ext.reverseproxy.backend.BackendProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @author eric.wittmann@gmail.com
 */
public class FileProvider implements BackendProvider {

    private final Vertx vertx;
    private String configFilePath;
    private long configFileRefresh;
    private List<Server> servers = new ArrayList<>();
    private Long cfLastModified;

    /**
     * Constructor.
     * 
     * @param vertx
     * @param config
     */
    public FileProvider(Vertx vertx, Map<String, Object> config) {
        this.vertx = vertx;
        this.configFilePath = String.valueOf(config.get("file"));
        this.configFileRefresh = 15000;
        if (config.containsKey("refresh")) {
            this.configFileRefresh = ((Number) config.get("refresh")).longValue();
        }
    }

    /**
     * @see io.vertx.ext.reverseproxy.backend.BackendProvider#start(io.vertx.core.Handler)
     */
    @Override
    public void start(Handler<AsyncResult<Void>> doneHandler) {
        refresh();
        vertx.setPeriodic(configFileRefresh, v -> {
            refresh();
        });
        System.out.println("\tWatching file '" + configFilePath + "' for proxy configuration changes.");
        BackendProvider.super.start(doneHandler);
    }

    /**
     * Refreshes the list of back-end servers to proxy to.
     */
    protected void refresh() {
        vertx.fileSystem().props(configFilePath, propsAr -> {
            FileProps props = propsAr.result();
            final Long lmt = props.lastModifiedTime();
            if (cfLastModified == null || cfLastModified.longValue() < lmt.longValue()) {
                System.out.println("\tLoading servers from: " + this.configFilePath);
                vertx.fileSystem().readFile(this.configFilePath, bufferAr -> {
                    if (bufferAr.failed()) {
                        bufferAr.cause().printStackTrace();
                        return;
                    }
                    final Buffer buffer = bufferAr.result();
                    ObjectMapper mapper = new ObjectMapper();
                    try {
                        Servers servers = mapper.readerFor(Servers.class).readValue(buffer.getBytes());
                        if (!FileProvider.this.servers.equals(servers.getServers())) {
                            FileProvider.this.servers = servers.getServers();
                            System.out.println("\tLoaded " + servers.getServers().size() + " servers from: " + this.configFilePath);
                        }
                        cfLastModified = lmt;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }
        });
    }

    /**
     * @see io.vertx.ext.reverseproxy.backend.BackendProvider#handle(io.vertx.ext.reverseproxy.ProxyRequest)
     */
    @Override
    public void handle(ProxyRequest request) {
        List<Server> servers = this.servers;
        for (Server server : servers) {
            if (request.frontRequest().path().startsWith(server.route)) {
                request.handle(() -> new SocketAddressImpl(server.port, server.address));
                return;
            }
        }
        request.next();
    }
    
    static class Servers {
        private List<Server> servers = new ArrayList<>();
        
        public Servers() {
        }

        public List<Server> getServers() {
            return servers;
        }

        public void setServers(List<Server> servers) {
            this.servers = servers;
        }
    }

    static class Server {

        private String id;
        private String route;
        private String address;
        private int port;

        public Server() {
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getRoute() {
            return route;
        }

        public void setRoute(String route) {
            this.route = route;
        }

        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }
    }

}
