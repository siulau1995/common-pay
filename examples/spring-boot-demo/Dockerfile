FROM maven:3.9.9-eclipse-temurin-8 AS build
WORKDIR /workspace

COPY pom.xml ./pom.xml
COPY src ./src
RUN mvn --batch-mode --no-transfer-progress -DskipTests install

COPY examples/spring-boot-demo ./examples/spring-boot-demo
RUN mvn --batch-mode --no-transfer-progress -f examples/spring-boot-demo/pom.xml package

FROM eclipse-temurin:8-jre
WORKDIR /app
COPY --from=build /workspace/examples/spring-boot-demo/target/common-pay-spring-boot-demo-0.2.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
