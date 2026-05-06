# ==========================================
# STAGE 1: Build Java Components
# ==========================================
FROM maven:3.8.6-openjdk-11 AS java-builder
WORKDIR /app

# 1. Build Translator (Local dependency)
COPY Metrics-Translator/ /app/Metrics-Translator/
RUN cd Metrics-Translator/Translator && mvn clean install -DskipTests

# 2. Build Metrics Adapter
COPY Metrics-Adapter/ /app/Metrics-Adapter/
RUN cd Metrics-Adapter && mvn clean package -DskipTests

# ==========================================
# STAGE 2: Final Runtime Image
# ==========================================
FROM adoptopenjdk/openjdk11
WORKDIR /app
COPY --from=java-builder /app/Metrics-Adapter/target/*.jar app.jar
EXPOSE 8085
ENTRYPOINT ["java","-jar","app.jar"]
