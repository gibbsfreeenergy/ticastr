FROM eclipse-temurin:21-jre

RUN apt-get update \
    && apt-get install -y --no-install-recommends wget \
    && rm -rf /var/lib/apt/lists/*

RUN addgroup --system spring && adduser --system --ingroup spring spring
RUN mkdir -p /data/uploads /data/search && chown -R spring:spring /data
WORKDIR /app
COPY deploy/backend/app.jar /app/app.jar

EXPOSE 8090
USER spring
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 CMD wget -q -O - http://localhost:8090/actuator/health | grep -q '"status":"UP"'
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
