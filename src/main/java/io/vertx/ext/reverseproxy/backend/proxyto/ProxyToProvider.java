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

package io.vertx.ext.reverseproxy.backend.proxyto;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.net.impl.SocketAddressImpl;
import io.vertx.ext.reverseproxy.ProxyRequest;
import io.vertx.ext.reverseproxy.backend.BackendProvider;

/**
 * @author eric.wittmann@gmail.com
 */
public class ProxyToProvider implements BackendProvider {

    /**
     * Constructor.
     */
    public ProxyToProvider() {
    }
    
    /**
     * @see io.vertx.ext.reverseproxy.backend.BackendProvider#start(io.vertx.core.Handler)
     */
    @Override
    public void start(Handler<AsyncResult<Void>> doneHandler) {
        BackendProvider.super.start(doneHandler);
        System.out.println("\tAccepting requests containing an X-Proxy-To http header, format 'HOST:PORT' (e.g. 'localhost:8181').");
    }

    /**
     * @see io.vertx.ext.reverseproxy.backend.BackendProvider#handle(io.vertx.ext.reverseproxy.ProxyRequest)
     */
    @Override
    public void handle(ProxyRequest request) {
        String proxyToHeader = request.frontRequest().getHeader("X-Proxy-To");
        if (proxyToHeader != null) {
            request.frontRequest().headers().remove("X-Proxy-To");
            request.handle(() -> headerToSockAddress(proxyToHeader));
        } else {
            request.next();
        }
    }

    /**
     * Parse the X-Proxy-To header and create a {@link SocketAddressImpl} from the information
     * found.
     * @param proxyToHeader value of the X-Proxy-To header.  Format:  HOST:PORT
     */
    private SocketAddressImpl headerToSockAddress(String proxyToHeader) {
        String[] split = proxyToHeader.split(":");
        String host = split[0];
        int port = 80;
        if (split.length > 1) {
            port = Integer.parseInt(split[1]);
        }
        return new SocketAddressImpl(port, host);
    }

}
