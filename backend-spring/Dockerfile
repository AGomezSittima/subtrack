# syntax=docker/dockerfile:1
FROM eclipse-temurin:25.0.5_8-jdk-noble AS build
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon --quiet dependencies > /dev/null
COPY src ./src
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon --quiet bootJar

FROM eclipse-temurin:25.0.5_8-jre-noble
# UID 10001 owns the SQLite volume; the FastAPI image and the migrate job use the same one.
RUN groupadd --system --gid 10001 subtrack \
    && useradd --system --uid 10001 --gid subtrack --no-create-home subtrack \
    && mkdir /data && chown subtrack:subtrack /data
COPY --from=build /src/build/libs/subtrack.jar /app/subtrack.jar
ENV DB_PATH=/data/subs.db
USER subtrack
EXPOSE 8000
# sqlite-jdbc loads its native library through System::load.
ENTRYPOINT ["java", "--enable-native-access=ALL-UNNAMED", "-jar", "/app/subtrack.jar"]
