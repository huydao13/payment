FROM maven:3.9-eclipse-temurin-17 AS build
ARG MODULE
WORKDIR /app

COPY pom.xml .
COPY order-service/pom.xml order-service/
COPY payment-service/pom.xml payment-service/
COPY inventory-service/pom.xml inventory-service/
COPY saga-orchestrator/pom.xml saga-orchestrator/

COPY ${MODULE}/src ${MODULE}/src

RUN mvn -q -pl ${MODULE} -am clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
ARG MODULE
WORKDIR /app
COPY --from=build /app/${MODULE}/target/*.jar app.jar
ENTRYPOINT ["java","-jar","/app/app.jar"]
