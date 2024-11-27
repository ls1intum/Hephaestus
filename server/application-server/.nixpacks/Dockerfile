FROM ghcr.io/railwayapp/nixpacks:ubuntu-1722297819

ENTRYPOINT ["/bin/bash", "-l", "-c"]
WORKDIR /app/


COPY .nixpacks/nixpkgs-59dc10b5a6f2a592af36375c68fda41246794b86.nix .nixpacks/nixpkgs-59dc10b5a6f2a592af36375c68fda41246794b86.nix
RUN nix-env -if .nixpacks/nixpkgs-59dc10b5a6f2a592af36375c68fda41246794b86.nix && nix-collect-garbage -d
RUN sudo apt-get update && sudo apt-get install -y --no-install-recommends wget

ARG NIXPACKS_JDK_VERSION=21
ARG NIXPACKS_METADATA=java

ENV NIXPACKS_JDK_VERSION=$NIXPACKS_JDK_VERSION NIXPACKS_METADATA=$NIXPACKS_METADATA

# setup phase
# noop

# build phase
COPY . /app/.
RUN --mount=type=cache,id=xE0e05wx0-m2/repository,target=/app/.m2/repository chmod +x ./mvnw
RUN --mount=type=cache,id=xE0e05wx0-m2/repository,target=/app/.m2/repository ./mvnw -DskipTests clean package




# start
COPY . /app
CMD ["java -Dserver.port=$PORT $JAVA_OPTS -jar target/*jar"]

