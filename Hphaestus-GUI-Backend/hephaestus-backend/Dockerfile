# ==========================================
# STAGE 1: Build Angular Frontend
# ==========================================
FROM node:16-alpine AS gui-builder
WORKDIR /app/gui
COPY Hphaestus-GUI/package*.json ./
RUN npm install
COPY Hphaestus-GUI/ ./
RUN npm run build -- --base-href /app/

# ==========================================
# STAGE 2: Build Java Backend
# ==========================================
FROM maven:3.8.6-openjdk-11 AS java-builder
WORKDIR /app

# 1. Build Translator (Local dependency)
COPY Metrics-Translator/ /app/Metrics-Translator/
RUN cd Metrics-Translator/Translator && mvn clean install -DskipTests

# 2. Prepare GUI Backend
COPY Hphaestus-GUI-Backend/ /app/Hphaestus-GUI-Backend/
# Copy built Angular files from Stage 1
COPY --from=gui-builder /app/gui/dist/hephaestus-gui/* /app/Hphaestus-GUI-Backend/hephaestus-backend/src/main/resources/static/app/

# Build GUI Backend
RUN cd Hphaestus-GUI-Backend/hephaestus-backend && mvn clean package -DskipTests

# ==========================================
# STAGE 3: Final Runtime Image
# ==========================================
FROM adoptopenjdk/openjdk11
WORKDIR /app
COPY --from=java-builder /app/Hphaestus-GUI-Backend/hephaestus-backend/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]
