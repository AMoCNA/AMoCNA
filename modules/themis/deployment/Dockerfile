# Build stage
FROM maven:3.9-eclipse-temurin-25 AS builder
WORKDIR /app

# Install daedalus dependency first
COPY modules/daedalus/pom.xml modules/daedalus/
COPY modules/daedalus/src modules/daedalus/src/
WORKDIR /app/modules/daedalus
RUN mvn clean install -DskipTests

# Copy the themis module structure
WORKDIR /app
COPY modules/themis/pom.xml modules/themis/
WORKDIR /app/modules/themis
RUN mvn dependency:go-offline

# Copy the source code using the correct path from project root
COPY modules/themis/src ./src

RUN mvn clean package -DskipTests

FROM eclipse-temurin:25-jre as runtime
WORKDIR /app
COPY --from=builder /app/modules/themis/target/themis-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080 50051

ENV SPRING_PROFILES_ACTIVE=dev
ENV SPRING_RABBITMQ_HOST=rabbitmq

ENTRYPOINT ["java", "-jar", "app.jar"]
