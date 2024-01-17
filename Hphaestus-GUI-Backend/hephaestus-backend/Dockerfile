FROM openjdk:11
COPY ./target/hephaestus-backend-0.0.1-SNAPSHOT.jar app.jar
WORKDIR /opt
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app.jar"]