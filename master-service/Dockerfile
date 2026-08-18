FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn/ .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw

COPY common/pom.xml common/pom.xml
COPY master-service/pom.xml master-service/pom.xml
COPY crawler-worker/pom.xml crawler-worker/pom.xml
RUN ./mvnw dependency:go-offline -B -pl master-service -am

COPY common/src common/src
COPY master-service/src master-service/src
RUN ./mvnw clean package -DskipTests -pl master-service -am

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/master-service/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
EXPOSE 8080