# Stage 1: build
# Base images are immutable; update each tag and digest together in one review.
FROM registry.access.redhat.com/ubi9/openjdk-21:1.24@sha256:a7a263e496bdc62bc7b9e8704d7e9ed5fd391418927fd505f56c8ba8c2e7197a AS build

USER root
WORKDIR /build

RUN microdnf install -y gzip tar && microdnf clean all

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline -q

COPY src/ src/
# PostgreSQL-backed tests run in CI, where a container runtime is available.
# Railway's BuildKit builder packages the already-gated source without Docker-in-Docker.
RUN ./mvnw package -q -DskipTests

# Stage 2: runtime
FROM registry.access.redhat.com/ubi9/openjdk-21-runtime:1.24@sha256:b8be612ac873a91d2a9e05937453358e719b155dc0304c070ffe0e94cd6aa5e2

ENV LANGUAGE='en_US:en'

COPY --chown=185 --from=build /build/target/quarkus-app/lib/ /deployments/lib/
COPY --chown=185 --from=build /build/target/quarkus-app/*.jar /deployments/
COPY --chown=185 --from=build /build/target/quarkus-app/app/ /deployments/app/
COPY --chown=185 --from=build /build/target/quarkus-app/quarkus/ /deployments/quarkus/

EXPOSE 8080
USER 185
ENV JAVA_OPTS_APPEND="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"
ENV JAVA_APP_JAR="/deployments/quarkus-run.jar"

ENTRYPOINT [ "/opt/jboss/container/java/run/run-java.sh" ]
