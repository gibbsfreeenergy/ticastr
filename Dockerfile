FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace
COPY blog-springboot/pom.xml ./pom.xml
RUN mvn -B -DskipTests dependency:go-offline
COPY blog-springboot/src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre

RUN addgroup --system spring && adduser --system --ingroup spring spring
WORKDIR /app
COPY --from=build /workspace/target/*.jar app.jar

EXPOSE 8090
USER spring
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 CMD wget -q -O - http://localhost:8090/actuator/health | grep -q '"status":"UP"'
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
