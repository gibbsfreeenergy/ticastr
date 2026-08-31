FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace
COPY blog-springboot/pom.xml ./pom.xml
RUN mvn -B -DskipTests dependency:go-offline
COPY blog-springboot/src ./src
RUN mvn -B -DskipTests package
RUN jar tf target/*.jar | grep -q 'BOOT-INF/lib/spring-data-redis-' \
    && jar tf target/*.jar | grep -q 'BOOT-INF/lib/lettuce-core-' \
    || (echo 'Redis-enabled runtime contract failed: client libraries are missing from the executable JAR' >&2 && exit 1)

FROM eclipse-temurin:21-jre

RUN apt-get update \
    && apt-get install -y --no-install-recommends wget \
    && rm -rf /var/lib/apt/lists/*

RUN addgroup --system spring && adduser --system --ingroup spring spring
RUN mkdir -p /data/uploads /data/search && chown -R spring:spring /data
WORKDIR /app
COPY --from=build /workspace/target/*.jar app.jar

EXPOSE 8090
USER spring
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 CMD wget -q -O - http://localhost:8090/actuator/health | grep -q '"status":"UP"'
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
