FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace
COPY blog-springboot/pom.xml ./pom.xml
RUN mvn -B -DskipTests dependency:go-offline
COPY blog-springboot/src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:25-jre

RUN addgroup --system spring && adduser --system --ingroup spring spring
WORKDIR /app
COPY --from=build /workspace/target/*.jar app.jar

EXPOSE 8090
USER spring
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
