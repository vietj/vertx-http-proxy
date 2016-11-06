# Vert.x Http Proxy

A dynamic HTTP reverse proxy for microservices based on Vert.x , right now it can discover Docker containers on the same host.

Usage : _package_ and then run the _fatjar_ :

````
> mvn package
> java -jar target/vertx-reverse-proxy-3.3.0-SNAPSHOT.jar
````

Docker containers are discovered using docker labels, for example:

````
docker run --rm -p 8082:8080 -l service.type=http.endpoint -l service.route=/hello ehazlett/docker-demo
````

Then backend server will be accessible on the proxy server under the `/hello` route.


## Behavior notes

The client posts a body and the backend closes the connection before upload is complete.
The client gets a `502` response and its connection is closed.

The client posts a body and the backend receives parts of the body then the client closes the connectino before
the upload is complete. The backend gets its connection closed.
