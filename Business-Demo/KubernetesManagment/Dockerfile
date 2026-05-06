# ==========================================
# STAGE 1: Build Java Components
# ==========================================
FROM maven:3.8.6-openjdk-11 AS java-builder
WORKDIR /app

# Kubernetes Management components
COPY Business-Demo/ /app/Business-Demo/
COPY Controller/ /app/Controller/

# Build Kubernetes Management
RUN cd Business-Demo/KubernetesManagment && mvn clean package -DskipTests

# ==========================================
# STAGE 2: Final Runtime Image
# ==========================================
FROM adoptopenjdk/openjdk11
WORKDIR /app
COPY --from=java-builder /app/Business-Demo/KubernetesManagment/target/*.jar app.jar
EXPOSE 8097
ENTRYPOINT ["java","-jar","app.jar"]
