# ==========================================
# STAGE 1: Build Java Components
# ==========================================
FROM maven:3.8.6-openjdk-11 AS java-builder
WORKDIR /app

# Business Demo components
COPY Business-Demo/ /app/Business-Demo/
COPY Controller/ /app/Controller/

# Build Metric Exporter (The actual 'business-demo' service)
RUN cd Business-Demo/MetricExporter && mvn clean package -DskipTests

# ==========================================
# STAGE 2: Final Runtime Image
# ==========================================
FROM adoptopenjdk/openjdk11
WORKDIR /app
COPY --from=java-builder /app/Business-Demo/MetricExporter/target/*.jar app.jar
EXPOSE 8099
ENTRYPOINT ["java","-jar","app.jar"]
