# Vertx Reverse Proxy

An http reverse proxy for microservices based on Vert.x , right now it can discover Docker containers on the same host.

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
