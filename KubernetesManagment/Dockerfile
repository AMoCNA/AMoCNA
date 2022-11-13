FROM openjdk:11
COPY ./target/kubernetes-management-0.0.1-SNAPSHOT.jar app.jar
WORKDIR /opt
EXPOSE 8097
ENTRYPOINT ["java","-jar","/app.jar"]