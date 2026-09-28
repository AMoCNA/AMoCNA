# Build stage
FROM maven:3.9-eclipse-temurin-25 AS builder
WORKDIR /app

# Copy the parent pom to allow Maven to resolve dependencies
COPY pom.xml .
RUN mvn install -N -B -ntp

# Install daedalus dependency first
COPY apps/core/daedalus/pom.xml apps/core/daedalus/
COPY apps/core/daedalus/src apps/core/daedalus/src/
WORKDIR /app/apps/core/daedalus
RUN mvn clean install -DskipTests -B -ntp

# Install common dependency
WORKDIR /app
COPY apps/core/common/pom.xml apps/core/common/
COPY apps/core/common/src apps/core/common/src/
WORKDIR /app/apps/core/common
RUN mvn clean install -DskipTests -B -ntp

# Copy the palamedes module structure
WORKDIR /app
COPY apps/core/palamedes/pom.xml apps/core/palamedes/
WORKDIR /app/apps/core/palamedes
RUN mvn dependency:go-offline -B -ntp

# Copy the source code using the correct path from project root
COPY apps/core/palamedes/src ./src

RUN mvn clean package -DskipTests -B -ntp

FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app
COPY --from=builder /app/apps/core/palamedes/target/*.jar app.jar

EXPOSE 8081

ARG SPRING_PROFILES_ACTIVE=prod
ENV SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE}

ENTRYPOINT ["java", "-jar", "app.jar"]
