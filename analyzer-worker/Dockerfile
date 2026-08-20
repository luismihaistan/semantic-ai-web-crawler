FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn/ .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw

COPY common/pom.xml common/pom.xml
COPY master-service/pom.xml master-service/pom.xml
COPY crawler-worker/pom.xml crawler-worker/pom.xml
COPY embedding-worker/pom.xml embedding-worker/pom.xml
COPY analyzer-worker/pom.xml analyzer-worker/pom.xml
RUN ./mvnw dependency:go-offline -B -pl analyzer-worker -am

COPY common/src common/src
COPY analyzer-worker/src analyzer-worker/src
RUN ./mvnw clean package -DskipTests -pl analyzer-worker -am

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/analyzer-worker/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]