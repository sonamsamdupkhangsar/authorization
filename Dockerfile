# syntax=docker/dockerfile:experimental
FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /workspace/app

COPY . /workspace/app

RUN --mount=type=secret,id=USERNAME --mount=type=secret,id=PERSONAL_ACCESS_TOKEN --mount=type=cache,target=/root/.gradle\
    export USERNAME=$(cat /run/secrets/USERNAME)\
    export PERSONAL_ACCESS_TOKEN=$(cat /run/secrets/PERSONAL_ACCESS_TOKEN) &&\
     ./gradlew clean build
RUN  mkdir -p build/dependency && (cd build/dependency; jar -xf ../libs/authorization-1.0.jar)

FROM eclipse-temurin:25-jdk-alpine
VOLUME /tmp
ARG DEPENDENCY=/workspace/app/build/dependency

COPY --from=build ${DEPENDENCY}/BOOT-INF/lib /app/lib
COPY --from=build ${DEPENDENCY}/META-INF /app/META-INF
COPY --from=build ${DEPENDENCY}/BOOT-INF/classes /app
COPY --from=build /workspace/app/newrelic/newrelic.jar /app/newrelic/newrelic.jar
COPY --from=build /workspace/app/newrelic/newrelic.yml /app/newrelic/newrelic.yml

ENTRYPOINT ["java", "-javaagent:app/newrelic/newrelic.jar","-cp","app:app/lib/*","me.sonam.auth.DefaultAuthorizationServerApplication"]

LABEL org.opencontainers.image.source https://github.com/sonamsamdupkhangsar/authorization
