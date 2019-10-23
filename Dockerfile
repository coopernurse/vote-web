FROM maven:3.6.2-jdk-11-slim AS BUILD_IMAGE
ENV APP_HOME=/root/dev/myapp/
RUN mkdir -p $APP_HOME/src/java
WORKDIR $APP_HOME
COPY pom.xml $APP_HOME
RUN mvn compile
COPY src $APP_HOME/src
RUN mvn package

FROM openjdk:14-jdk-alpine
WORKDIR /root/
COPY --from=BUILD_IMAGE /root/dev/myapp/target/vote-1.0-SNAPSHOT-jar-with-dependencies.jar app.jar
EXPOSE 8080
CMD ["java", "-Xmx100m", "-cp", "app.jar", "voteweb.MainKt"]