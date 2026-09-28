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

# Copy the themis module structure
WORKDIR /app
COPY apps/core/themis/pom.xml apps/core/themis/
WORKDIR /app/apps/core/themis
RUN mvn dependency:go-offline -B -ntp

# Copy the source code using the correct path from project root
COPY apps/core/themis/src ./src

RUN mvn clean package -DskipTests -B -ntp

FROM eclipse-temurin:25-jre AS runtime
ARG TARGETARCH=amd64
WORKDIR /app
COPY --from=builder /app/apps/core/themis/target/*.jar app.jar

# kubectl for SHELL blueprint actions (e.g. ImageUpdateIntent)
RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl \
    && KUBECTL_VERSION="$(curl -fsSL https://dl.k8s.io/release/stable.txt)" \
    && curl -fsSL "https://dl.k8s.io/release/${KUBECTL_VERSION}/bin/linux/${TARGETARCH}/kubectl" \
         -o /usr/local/bin/kubectl \
    && chmod +x /usr/local/bin/kubectl \
    && kubectl version --client \
    && apt-get purge -y curl \
    && apt-get autoremove -y \
    && rm -rf /var/lib/apt/lists/*

EXPOSE 8080 50051

ARG SPRING_PROFILES_ACTIVE=prod
ENV SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE}

ENTRYPOINT ["java", "-jar", "app.jar"]
