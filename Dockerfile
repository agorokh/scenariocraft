# syntax=docker/dockerfile:1.7

FROM gradle:9.6.1-jdk21-alpine AS build
WORKDIR /workspace
COPY --chown=gradle:gradle \
    gradlew gradle.properties settings.gradle.kts build.gradle.kts ./
COPY --chown=gradle:gradle gradle ./gradle
COPY --chown=gradle:gradle renderer/build.gradle.kts ./renderer/build.gradle.kts
COPY --chown=gradle:gradle judge/build.gradle.kts ./judge/build.gradle.kts
RUN ./gradlew --version
COPY --chown=gradle:gradle src ./src
COPY --chown=gradle:gradle renderer/src ./renderer/src
COPY --chown=gradle:gradle judge/src ./judge/src
COPY --chown=gradle:gradle judge/personas.yml judge/rubric.md ./judge/
COPY --chown=gradle:gradle site ./site
RUN ./gradlew build :renderer:installDist --no-daemon

FROM alpine:3.23 AS plugin-installer
COPY --from=build /workspace/build/libs/ScenarioCraft-0.1.0-SNAPSHOT.jar /opt/scenariocraft/ScenarioCraft.jar
COPY demo/install-plugin.sh /usr/local/bin/install-plugin
ENTRYPOINT ["/usr/local/bin/install-plugin"]

FROM eclipse-temurin:21-jre-alpine AS judge
RUN addgroup -g 1000 scenariocraft \
    && adduser -D -u 1000 -G scenariocraft scenariocraft
COPY --from=build --chown=1000:1000 /workspace/judge/build/install/judge /opt/scenariocraft/judge
COPY --chown=1000:1000 judge/personas.yml judge/rubric.md /opt/scenariocraft/config/
COPY --chown=1000:1000 demo/judge-loop.sh /opt/scenariocraft/bin/judge-loop
USER 1000:1000
ENTRYPOINT ["/opt/scenariocraft/bin/judge-loop"]

FROM node:24-alpine AS proof-runner
RUN apk add --no-cache openjdk21-jre
WORKDIR /opt/scenariocraft/e2e
COPY e2e/round-driver/package.json e2e/round-driver/package-lock.json ./
RUN npm ci --omit=dev --ignore-scripts --no-audit --no-fund
COPY e2e/round-driver ./
COPY --from=build /workspace/renderer/build/install/renderer /opt/scenariocraft/renderer
USER node
ENTRYPOINT ["node", "/opt/scenariocraft/e2e/driver.mjs"]
