# ---------- Stage 1: build the jar with Maven ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

COPY pom.xml .
COPY src ./src

# The Maven repository is kept in a build cache, so libraries are downloaded only once
# (and a build that failed on a slow network continues where it stopped).
RUN --mount=type=cache,target=/root/.m2 \
    mvn -q -B -DskipTests -Dmaven.wagon.http.retryHandler.count=5 package

# ---------- Stage 2: small runtime image ----------
FROM eclipse-temurin:17-jre
WORKDIR /app

RUN useradd --system --uid 1001 evoting
COPY --from=build /app/target/secure-evoting-1.0.0.jar app.jar
USER evoting

EXPOSE 8085
ENTRYPOINT ["java", "-jar", "app.jar"]
