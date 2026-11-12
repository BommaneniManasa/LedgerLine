FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /app
COPY pom.xml .
COPY src ./src

RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre

RUN useradd --system --uid 10001 ledgerline

WORKDIR /app
COPY --chown=ledgerline:ledgerline --from=build /app/target/ledgerline-1.0.0.jar app.jar

USER ledgerline

EXPOSE 10000

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
