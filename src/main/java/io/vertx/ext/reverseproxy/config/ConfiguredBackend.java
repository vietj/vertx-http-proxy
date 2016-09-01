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

package io.vertx.ext.reverseproxy.config;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.reverseproxy.backend.BackendProvider;
import io.vertx.ext.reverseproxy.backend.docker.DockerProvider;
import io.vertx.ext.reverseproxy.backend.file.FileProvider;
import io.vertx.ext.reverseproxy.backend.proxyto.ProxyToProvider;

import java.util.HashMap;
import java.util.Map;

/**
 * @author eric.wittmann@gmail.com
 */
@DataObject
public class ConfiguredBackend {
    
    @Nullable
    private String name;
    private BackendType type;
    @Nullable
    private String customClass;
    @Nullable
    private Map<String, Object> config = new HashMap<>();

    public ConfiguredBackend() {
    }

    public ConfiguredBackend(JsonObject json) {
    }

    public ConfiguredBackend(HttpProxyOptions that) {
    }

    public ConfiguredBackend(BackendType type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public ConfiguredBackend setName(String name) {
        this.name = name;
        return this;
    }

    public BackendType getType() {
        return type;
    }

    public ConfiguredBackend setType(BackendType type) {
        this.type = type;
        return this;
    }

    public String getCustomClass() {
        return customClass;
    }

    public ConfiguredBackend setCustomClass(String customClass) {
        this.customClass = customClass;
        return this;
    }

    public Map<String, Object> getConfig() {
        return config;
    }

    public ConfiguredBackend setConfig(Map<String, Object> config) {
        this.config = config;
        return this;
    }

    public BackendProvider create(Vertx vertx) {
        if (type == BackendType.proxyTo) {
            return new ProxyToProvider();
        }
        if (type == BackendType.docker) {
            return new DockerProvider(vertx);
        }
        if (type == BackendType.file) {
            return new FileProvider(vertx, config);
        }
        if (type == BackendType.custom) {
            // TODO support custom back-ends
        }
        return null;
    }

}
