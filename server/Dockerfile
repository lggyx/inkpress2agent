FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY maven-settings.xml .
COPY pom.xml .
RUN mvn -q -B -s maven-settings.xml dependency:go-offline
COPY src ./src
RUN mvn -q -B -s maven-settings.xml -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
ENV TZ=Asia/Shanghai
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
