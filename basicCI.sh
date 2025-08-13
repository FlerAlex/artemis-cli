#!/bin/bash

export PATH=/usr/local/bin:/opt/homebrew/bin:$PATH

# Clean and compile
sbt clean compile

# Run tests
#sbt test

# Create assembly JAR (if needed for deployment)
sbt assembly

docker exec -it client1 mkdir -p /app/config
docker exec -it client2 mkdir -p /app/config
docker cp target/scala-3.6.3/artemis-cli-0.2.0-SNAPSHOT.jar client1:/app
docker cp target/scala-3.6.3/artemis-cli-0.2.0-SNAPSHOT.jar client2:/app
docker cp logback.xml client1:/app/config
docker cp logback.xml client2:/app/config
docker cp artemis-cli.sh client1:/app/artemis-cli
docker cp artemis-cli.sh client2:/app/artemis-cli
docker cp application.conf client2:/app/config
docker cp application.conf client1:/app/config
