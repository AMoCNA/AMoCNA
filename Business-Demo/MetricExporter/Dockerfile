FROM openjdk:11
COPY ./target/business-demo-0.0.1-SNAPSHOT.jar app.jar
WORKDIR /opt
EXPOSE 8099
ENTRYPOINT ["java","-jar","/app.jar"]